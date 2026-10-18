package com.vidgravity.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.util.UUID

/** One local kit; immutable imported logo files keep older projects reproducible. Call on IO. */
internal class CreatorBrandRepository(private val context:Context) {
    private val file=File(context.filesDir,"creator-brand.json")
    fun load():CreatorBrand = synchronized(lock) {
        if(!file.exists() && !File(file.path+".bak").exists()) CreatorBrand()
        else CreatorBrand.decode(AtomicFile(file).openRead().bufferedReader().use {it.readText()})
    }
    fun save(brand:CreatorBrand):Unit = synchronized(lock) {
        val text=brand.json(); val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try {stream.write(text.toByteArray(Charsets.UTF_8));atomic.finishWrite(stream)} catch(failure:Exception) {atomic.failWrite(stream);throw failure}
    }
    fun importLogo(uri:Uri):String {
        require(uri.scheme=="content")
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)!!.use {BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth<=32768 && bounds.outHeight<=32768) {"Choose a supported logo image."}
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>512) sample*=2
        val bitmap=context.contentResolver.openInputStream(uri)!!.use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})} ?: error("Logo could not be decoded.")
        val root=File(context.filesDir,"media/brand").apply {check(exists() || mkdirs())}
        val target=File(root,"${UUID.randomUUID()}.png")
        try {target.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};return MediaLibraryIndexer.localUri(context,target).toString()}
        catch(failure:Exception) {target.delete();throw failure} finally {bitmap.recycle()}
    }
    companion object {private val lock=Any()}
}
