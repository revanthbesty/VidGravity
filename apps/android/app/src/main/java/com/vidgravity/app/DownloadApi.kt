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
data class TransferReceipt(val bytesWritten: Long, val totalBytes: Long?, val sha256: String?)

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
                        height = item.optInt("height", Regex("(?:\\d{3,4}x(\\d{3,4})|(?:^|\\D)(\\d{3,4})p").find(item.optString("resolution"))?.let { match -> (match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.groupValues.getOrNull(2))?.toIntOrNull() } ?: 0),
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
            val expectedHash = connection.getHeaderField("X-Content-SHA256")
                ?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
            destination.parentFile?.mkdirs()
            var received = existingOffset
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var lastUpdate = System.currentTimeMillis()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
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
            return TransferReceipt(received, total, expectedHash)
        } finally { connection.disconnect() }
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
