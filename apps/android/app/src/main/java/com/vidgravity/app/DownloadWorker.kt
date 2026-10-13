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
import java.io.IOException
import java.security.MessageDigest
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
            promoteOrNotify(title, "Analyzing source", 0, false)
            val analysis = DownloadApi.analyzeSource(url)
            title = analysis.title.ifBlank { title }
            if (remoteJobId == null) {
                remoteJobId = DownloadApi.createJob(url, selector)
                registry.edit().putString(workId.toString(), remoteJobId).commit()
            }
            var job: RemoteDownloadJob
            var lastKnownProgress = 0
            while (true) {
                if (isStopped) throw CancellationException("Work was cancelled")
                while (DownloadActionReceiver.isPaused(applicationContext, workId)) {
                    promoteOrNotify(title, "Paused", 0, true)
                    setProgress(workDataOf("state" to "paused", "progress" to lastKnownProgress,
                        "media_url" to url, "format_selector" to selector, "media_title" to title))
                    delay(500)
                    if (isStopped) throw CancellationException("Work was cancelled")
                }
                job = DownloadApi.getJob(remoteJobId!!)
                lastKnownProgress = job.progress
                setProgress(workDataOf("remoteJobId" to remoteJobId, "state" to job.state, "progress" to job.progress,
                    "media_url" to url, "format_selector" to selector, "media_title" to title))
                promoteOrNotify(title, job.message, job.progress, false)
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
            var lastProgressBytes = 0L
            var lastProgressAt = System.currentTimeMillis()
            var currentSpeed = 0L
            val receipt = DownloadApi.downloadResult(remoteJobId!!, partial) { bytes, total ->
                val now = System.currentTimeMillis()
                val elapsed = (now - lastProgressAt).coerceAtLeast(1L)
                if (elapsed >= 400L) {
                    currentSpeed = ((bytes - lastProgressBytes).coerceAtLeast(0L) * 1000L) / elapsed
                    lastProgressBytes = bytes
                    lastProgressAt = now
                }
                val percent = if (total != null && total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 99) else 0
                val eta = if (total != null && currentSpeed > 0L) ((total - bytes).coerceAtLeast(0L) / currentSpeed) else -1L
                setProgressAsync(workDataOf("bytes_downloaded" to bytes, "total_bytes" to (total ?: -1L),
                    "bytes_per_second" to currentSpeed, "eta_seconds" to eta, "progress" to percent,
                    "media_url" to url, "format_selector" to selector, "media_title" to title))
                DownloadNotifications.progress(applicationContext, workId, title,
                    if (total != null) (bytes / 1_000_000).toString() + " / " + (total / 1_000_000) + " MB" else (bytes / 1_000_000).toString() + " MB transferred",
                    percent, false)
            }
            // The provider may omit a precomputed checksum. Hash received bytes locally;
            // compare against the companion only when it supplied a valid SHA-256.
            val actual = receipt.calculatedSha256 ?: DownloadApi.sha256(partial)
            val expected = receipt.sha256
            if (expected != null && !actual.equals(expected, ignoreCase = true)) {
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
        } catch (networkFailure: IOException) {
            if (runAttemptCount < 3) Result.retry()
            else {
                partial.delete()
                remoteJobId?.let { runCatching { DownloadApi.cancelJob(it) } }
                registry.edit().remove(workId.toString()).commit()
                DownloadActionReceiver.clear(applicationContext, workId)
                DownloadNotifications.terminal(applicationContext, title, "Network error after retries: " + (networkFailure.message ?: "connection interrupted"), false)
                failureResult("Network error after retries: " + (networkFailure.message ?: "connection interrupted"), url, selector, title)
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

    /** Foreground promotion is best-effort: Android may reject it for app-state/quota reasons. */
    private suspend fun promoteOrNotify(title: String, message: String, percent: Int, paused: Boolean) {
        try {
            setForeground(DownloadNotifications.foreground(applicationContext, workId, title, message, percent, paused))
        } catch (failure: RuntimeException) {
            // Do not fail a requested transfer only because Android denied foreground promotion.
            DownloadNotifications.progress(applicationContext, workId, title, message, percent, paused)
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
            // Use a stable identity for the same URL + selector. KEEP suppresses a second
            // unfinished copy while still allowing the user to download it again after completion.
            val identity = (url.trim().lowercase() + "\\n" + selector.trim()).toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(identity)
                .take(12).joinToString("") { byte -> "%02x".format(byte) }
            WorkManager.getInstance(context).enqueueUniqueWork("vg-download-" + digest, ExistingWorkPolicy.KEEP, request)
            return request.id
        }
    }
}
