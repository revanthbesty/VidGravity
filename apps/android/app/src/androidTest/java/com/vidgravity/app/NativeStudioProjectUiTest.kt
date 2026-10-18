package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class NativeStudioProjectUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trimSplitOverlayProjectCanSaveAndReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = studioFixture("native-source.mp4")
        val media = LibraryMedia(MediaLibraryIndexer.localUri(context, source), source.name, source.length(), "video/mp4", 0)
        compose.setContent { MaterialTheme { NativeStudioHub(media, onClose = {}, onBusyChanged = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty() }
        val nameField = compose.onNodeWithText("Project name")
        nameField.performTextReplacement("Sprint 2 saved project")
        nameField.performImeAction()
        compose.clickStudioToolbar("Split at midpoint")
        compose.onNodeWithText("2. native-source.mp4", substring = true).assertExists(compose.onRoot(useUnmergedTree=true).printToString())
        val overlayField = compose.onNodeWithText("Centered text overlay").performScrollTo()
        overlayField.performTextReplacement("Saved caption")
        overlayField.performImeAction()
        compose.clickStudioToolbar("Mono")
        compose.onNodeWithText("Mono").assertIsSelected()
        compose.onNodeWithText("Save project").performScrollTo().performClick()
        compose.onNodeWithText("Projects", substring = false).performScrollTo().performClick()
        compose.reopenStudioProject("Sprint 2 saved project")
        compose.onNodeWithText("Sprint 2 saved project").assertExists()
        compose.onNodeWithText("Saved caption").performScrollTo().assertExists()
        compose.onNodeWithText("Share current export").performScrollTo().assertIsNotEnabled()
    }
}

/** Bring the horizontal toolbar into the vertical viewport before scrolling to its action. */
internal fun ComposeContentTestRule.clickStudioToolbar(label: String) {
    if(label=="Add title cue") clickStudioToolbar("Tools: Captions")
    val action = onNodeWithText(label)
    action.onParent().performScrollTo()
    action.performScrollTo().assertIsDisplayed().performClick()
}

/** Wait for the saved card, not the identically named field in the closing editor. */
internal fun ComposeContentTestRule.reopenStudioProject(title: String) {
    val action = hasText("Continue editing") and hasAnySibling(hasText(title))
    waitUntil(10_000) { onAllNodes(action).fetchSemanticsNodes().size == 1 }
    onNode(action).performScrollTo().assertIsDisplayed().performClick()
}
