package com.vidgravity.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.effect.*
import androidx.media3.transformer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal object NativeStudioRenderer {
    fun videoEffects(project: StudioProject, clip: StudioClip, resize: Boolean = true): List<Effect> {
        val effects = mutableListOf<Effect>()
        when (project.filter) {
            StudioFilter.MONO -> effects += RgbFilter.createGrayscaleFilter()
            StudioFilter.INVERT -> effects += RgbFilter.createInvertedFilter()
            else -> Unit
        }
        if (project.overlay.isNotBlank()) {
            val text = SpannableString(project.overlay).apply {
                setSpan(ForegroundColorSpan(android.graphics.Color.WHITE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(AbsoluteSizeSpan(42, true), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            effects += OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(text)))
        }
        if (project.fadeEdges) effects += RgbMatrix { timeUs, _ ->
            val gain = StudioFade.gain(timeUs, clip.durationMs * 1000)
            floatArrayOf(gain,0f,0f,0f, 0f,gain,0f,0f, 0f,0f,gain,0f, 0f,0f,0f,1f)
        }
        if (resize) effects += Presentation.createForHeight(project.resolution.height)
        return effects
    }

    private fun audioEffects(volume: Float) = listOf<AudioProcessor>(StudioVolumeProcessor(volume))

    fun composition(project: StudioProject): Composition {
        project.validate()
        val clips = project.clips.map { clip ->
            val source = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs).build()).build()
            EditedMediaItem.Builder(source).setRemoveAudio(project.originalVolume == 0f)
                .setEffects(Effects(audioEffects(project.originalVolume), videoEffects(project, clip))).build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(clips).build())
        fun addAudio(uri: String?, volume: Float, loop: Boolean) {
            if (uri == null || volume == 0f) return
            val audio = MediaItem.Builder().setUri(uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(project.durationMs).build()).build()
            val item = EditedMediaItem.Builder(audio).setRemoveVideo(true)
                .setEffects(Effects(audioEffects(volume), emptyList())).build()
            sequences += EditedMediaItemSequence.Builder(item).setIsLooping(loop).build()
        }
        addAudio(project.musicUri, project.musicVolume, true)
        addAudio(project.voiceUri, project.voiceVolume, false)
        return Composition.Builder(sequences)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
    }

    suspend fun render(context: Context, project: StudioProject, output: File, onProgress: (Int?) -> Unit) {
        project.validate()
        var transformer: Transformer? = null
        val handler = Handler(Looper.getMainLooper())
        try {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    transformer = Transformer.Builder(context.applicationContext)
                        .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setPortraitEncodingEnabled(true)
                        .setEncoderFactory(DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                handler.removeCallbacksAndMessages(null)
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                handler.removeCallbacksAndMessages(null)
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(
                                    "Device export failed (code ${exportException.errorCode}). Try 720p or another H.264 source."))
                            }
                        }).build()
                    try {
                        transformer!!.start(composition(project), output.absolutePath)
                        val poll = object : Runnable {
                            override fun run() {
                                if (!continuation.isActive) return
                                val progress = ProgressHolder()
                                val state = transformer!!.getProgress(progress)
                                onProgress(if (state == Transformer.PROGRESS_STATE_AVAILABLE) progress.progress.coerceIn(0, 99) else null)
                                handler.postDelayed(this, 500)
                            }
                        }
                        handler.post(poll)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                    continuation.invokeOnCancellation { handler.post { transformer?.cancel() } }
                }
            }
            check(output.isFile && output.length() > 0L) { "The encoder returned an empty file." }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                handler.removeCallbacksAndMessages(null)
                transformer?.cancel()
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class StudioVolumeProcessor(private val gain: Float) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 4) output.putFloat((inputBuffer.float * gain).coerceIn(-1f, 1f))
        } else {
            while (inputBuffer.remaining() >= 2) output.putShort((inputBuffer.short * gain).toInt().coerceIn(-32768, 32767).toShort())
        }
        output.flip()
    }
}
