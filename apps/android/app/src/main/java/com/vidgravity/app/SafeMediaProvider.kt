package com.vidgravity.app

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File

/** Retain existing persisted media URI paths, but never expose arbitrary cache metadata. */
class SafeMediaProvider:FileProvider() {
    private fun validate(uri:Uri) {
        require(uri.scheme=="content" && uri.authority==context!!.packageName+".files")
        val parts=uri.pathSegments
        require(parts.isNotEmpty() && parts.none {it==".." || it=="." || it.contains('\\') || it.contains('/')}) {"Invalid shared path"}
        if(parts.first()=="snap_studio_cache") {
            val root=context!!.cacheDir.canonicalFile
            val target=File(root,parts.drop(1).joinToString("/")).canonicalFile
            require(target.path.startsWith(root.path+File.separator) && target.extension.lowercase() in setOf("mp4","webm","mkv","jpg","jpeg","png","webp","m4a","mp3","wav","flac")) {"Only cached media can be shared"}
        }
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor? {
        validate(uri);require(mode=="r") {"Shared media is read-only"};return super.openFile(uri,mode)
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        validate(uri);return super.query(uri,projection,selection,selectionArgs,sortOrder)
    }
    override fun getType(uri:Uri):String? {validate(uri);return super.getType(uri)}
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int {validate(uri);return super.delete(uri,selection,selectionArgs)}
}
