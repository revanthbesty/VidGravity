package com.vidgravity.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

data class PublishedMedia(val uri: Uri, val displayName: String, val sizeBytes: Long, val sha256: String)

object MediaLibraryIndexer {
    fun publish(context: Context, source: File, originalName: String, hash: String): PublishedMedia {
        require(source.isFile && source.length() > 0) { "Downloaded media is empty." }
        val safeName = originalName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").take(140).ifBlank { "vidgravity-media.mp4" }
        val extension = safeName.substringAfterLast('.', "mp4").lowercase()
        val mime = when (extension) {
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "video/mp4"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (mime.startsWith("audio/")) "Music/VidGravity" else "Movies/VidGravity")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore could not create the download item.")
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().buffered(128 * 1024).use { it.copyTo(output, 128 * 1024) }
            } ?: throw IllegalStateException("MediaStore output stream was unavailable.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            LibraryIndexDb(context).use { db ->
                db.writableDatabase.insertOrThrow("media", null, ContentValues().apply {
                    put("content_uri", uri.toString())
                    put("display_name", safeName)
                    put("mime_type", mime)
                    put("size_bytes", source.length())
                    put("sha256", hash)
                    put("created_at_ms", System.currentTimeMillis())
                })
            }
            context.contentResolver.notifyChange(uri, null)
            return PublishedMedia(uri, safeName, source.length(), hash)
        } catch (failure: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw failure
        }
    }
}

private class LibraryIndexDb(context: Context) : SQLiteOpenHelper(context, "vidgravity_library.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE media (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            content_uri TEXT NOT NULL UNIQUE,
            display_name TEXT NOT NULL,
            mime_type TEXT NOT NULL,
            size_bytes INTEGER NOT NULL,
            sha256 TEXT NOT NULL,
            created_at_ms INTEGER NOT NULL
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
