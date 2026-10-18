package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class SocialCreatorUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun socialTemplateCreatesEditableProjectAndBackNavigationWorks() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val file=studioFixture("native-source.mp4")
        val media=LibraryMedia(MediaLibraryIndexer.localUri(context,file),file.name,file.length(),"video/mp4",0)
        compose.setContent {MaterialTheme {NativeStudioHub(media,onClose={},onBusyChanged={})}}
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("social_creator").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("social_creator").performScrollTo().performClick()
        compose.onNodeWithText("Social Creator",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Preview: Product showcase").performScrollTo().performClick()
        val title=compose.onNodeWithText("Social opening title").performScrollTo();title.performTextReplacement("Sprint 5 launch");title.performImeAction()
        compose.onNodeWithTag("social_create").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Project name") and hasText("Product showcase · Sprint 5 launch")).assertExists()
        compose.onNodeWithTag("social_creator").performScrollTo().performClick()
        compose.onNodeWithText("Back to editor").performClick()
        compose.onNodeWithText("Project name").assertExists()
    }
    @Test fun localBrandCanSaveReloadAndApply() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val file=studioFixture("native-source.mp4")
        val p=StudioProject(title="Brand UI",clips=listOf(StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,file))))
        compose.setContent {MaterialTheme {SocialCreatorWorkspace(p,0,{},{},{},{})}}
        compose.onNodeWithText("Brand kit",substring=false).performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("brand_save").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        val field=compose.onNodeWithText("Brand name").performScrollTo();field.performTextReplacement("Sprint 5 Brand");field.performImeAction()
        compose.onNodeWithTag("brand_save").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Brand kit saved on this device.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Reload brand kit").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Brand kit reopened.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Brand name") and hasText("Sprint 5 Brand")).assertExists()
    }
}
