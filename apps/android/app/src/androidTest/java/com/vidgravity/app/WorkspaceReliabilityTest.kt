package com.vidgravity.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WorkspaceReliabilityTest {
    @Test fun repeatedThumbnailRequestsUseOneBoundedDecode():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"sprint6-thumbnail.png")
        val bitmap=Bitmap.createBitmap(2048,1024,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.MAGENTA)
        try {file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
        val media=LibraryMedia(MediaLibraryIndexer.localUri(context,file),file.name,file.length(),"image/png",1)
        try {
            MediaThumbnails.clear();val start=android.os.SystemClock.elapsedRealtime()
            val first=MediaThumbnails.load(context,media)!!;val second=MediaThumbnails.load(context,media)!!
            assertSame(first,second);assertTrue(first.width<=320 && first.height<=320)
            assertEquals(1,MediaThumbnails.decodeCount.get())
            android.util.Log.i("VGPerf","thumbnail requests=2 decodes=1 cacheHits=1 decoded=${first.width}x${first.height} bitmapBytes=${first.allocationByteCount} elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}")
        } finally {MediaThumbnails.clear();file.delete()}
    }
    @Test fun staleProjectSaveCannotOverwriteNewerDataAcrossRepositoryInstances() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val one=StudioRepository(context);val two=StudioRepository(context)
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture("native-source.mp4")))
        val old=StudioProject(title="Old autosave",clips=listOf(clip));val newer=old.copy(title="Newer navigation save",updatedAt=old.updatedAt+100)
        try {one.save(newer);two.save(old);assertEquals(newer,one.load(old.id))} finally {one.delete(old.id)}
    }
    @Test fun syntheticCameraResultsRetainMediaForExistingStudioFlows() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val uri=MediaLibraryIndexer.localUri(context,studioFixture("native-source.mp4"))
        val video=CameraResultMedia.read(Activity.RESULT_OK,Intent().setDataAndType(uri,"video/mp4").putExtra(Intent.EXTRA_TITLE,"Camera video"))!!
        assertEquals(uri,video.uri);assertEquals("video/mp4",video.mimeType)
        val project=StudioProject(title=video.displayName,clips=listOf(StudioMediaImports.video(context,video.uri)))
        val repository=StudioRepository(context);try {repository.save(project);assertEquals(project,repository.load(project.id))} finally {repository.delete(project.id)}
        val image=CameraResultMedia.read(Activity.RESULT_OK,Intent().setDataAndType(uri,"image/png"))!!;assertTrue(image.mimeType.startsWith("image/"))
        assertNull(CameraResultMedia.read(Activity.RESULT_CANCELED,Intent().setDataAndType(uri,"video/mp4")))
        assertNull(CameraResultMedia.read(Activity.RESULT_OK,Intent().setDataAndType(android.net.Uri.parse("https://example.test/video"),"video/mp4")))
    }
}
