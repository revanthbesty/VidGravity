package com.vidgravity.app

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real encoder / persistence checks. Metrics are observations, never device performance promises. */
class ReleaseQaTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun metric(name:String,values:JSONObject) {
        val dir=File(context.getExternalFilesDir(null),"smoke-evidence").apply {mkdirs()}
        values.put("api",android.os.Build.VERSION.SDK_INT)
        File(dir,"$name.json").writeText(values.toString(2))
        android.util.Log.i("VGPerf","$name $values")
    }
    @Test fun fullHdExportHasRequestedCadenceAndIntactSources():Unit=runBlocking {
        val source=studioFixture("native-source.mp4");val sourceHash=DownloadApi.sha256(source)
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source)).copy(endMs=1000)
        val p=StudioProject(title="Release QA",clips=listOf(clip),resolution=StudioResolution.FULL_HD,
            tools=StudioTools(frameRate=15,bitrate=2_000_000))
        val out=File(context.cacheDir,"release-qa.mp4")
        val start=android.os.SystemClock.elapsedRealtime()
        try {
            withTimeout(120_000) {NativeStudioRenderer.render(context,p,out) {}}
            val elapsed=android.os.SystemClock.elapsedRealtime()-start
            val extractor=MediaExtractor()
            try {
                extractor.setDataSource(out.path)
                val video=(0 until extractor.trackCount).first {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")}
                val format=extractor.getTrackFormat(video)
                assertEquals(1080,format.getInteger(MediaFormat.KEY_HEIGHT))
                assertEquals(1920,format.getInteger(MediaFormat.KEY_WIDTH))
                assertEquals("video/avc",format.getString(MediaFormat.KEY_MIME))
                extractor.selectTrack(video)
                val timestamps=mutableListOf<Long>()
                while(extractor.sampleTime>=0) {timestamps.add(extractor.sampleTime);assertTrue(extractor.sampleSize>0);if(!extractor.advance()) break}
                metric("export-fullhd-diagnostics",JSONObject().put("render_ms",elapsed).put("bytes",out.length()).put("format",format.toString()).put("timestamps_us",org.json.JSONArray(timestamps)))
                assertTrue("One-second trim must preserve its video tail: $format",format.getLong(MediaFormat.KEY_DURATION) in 900_000L..1_100_000L)
                assertTrue("Expected approximately 15 encoded samples, got ${timestamps.size}: $timestamps",timestamps.size in 14..17)
                assertTrue(timestamps.zipWithNext().all {(a,b)->b>a})
                val deltas=timestamps.zipWithNext {a,b->b-a}.sorted()
                val median=deltas[deltas.size/2]
                assertTrue("15 FPS sample cadence: $median us",median in 60_000L..74_000L)
                assertEquals(sourceHash,DownloadApi.sha256(source))
                metric("export-fullhd",JSONObject().put("render_ms",elapsed).put("bytes",out.length()).put("samples",timestamps.size).put("median_sample_us",median).put("sha256",DownloadApi.sha256(out)))
            } finally {extractor.release()}
        } catch(failure:AssertionError) {
            val evidence=File(context.getExternalFilesDir(null),"smoke-evidence").apply {mkdirs()}
            if(out.isFile) out.copyTo(File(evidence,"fps-failure.mp4"),overwrite=true)
            throw failure
        } finally {out.delete()}
    }
    @Test fun higherRateSourceStillDropsToRequestedCap():Unit=runBlocking {
        val source=studioFixture("native-fast.mp4");val hash=DownloadApi.sha256(source)
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source)).copy(endMs=1000)
        val p=StudioProject(title="30 to 15 FPS",clips=listOf(clip),tools=StudioTools(frameRate=15))
        val out=File(context.cacheDir,"release-frame-cap.mp4")
        try {
            withTimeout(120_000) {NativeStudioRenderer.render(context,p,out) {}}
            val extractor=MediaExtractor()
            try {
                extractor.setDataSource(out.path)
                val track=(0 until extractor.trackCount).first {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")}
                extractor.selectTrack(track);val times=mutableListOf<Long>()
                while(extractor.sampleTime>=0) {times.add(extractor.sampleTime);if(!extractor.advance()) break}
                metric("export-frame-cap",JSONObject().put("samples",times.size).put("timestamps_us",org.json.JSONArray(times)))
                assertTrue("30FPS source must be reduced near15FPS, got $times",times.size in 14..17)
                assertTrue(times.zipWithNext().all {(a,b)->b-a in 60_000L..74_000L})
                assertEquals(hash,DownloadApi.sha256(source))
            } finally {extractor.release()}
        } finally {out.delete()}
    }
    @Test fun cancelledEncoderReleasesResourcesAndNextRenderSucceeds():Unit=runBlocking {
        val source=studioFixture("native-source.mp4");val hash=DownloadApi.sha256(source)
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source))
        val p=StudioProject(title="Cancel recovery",clips=(0 until 12).map {clip.copy(id=java.util.UUID.randomUUID().toString())})
        val partial=File(context.cacheDir,"release-cancel.mp4");val retry=File(context.cacheDir,"release-retry.mp4")
        val started=CompletableDeferred<Unit>()
        val job=launch {NativeStudioRenderer.render(context,p,partial) {started.complete(Unit)}}
        try {
            withTimeout(30_000) {started.await()}
            job.cancelAndJoin();assertTrue(job.isCancelled)
            // Callers own partial files; worker finally deletes its output. Do not publish a partial.
            partial.delete()
            withTimeout(120_000) {NativeStudioRenderer.render(context,p.copy(clips=listOf(clip.copy(endMs=1000))),retry) {}}
            assertTrue(retry.length()>1000);assertEquals(hash,DownloadApi.sha256(source))
        } finally {job.cancelAndJoin();partial.delete();retry.delete()}
    }
    @Test fun maximumTimelineAndLargeRepositoryRemainRoundTrippable() {
        val repository=StudioRepository(context)
        val clip=StudioClip(uri="content://media/video/12",name="Fixture",sourceDurationMs=150_000)
        val p=StudioProject(title="Large timeline",clips=(0 until 12).map {clip.copy(id=java.util.UUID.randomUUID().toString())},
            tools=StudioTools(captions=(0 until 100).map {StudioCaption(it*1000L,it*1000L+900,"Cue $it")}))
        val projects=(0 until 60).map {p.copy(id=java.util.UUID.randomUUID().toString(),title="QA $it")}
        try {
            val start=android.os.SystemClock.elapsedRealtime();projects.forEach {repository.save(it)}
            val saved=android.os.SystemClock.elapsedRealtime();val scan=repository.scan()
            val scanned=android.os.SystemClock.elapsedRealtime()
            val ids=projects.map {it.id}.toSet()
            assertEquals(60,scan.projects.count {it.id in ids});assertEquals(projects[0],repository.load(projects[0].id))
            metric("project-scan",JSONObject().put("projects",60).put("clips_per_project",12).put("cues_per_project",100).put("save_ms",saved-start).put("scan_ms",scanned-saved))
        } finally {projects.forEach {repository.delete(it.id)}}
    }
}
