package com.vidgravity.app

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal object StudioFreezeFrame {
    /** Source frame only: project effects remain editable and are applied once on final export. */
    suspend fun create(context: Context, clip: StudioClip, outputOffsetMs: Long): StudioClip {
        val still=File(context.cacheDir,"freeze-${UUID.randomUUID()}.png")
        val output=File(File(context.filesDir,"media/freezes").apply {mkdirs()},"freeze-${UUID.randomUUID()}.mp4")
        try {
            withContext(Dispatchers.IO) {
                val sourceTime=(clip.startMs+outputOffsetMs.coerceIn(0,clip.playbackDurationMs-1)*clip.speed).toLong()
                val frame=ThumbnailRenderer.frame(context,clip.uri,sourceTime)
                try {still.outputStream().use {check(frame.compress(Bitmap.CompressFormat.PNG,100,it))}} finally {frame.recycle()}
            }
            NativeStudioRenderer.renderStill(context,still,output)
            return withContext(Dispatchers.IO) {
                StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,output)).copy(name="Freeze · ${clip.name}",frame=clip.frame,opacity=clip.opacity)
            }
        } catch(failure: Throwable) {output.delete();throw failure}
        finally {still.delete()}
    }
}
