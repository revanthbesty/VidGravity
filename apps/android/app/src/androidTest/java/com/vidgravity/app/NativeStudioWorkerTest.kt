package com.vidgravity.app

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NativeStudioWorkerTest {
    @Test fun foregroundExportPublishesVerifiedFileAndKeepsEditedProjectStale(): Unit = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = studioFixture("native-source.mp4")
        val clip = StudioMediaImports.video(context, MediaLibraryIndexer.localUri(context, source))
        val project = StudioProject(title = "Worker integration", clips = listOf(clip))
        val repository = StudioRepository(context)
        val manager = WorkManager.getInstance(context)
        val copied = File(context.cacheDir, "worker-verification.mp4")
        var workId: java.util.UUID? = null
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            val id = NativeStudioExportWorker.enqueue(context, project)
            workId = id
            val completed = withTimeout(120_000) {
                var info = manager.getWorkInfoById(id).get()!!
                while (!info.state.isFinished) { delay(250); info = manager.getWorkInfoById(id).get()!! }
                info
            }
            assertEquals(completed.outputData.getString("error"), WorkInfo.State.SUCCEEDED, completed.state)
            val uri = Uri.parse(completed.outputData.getString("content_uri")!!)
            context.contentResolver.openInputStream(uri)!!.use { input -> copied.outputStream().use { input.copyTo(it) } }
            assertEquals(completed.outputData.getString("sha256"), DownloadApi.sha256(copied))
            assertTrue(MediaLibraryIndexer.list(context).any { it.uri == uri })
            assertEquals(project.fingerprint(), repository.record(project.id)!!.fingerprint)
            val changed = project.copy(overlay = "Edited after export")
            repository.save(changed)
            assertNotEquals(repository.record(project.id)!!.fingerprint, repository.load(project.id).fingerprint())
            MediaLibraryIndexer.delete(context, LibraryMedia(uri, "Worker integration", copied.length(), "video/mp4", 0))
        } finally {
            workId?.let { manager.cancelWorkById(it).result.get() }
            repository.delete(project.id)
            copied.delete()
            activity.close()
        }
    }
}
