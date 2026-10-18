package com.vidgravity.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class NativeStudioExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = StudioRepository(applicationContext)
        val output = File(applicationContext.cacheDir, "studio-$id.mp4")
        try {
            val project = repository.request(id.toString())
            output.delete()
            SocialCreator.requireSpace(android.os.StatFs(applicationContext.cacheDir.path).availableBytes,SocialCreator.estimatedBytes(project))
            (project.clips.map {it.uri}+listOfNotNull(project.musicUri,project.voiceUri,project.tools.social.logo)+project.tools.audio.map {it.uri}).distinct().forEach { source ->
                applicationContext.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(source),"r")?.use {} ?: error("Source media is missing. Relink media in Studio and retry.")
            }
            setForeground(getForegroundInfo())
            setProgress(workDataOf("stage" to "Rendering on device"))
            NativeStudioRenderer.render(applicationContext, project, output) { progress ->
                setProgressAsync(workDataOf("progress" to (progress ?: -1), "stage" to "Rendering on device"))
            }
            ensureActive()
            setProgress(workDataOf("progress" to 99, "stage" to "Saving to Library"))
            val retriever = android.media.MediaMetadataRetriever()
            val dimensions = try {
                retriever.setDataSource(output.absolutePath)
                (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0) to
                    (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)
            } finally { retriever.release() }
            check(dimensions.first > 0 && dimensions.second > 0) { "The output video could not be verified." }
            val published = MediaLibraryIndexer.publish(applicationContext, output,
                "${project.title.take(60)}_Studio.mp4", DownloadApi.sha256(output))
            val record = repository.record(project.id)
            if (record?.workId == id.toString()) repository.setRecord(project.id,
                StudioExportRecord(id.toString(), project.fingerprint(), published.uri.toString(), dimensions.first, dimensions.second))
            repository.deleteRequest(id.toString())
            Result.success(workDataOf("content_uri" to published.uri.toString(), "width" to dimensions.first,
                "height" to dimensions.second, "sha256" to published.sha256))
        } catch (cancelled: CancellationException) {
            repository.deleteRequest(id.toString())
            throw cancelled
        } catch (failure: Exception) {
            repository.deleteRequest(id.toString())
            Result.failure(workDataOf("error" to (failure.message ?: "Export failed. Try a smaller preset.").take(240)))
        } finally { output.delete() }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channel = "vg-studio-export"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(channel, "Studio exports", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("VidGravity Studio")
            .setContentText("Rendering video on this device").setOngoing(true).build()
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            else if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        return ForegroundInfo(18000 + (id.hashCode() and 4095), notification, type)
    }

    companion object {
        /** Call on IO. Keep an immutable request so later project edits cannot alter a running render. */
        @Synchronized fun enqueue(context: Context, project: StudioProject): UUID {
            project.validate()
            val manager = WorkManager.getInstance(context)
            val name = "studio-export-${project.id}"
            check(manager.getWorkInfosByTag(name).get().none { !it.state.isFinished } && manager.getWorkInfosForUniqueWork(name).get().none { !it.state.isFinished }) { "This project already has an export queued or running." }
            val previous=StudioRepository(context).record(project.id)
            if(previous?.uri!=null && previous.fingerprint==project.fingerprint()) {
                val readable=runCatching {context.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(previous.uri),"r")?.use {true} ?: false}.getOrDefault(false)
                if(readable && manager.getWorkInfoById(UUID.fromString(previous.workId)).get()?.state==WorkInfo.State.SUCCEEDED) return UUID.fromString(previous.workId)
            }
            val request = OneTimeWorkRequestBuilder<NativeStudioExportWorker>().addTag("vidgravity-studio-export").addTag(name).build()
            val repository = StudioRepository(context)
            repository.save(project)
            repository.saveRequest(request.id.toString(), project)
            repository.setRecord(project.id, StudioExportRecord(request.id.toString(), project.fingerprint()))
            manager.enqueueUniqueWork("vidgravity-native-render-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
            return request.id
        }
    }
}
