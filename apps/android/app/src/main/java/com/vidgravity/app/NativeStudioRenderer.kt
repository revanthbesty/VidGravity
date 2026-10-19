package com.vidgravity.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.effect.*
import androidx.media3.transformer.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val renderMutex=Mutex()
    fun videoEffects(project: StudioProject, clip: StudioClip, resize: Boolean = true, context: Context? = null, sourceFrameRate: Float? = null): List<Effect> {
        val effects = mutableListOf<Effect>()
        val tools = project.tools
        if (resize && clip.speed != 1f) effects += SpeedChangeEffect(clip.speed)
        if (clip.frame != StudioFrame()) effects += Crop(clip.frame.left*2-1,clip.frame.right*2-1,1-clip.frame.bottom*2,1-clip.frame.top*2)
        if (tools.soften > 0f) effects += GaussianBlur(tools.soften)
        if (tools.crop > 0) effects += Crop(-1f+tools.crop*2,1f-tools.crop*2,-1f+tools.crop*2,1f-tools.crop*2)
        if (tools.rotation != 0) effects += ScaleAndRotateTransformation.Builder().setRotationDegrees(tools.rotation.toFloat()).build()
        if (tools.brightness != 0f) effects += Brightness(tools.brightness)
        if (tools.contrast != 0f) effects += Contrast(tools.contrast)
        if (tools.saturation != 0f) effects += HslAdjustment.Builder().adjustSaturation(tools.saturation).build()
        if (tools.aspect != StudioAspect.SOURCE) effects += Presentation.createForAspectRatio(tools.aspect.ratio, if(tools.social.fit==CreatorFit.FILL) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT)
        // Media3's default dropper buffers the next frame and discards its pending tail at EOS.
        // Do not shorten an already-at/below-target stream. FPS is a cap, not interpolation.
        if (resize && tools.frameRate > 0 && (sourceFrameRate == null || sourceFrameRate * clip.speed > tools.frameRate + 0.01f))
            effects += FrameDropEffect.createDefaultFrameDropEffect(tools.frameRate.toFloat())
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
        if (tools.captions.isNotEmpty()) {
            val offset = project.clips.takeWhile { it.id != clip.id }.sumOf { it.playbackDurationMs }
            effects += OverlayEffect(listOf(object : TextOverlay() {
                override fun getText(presentationTimeUs: Long): SpannableString {
                    val time = offset + (presentationTimeUs / 1000 / if (resize) 1f else clip.speed).toLong()
                    val value = tools.captions.filter { time >= it.startMs && time < it.endMs }.joinToString("\n") { it.text }.ifEmpty { " " }
                    return SpannableString(value).apply {
                        setSpan(ForegroundColorSpan(tools.captionColor),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(AbsoluteSizeSpan(tools.captionSize,true),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(TypefaceSpan(tools.captionFont),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = CreatorOverlays.captionSettings(project,offset+(presentationTimeUs/1000/if(resize) 1f else clip.speed).toLong())
            }))
        }
        val textLayers=StudioTextOverlays.overlays(tools.textLayers,project.clips.takeWhile {it.id!=clip.id}.sumOf {it.playbackDurationMs},if(resize) 1f else clip.speed)
        if(textLayers.isNotEmpty()) effects+=OverlayEffect(textLayers)
        val graphics=CreatorOverlays.overlays(context,project,project.clips.takeWhile {it.id!=clip.id}.sumOf {it.playbackDurationMs},if(resize) 1f else clip.speed)
        if(graphics.isNotEmpty()) effects+=OverlayEffect(graphics)
        if (project.fadeEdges || clip.opacity < 1f) effects += RgbMatrix { timeUs, _ ->
            val gain = clip.opacity * if(project.fadeEdges) StudioFade.gain(
                (timeUs / if(resize) 1f else clip.speed).toLong(),clip.playbackDurationMs*1000,tools.fadeDurationMs*1000) else 1f
            floatArrayOf(gain,0f,0f,0f, 0f,gain,0f,0f, 0f,0f,gain,0f, 0f,0f,0f,1f)
        }
        if (resize) {
            val size=project.resolution.height
            effects += when(tools.aspect) {
                StudioAspect.PORTRAIT -> Presentation.createForWidthAndHeight(size, size*16/9, Presentation.LAYOUT_SCALE_TO_FIT)
                StudioAspect.SQUARE -> Presentation.createForWidthAndHeight(size,size,Presentation.LAYOUT_SCALE_TO_FIT)
                StudioAspect.WIDE -> Presentation.createForWidthAndHeight(size*16/9,size,Presentation.LAYOUT_SCALE_TO_FIT)
                StudioAspect.FEED -> Presentation.createForWidthAndHeight(size,size*5/4,Presentation.LAYOUT_SCALE_TO_FIT)
                StudioAspect.CLASSIC -> Presentation.createForWidthAndHeight(size*4/3,size,Presentation.LAYOUT_SCALE_TO_FIT)
                StudioAspect.SOURCE -> Presentation.createForHeight(size)
            }
            // E.g. 9:16 at height720 becomes405px wide. Align the final GL canvas
            // before strict AVC encoding, without enabling a silent quality fallback.
            effects += StudioEncoderCanvas()
        }
        return effects
    }

    private fun audioEffects(volume: Float) = listOf<AudioProcessor>(StudioVolumeProcessor(volume))

    private fun sourceFrameRate(context: Context, uri: String): Float? = runCatching {
        val extractor=android.media.MediaExtractor()
        try {
            extractor.setDataSource(context,android.net.Uri.parse(uri),null)
            val format=(0 until extractor.trackCount).map {extractor.getTrackFormat(it)}
                .firstOrNull {it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/")==true}
            format?.let {
                runCatching {it.getInteger(android.media.MediaFormat.KEY_FRAME_RATE).toFloat()}
                    .getOrElse {_ -> runCatching {it.getFloat(android.media.MediaFormat.KEY_FRAME_RATE)}.getOrNull()}
                    ?.takeIf {rate -> rate.isFinite() && rate>0f}
            }
        } finally {extractor.release()}
    }.getOrNull()

    fun composition(project: StudioProject, context: Context? = null): Composition {
        project.validate()
        // Probe once per distinct source, on the renderer's preparation IO dispatcher.
        val frameRates=if(context!=null && project.tools.frameRate>0)
            project.clips.map {it.uri}.distinct().associateWith {sourceFrameRate(context,it)} else emptyMap()
        val clips = project.clips.map { clip ->
            val audio = audioEffects(project.originalVolume).toMutableList()
            if (clip.speed != 1f) audio += SpeedChangingAudioProcessor(object : SpeedProvider {
                override fun getSpeed(timeUs: Long) = clip.speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
            })
            val source = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs)
                    .setEndPositionMs(clip.endMs).build()).build()
            EditedMediaItem.Builder(source).setRemoveAudio(project.originalVolume == 0f)
                .setEffects(Effects(audio, videoEffects(project, clip,context=context,sourceFrameRate=frameRates[clip.uri]))).build()
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
        project.tools.audio.forEach { lane ->
            val end = minOf(lane.endMs, lane.startMs + project.durationMs - lane.offsetMs)
            val media = MediaItem.Builder().setUri(lane.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(lane.startMs).setEndPositionMs(end).build()).build()
            val audioItem = EditedMediaItem.Builder(media).setRemoveVideo(true).setEffects(Effects(
                listOf(StudioVolumeProcessor(lane.volume, (end-lane.startMs)*1000, lane.fadeInMs*1000, lane.fadeOutMs*1000)), emptyList())).build()
            val sequence = EditedMediaItemSequence.Builder(emptyList<EditedMediaItem>())
            if (lane.offsetMs > 0) sequence.addGap(lane.offsetMs * 1000)
            sequence.addItem(audioItem)
            sequences += sequence.build()
        }
        return Composition.Builder(sequences)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
    }

    suspend fun render(context: Context, project: StudioProject, output: File, onProgress: (Int?) -> Unit) = renderMutex.withLock {
        project.validate()
        val prepared=withContext(Dispatchers.IO) {composition(project,context)}
        renderPrepared(context,project.tools.bitrate,prepared,output,onProgress)
    }

    /** Encode a decoded still into a normal editable silent video; no permanent image decoder dependency. */
    suspend fun renderStill(context: Context, image: File, output: File) = renderMutex.withLock {
        val item=EditedMediaItem.Builder(MediaItem.Builder().setUri(android.net.Uri.fromFile(image)).setImageDurationMs(2000).build())
            .setFrameRate(30).setRemoveAudio(true).setEffects(Effects(emptyList(),listOf(Presentation.createForHeight(720),StudioEncoderCanvas()))).build()
        renderPrepared(context,0,Composition.Builder(EditedMediaItemSequence.Builder(item).build()).build(),output) {}
    }

    private suspend fun renderPrepared(context: Context, bitrate: Int, prepared: Composition, output: File, onProgress: (Int?)->Unit) {
        var transformer: Transformer? = null
        val handler = Handler(Looper.getMainLooper())
        try {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    transformer = Transformer.Builder(context.applicationContext)
                        .apply {
                            if(android.os.Build.VERSION.SDK_INT==29) setAssetLoaderFactory(DefaultAssetLoaderFactory(
                                context.applicationContext,StudioExportDecoders(context.applicationContext),androidx.media3.common.util.Clock.DEFAULT))
                        }
                        .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setPortraitEncodingEnabled(true)
                        .setEncoderFactory(DefaultEncoderFactory.Builder(context).setEnableFallback(false).apply {
                            if (bitrate > 0) setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                        }.build())
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                handler.removeCallbacksAndMessages(null)
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                val codec=generateSequence<Throwable>(exportException) {it.cause}.take(8).filterIsInstance<android.media.MediaCodec.CodecException>().firstOrNull()
                                android.util.Log.w("VGStudio","render.failed code=${exportException.errorCode} cause=${exportException.cause?.javaClass?.simpleName} codec=${codec?.diagnosticInfo}")
                                handler.removeCallbacksAndMessages(null)
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(
                                    "Device export failed (code ${exportException.errorCode}). Try 720p or another H.264 source.", exportException))
                            }
                        }).build()
                    try {
                        transformer!!.start(prepared, output.absolutePath)
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
internal class StudioVolumeProcessor(private val gain: Float, private val durationUs: Long = Long.MAX_VALUE,
    private val fadeInUs: Long = 0, private val fadeOutUs: Long = 0) : BaseAudioProcessor() {
    private var samples = 0L
    override fun onFlush() { samples = 0 }
    private fun nextGain(): Float {
        val time = (samples++ / inputAudioFormat.channelCount) * 1_000_000L / inputAudioFormat.sampleRate
        val fadeIn = if (fadeInUs > 0) time.toFloat()/fadeInUs else 1f
        val fadeOut = if (fadeOutUs > 0) (durationUs-time).toFloat()/fadeOutUs else 1f
        return gain * minOf(1f,fadeIn,fadeOut).coerceIn(0f,1f)
    }
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 4) output.putFloat((inputBuffer.float * nextGain()).coerceIn(-1f, 1f))
        } else {
            while (inputBuffer.remaining() >= 2) output.putShort((inputBuffer.short * nextGain()).toInt().coerceIn(-32768, 32767).toShort())
        }
        output.flip()
    }
}
