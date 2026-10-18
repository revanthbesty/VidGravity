package com.vidgravity.app

import android.app.Activity
import android.content.Intent

internal object CameraResultMedia {
    fun read(resultCode:Int,data:Intent?):LibraryMedia? {
        val uri=data?.data ?: return null
        if(resultCode!=Activity.RESULT_OK || uri.scheme!="content") return null
        val mime=data.type.orEmpty()
        if(!mime.startsWith("video/") && !mime.startsWith("image/")) return null
        return LibraryMedia(uri,data.getStringExtra(Intent.EXTRA_TITLE)?.take(200) ?: "Camera capture",0,mime,System.currentTimeMillis())
    }
}
