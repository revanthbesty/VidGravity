package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StudioSprint10UiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun layersDragUndoSaveReopenAndTrashConfirmationPreserveTheProject() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=StudioRepository(context)
        val source=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture("native-source.mp4")))
        val p=StudioProject(title="Sprint 10 layers",clips=listOf(source.copy(endMs=2000)))
        repository.save(p)
        try {
            compose.setContent {MaterialTheme {NativeStudioHub(onClose={},onBusyChanged={})}}
            compose.reopenStudioProject(p.title)
            compose.clickStudioToolbar("Tools: Text layers")
            compose.onNodeWithTag("studio_add_text_layer").performScrollTo().performClick()
            val field=compose.onNodeWithTag("studio_layer_text").performScrollTo()
            field.performTextReplacement("Independent title");field.performImeAction()
            compose.onNodeWithText("Text end (ms)").performScrollTo().performTextReplacement("1000")
            compose.onNodeWithText("Apply Text end (ms)").performClick()
            compose.clickStudioToolbar("Background: Navy")
            compose.onNodeWithContentDescription("Timeline T1 · Independent title").performScrollTo().performTouchInput {
                down(center);advanceEventTime(700);moveBy(Offset(width.toFloat()*0.5f,0f));advanceEventTime(100);up()
            }
            compose.onNodeWithText("Save project").performScrollTo().performClick()
            compose.waitUntil(10_000) {repository.load(p.id).tools.textLayers.singleOrNull()?.startMs?.let {it>100}==true}
            val moved=repository.load(p.id).tools.textLayers.single()
            assertEquals(1000L,moved.endMs-moved.startMs)
            compose.onNodeWithText("Undo").performScrollTo().performClick()
            compose.onNodeWithText("Save project").performScrollTo().performClick()
            compose.waitUntil(10_000) {repository.load(p.id).tools.textLayers.single().startMs==0L}
            compose.onNodeWithText("Redo").performScrollTo().performClick()
            compose.onNodeWithText("Projects",substring=false).performScrollTo().performClick()
            compose.reopenStudioProject(p.title)
            compose.clickStudioToolbar("Tools: Text layers")
            compose.onNodeWithTag("studio_layer_text").performScrollTo().assertTextContains("Independent title")
            assertEquals(moved,repository.load(p.id).tools.textLayers.single())
            compose.onNodeWithText("Projects",substring=false).performScrollTo().performClick()
            val trash=hasText("Move to trash") and hasAnyAncestor(hasTestTag("studio_project_${p.id}"))
            compose.onNode(trash).performScrollTo().performClick()
            compose.onNodeWithText("Keep project").performClick()
            assertEquals(moved,repository.load(p.id).tools.textLayers.single())
            compose.onNode(trash).performClick()
            compose.onNodeWithText("Confirm move to trash").performClick()
            compose.waitUntil(10_000) {repository.trashed().any {it.id==p.id}}
            compose.onNodeWithText("Restore ${p.title}").performScrollTo().performClick()
            compose.waitUntil(10_000) {repository.list().any {it.id==p.id}}
            assertEquals(moved,repository.load(p.id).tools.textLayers.single())
        } finally {runCatching {repository.restore(p.id)};repository.delete(p.id)}
    }
}
