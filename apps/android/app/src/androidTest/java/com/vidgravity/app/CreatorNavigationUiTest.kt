package com.vidgravity.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class CreatorNavigationUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun back() {compose.waitForIdle();compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}

    @Test fun fiveCoreDestinationsNestedBackAndLibraryStateSurviveNavigationAndRecreation() {
        compose.awaitVidGravityFocus()
        for(tab in listOf("Home","Camera","Library","Studio","Settings")) {
            compose.onAllNodesWithTag("nav_$tab").assertCountEquals(1)
            compose.onNodeWithTag("nav_$tab").performClick().assertIsSelected()
        }
        compose.onNodeWithText("Privacy & Data",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("privacy_data").assertExists()
        back()
        compose.onNodeWithTag("privacy_data").assertDoesNotExist()
        compose.onNodeWithTag("nav_Settings").assertIsSelected()
        compose.onNodeWithTag("nav_Library").performClick()
        compose.onNodeWithTag("library_list").performScrollToNode(hasText("Search media"))
        val query="Sprint 9 retained search"
        compose.onNodeWithText("Search media").performTextReplacement(query)
        compose.onNodeWithTag("nav_Camera").performClick()
        compose.onNodeWithTag("camera_workspace").assertExists()
        compose.onNodeWithTag("camera_open").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("nav_Library").performClick()
        compose.onNodeWithText("Search media").assertTextContains(query)
        compose.activityRule.scenario.recreate()
        compose.awaitVidGravityFocus()
        compose.onNodeWithTag("nav_Library").assertIsSelected()
        compose.onNodeWithText("Search media").assertTextContains(query)
        compose.onNodeWithTag("library_list").performScrollToNode(hasText("Swipe Feed"))
        compose.onNodeWithText("Swipe Feed").performClick()
        compose.onNodeWithText("Back to Library").assertExists()
        back()
        compose.onNodeWithTag("library_list").assertExists()
        compose.onNodeWithTag("nav_Library").assertIsSelected()
        compose.onNodeWithTag("nav_Downloads").performClick().assertIsSelected()
        back()
        compose.onNodeWithTag("nav_Home").assertIsSelected()
    }

    @Test fun missingVideoShowsRecoverablePlaybackError() {
        // Use a real readable library entry whose bytes are unsupported as video.
        val context=compose.activity
        val file=java.io.File(context.cacheDir,"sprint9-invalid-video.mp4").apply {writeText("unsupported video")}
        val uri=MediaLibraryIndexer.localUri(context,file)
        MediaLibraryIndexer.register(context,uri,file.name,"video/mp4",file.length())
        try {
            compose.awaitVidGravityFocus();compose.onNodeWithTag("nav_Library").performClick()
            compose.onNodeWithTag("library_list").performScrollToNode(hasText("Search media"))
            compose.onNodeWithText("Search media").performTextReplacement(file.name)
            val mediaCard=hasText(file.name) and hasSetTextAction().not()
            compose.onNodeWithTag("library_list").performScrollToNode(mediaCard)
            compose.onNode(mediaCard).performClick()
            compose.waitUntil(15_000) {compose.onAllNodesWithTag("player_error").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Retry playback").assertIsEnabled()
            compose.onNodeWithTag("player_share").assertIsNotEnabled()
            back();compose.onNodeWithTag("nav_Library").assertIsSelected()
        } finally {MediaLibraryIndexer.delete(context,LibraryMedia(uri,file.name,file.length(),"video/mp4",0));file.delete()}
    }
}
