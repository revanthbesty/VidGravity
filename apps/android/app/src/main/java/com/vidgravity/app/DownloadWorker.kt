package com.vidgravity.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private var remoteJobId: String? = null
    private val workId: UUID get() = id

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val rawUrl = inputData.getString(KEY_URL)
        val url = rawUrl?.let(ShareReceiverActivity::normalizeSharedUrl)
            ?: return@withContext Result.failure(workDataOf("error" to "Invalid shared URL"))
        val preferences = SecureDownloadPreferences.load(applicationContext)
        val selector = inputData.getString(KEY_FORMAT)?.takeIf { it.isNotBlank() } ?: preferences.preferredFormatSelector
        var title = inputData.getString(KEY_TITLE)?.take(180) ?: ShareReceiverActivity.friendlyTitle(url)
        val workDirectory = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        val partial = File(workDirectory, "downloads/" + workId + ".part")
        val registry = applicationContext.getSharedPreferences(REGISTRY, Context.MODE_PRIVATE)
        remoteJobId = registry.getString(workId.toString(), null)

        try {
            setForeground(DownloadNotifications.foreground(applicationContext, workId, title, "Analyzing source", 0, false))
            val analysis = DownloadApi.analyzeSource(url)
            title = analysis.title.ifBlank { title }
            if (remoteJobId == null) {
                remoteJobId = DownloadApi.createJob(url, selector)
                registry.edit().putString(workId.toString(), remoteJobId).commit()
            }
            var job: RemoteDownloadJob
            while (true) {
                if (isStopped) throw CancellationException("Work was cancelled")
                while (DownloadActionReceiver.isPaused(applicationContext, workId)) {
                    setForeground(DownloadNotifications.foreground(applicationContext, workId, title, "Paused", 0, true))
                    delay(500)
                    if (isStopped) throw CancellationException("Work was cancelled")
                }
                job = DownloadApi.getJob(remoteJobId!!)
                setProgress(workDataOf("remoteJobId" to remoteJobId, "state" to job.state, "progress" to job.progress))
                setForeground(DownloadNotifications.foreground(applicationContext, workId, title, job.message, job.progress, false))
                when (job.state) {
                    "completed" -> break
                    "failed", "cancelled", "expired", "restricted" -> {
                        partial.delete()
                        registry.edit().remove(workId.toString()).commit()
                        DownloadActionReceiver.clear(applicationContext, workId)
                        return@withContext failureResult(job.message, url, selector, title)
                    }
                    "queued", "running", "processing", "downloading", "cancelling" -> delay(if (job.state == "queued") 2500 else 1500)
                    else -> delay(2000)
                }
            }
            val receipt = DownloadApi.downloadResult(remoteJobId!!, partial) { bytes, total ->
                val percent = if (total != null && total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 99) else 0
                setProgressAsync(workDataOf("bytes_downloaded" to bytes, "total_bytes" to (total ?: -1L), "eta_seconds" to -1L, "progress" to percent))
                setForegroundAsync(DownloadNotifications.foreground(applicationContext, workId, title,
                    if (total != null) (bytes / 1_000_000).toString() + " / " + (total / 1_000_000) + " MB" else (bytes / 1_000_000).toString() + " MB transferred",
                    percent, false))
            }
            val expected = receipt.sha256 ?: throw IllegalStateException("The companion omitted a trusted SHA-256 checksum; media was not published.")
            val actual = DownloadApi.sha256(partial)
            if (!actual.equals(expected, ignoreCase = true)) {
                partial.delete()
                throw IllegalStateException("SHA-256 verification failed; the partial file was discarded.")
            }
            val published = MediaLibraryIndexer.publish(applicationContext, partial, job.filename.ifBlank { title }, actual)
            partial.delete()
            registry.edit().remove(workId.toString()).commit()
            DownloadActionReceiver.clear(applicationContext, workId)
            DownloadNotifications.terminal(applicationContext, published.displayName, "Saved and indexed", true)
            Result.success(workDataOf("content_uri" to published.uri.toString(), "display_name" to published.displayName,
                "sha256" to actual, "bytes_downloaded" to published.sizeBytes))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                remoteJobId?.let { runCatching { DownloadApi.cancelJob(it) } }
                partial.delete()
                registry.edit().remove(workId.toString()).commit()
                DownloadActionReceiver.clear(applicationContext, workId)
            }
            throw cancelled
        } catch (timeout: SocketTimeoutException) {
            if (runAttemptCount < 3) Result.retry()
            else {
                partial.delete()
                remoteJobId?.let { runCatching { DownloadApi.cancelJob(it) } }
                registry.edit().remove(workId.toString()).commit()
                DownloadActionReceiver.clear(applicationContext, workId)
                DownloadNotifications.terminal(applicationContext, title, "Network timeout after retries", false)
                failureResult("Network timeout after retries", url, selector, title)
            }
        } catch (failure: Exception) {
            remoteJobId?.let { runCatching { DownloadApi.cancelJob(it) } }
            partial.delete()
            registry.edit().remove(workId.toString()).commit()
            DownloadActionReceiver.clear(applicationContext, workId)
            DownloadNotifications.terminal(applicationContext, title, failure.message ?: "Download failed", false)
            failureResult(failure.message ?: "Download failed", url, selector, title)
        }
    }

    companion object {
        const val KEY_URL = "shared_url"
        const val KEY_FORMAT = "format_selector"
        const val KEY_TITLE = "media_title"
        const val RETRY_URL = "retry_url"
        const val RETRY_FORMAT = "retry_format"
        const val RETRY_TITLE = "retry_title"
        private const val REGISTRY = "vg_remote_job_registry"

        private fun failureResult(message: String, url: String, selector: String, title: String): Result =
            Result.failure(workDataOf(
                "error" to message.take(300),
                RETRY_URL to url,
                RETRY_FORMAT to selector,
                RETRY_TITLE to title.take(180)
            ))

        fun enqueue(context: Context, url: String, selector: String, title: String, unmeteredOnly: Boolean): UUID {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_URL to url, KEY_FORMAT to selector, KEY_TITLE to title))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("vidgravity-download")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("vg-download-" + request.id, ExistingWorkPolicy.KEEP, request)
            return request.id
        }
    }
}
