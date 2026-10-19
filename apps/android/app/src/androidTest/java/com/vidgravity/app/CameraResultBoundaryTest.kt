package com.vidgravity.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CameraResultBoundaryTest {
    @Test fun cancelledMalformedMismatchedEmptyAndMissingCaptureResultsNeverRegister() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=studioFixture("native-source.mp4")
        val uri=MediaLibraryIndexer.localUri(context,source)
        assertNull(CameraResultMedia.read(Activity.RESULT_CANCELED,Intent().setDataAndType(uri,"video/mp4")))
        for(value in listOf("file:///private/video.mp4","https://example.invalid/video","content://user@media/video/1","content://media:123/video/1","content://media/video/1#bad")) {
            assertNull(value,CameraResultMedia.read(Activity.RESULT_OK,Intent().setDataAndType(Uri.parse(value),"video/mp4")))
        }
        assertNull(CameraResultMedia.read(Activity.RESULT_OK,Intent().setDataAndType(uri,"text/plain")))
        val empty=File(context.cacheDir,"sprint9-empty.mp4").apply {writeBytes(byteArrayOf())}
        val invalid=File(context.cacheDir,"sprint9-invalid-result.mp4").apply {writeText("not encoded video")}
        val missing=File(context.cacheDir,"sprint9-missing.mp4").apply {delete()}
        try {
            for(media in listOf(
                LibraryMedia(uri,"Mismatch",0,"image/png",0),
                LibraryMedia(MediaLibraryIndexer.localUri(context,invalid),invalid.name,invalid.length(),"video/mp4",0),
                LibraryMedia(MediaLibraryIndexer.localUri(context,empty),empty.name,0,"video/mp4",0),
                LibraryMedia(MediaLibraryIndexer.localUri(context,missing),missing.name,0,"video/mp4",0))) {
                assertTrue("Invalid result accepted: ${media.displayName}",runCatching {CameraResultMedia.verify(context,media)}.isFailure)
            }
            assertFalse(MediaLibraryIndexer.list(context).any {it.uri==MediaLibraryIndexer.localUri(context,empty) || it.uri==MediaLibraryIndexer.localUri(context,missing)})
            val result=CameraResultMedia.read(Activity.RESULT_OK,Intent().setDataAndType(uri,"video/mp4").putExtra(Intent.EXTRA_TITLE,"Verified camera result"))!!
            val verified=CameraResultMedia.verify(context,result)
            assertEquals(source.length(),verified.sizeBytes)
            assertTrue(MediaLibraryIndexer.list(context).any {it.uri==uri})
        } finally {empty.delete();invalid.delete();MediaLibraryIndexer.delete(context,LibraryMedia(uri,source.name,source.length(),"video/mp4",0))}
    }
}
