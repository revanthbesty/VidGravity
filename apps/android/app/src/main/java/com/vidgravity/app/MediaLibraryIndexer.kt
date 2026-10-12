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

data class LibraryMedia(val uri: Uri, val displayName: String, val sizeBytes: Long, val mimeType: String, val createdAtMs: Long, val isFavorite: Boolean = false)

object MediaLibraryIndexer {
    fun list(context: Context): List<LibraryMedia> {
        LibraryIndexDb(context).use { helper ->
            val rows = mutableListOf<LibraryMedia>()
            helper.readableDatabase.rawQuery(
                "SELECT content_uri, display_name, size_bytes, mime_type, created_at_ms, is_favorite FROM media ORDER BY created_at_ms DESC",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = runCatching { Uri.parse(cursor.getString(0)) }.getOrNull() ?: continue
                    rows += LibraryMedia(uri, cursor.getString(1), cursor.getLong(2), cursor.getString(3), cursor.getLong(4), cursor.getInt(5) != 0)
                }
            }
            return rows
        }
    }

    fun setFavorite(context: Context, uri: Uri, favorite: Boolean) {
        LibraryIndexDb(context).use { db ->
            db.writableDatabase.update("media", ContentValues().apply { put("is_favorite", if (favorite) 1 else 0) },
                "content_uri=?", arrayOf(uri.toString()))
        }
    }

    fun rename(context: Context, media: LibraryMedia, newName: String): Boolean {
        val safeName = newName.trim().replace("/", "_").replace("\\", "_").take(140)
        if (safeName.isBlank()) return false
        val updated = context.contentResolver.update(media.uri,
            ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, safeName) }, null, null)
        if (updated <= 0) return false
        LibraryIndexDb(context).use { db ->
            db.writableDatabase.update("media", ContentValues().apply { put("display_name", safeName) },
                "content_uri=?", arrayOf(media.uri.toString()))
        }
        return true
    }

    fun delete(context: Context, media: LibraryMedia): Boolean {
        val deleted = context.contentResolver.delete(media.uri, null, null) > 0
        if (deleted) LibraryIndexDb(context).use { db ->
            db.writableDatabase.delete("media", "content_uri=?", arrayOf(media.uri.toString()))
        }
        return deleted
    }

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
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else if (mime.startsWith("audio/")) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values)
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

private class LibraryIndexDb(context: Context) : SQLiteOpenHelper(context, "vidgravity_library.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE media (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            content_uri TEXT NOT NULL UNIQUE,
            display_name TEXT NOT NULL,
            mime_type TEXT NOT NULL,
            size_bytes INTEGER NOT NULL,
            sha256 TEXT NOT NULL,
            created_at_ms INTEGER NOT NULL,
            is_favorite INTEGER NOT NULL DEFAULT 0
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE media ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
    }
}
