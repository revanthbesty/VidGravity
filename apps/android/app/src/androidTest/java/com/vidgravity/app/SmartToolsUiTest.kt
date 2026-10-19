package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SmartToolsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun reviewIsRequiredCutsUndoRedoAndAnalysisSurvivesReopen() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val repository=StudioRepository(context)
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture("creative-source.mp4")))
        val p=StudioProject(title="Sprint 11 reviewed cuts",clips=listOf(clip));repository.save(p)
        try {
            compose.setContent {MaterialTheme {NativeStudioHub(onClose={},onBusyChanged={})}}
            compose.reopenStudioProject(p.title)
            compose.onNodeWithTag("creative_studio").performScrollTo().performClick()
            compose.onNodeWithTag("ai_tools").assertExists()
            compose.onNodeWithTag("ai_tool_silence").performScrollTo().performClick()
            compose.onNodeWithTag("ai_analyze").performScrollTo().performClick()
            compose.waitUntil(90_000) {repository.load(p.id).clips.single().smart?.silence?.isNotEmpty()==true}
            assertEquals(clip.durationMs,repository.load(p.id).durationMs)
            compose.onNodeWithTag("ai_silence_0").performScrollTo().performClick()
            compose.onNodeWithTag("ai_review_cuts").performScrollTo().performClick()
            compose.onNodeWithText("Keep editing",substring=false).performClick()
            assertEquals(clip.durationMs,repository.load(p.id).durationMs)
            compose.onNodeWithTag("ai_review_cuts").performScrollTo().performClick()
            compose.onNodeWithTag("ai_confirm_cuts").performClick()
            compose.waitUntil(10_000) {repository.load(p.id).durationMs<clip.durationMs}
            val edited=repository.load(p.id);assertTrue(edited.clips.all {it.uri==clip.uri})
            compose.onNodeWithText("Back to editor",substring=false).performClick()
            compose.onNodeWithText("Undo",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("Save project").performScrollTo().performClick()
            compose.waitUntil(10_000) {repository.load(p.id).durationMs==clip.durationMs}
            compose.onNodeWithText("Redo",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("Save project").performScrollTo().performClick()
            compose.waitUntil(10_000) {repository.load(p.id).durationMs==edited.durationMs}
            compose.onNodeWithText("Projects",substring=false).performScrollTo().performClick()
            val action=hasText("Move to trash") and hasAnyAncestor(hasTestTag("studio_project_${p.id}")) and isEnabled()
            compose.waitUntil(10_000) {compose.onAllNodes(action).fetchSemanticsNodes().size==1}
            compose.reopenStudioProject(p.title)
            compose.onNodeWithTag("creative_studio").performScrollTo().performClick()
            compose.onNodeWithTag("ai_tools").assertExists()
            assertTrue(repository.load(p.id).clips.all {it.smart!=null})
            compose.onNodeWithText("Back to editor",substring=false).performClick()
            compose.onNodeWithText("Projects",substring=false).performScrollTo().performClick()
            compose.waitUntil(10_000) {compose.onAllNodes(action).fetchSemanticsNodes().size==1}
        } finally {repository.delete(p.id)}
    }
}
