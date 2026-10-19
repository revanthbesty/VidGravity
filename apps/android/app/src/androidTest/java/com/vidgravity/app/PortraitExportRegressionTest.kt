package com.vidgravity.app

import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class PortraitExportRegressionTest {
    @Test fun portraitSilentSplitVideoRendersAtAnEvenCanvasWithoutQualityFallback():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=studioFixture("native-portrait-silent.mp4");val hash=DownloadApi.sha256(source)
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source))
        val project=StudioProject(title="Portrait AVC regression",clips=clip.split(1000),overlay="PORTRAIT",filter=StudioFilter.MONO)
        val output=File(context.cacheDir,"portrait-canvas-regression.mp4")
        try {
            // Reproduce the third-party geometry that reached the strict encoder.
            val unaligned=androidx.media3.effect.Presentation.createForHeight(720).configure(90,160)
            assertEquals(405,unaligned.width);assertEquals(720,unaligned.height)
            val canvas=StudioEncoderCanvas();val aligned=canvas.configure(405,720)
            assertEquals(406,aligned.width);assertEquals(720,aligned.height)
            val values=FloatArray(9);canvas.getMatrix(0).getValues(values)
            assertEquals(405f/406,values[android.graphics.Matrix.MSCALE_X],0.0001f)
            assertEquals(1f,values[android.graphics.Matrix.MSCALE_Y],0.0001f)
            assertTrue(canvas.isNoOp(1280,720));assertFalse(canvas.isNoOp(405,720))
            withTimeout(120_000) {NativeStudioRenderer.render(context,project,output) {}}
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(output.path)
                assertEquals("406",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                assertEquals("720",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                assertEquals("yes",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                assertTrue(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 1700..2400)
                val frame=reader.getFrameAtTime(500_000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                try {
                    var brightness=0L;var chroma=0L;var count=0
                    for(y in 0 until frame.height step 20) for(x in 0 until frame.width step 20) {
                        val pixel=frame.getPixel(x,y);val r=android.graphics.Color.red(pixel);val g=android.graphics.Color.green(pixel);val b=android.graphics.Color.blue(pixel)
                        brightness+=r+g+b;chroma+=kotlin.math.abs(r-g)+kotlin.math.abs(g-b);count++
                    }
                    assertTrue("Portrait render is black",brightness/count>20)
                    assertTrue("Mono was not rendered",chroma/count<25)
                } finally {frame.recycle()}
            } finally {reader.release()}
            assertEquals(hash,DownloadApi.sha256(source))
            android.util.Log.i("VGSmoke","sprint9.portrait-silent-split-avc406x720.passed")
        } finally {output.delete()}
    }
}
