package com.vidgravity.app

import android.app.Activity
import android.content.Intent
import android.content.Context

internal object CameraResultMedia {
    fun read(resultCode:Int,data:Intent?):LibraryMedia? {
        val uri=data?.data ?: return null
        if(resultCode!=Activity.RESULT_OK || !LocalMediaPolicy.validUri(uri.toString())) return null
        val mime=data.type.orEmpty()
        if(!mime.startsWith("video/") && !mime.startsWith("image/")) return null
        return LibraryMedia(uri,data.getStringExtra(Intent.EXTRA_TITLE)?.take(200) ?: "Camera capture",0,mime,System.currentTimeMillis())
    }

    /** Call on IO: a successful Activity result alone does not prove the file is readable. */
    fun verify(context:Context,media:LibraryMedia):LibraryMedia {
        require(LocalMediaPolicy.validUri(media.uri.toString())) { "Capture URI is invalid." }
        val actual=context.contentResolver.getType(media.uri)
        require(actual!=null && actual==media.mimeType && (actual.startsWith("video/") || actual.startsWith("image/"))) {
            "Capture type could not be verified."
        }
        val size=context.contentResolver.openAssetFileDescriptor(media.uri,"r")?.use {it.length}
            ?: error("Capture is unavailable.")
        require(size!=0L) { "Capture is empty." }
        context.contentResolver.openInputStream(media.uri)?.use {require(it.read()!=-1) {"Capture is empty."}}
            ?: error("Capture is unavailable.")
        MediaLibraryIndexer.register(context,media.uri,media.displayName,actual,size)
        return media.copy(sizeBytes=size.coerceAtLeast(0))
    }
}
