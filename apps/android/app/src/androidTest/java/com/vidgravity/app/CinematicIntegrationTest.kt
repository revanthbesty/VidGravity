package com.vidgravity.app

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CinematicIntegrationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun clip(name:String)=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture(name)))
    private suspend fun render(p:StudioProject,check:(MediaMetadataRetriever,File)->Unit) {
        val output=File(context.cacheDir,"cinema-${UUID.randomUUID()}.mp4");val reader=MediaMetadataRetriever()
        try {
            withTimeout(120_000) {NativeStudioRenderer.render(context,p,output) {}}
            reader.setDataSource(output.path)
            assertEquals("yes",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
            val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Wrong output duration $duration vs ${p.durationMs}",kotlin.math.abs(duration-p.durationMs)<350)
            check(reader,output)
        } catch(failure:Throwable) {
            runCatching {
                val dir=File(context.getExternalFilesDir(null),"smoke-evidence/sprint12/${p.title.replace(Regex("[^a-zA-Z0-9-]"),"_")}-${UUID.randomUUID()}").apply {mkdirs()}
                if(output.exists()) output.copyTo(File(dir,"failed-output.mp4"))
                for(us in listOf(200_000L,600_000L,1_200_000L,2_200_000L)) {
                    val b=reader.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    try {File(dir,"frame-$us.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {b.recycle()}
                }
            }
            throw failure
        } finally {reader.release();output.delete()}
    }
    private fun color(reader:MediaMetadataRetriever,timeUs:Long=500_000,x:Float=0.5f,y:Float=0.5f):Int {
        val b=reader.getFrameAtTime(timeUs,MediaMetadataRetriever.OPTION_CLOSEST)!!
        return try {b.getPixel((x*b.width).toInt().coerceIn(0,b.width-1),(y*b.height).toInt().coerceIn(0,b.height-1))} finally {b.recycle()}
    }
    @Test fun warmColorGradeAndZeroLookIntensityRenderRealPixelsAfterSaveReopen():Unit=runBlocking {
        val file=studioFixture("smart-quiet.mp4");val hash=DownloadApi.sha256(file)
        val c=clip("smart-quiet.mp4").copy(endMs=1000,cinema=CinemaStyle(CinemaLook.WARM,grade=CinemaGrade(exposure=0.15f,temperature=0.5f)))
        val p=StudioProject(title="Cinematic warm grade",clips=listOf(c),originalVolume=0f);val repository=StudioRepository(context)
        try {
            repository.save(p);assertEquals(p,repository.load(p.id))
            render(repository.load(p.id)) {r,_->val pixel=color(r);assertTrue("Warm grade was missing: $pixel",Color.red(pixel)>Color.blue(pixel)+25)}
            val zero=p.copy(clips=listOf(c.copy(cinema=CinemaStyle(CinemaLook.WARM,intensity=0f))))
            render(zero) {r,_->val pixel=color(r);assertTrue("Zero intensity did not restore neutral source",kotlin.math.abs(Color.red(pixel)-Color.blue(pixel))<10)}
        } finally {repository.delete(p.id)}
        assertEquals(hash,DownloadApi.sha256(file))
    }
}
