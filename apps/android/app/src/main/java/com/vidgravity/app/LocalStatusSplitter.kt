package com.vidgravity.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil

/**
 * Device-local fallback for WhatsApp Status splitting when the remote export endpoint
 * is unavailable. Outputs are temporary MP4 files; callers publish them only after each
 * completed export. The selected source is never modified.
 */
object LocalStatusSplitter {
    suspend fun split(
        context: Context,
        sourceUri: Uri,
        splitSeconds: Int,
        durationSeconds: Float,
        startIndex: Int = 0,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<File> {
        require(splitSeconds in setOf(30, 60, 90, 120)) { "Choose a supported 30, 60, 90, or 120-second split target." }
        require(durationSeconds.isFinite() && durationSeconds > 0f && durationSeconds <= 600f) {
            "For safety and predictable processing, split videos up to 10 minutes per batch."
        }
        val total = ceil(durationSeconds / splitSeconds).toInt()
        require(startIndex in 0..total) { "Invalid split resume position." }
        val outputs = mutableListOf<File>()
        try {
            for (index in startIndex until total) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val startMs = (index * splitSeconds * 1000L).coerceAtLeast(0L)
                val endMs = minOf(((index + 1) * splitSeconds * 1000L), (durationSeconds * 1000f).toLong())
                require(endMs > startMs) { "The selected video has no media remaining for this clip." }
                onProgress(index + 1, total)
                val output = File.createTempFile(
                    "vg-status-${String.format(Locale.US, "%02d", index + 1)}-",
                    ".mp4",
                    context.cacheDir
                )
                output.delete()
                try {
                    exportClip(context, sourceUri, startMs, endMs, output)
                    require(output.isFile && output.length() > 0L) { "Local export produced an empty clip." }
                    outputs += output
                } catch (failure: Throwable) {
                    output.delete()
                    throw failure
                }
            }
            return outputs
        } catch (failure: Throwable) {
            outputs.forEach { it.delete() }
            throw failure
        }
    }

    private suspend fun exportClip(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        output: File
    ) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val mediaItem = MediaItem.Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build()
                    )
                    .build()
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(
                            IllegalStateException("On-device clip export failed: ${exportException.message ?: "unsupported media"}", exportException)
                        )
                    }
                }
                val transformer = Transformer.Builder(context.applicationContext)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(listener)
                    .build()
                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { runCatching { transformer.cancel() } }
                }
                try {
                    transformer.start(EditedMediaItem.Builder(mediaItem).build(), output.absolutePath)
                } catch (failure: Throwable) {
                    continuation.resumeWithException(failure)
                }
            }
        }
    }
}
