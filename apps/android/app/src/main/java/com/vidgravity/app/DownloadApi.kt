package com.vidgravity.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

data class FormatOption(
    val selector: String,
    val formatId: String,
    val resolution: String,
    val container: String,
    val videoCodec: String,
    val audioCodec: String,
    val sizeBytes: Long?,
    val width: Int = 0,
    val height: Int = 0,
    val hasVideo: Boolean = videoCodec.isNotBlank() && videoCodec != "none",
    val hasAudio: Boolean = audioCodec.isNotBlank() && audioCodec != "none",
    val note: String = "",
    val fps: Double? = null,
    val dynamicRange: String = ""
) {
    fun displayName(): String {
        val kind = if (hasVideo && hasAudio) "Video + audio" else if (hasVideo) "Video · audio combined on download" else "Audio only"
        val size = sizeBytes?.let { String.format("%.1f MB", it / 1_000_000.0) } ?: "Size varies by source"
        return listOf(container.uppercase(), kind, size).joinToString(" · ")
    }
}

data class SourceAnalysisResult(val title: String, val durationSeconds: Double?, val formats: List<FormatOption>)
data class RemoteDownloadJob(val id: String, val state: String, val progress: Int, val message: String, val filename: String)
data class TransferReceipt(val bytesWritten: Long, val totalBytes: Long?, val sha256: String?, val calculatedSha256: String? = null)

object DownloadApi {
    private fun baseUrl(): String {
        val uri = Uri.parse(BuildConfig.VG_API_BASE_URL.trim().trimEnd('/'))
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.port in listOf(-1, 443)) {
            "Configure a public HTTPS VidGravity companion endpoint."
        }
        val host = uri.host!!.lowercase()
        require(host !in setOf("localhost", "127.0.0.1", "::1") && !host.endsWith(".local") && !host.endsWith(".localhost")) {
            "Local companion endpoints are not allowed."
        }
        return uri.toString().trimEnd('/')
    }

    private fun open(path: String, method: String, contentType: String? = null): HttpURLConnection {
        val connection = URL(baseUrl() + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "VidGravity-Android/1.0")
        connection.setRequestProperty("Cache-Control", "no-store")
        if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
        return connection
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Companion HTTP " + code + ": " + body.take(400))
        return JSONObject(body)
    }

    fun analyzeSource(url: String): SourceAnalysisResult {
        val connection = open("/api/formats?url=" + URLEncoder.encode(url, "UTF-8"), "GET")
        try {
            val json = readJson(connection)
            val formats = json.optJSONArray("formats") ?: JSONArray()
            val rows = buildList {
                for (index in 0 until formats.length()) {
                    val item = formats.optJSONObject(index) ?: continue
                    val selector = item.optString("selector", item.optString("id", ""))
                    if (selector.isBlank() || selector.length > 200) continue
                    add(FormatOption(
                        selector = selector,
                        formatId = item.optString("id", selector),
                        resolution = item.optString("resolution", "Quality unavailable"),
                        container = item.optString("ext", "mp4"),
                        videoCodec = item.optString("vcodec", "unknown"),
                        audioCodec = item.optString("acodec", "unknown"),
                        sizeBytes = if (item.has("filesize") && !item.isNull("filesize")) item.optLong("filesize").takeIf { it > 0 } else null,
                        width = item.optInt("width", Regex("(\\d{3,4})x\\d{3,4}").find(item.optString("resolution"))?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0),
                        height = item.optInt("height", Regex("(?:\\d{3,4}x(\\d{3,4})|(?:^|\\D)(\\d{3,4})p)").find(item.optString("resolution"))?.let { match -> (match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.groupValues.getOrNull(2))?.toIntOrNull() } ?: 0),
                        hasVideo = item.optBoolean("has_video", item.optString("vcodec", "none") != "none"),
                        hasAudio = item.optBoolean("has_audio", item.optString("acodec", "none") != "none"),
                        note = item.optString("note", ""),
                        fps = if (item.has("fps") && !item.isNull("fps")) item.optDouble("fps").takeIf { it > 0 } else null,
                        dynamicRange = item.optString("dynamic_range", "")
                    ))
                }
            }
            return SourceAnalysisResult(
                title = json.optString("title", "Shared media").take(180),
                durationSeconds = if (json.has("duration") && !json.isNull("duration")) json.optDouble("duration") else null,
                formats = rows
            )
        } finally { connection.disconnect() }
    }

    fun createJob(url: String, selector: String): String {
        require(selector.isNotBlank() && selector.length <= 200 && selector.matches(Regex("[A-Za-z0-9_*+./,\\[\\]()-]+"))) {
            "Invalid format selector."
        }
        val connection = open("/api/download-jobs", "POST", "application/json; charset=utf-8")
        connection.doOutput = true
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("url", url).put("format", selector).toString().toByteArray(Charsets.UTF_8))
            }
            val id = readJson(connection).optString("id")
            require(id.matches(Regex("[A-Fa-f0-9-]{16,64}"))) { "Companion returned an invalid task ID." }
            return id
        } finally { connection.disconnect() }
    }

    fun getJob(id: String): RemoteDownloadJob {
        require(id.matches(Regex("[A-Fa-f0-9-]{16,64}"))) { "Invalid task ID." }
        val connection = open("/api/download-jobs/" + id, "GET")
        try {
            val json = readJson(connection)
            return RemoteDownloadJob(
                id, json.optString("state", "unknown").lowercase(),
                json.optInt("progress", 0).coerceIn(0, 100),
                json.optString("message", "Processing").take(200),
                json.optString("filename", "vidgravity-media").take(180)
            )
        } finally { connection.disconnect() }
    }

    fun cancelJob(id: String) {
        if (!id.matches(Regex("[A-Fa-f0-9-]{16,64}"))) return
        val connection = open("/api/download-jobs/" + id + "/cancel", "POST", "application/json")
        connection.doOutput = true
        try {
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            runCatching { connection.responseCode }
        } finally { connection.disconnect() }
    }

    fun downloadResult(id: String, destination: File, onProgress: (Long, Long?) -> Unit): TransferReceipt {
        require(id.matches(Regex("[A-Fa-f0-9-]{16,64}"))) { "Invalid task ID." }
        val offset = destination.takeIf { it.isFile }?.length() ?: 0L
        val connection = open("/api/download-jobs/" + id + "/result", "GET")
        if (offset > 0L) connection.setRequestProperty("Range", "bytes=" + offset + "-")
        try {
            val code = connection.responseCode
            if (code == 416 && offset > 0L) {
                // A stale or corrupt partial can no longer be resumed. Discard it and
                // retry this request once from byte zero rather than leaving WorkManager
                // to repeat the same invalid Range request.
                connection.disconnect()
                destination.delete()
                return downloadResult(id, destination, onProgress)
            }
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Result HTTP " + code + ": " + detail.take(300))
            }
            val contentRange = connection.getHeaderField("Content-Range")
            val append = offset > 0L && code == HttpURLConnection.HTTP_PARTIAL &&
                contentRange?.startsWith("bytes " + offset + "-") == true
            val existingOffset = if (append) offset else 0L
            val total = if (append && contentRange != null) {
                contentRange.substringAfterLast('/').toLongOrNull()
            } else connection.getHeaderFieldLong("Content-Length", -1L).takeIf { it >= 0 }
            val rawExpectedHash = connection.getHeaderField("X-Content-SHA256")?.trim()
            val expectedHash = rawExpectedHash?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
            // A malformed checksum must not be treated as a trusted checksum. We still
            // calculate SHA-256 locally and can safely publish when no server digest exists.
            destination.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            if (append && existingOffset > 0L) {
                destination.inputStream().buffered(128 * 1024).use { existing ->
                    val prefix = ByteArray(128 * 1024)
                    var remaining = existingOffset
                    while (remaining > 0L) {
                        val count = existing.read(prefix, 0, minOf(prefix.size.toLong(), remaining).toInt())
                        if (count < 0) throw IllegalStateException("Partial file changed while resuming the transfer.")
                        digest.update(prefix, 0, count)
                        remaining -= count
                    }
                }
            }
            var received = existingOffset
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var lastUpdate = System.currentTimeMillis()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                        if (System.currentTimeMillis() - lastUpdate > 400) {
                            onProgress(received, total)
                            lastUpdate = System.currentTimeMillis()
                        }
                    }
                    output.fd.sync()
                }
            }
            onProgress(received, total)
            if (total != null && total > 0L && received != total) {
                throw java.io.EOFException("Transfer incomplete: received $received of $total bytes. Retry the download.")
            }
            val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedHash != null && !calculatedHash.equals(expectedHash, ignoreCase = true)) {
                // Never resume a file whose content failed integrity validation. A retry
                // must begin with a clean destination or the stale bytes will recur.
                destination.delete()
                throw java.io.IOException("SHA-256 verification failed: downloaded bytes do not match the companion checksum.")
            }
            return TransferReceipt(received, total, expectedHash, calculatedHash)
        } finally { connection.disconnect() }
    }


    /** Upload a bounded local video to the companion renderer, verify its digest, and publish a separate derivative. */
    fun exportMedia(
        context: android.content.Context,
        sourceUri: Uri,
        preset: String,
        mode: String,
        startSeconds: Float,
        trimSeconds: Float,
        outputFormat: String = "mp4"
    ): Uri {
        require(outputFormat in setOf("mp4", "webp")) { "Unsupported export format." }
        require(if (outputFormat == "webp") preset == "animated-sticker" else preset in setOf("whatsapp-status", "instagram-story", "instagram-reels", "youtube-shorts", "tiktok")) { "Unsupported export preset." }
        require(mode in setOf("smart-reframe", "blurred-backdrop", "fit-letterbox", "privacy-blur", "audio-story")) { "Unsupported framing mode." }
        if (outputFormat == "webp") {
            require(mode == "fit-letterbox" && trimSeconds <= 10f) { "Animated stickers must use fit-letterbox framing and be 10 seconds or shorter." }
            require(context.contentResolver.getType(sourceUri)?.startsWith("video/") == true) { "Animated stickers require a video source." }
        }
        if (mode == "audio-story") {
            require(outputFormat == "mp4" && preset in setOf("whatsapp-status", "instagram-story", "instagram-reels", "youtube-shorts", "tiktok")) {
                "Audio visualizer stories require a vertical MP4 social preset."
            }
            require(context.contentResolver.getType(sourceUri)?.startsWith("audio/") == true) { "Audio visualizer stories require an audio source." }
        }
        require(startSeconds.isFinite() && startSeconds >= 0f && startSeconds < 3600f) { "Invalid clip start time." }
        require(trimSeconds.isFinite() && trimSeconds > 0f && trimSeconds <= 3600f && startSeconds + trimSeconds <= 3600f) { "Invalid clip duration." }
        val sourceFile = File.createTempFile("vg-story-source-", ".bin", context.cacheDir)
        val outputFile = File.createTempFile("vg-story-export-", if (outputFormat == "webp") ".webp" else ".mp4", context.cacheDir)
        var publishedUri: Uri? = null
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(sourceFile).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 256L * 1024L * 1024L) { "Source media exceeds the 256 MiB export limit." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            } ?: throw IllegalStateException("Cannot open the selected source media.")
            require(sourceFile.length() > 0L) { "The selected source media is empty." }
            val mime = context.contentResolver.getType(sourceUri)?.takeIf { it.startsWith("video/") || (mode == "audio-story" && it.startsWith("audio/")) } ?: "application/octet-stream"
            val connection = open("/api/media/export", "POST", mime)
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 1_800_000
            connection.setFixedLengthStreamingMode(sourceFile.length())
            connection.setRequestProperty("X-VG-Preset", preset)
            connection.setRequestProperty("X-VG-Format", outputFormat)
            connection.setRequestProperty("X-VG-Mode", mode)
            connection.setRequestProperty("X-VG-Start-Seconds", "%.3f".format(java.util.Locale.US, startSeconds))
            connection.setRequestProperty("X-VG-Trim-Seconds", "%.3f".format(java.util.Locale.US, trimSeconds))
            try {
                sourceFile.inputStream().buffered(128 * 1024).use { input ->
                    connection.outputStream.buffered(128 * 1024).use { output -> input.copyTo(output, 128 * 1024) }
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Export HTTP $code: ${detail.take(300)}")
                }
                val expected = connection.getHeaderField("X-Content-SHA256")?.trim()?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                    ?: throw java.io.IOException("The renderer did not provide a valid SHA-256 checksum.")
                connection.inputStream.buffered(128 * 1024).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 256L * 1024L * 1024L) { "Export exceeds the 256 MiB response limit." }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                if (!sha256(outputFile).equals(expected, ignoreCase = true)) {
                    throw java.io.IOException("Export SHA-256 verification failed; the file was not published.")
                }
            } finally { connection.disconnect() }
            val values = android.content.ContentValues().apply {
                val suffix = if (outputFormat == "webp") "Sticker" else "Story"
                val extension = if (outputFormat == "webp") "webp" else "mp4"
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "VidGravity_${suffix}_${System.currentTimeMillis()}.$extension")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (outputFormat == "webp") "image/webp" else "video/mp4")
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val directory = if (outputFormat == "webp") android.os.Environment.DIRECTORY_PICTURES + "/VidGravity/Stickers" else android.os.Environment.DIRECTORY_MOVIES + "/VidGravity"
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, directory)
                    // Do not expose a partially written export to other apps or Library.
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = if (outputFormat == "webp") android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI else android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            publishedUri = context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("Android could not create a library entry for the exported video.")
            context.contentResolver.openOutputStream(publishedUri, "w")?.use { output ->
                outputFile.inputStream().buffered(128 * 1024).use { it.copyTo(output, 128 * 1024) }
            } ?: throw IllegalStateException("Android could not write the exported media.")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val published = context.contentResolver.update(
                    publishedUri,
                    android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
                if (published <= 0) throw IllegalStateException("Android could not publish the completed export.")
            }
            context.contentResolver.notifyChange(publishedUri, null)
            return publishedUri
        } catch (failure: Exception) {
            publishedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            throw failure
        } finally {
            sourceFile.delete()
            outputFile.delete()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(128 * 1024).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
