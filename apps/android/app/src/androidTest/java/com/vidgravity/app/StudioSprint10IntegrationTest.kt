package com.vidgravity.app

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StudioSprint10IntegrationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun clip(name: String)=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture(name)))
    private suspend fun render(p: StudioProject, check: (MediaMetadataRetriever,File)->Unit) {
        val out=File(context.cacheDir,"sprint10-${UUID.randomUUID()}.mp4")
        val reader=MediaMetadataRetriever()
        try {
            withTimeout(120_000) {NativeStudioRenderer.render(context,p,out) {}}
            reader.setDataSource(out.path)
            assertEquals("yes",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
            assertEquals("720",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            check(reader,out)
        } finally {reader.release();out.delete()}
    }
    private fun brightness(frame: Bitmap): Double {
        var sum=0L;var n=0
        for(y in 0 until frame.height step 16) for(x in 0 until frame.width step 16) {val c=frame.getPixel(x,y);sum+=Color.red(c)+Color.green(c)+Color.blue(c);n++}
        return sum/(n*3.0)
    }
    private fun read(reader: MediaMetadataRetriever, us: Long, check: (Bitmap)->Unit) {
        val bitmap=reader.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST)!!
        try {check(bitmap)} finally {bitmap.recycle()}
    }
    @Test fun scenarioATrimOpacityAndAdjustableFadeReachEncodedPixels():Unit=runBlocking {
        val source=clip("creative-source.mp4").copy(startMs=500,endMs=1500,opacity=0.5f)
        val hash=DownloadApi.sha256(studioFixture("creative-source.mp4"))
        render(StudioProject(title="A trim fade",clips=listOf(source),fadeEdges=true,originalVolume=0f,tools=StudioTools(fadeDurationMs=400))) {r,_ ->
            assertTrue(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 800..1200)
            read(r,500_000) {assertTrue("Opacity missing",brightness(it) in 165.0..200.0)}
            read(r,100_000) {assertTrue("Adjustable fade missing",brightness(it)<130)}
        }
        assertEquals(hash,DownloadApi.sha256(studioFixture("creative-source.mp4")))
    }
    @Test fun scenarioBSplitAndRearrangeChangeActualRenderedSceneOrder():Unit=runBlocking {
        val source=clip("creative-source.mp4").copy(endMs=4000)
        val split=StudioProject(title="B split reorder",clips=source.split(2000),originalVolume=0f)
        render(StudioEdits.move(split,0,1)) {r,_ ->
            assertTrue(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 3700..4300)
            read(r,500_000) {assertTrue("Reordered first scene is not black",brightness(it)<15)}
            read(r,2_500_000) {assertTrue("Reordered second scene is not white",brightness(it)>225)}
        }
    }
    @Test fun scenariosCDRichTextAndDelayedMusicPersistReopenAndRenderAtOutputTime():Unit=runBlocking {
        val source=clip("native-source.mp4");val music=studioFixture("native-music.wav")
        val layers=listOf(StudioTextLayer(text="SPRINT TEN",startMs=750,endMs=1750,font="monospace",color=Color.CYAN,background=0xFFAA0000.toInt(),size=56,x=0.3f,y=-0.1f,rotation=15f,animation=CreatorAnimation.SLIDE),
            StudioTextLayer(text="FRONT",startMs=750,endMs=1750,color=Color.YELLOW,size=42,x=-0.5f,y=0.4f,animation=CreatorAnimation.FADE))
        val p=StudioProject(title="C D reopen",clips=listOf(source.copy(endMs=1000,speed=2f),source.copy(id=UUID.randomUUID().toString(),startMs=1000,speed=0.5f)),filter=StudioFilter.MONO,originalVolume=0f,
            tools=StudioTools(textLayers=layers,audio=listOf(StudioAudioLane(uri=MediaLibraryIndexer.localUri(context,music).toString(),name="Music",sourceDurationMs=1000,offsetMs=750,fadeInMs=100,fadeOutMs=100))))
        val repository=StudioRepository(context)
        try {
            repository.save(p);val reopened=repository.load(p.id);assertEquals(p,reopened)
            render(reopened) {r,out ->
                assertEquals("yes",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                fun colors(b: Bitmap): Pair<Int,Int> {
                    var cyan=0;var yellow=0
                    for(y in 0 until b.height step 2) for(x in 0 until b.width step 2) {val c=b.getPixel(x,y)
                        if(Color.green(c)>170 && Color.blue(c)>170 && Color.red(c)<100) cyan++
                        if(Color.green(c)>170 && Color.red(c)>170 && Color.blue(c)<100) yellow++
                    };return cyan to yellow
                }
                read(r,100_000) {assertEquals(0 to 0,colors(it))}
                read(r,1_250_000) {val counts=colors(it);assertTrue("Layer colors absent: $counts",counts.first>40 && counts.second>40)}
                read(r,2_200_000) {assertEquals("Layers outlived their output-time range",0 to 0,colors(it))}
                val levels=studioAudioLevels(out)
                assertTrue("Audio offset missing: $levels",levels.first<0.005)
                assertTrue("Music did not reach actual PCM: $levels",levels.second>0.01)
            }
        } finally {repository.delete(p.id)}
    }
    @Test fun freezeFrameCreatesEditableSilentVideoAndRetainsTheSameImage():Unit=runBlocking {
        val original=clip("native-source.mp4")
        val frozen=withTimeout(120_000) {StudioFreezeFrame.create(context,original,800)}
        try {
            render(StudioProject(title="Freeze export",clips=listOf(frozen),originalVolume=0f)) {r,_ ->
                assertTrue(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 1800..2300)
                var first:IntArray?=null
                read(r,200_000) {b ->first=IntArray(32) {i ->b.getPixel((i%8+1)*b.width/9,(i/8+1)*b.height/5)}}
                read(r,1_700_000) {b ->val values=IntArray(32) {i ->b.getPixel((i%8+1)*b.width/9,(i/8+1)*b.height/5)}
                    val delta=values.indices.sumOf {i ->kotlin.math.abs(Color.red(values[i])-Color.red(first!![i]))+kotlin.math.abs(Color.green(values[i])-Color.green(first!![i]))+kotlin.math.abs(Color.blue(values[i])-Color.blue(first!![i]))}
                    assertTrue("Freeze image changed over time: $delta",delta<32*12)
                }
            }
        } finally {context.contentResolver.delete(android.net.Uri.parse(frozen.uri),null,null)}
    }
}
