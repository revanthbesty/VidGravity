package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import android.util.Log
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
        val audio=studioFixture("native-music.wav")
        val p=StudioProject(title="Sprint 10 layers",clips=listOf(source.copy(endMs=2000)),tools=StudioTools(audio=listOf(StudioAudioLane(uri=MediaLibraryIndexer.localUri(context,audio).toString(),name="Music",sourceDurationMs=1000))))
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
            compose.waitUntil(10_000) {repository.load(p.id).tools.textLayers.singleOrNull()?.endMs==1000L}
            compose.clickStudioToolbar("Background: Navy")
            // Bring the horizontal track container into the vertical viewport first.
            compose.onNodeWithContentDescription("Timeline A1 · Music").onParent().performScrollTo()
            compose.onNodeWithContentDescription("Timeline A1 · Music").performScrollTo().assertIsDisplayed().performTouchInput {
                swipe(start=center,end=center+Offset(width.toFloat()*0.75f,0f),durationMillis=450)
            }
            compose.onNodeWithContentDescription("Timeline T1 · Independent title").onParent().performScrollTo()
            compose.onNodeWithContentDescription("Timeline T1 · Independent title").performScrollTo().assertIsDisplayed().performTouchInput {
                swipe(start=center,end=center+Offset(width.toFloat()*0.75f,0f),durationMillis=450)
            }
            compose.onNodeWithText("Save project").performScrollTo().performClick()
            compose.waitUntil(10_000) {repository.load(p.id).tools.textLayers.singleOrNull()?.startMs?.let {it>100}==true}
            assertTrue(repository.load(p.id).tools.audio.single().offsetMs>100)
            val moved=repository.load(p.id).tools.textLayers.single()
            assertEquals(1000L,moved.endMs-moved.startMs)
            assertEquals(0xFF14245A.toInt(),moved.background)
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
            // The IO write completes before the hub reload publishes its Compose state.
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Restore ${p.title}").fetchSemanticsNodes().size==1
            }
            compose.onNodeWithText("Restore ${p.title}").performScrollTo().performClick()
            compose.waitUntil(10_000) {repository.list().any {it.id==p.id}}
            assertEquals(moved,repository.load(p.id).tools.textLayers.single())
        } catch(failure:Throwable) {
            runCatching {compose.onRoot(useUnmergedTree=true).printToString().chunked(3000).forEach {Log.i("VGSmoke","sprint10.ui.failure.tree=$it")}}
            runCatching {val saved=repository.load(p.id);Log.i("VGSmoke","sprint10.ui.failure.text=${saved.tools.textLayers.map {it.startMs to it.endMs}} audioOffsets=${saved.tools.audio.map {it.offsetMs}}")}
            throw failure
        } finally {runCatching {repository.restore(p.id)};repository.delete(p.id)}
    }
}
