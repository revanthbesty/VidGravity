package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.compose.ui.geometry.Offset

class StudioHistoryUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun longPressDragReordersThePersistedTimeline() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=studioFixture("native-source.mp4")
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source))
        val project=StudioProject(title="Drag timeline",clips=listOf(clip.copy(name="First"),clip.copy(id=java.util.UUID.randomUUID().toString(),name="Second")))
        val repository=StudioRepository(context)
        repository.save(project)
        try {
            compose.setContent {MaterialTheme {NativeStudioHub(onClose={},onBusyChanged={})}}
            compose.waitUntil(10_000) {compose.onAllNodesWithText("Drag timeline").fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodesWithText("Continue editing").onFirst().performClick()
            compose.onNodeWithContentDescription("Video clip 1: First").performScrollTo().performTouchInput {
                down(center); advanceEventTime(700); moveBy(Offset(width.toFloat()*1.5f,0f)); advanceEventTime(100); up()
            }
            compose.onNodeWithContentDescription("Video clip 1: Second").assertExists()
            compose.onNodeWithText("Save project").performScrollTo().performClick()
            compose.waitUntil(5000) {repository.load(project.id).clips.first().name=="Second"}
            assertEquals(listOf("Second","First"),repository.load(project.id).clips.map {it.name})
        } finally {repository.delete(project.id)}
    }
    @Test fun duplicateUndoRedoAndTimedTitleSaveAndReopen() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=studioFixture("native-source.mp4")
        val media=LibraryMedia(MediaLibraryIndexer.localUri(context,source),source.name,source.length(),"video/mp4",0)
        compose.setContent {MaterialTheme {NativeStudioHub(media,onClose={},onBusyChanged={})}}
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Project name").performTextReplacement("Sprint 3 history")
        compose.onNodeWithText("Duplicate clip").performScrollTo().performClick()
        compose.onNodeWithText("2. native-source.mp4",substring=true).assertExists(compose.onRoot(useUnmergedTree=true).printToString())
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.onNodeWithText("2. native-source.mp4",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Redo").performClick()
        compose.onNodeWithText("2. native-source.mp4",substring=true).assertExists(compose.onRoot(useUnmergedTree=true).printToString())
        compose.onNodeWithText("Add title cue").performScrollTo().performClick()
        compose.onNodeWithText("Caption 1").performScrollTo().performTextReplacement("Sprint 3 caption")
        compose.onNodeWithText("Save project").performScrollTo().performClick()
        compose.onNodeWithText("Projects",substring=false).performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Sprint 3 history").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText("Continue editing").onFirst().performClick()
        compose.onNodeWithText("Sprint 3 caption").performScrollTo().assertExists()
        compose.onNodeWithText("Share current export").performScrollTo().assertIsNotEnabled()
    }
}
