package com.vidgravity.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real editor history/autosave/reopen, not a mocked rendering or persistence implementation. */
class CinematicWorkspaceUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun seeded(two:Boolean=true):StudioProject {
        val c=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture("native-fast.mp4"))).copy(endMs=1800)
        return StudioProject(title="Cinema UI ${UUID.randomUUID()}",clips=if(two) listOf(c,c.copy(id=UUID.randomUUID().toString())) else listOf(c))
    }
    private fun open(title:String) {
        compose.reopenStudioProject(title)
        compose.onNodeWithTag("cinema_open").performScrollTo().performClick()
        compose.onNodeWithTag("cinema_studio").assertExists()
    }
    private fun tab(name:String) {compose.onNodeWithTag("cinema_tab_$name").performScrollTo().performClick()}
    private fun graphicsAction(tag:String) {
        val action=compose.onNodeWithTag(tag)
        // Reveal the horizontal row in its vertical viewport before scrolling its item.
        action.onParent().performScrollTo()
        action.performScrollTo().assertIsDisplayed().performClick()
    }
    private fun text(tag:String,value:String) {
        val node=compose.onNodeWithTag(tag).performScrollTo()
        node.performTextReplacement(value)
        node.performImeAction()
    }
    private fun awaitSaved(repository:StudioRepository,id:String,check:(StudioProject)->Boolean) {
        try {compose.waitUntil(15_000) {check(repository.load(id))}}
        catch(failure:androidx.compose.ui.test.ComposeTimeoutException) {
            val p=repository.load(id)
            throw AssertionError("Saved state: text=${p.tools.textLayers.size} graphics=${p.tools.graphics.size} audio=${p.tools.audio.size} clips=${p.clips.size}; UI=${compose.onRoot().printToString().take(12000)}",failure)
        }
    }
    private fun projects() {
        compose.onNodeWithText("Back to editor",substring=false).performClick()
        compose.onNodeWithText("Projects",substring=false).performScrollTo().performClick()
        compose.waitUntil(15_000) {compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isEmpty()}
    }
    @Test fun colorPresetClipboardUndoRedoThemeAndSaveReopenPreserveEdits() {
        val repository=StudioRepository(context);val p=seeded();repository.save(p)
        val dark=mutableStateOf(true);val presets=CinemaPresetRepository(context);val presetName="UI ${UUID.randomUUID().toString().take(8)}"
        try {
            compose.setContent {VgTheme(dark.value) {NativeStudioHub(onClose={},onBusyChanged={})}}
            compose.waitUntil(15_000) {compose.onAllNodesWithTag("studio_project_${p.id}").fetchSemanticsNodes().isNotEmpty()}
            // The displayed card is deliberately stale. Opening it must read the latest disk file.
            repository.save(p.copy(overlay="Latest saved snapshot",updatedAt=p.updatedAt+1))
            open(p.title)
            compose.onNodeWithTag("cinema_look_warm").performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.clips[0].cinema.look==CinemaLook.WARM}
            assertEquals("Latest saved snapshot",repository.load(p.id).overlay)
            compose.onNodeWithTag("cinema_compare").performClick()
            compose.onNodeWithTag("cinema_compare").assertIsSelected()
            assertEquals(CinemaLook.WARM,repository.load(p.id).clips[0].cinema.look)
            tab("color")
            compose.onNodeWithContentDescription("Exposure EV").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) {it(0.5f)}
            text("cinema_preset_name",presetName)
            compose.onNodeWithTag("cinema_save_preset").performScrollTo().performClick()
            compose.waitUntil(15_000) {presets.list().presets.any {it.name==presetName}}
            compose.onNodeWithText("Copy color",substring=false).performScrollTo().performClick()
            compose.onNodeWithTag("cinema_clip_1").performScrollTo().performClick()
            compose.onNodeWithTag("cinema_paste_color").performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.clips[1].cinema.look==CinemaLook.WARM && it.clips[1].cinema.grade.exposure==0.5f}
            compose.onNodeWithText("Undo",substring=false).performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.clips[1].cinema.look==CinemaLook.ORIGINAL}
            compose.onNodeWithText("Redo",substring=false).performClick()
            awaitSaved(repository,p.id) {it.clips[1].cinema.grade.exposure==0.5f}
            compose.runOnIdle {dark.value=false}
            compose.onNodeWithTag("cinema_studio").assertExists()
            compose.runOnIdle {dark.value=true}
            compose.onNodeWithText("Save project",substring=false).performScrollTo().performClick()
            projects();open(p.title)
            assertEquals(repository.load(p.id).clips[0].cinema.colorOnly(),repository.load(p.id).clips[1].cinema.colorOnly())
            tab("color")
            compose.onNodeWithTag("cinema_reset_color").performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.clips[0].cinema.look==CinemaLook.ORIGINAL}
            compose.onNodeWithText("Apply preset: $presetName",substring=false).performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.clips[0].cinema.look==CinemaLook.WARM && it.clips[0].cinema.grade.exposure==0.5f}
            projects()
        } finally {
            presets.list().presets.filter {it.name==presetName}.forEach {presets.delete(it.id)}
            repository.delete(p.id)
        }
    }
    @Test fun motionGraphicsTransitionAndMixerControlsPersistAndReopen() {
        val repository=StudioRepository(context);val original=seeded()
        val music=studioFixture("native-music.wav")
        val lane=StudioAudioLane(uri=MediaLibraryIndexer.localUri(context,music).toString(),name="UI music",sourceDurationMs=1000)
        val p=original.copy(tools=original.tools.copy(audio=listOf(lane)));repository.save(p)
        try {
            compose.setContent {VgTheme(true) {NativeStudioHub(onClose={},onBusyChanged={})}}
            open(p.title);tab("mixer")
            compose.onNodeWithTag("cinema_solo_${lane.id}").performScrollTo().performClick()
            compose.onNodeWithTag("cinema_mute_${lane.id}").performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.tools.audio.single().solo && it.tools.audio.single().muted}
            text("cinema_audio_split_${lane.id}","500")
            compose.onNodeWithTag("cinema_audio_split_apply_${lane.id}").performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.tools.audio.size==2}
            assertTrue(repository.load(p.id).tools.audio.all {it.envelope!=null && it.solo && it.muted})
            tab("motion")
            val motionId="cinema_clip_motion_${p.clips[0].id}"
            text("${motionId}_time","0")
            compose.onNodeWithContentDescription("Motion opacity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) {it(0.4f)}
            compose.onNodeWithTag("${motionId}_apply").performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.clips[0].cinema.motion.singleOrNull()?.pose?.opacity==0.4f}
            tab("graphics")
            graphicsAction("cinema_title_template_1")
            awaitSaved(repository,p.id) {it.tools.textLayers.size==1}
            graphicsAction("cinema_add_rectangle")
            awaitSaved(repository,p.id) {it.tools.textLayers.size==1 && it.tools.graphics.size==1}
            tab("transitions")
            compose.onNodeWithTag("cinema_transition_white").performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.clips[0].cinema.exit.kind==CinemaTransitionKind.WHITE}
            compose.onNodeWithText("Save project",substring=false).performScrollTo().performClick()
            projects();open(p.title)
            val reopened=repository.load(p.id)
            assertEquals(4,reopened.tools.textLayers.single().motion.size)
            assertEquals(CinemaGraphicKind.RECTANGLE,reopened.tools.graphics.single().kind)
            assertEquals(2,reopened.tools.audio.size)
            assertEquals(0.4f,reopened.clips[0].cinema.motion.single().pose.opacity,0f)
            assertEquals(CinemaTransitionKind.WHITE,reopened.clips[0].cinema.exit.kind)
            projects()
        } finally {repository.delete(p.id)}
    }
    @Test fun speedReviewCancelApplyUndoRedoAndLegacyProjectMigration() {
        val repository=StudioRepository(context);val p=seeded(false);repository.save(p)
        // An actual schema-6 file, with no new clip field, must reopen without data loss.
        val file=java.io.File(context.filesDir,"studio-projects/${p.id}.json")
        val json=org.json.JSONObject(file.readText()).put("schema",6)
        json.getJSONArray("clips").getJSONObject(0).remove("cinema");file.writeText(json.toString())
        try {
            compose.setContent {VgTheme(false) {NativeStudioHub(onClose={},onBusyChanged={})}}
            open(p.title);tab("speed")
            compose.onNodeWithTag("cinema_speed_review").performScrollTo().performClick()
            compose.onNodeWithText("Keep editing",substring=false).performClick()
            assertEquals(1,repository.load(p.id).clips.size)
            compose.onNodeWithTag("cinema_speed_review").performScrollTo().performClick()
            compose.onNodeWithTag("cinema_speed_confirm").performClick()
            awaitSaved(repository,p.id) {it.clips.size==3}
            assertEquals(2100L,repository.load(p.id).durationMs)
            compose.onNodeWithText("Undo",substring=false).performScrollTo().performClick()
            awaitSaved(repository,p.id) {it.clips.size==1}
            compose.onNodeWithText("Redo",substring=false).performClick()
            awaitSaved(repository,p.id) {it.clips.size==3}
            projects();open(p.title)
            assertEquals(listOf(0.5f,1f,2f),repository.load(p.id).clips.map {it.speed})
            assertEquals(7,org.json.JSONObject(file.readText()).getInt("schema"))
            projects()
        } finally {repository.delete(p.id)}
    }
}
