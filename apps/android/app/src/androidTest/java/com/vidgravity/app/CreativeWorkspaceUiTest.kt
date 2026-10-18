package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class CreativeWorkspaceUiTest {
    @get:Rule val compose=createComposeRule()
    private fun open() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=studioFixture("creative-source.mp4")
        val media=LibraryMedia(MediaLibraryIndexer.localUri(context,file),file.name,file.length(),"video/mp4",0)
        compose.setContent {MaterialTheme {NativeStudioHub(media,onClose={},onBusyChanged={})}}
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("creative_studio").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("creative_studio").performScrollTo().performClick()
        compose.onNodeWithText("Creative Studio",substring=false).assertIsDisplayed()
    }
    @Test fun offlineCaptionFallbackAndTemplateCreationRemainEditableAndReopen() {
        open()
        compose.onNodeWithText("Captions",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("speech_fallback").assertExists()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        if(!OfflineDictation.available(context)) {
            compose.onNodeWithText("Dictate English caption · microphone").assertIsNotEnabled()
        }
        compose.onNodeWithText("Return to manual caption editor").performScrollTo().performClick()
        compose.onNodeWithTag("creative_studio").performScrollTo().performClick()
        compose.onNodeWithText("Templates",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Preview YouTube Shorts").performScrollTo().performClick()
        val title=compose.onNodeWithText("Template opening title").performScrollTo()
        title.performTextReplacement("Sprint 4 editable template")
        title.performImeAction()
        compose.onNodeWithTag("create_template").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("YouTube Shorts",substring=true).assertExists()
        compose.onNodeWithText("Sprint 4 editable template").assertExists()
        compose.onNodeWithText("Save project").performScrollTo().performClick()
        compose.onNodeWithText("Projects",substring=false).performScrollTo().performClick()
        compose.onAllNodesWithText("Continue editing").onFirst().performClick()
        compose.onNodeWithText("Sprint 4 editable template").assertExists()
    }
    @Test fun offlineWritingSuggestionsAndThumbnailSettingsCanBeSavedAndReopened() {
        open()
        compose.onNodeWithText("Assistant",substring=false).performScrollTo().performClick()
        val topic=compose.onNodeWithText("Content topic")
        topic.performTextReplacement("Trail camera tips")
        topic.performImeAction()
        compose.onNodeWithText("Generate offline writing suggestions").performScrollTo().performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithText("Trail camera tips: start here").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Offline writing templates · rule-based, no language model").assertExists()
        compose.onNodeWithText("Thumbnails",substring=false).performScrollTo().performClick()
        val title=compose.onNodeWithText("Thumbnail title").performScrollTo()
        title.performTextReplacement("Sprint 4 saved thumbnail")
        title.performImeAction()
        compose.onNodeWithText("Save thumbnail project").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Reopen thumbnail: Sprint 4 saved thumbnail").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Reopen thumbnail: Sprint 4 saved thumbnail").performScrollTo().performClick()
        compose.onNodeWithText("Sprint 4 saved thumbnail").assertExists()
        compose.onNodeWithText("Share current thumbnail").performScrollTo().assertIsNotEnabled()
    }
}
