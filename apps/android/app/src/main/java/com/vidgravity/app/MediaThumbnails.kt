package com.vidgravity.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

internal object MediaThumbnails {
    private val cache=object:LruCache<String,Bitmap>(8*1024*1024) {override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    private val decoding=Semaphore(2)
    val decodeCount=AtomicInteger()
    fun clear() {cache.evictAll();decodeCount.set(0)}
    suspend fun load(context:Context,media:LibraryMedia):Bitmap?=withContext(Dispatchers.IO) {
        val key="${media.uri}:${media.sizeBytes}:${media.createdAtMs}"
        cache.get(key)?.let {return@withContext it}
        decoding.withPermit {
            cache.get(key)?.let {return@withPermit it}
            ensureActive()
            val bitmap=try {
                decodeCount.incrementAndGet()
                when {
                    media.mimeType.startsWith("image/") -> {
                        val b=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                        context.contentResolver.openInputStream(media.uri)?.use {BitmapFactory.decodeStream(it,null,b)}
                        if(b.outWidth<=0 || b.outHeight<=0) null else {
                            var sample=1;while(b.outWidth/sample>320 || b.outHeight/sample>320) sample*=2
                            context.contentResolver.openInputStream(media.uri)?.use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})}
                        }
                    }
                    media.mimeType.startsWith("video/") && Build.VERSION.SDK_INT>=27 -> {
                        val reader=MediaMetadataRetriever();try {reader.setDataSource(context,media.uri);reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,320,180)} finally {reader.release()}
                    }
                    else -> null
                }
            } catch(c:CancellationException) {throw c} catch(e:Exception) {null}
            if(bitmap!=null) cache.put(key,bitmap)
            bitmap
        }
    }
}
@Composable internal fun MediaThumbnail(media:LibraryMedia,modifier:Modifier=Modifier) {
    val context=LocalContext.current;var bitmap by remember(media.uri,media.sizeBytes) {mutableStateOf<Bitmap?>(null)}
    LaunchedEffect(media.uri,media.sizeBytes) {bitmap=MediaThumbnails.load(context,media)}
    Surface(modifier=modifier,color=MaterialTheme.colorScheme.surfaceVariant) {
        bitmap?.let {Image(it.asImageBitmap(),"Preview of ${media.displayName}",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
            ?: Box(Modifier.padding(16.dp)) {Icon(if(media.mimeType.startsWith("audio/")) Icons.Default.MusicNote else Icons.Default.Movie,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)}
    }
}
