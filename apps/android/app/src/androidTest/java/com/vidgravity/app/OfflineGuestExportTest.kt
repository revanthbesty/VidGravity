package com.vidgravity.app

import android.net.ConnectivityManager
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OfflineGuestExportTest {
    @Test fun disconnectedGuestCanImportEditSaveReopenAndRenderCaptions():Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        fun shell(command:String) {instrumentation.uiAutomation.executeShellCommand(command).use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {input->input.readBytes()}}}
        val wifi=Settings.Global.getInt(context.contentResolver,"wifi_on",0)!=0
        val data=Settings.Global.getInt(context.contentResolver,"mobile_data",0)!=0
        val network=context.getSystemService(ConnectivityManager::class.java)
        val repository=StudioRepository(context);var project:StudioProject?=null
        val output=File(context.cacheDir,"privacy-offline-render.mp4")
        try {
            shell("svc wifi disable");shell("svc data disable")
            withTimeout(15_000) {while(network.activeNetwork!=null) delay(100)}
            assertNull("Offline verification requires no active network",network.activeNetwork)
            assertNull(AccountRuntime.get(context).profile.value)
            val source=studioFixture("native-source.mp4");val original=DownloadApi.sha256(source)
            val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source)).copy(endMs=1000)
            project=StudioProject(title="Offline guest",clips=clip.split(500),overlay="Offline caption")
            repository.save(project);val reopened=repository.load(project.id)
            assertEquals(2,reopened.clips.size);assertEquals("Offline caption",reopened.overlay)
            withTimeout(120_000) {NativeStudioRenderer.render(context,reopened,output) {}}
            assertTrue(output.length()>0)
            val retriever=android.media.MediaMetadataRetriever()
            try {retriever.setDataSource(output.path);assertEquals("yes",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))} finally {retriever.release()}
            assertEquals(original,DownloadApi.sha256(source));assertNull(network.activeNetwork)
        } finally {
            project?.let {repository.delete(it.id)};output.delete()
            if(wifi) shell("svc wifi enable")
            if(data) shell("svc data enable")
        }
    }
}
