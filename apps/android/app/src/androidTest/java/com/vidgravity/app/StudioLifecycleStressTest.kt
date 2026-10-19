package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/** Exercise pending preview work and disposal repeatedly; does not assert a framework root cause. */
class StudioLifecycleStressTest {
    @get:Rule val compose=createComposeRule()
    @Test fun repeatedSocialPreviewBackAndEditorDisposalRemainUsable() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=studioFixture("native-source.mp4")
        val media=LibraryMedia(MediaLibraryIndexer.localUri(context,file),file.name,file.length(),"video/mp4",0)
        val mounted=mutableStateOf(true)
        val repository=StudioRepository(context)
        val before=repository.list().map {it.id}.toSet()
        compose.setContent {MaterialTheme {if(mounted.value) NativeStudioHub(media,onClose={},onBusyChanged={})}}
        try {
            repeat(4) {
                compose.waitUntil(10_000) {compose.onAllNodesWithTag("social_creator").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("social_creator").performScrollTo().performClick()
                compose.onNodeWithText("Social Creator",substring=false).assertIsDisplayed()
                compose.onNodeWithText("Preview: Product showcase").performScrollTo().performClick()
                compose.onNodeWithText("Back to editor").performClick()
                compose.onNodeWithText("Project name").assertExists()
                compose.runOnIdle {mounted.value=false}
                compose.waitForIdle()
                compose.runOnIdle {mounted.value=true}
            }
            compose.waitUntil(10_000) {compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Project name").assertExists()
        } finally {
            compose.runOnIdle {mounted.value=false};compose.waitForIdle()
            repository.list().filter {it.id !in before}.forEach {repository.delete(it.id);java.io.File(context.filesDir,"studio-projects/recovery/${it.id}.json").delete()}
        }
    }
}
