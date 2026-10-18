package com.vidgravity.app

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CreativeIntegrationTest {
    @Test fun realFrameAndPcmAnalysisFindScenesAndSilenceWithoutChangingSource():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=studioFixture("creative-source.mp4"); val hash=DownloadApi.sha256(file)
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,file))
        val events=mutableListOf<Float>()
        val analysis=withTimeout(90_000) {CreativeAnalyzer.analyze(context,clip) {_,progress -> events+=progress}}
        assertTrue(analysis.frames.isNotEmpty()); assertTrue(analysis.scenes.isNotEmpty())
        assertTrue("Expected synthetic silence, got ${analysis.silence}",analysis.silence.any {it.startMs<=1400 && it.endMs>=2000})
        assertTrue(analysis.audio.isNotEmpty()); assertEquals(1f,events.last(),0.0001f)
        val source=StudioProject(title="Smart source",clips=listOf(clip))
        val cut=CreativeRules.derivedCut(source,clip,CreativeRules.keepAudible(analysis.analyzedMs,analysis.silence),"Smart cut")
        assertNotEquals(source.id,cut.id); assertTrue(cut.durationMs<source.durationMs)
        assertEquals(hash,DownloadApi.sha256(file))
        val started=CompletableDeferred<Unit>()
        val cancel=launch(Dispatchers.IO) {CreativeAnalyzer.analyze(context,clip) {_,_ -> started.complete(Unit)}}
        withTimeout(10_000) {started.await()}
        cancel.cancelAndJoin(); assertTrue(cancel.isCancelled); assertEquals(hash,DownloadApi.sha256(file))
    }
    @Test fun editablePortraitTemplateAndSoftFocusRenderAtAligned720By1280():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=studioFixture("native-source.mp4")
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,file)).copy(frame=CreativeRules.portraitFrame(16f/9f,0.6f))
        val source=StudioProject(title="Template source",clips=listOf(clip),tools=StudioTools(soften=1f))
        val next=CreativeTemplates.create(source,CreativeTemplates.all.first(),"SPRINT 4",2)
        val repository=StudioRepository(context); val output=File(context.cacheDir,"creative-${UUID.randomUUID()}.mp4")
        try {
            repository.save(next); assertEquals(next,repository.load(next.id))
            withTimeout(120_000) {NativeStudioRenderer.render(context,next,output) {}}
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(output.absolutePath)
                assertEquals("720",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                assertEquals("1280",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                assertEquals("yes",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Unexpected template duration $duration",duration in 1750..2250)
                val bitmap=reader.getFrameAtTime(500_000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                var brightness=0L; var count=0
                for(y in 0 until bitmap.height step 16) for(x in 0 until bitmap.width step 16) {val p=bitmap.getPixel(x,y); brightness+=android.graphics.Color.red(p)+android.graphics.Color.green(p)+android.graphics.Color.blue(p); count++}
                bitmap.recycle(); assertTrue("Portrait output is black",brightness/count>15)
            } finally {reader.release()}
        } finally {repository.delete(next.id); output.delete()}
    }
    @Test fun thumbnailProjectsReopenAndPublishAHashedPngToLibrary():Unit=runBlocking(Dispatchers.IO) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=studioFixture("creative-source.mp4")
        val p=StudioProject(title="Thumbnail source",clips=listOf(StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source))))
        val t=StudioThumbnail(projectId=p.id,source=p.clips[0].uri,durationMs=p.clips[0].sourceDurationMs,timeMs=500,title="SPRINT 4",subtitle="OFFLINE",badge="TIP",color=0xFFFFFF00.toInt())
        val repository=ThumbnailRepository(context); val copied=File(context.cacheDir,"thumbnail-check.png")
        var published:PublishedMedia?=null
        try {
            repository.save(t); assertEquals(t,repository.load(t.id)); assertTrue(repository.list(p.id).any {it.id==t.id})
            val newer=t.copy(subtitle="Newer settings",updatedAt=t.updatedAt+1000)
            repository.save(newer); repository.save(t)
            assertEquals("Older autosave must not replace newer settings",newer,repository.load(t.id))
            val media=ThumbnailRenderer.publish(context,t); published=media
            context.contentResolver.openInputStream(media.uri)!!.use {input -> copied.outputStream().use {input.copyTo(it)}}
            assertEquals(media.sha256,DownloadApi.sha256(copied))
            val bitmap=BitmapFactory.decodeFile(copied.absolutePath)
            assertEquals(1280,bitmap.width); assertEquals(720,bitmap.height); bitmap.recycle()
            assertTrue(MediaLibraryIndexer.list(context).any {it.uri==media.uri && it.mimeType=="image/png"})
        } finally {
            published?.let {MediaLibraryIndexer.delete(context,LibraryMedia(it.uri,"Thumbnail",copied.length(),"image/png",0))}
            repository.delete(t.id); copied.delete()
        }
    }
}
