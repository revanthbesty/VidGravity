package com.vidgravity.app

import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StudioAdvancedTest {
    @Test fun speedGeometryCaptionsAndOffsetAudioExportThroughRealEncoder(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=studioFixture("native-source.mp4"); val music=studioFixture("native-music.wav")
        val uri=MediaLibraryIndexer.localUri(context,source).toString()
        val audio=MediaLibraryIndexer.localUri(context,music).toString()
        val project=StudioProject(title="Advanced render",originalVolume=0f,filter=StudioFilter.MONO,clips=listOf(
            StudioClip(uri=uri,name=source.name,sourceDurationMs=2000,endMs=1000,speed=2f),
            StudioClip(uri=uri,name=source.name,sourceDurationMs=2000,startMs=1000,speed=0.5f)),
            tools=StudioTools(brightness=0.1f,contrast=0.1f,saturation=-20f,rotation=90,crop=0.1f,aspect=StudioAspect.SQUARE,
                captionColor=0xFFFFFF00.toInt(),captionSize=48,captions=listOf(StudioCaption(500,2000,"SPRINT 3")),
                audio=listOf(StudioAudioLane(uri=audio,name="A1",sourceDurationMs=1000,offsetMs=750,fadeInMs=100,fadeOutMs=100),
                    StudioAudioLane(uri=audio,name="A2",sourceDurationMs=1000,startMs=100,offsetMs=1250,volume=0.2f)),frameRate=15,bitrate=2_000_000))
        val output=File(context.cacheDir,"advanced-${UUID.randomUUID()}.mp4")
        try {
            withTimeout(120_000) {NativeStudioRenderer.render(context,project,output) {}}
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(output.absolutePath)
                val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Speed-adjusted timeline duration $duration",duration in 2300..2800)
                assertEquals("720",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                assertEquals("720",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                assertEquals("yes",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                val before=reader.getFrameAtTime(100_000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                var earlyYellow=0
                for(y in 0 until before.height step 4) for(x in 0 until before.width step 4) {
                    val pixel=before.getPixel(x,y)
                    if(android.graphics.Color.red(pixel)>180 && android.graphics.Color.green(pixel)>180 && android.graphics.Color.blue(pixel)<90) earlyYellow++
                }
                before.recycle()
                assertEquals("Caption appeared before its cue",0,earlyYellow)
                val frame=reader.getFrameAtTime(1_000_000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                var yellow=0
                for(y in frame.height/2 until frame.height step 2) for(x in 0 until frame.width step 2) {
                    val pixel=frame.getPixel(x,y)
                    if(android.graphics.Color.red(pixel)>180 && android.graphics.Color.green(pixel)>180 && android.graphics.Color.blue(pixel)<90) yellow++
                }
                assertTrue("Timed yellow caption did not reach output",yellow>30)
                frame.recycle()
            } finally {reader.release()}
            val levels=studioAudioLevels(output)
            assertTrue("Offset lane should begin with silence: $levels",levels.first < 0.005)
            assertTrue("Offset audio was not mixed into output: $levels",levels.second > 0.01)
        } finally {output.delete()}
    }
    @Test fun originalAudioRemainsSynchronizedAtDoubleSpeed(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=studioFixture("native-source.mp4")
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source)).copy(speed=2f)
        val project=StudioProject(title="Speed audio",clips=listOf(clip))
        val output=File(context.cacheDir,"speed-audio-${UUID.randomUUID()}.mp4")
        try {
            withTimeout(120_000) {NativeStudioRenderer.render(context,project,output) {}}
            val extractor=android.media.MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val durations=(0 until extractor.trackCount).associate {index ->
                    val format=extractor.getTrackFormat(index)
                    format.getString(android.media.MediaFormat.KEY_MIME)!!.substringBefore('/') to format.getLong(android.media.MediaFormat.KEY_DURATION)
                }
                assertTrue("Speed video duration: $durations",durations.getValue("video") in 850_000..1_200_000)
                assertTrue("Speed audio duration: $durations",durations.getValue("audio") in 850_000..1_200_000)
                assertTrue("Audio/video lost sync: $durations",kotlin.math.abs(durations.getValue("video")-durations.getValue("audio"))<150_000)
            } finally {extractor.release()}
        } finally {output.delete()}
    }
    @Test fun duplicateTrashRestorePreserveOriginalAndEditableContent() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=StudioRepository(context)
        val source=studioFixture("native-source.mp4")
        val project=StudioProject(title="Project safety",clips=listOf(StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source))))
        var copy: StudioProject?=null
        try {
            repository.save(project); copy=repository.duplicate(project)
            assertNotEquals(project.id,copy.id); assertEquals(project.clips,repository.load(copy.id).clips)
            repository.trash(project.id)
            assertFalse(repository.list().any {it.id==project.id}); assertTrue(repository.trashed().any {it.id==project.id})
            assertTrue(source.exists()); repository.restore(project.id); assertEquals(project,repository.load(project.id))
            assertFalse(repository.trashed().any {it.id==project.id})
        } finally {repository.delete(project.id); copy?.let {repository.delete(it.id)}}
    }
}

/** Decode the actual AAC export, checking the silent lead-in and audible delayed lane. */
internal fun studioAudioLevels(file:File): Pair<Double,Double> {
    val extractor=android.media.MediaExtractor(); var codec:android.media.MediaCodec?=null
    try {
        extractor.setDataSource(file.absolutePath)
        val track=(0 until extractor.trackCount).first {extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)!!.startsWith("audio/")}
        extractor.selectTrack(track); val format=extractor.getTrackFormat(track)
        codec=android.media.MediaCodec.createDecoderByType(format.getString(android.media.MediaFormat.KEY_MIME)!!)
        codec.configure(format,null,null,0); codec.start()
        val info=android.media.MediaCodec.BufferInfo(); var inputDone=false; var outputDone=false
        var early=0.0; var audible=0.0; var earlyCount=0; var audibleCount=0
        val deadline=System.nanoTime()+30_000_000_000L
        while(!outputDone && System.nanoTime()<deadline) {
            if(!inputDone) {val i=codec.dequeueInputBuffer(10_000); if(i>=0) {
                val buffer=codec.getInputBuffer(i)!!; val size=extractor.readSampleData(buffer,0)
                if(size<0) {codec.queueInputBuffer(i,0,0,0,android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true}
                else {codec.queueInputBuffer(i,0,size,extractor.sampleTime,0); extractor.advance()}
            }}
            val index=codec.dequeueOutputBuffer(info,10_000)
            if(index>=0) {
                val buffer=codec.getOutputBuffer(index)!!.duplicate().order(java.nio.ByteOrder.nativeOrder())
                buffer.position(info.offset); buffer.limit(info.offset+info.size)
                while(buffer.remaining()>=2) {
                    val value=buffer.short.toDouble()/32768
                    if(info.presentationTimeUs in 100_000..400_000) {early+=value*value; earlyCount++}
                    if(info.presentationTimeUs in 1_000_000..1_400_000) {audible+=value*value; audibleCount++}
                }
                outputDone=info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                codec.releaseOutputBuffer(index,false)
            }
        }
        assertTrue("AAC decode did not finish",outputDone); assertTrue(earlyCount>0 && audibleCount>0)
        return kotlin.math.sqrt(early/earlyCount) to kotlin.math.sqrt(audible/audibleCount)
    } finally {runCatching {codec?.stop()}; codec?.release(); extractor.release()}
}
