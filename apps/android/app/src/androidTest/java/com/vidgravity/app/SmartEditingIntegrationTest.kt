package com.vidgravity.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SmartEditingIntegrationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(name:String)=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture(name)))
    private suspend fun render(p:StudioProject,check:(MediaMetadataRetriever,File)->Unit) {
        val file=File(context.cacheDir,"smart-${UUID.randomUUID()}.mp4");val reader=MediaMetadataRetriever()
        try {
            withTimeout(120_000) {NativeStudioRenderer.render(context,p,file) {}}
            reader.setDataSource(file.path);assertEquals("yes",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));check(reader,file)
        } catch(failure:Throwable) {
            runCatching {val dir=File(context.getExternalFilesDir(null),"smoke-evidence/sprint11/${p.title.replace(Regex("[^a-zA-Z0-9-]"),"_")}").apply {mkdirs()}
                file.copyTo(File(dir,"failed-output.mp4"),overwrite=true)
                for(us in listOf(300_000L,1_600_000L)) {val frame=reader.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    try {File(dir,"frame-$us.png").outputStream().use {frame.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {frame.recycle()}}}
            throw failure
        } finally {reader.release();file.delete()}
    }
    private fun pixel(r:MediaMetadataRetriever,time:Long):Int {
        val b=r.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST)!!
        return try {b.getPixel(b.width/2,b.height/2)} finally {b.recycle()}
    }
    @Test fun silenceReviewRetimeSaveReopenAndExportPreserveSourceAndUncutScenes():Unit=runBlocking {
        val file=studioFixture("creative-source.mp4");val c=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,file));val hash=DownloadApi.sha256(file)
        val notes=withTimeout(90_000) {SmartAnalyzer.analyze(context,c) {_,_->}}
        assertTrue(notes.silence.isNotEmpty());assertTrue(notes.scenes.isNotEmpty())
        val p=StudioProject(title="Silence review",clips=listOf(c.copy(smart=notes)))
        // AAC priming/padding can make container duration 5023 ms for the nominal 5 s fixture.
        // Before approval, preserve the exact imported source duration rather than assuming 5000.
        assertEquals(c.playbackDurationMs,p.durationMs)
        val next=SmartCuts.remove(p,0,notes.silence);val repository=StudioRepository(context)
        try {
            repository.save(next);val saved=repository.load(next.id);assertEquals(next,saved)
            assertTrue(saved.durationMs<c.playbackDurationMs);assertEquals(c.endMs,saved.clips.last().endMs)
            render(saved) {r,_->
                val actual=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Wrong reviewed-cut duration $actual vs ${saved.durationMs}",kotlin.math.abs(actual-saved.durationMs)<350)
                assertTrue("Opening white scene was lost",Color.red(pixel(r,200_000))>210)
                val tail=pixel(r,(actual-300)*1000);assertTrue("Uncut red tail was lost",Color.red(tail)>150 && Color.blue(tail)<80)
            }
        } finally {repository.delete(next.id)}
        assertEquals(hash,DownloadApi.sha256(file))
    }
    @Test fun detectedScenesSplitSaveAndRetainActualScenePixels():Unit=runBlocking {
        val c=source("creative-source.mp4");val notes=withTimeout(90_000) {SmartAnalyzer.analyze(context,c) {_,_->}}
        val p=StudioProject(title="Scene splits",clips=listOf(c.copy(smart=notes)),originalVolume=0f)
        val next=SmartCuts.split(p,0,notes.scenes.first());val repository=StudioRepository(context)
        try {
            repository.save(next);val saved=repository.load(next.id);assertEquals(notes,saved.clips.last().smart);assertEquals(2,saved.clips.size)
            render(saved) {r,_->assertTrue(Color.red(pixel(r,500_000))>210);assertTrue(Color.red(pixel(r,2_500_000))<25)}
        } finally {repository.delete(next.id)}
    }
    @Test fun manualPanKeysPersistAndMoveTheEncodedPortraitBetweenRealSourceRegions():Unit=runBlocking {
        val c=source("smart-pan.mp4");val crop=SmartFraming.frame(16f/9f,StudioAspect.PORTRAIT)
        val keys=listOf(SmartFrameKey(0,0.25f,0.5f),SmartFrameKey(1000,0.25f,0.5f),SmartFrameKey(1100,0.75f,0.5f),SmartFrameKey(2000,0.75f,0.5f))
        val p=StudioProject(title="Pan output",clips=listOf(c.copy(frame=crop,framingKeys=keys)),tools=StudioTools(aspect=StudioAspect.PORTRAIT))
        val repository=StudioRepository(context)
        try {
            repository.save(p);assertEquals(p,repository.load(p.id))
            render(repository.load(p.id)) {r,_->
                assertEquals("720",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));assertEquals("1280",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                val early=pixel(r,300_000);val late=pixel(r,1_600_000)
                assertTrue("Pan did not show blue first: $early",Color.blue(early)>150 && Color.red(early)<80)
                assertTrue("Pan did not show red later: $late",Color.red(late)>150 && Color.blue(late)<80)
            }
        } finally {repository.delete(p.id)}
    }
    @Test fun measuredNormalizationChangesActualAacPcmRatherThanOnlyUiState():Unit=runBlocking {
        val c=source("smart-quiet.mp4");val notes=withTimeout(90_000) {SmartAnalyzer.analyze(context,c) {_,_->}}
        val gain=SmartRanking.normalization(notes.rms,notes.peak);assertTrue("Expected bounded boost for quiet sine, got $gain",gain>=3.5f)
        var before=0.0
        render(StudioProject(title="Before gain",clips=listOf(c))) {_,file->before=studioAudioLevels(file).second;assertTrue(before>0.005)}
        val p=StudioProject(title="After gain",clips=listOf(c.copy(audioGain=gain,smart=notes)));val repository=StudioRepository(context)
        try {
            repository.save(p);assertEquals(gain,repository.load(p.id).clips.single().audioGain,0.0001f)
            render(repository.load(p.id)) {_,file->val after=studioAudioLevels(file).second;assertTrue("AAC gain missing: before=$before after=$after gain=$gain",after/before in 3.0..4.8)}
        } finally {repository.delete(p.id)}
    }
    @Test fun rankedThumbnailFrameBecomesAnEditableHashedPngInLibrary():Unit=runBlocking(Dispatchers.IO) {
        val c=source("creative-source.mp4");val notes=withTimeout(90_000) {SmartAnalyzer.analyze(context,c) {_,_->}}
        val best=notes.thumbnails.first();assertTrue(best.startMs in c.startMs until c.endMs)
        val p=StudioProject(title="Ranked thumbnail",clips=listOf(c));val t=StudioThumbnail(projectId=p.id,source=c.uri,durationMs=c.sourceDurationMs,timeMs=best.startMs,title="SMART FRAME")
        val repository=ThumbnailRepository(context);var published:PublishedMedia?=null;val copy=File(context.cacheDir,"smart-thumb-${UUID.randomUUID()}.png")
        try {
            repository.save(t);assertEquals(t,repository.load(t.id));published=ThumbnailRenderer.publish(context,repository.load(t.id))
            context.contentResolver.openInputStream(published!!.uri)!!.use {input->copy.outputStream().use {input.copyTo(it)}}
            assertEquals(published!!.sha256,DownloadApi.sha256(copy));val image=BitmapFactory.decodeFile(copy.path)
            assertEquals(1280,image.width);assertEquals(720,image.height);image.recycle()
            assertTrue(MediaLibraryIndexer.list(context).any {it.uri==published!!.uri && it.mimeType=="image/png"})
        } finally {published?.let {MediaLibraryIndexer.delete(context,LibraryMedia(it.uri,"Smart thumbnail",copy.length(),"image/png",0))};repository.delete(t.id);copy.delete()}
    }
    @Test fun cutsBeforeMusicPreserveItsFadeAcrossUnchangedVideoBoundaries():Unit=runBlocking {
        val videoFile=studioFixture("smart-pan.mp4");val musicFile=studioFixture("smart-quiet.mp4")
        val sourceHashes=listOf(DownloadApi.sha256(videoFile),DownloadApi.sha256(musicFile))
        val video=source("smart-pan.mp4");val music=source("smart-quiet.mp4")
        val clips=List(5) {video.copy(id=UUID.randomUUID().toString(),endMs=1000)}
        val lane=StudioAudioLane(uri=music.uri,name="Fade across video boundary",sourceDurationMs=music.sourceDurationMs,
            endMs=1800,offsetMs=3000,volume=1f,fadeInMs=1200,fadeOutMs=200)
        val p=StudioProject(title="Uncut music reference",clips=clips,originalVolume=0f,tools=StudioTools(audio=listOf(lane)))
        val next=SmartCuts.remove(p,0,listOf(CreativeRange(200,400)))
        // The music starts after the removed range. Its source, fade and identity
        // must survive intact while its composition offset moves back 200 ms.
        assertEquals(listOf(lane.copy(offsetMs=2800)),next.tools.audio)
        val repository=StudioRepository(context)
        suspend fun levels(project:StudioProject):List<AudioObservation> {
            val copy=File(context.cacheDir,"continuity-${UUID.randomUUID()}.mp4")
            try {
                var duration=0L
                render(project) {reader,file->
                    duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    assertTrue("Export duration $duration vs ${project.durationMs}",kotlin.math.abs(duration-project.durationMs)<350)
                    file.copyTo(copy,overwrite=true)
                }
                return withTimeout(60_000) {CreativeAnalyzer.waveform(context,StudioAudioLane(uri=MediaLibraryIndexer.localUri(context,copy).toString(),name="Decoded export",sourceDurationMs=duration))}
            } finally {copy.delete()}
        }
        try {
            repository.save(next);val saved=repository.load(next.id);assertEquals(next,saved)
            val reference=levels(p);val actual=levels(saved)
            for(time in listOf(4000L,4400L)) {
                val before=reference.first {it.startMs==time}.rms
                val after=actual.first {it.startMs==time-200}.rms
                assertTrue("Reference music missing at $time: $before",before>0.005f)
                assertTrue("Fade restarted at an uncut boundary: $time before=$before after=$after",after/before in 0.8f..1.2f)
            }
            assertTrue("Music began before its retimed offset",actual.filter {it.endMs<=2600}.all {it.rms<0.001f})
        } finally {repository.delete(next.id)}
        assertEquals(sourceHashes,listOf(DownloadApi.sha256(videoFile),DownloadApi.sha256(musicFile)))
    }
    @Test fun savedAnalysisCacheAndCancellationKeepProjectAndMediaIntact():Unit=runBlocking {
        val file=studioFixture("creative-source.mp4");val hash=DownloadApi.sha256(file);val c=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,file))
        val notes=withTimeout(90_000) {SmartAnalyzer.analyze(context,c) {_,_->}};val events=mutableListOf<String>()
        val cached=SmartAnalyzer.analyze(context,c.copy(smart=notes)) {label,_->events+=label}
        assertEquals(notes,cached);assertEquals(1,events.size);assertTrue(events.single().contains("saved"))
        val started=CompletableDeferred<Unit>();val task=launch {SmartAnalyzer.analyze(context,c,force=true) {_,_->started.complete(Unit)}}
        withTimeout(15_000) {started.await()};task.cancelAndJoin();assertTrue(task.isCancelled)
        assertEquals(hash,DownloadApi.sha256(file));assertNull(c.smart)
        val after=withTimeout(90_000) {SmartAnalyzer.analyze(context,c.copy(smart=notes)) {_,_->}};assertEquals(notes,after)
    }
}
