package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.onNodeWithText("Project name").performTextReplacement("Sprint 2 saved project")
        compose.onNodeWithText("Split at midpoint").performScrollTo().performClick()
        compose.onNodeWithText("2. native-source.mp4", substring = true).assertExists(compose.onRoot(useUnmergedTree=true).printToString())
        compose.onNodeWithText("Centered text overlay").performScrollTo().performTextReplacement("Saved caption")
        compose.onNodeWithText("Mono").performScrollTo().performClick()
        compose.onNodeWithText("Save project").performScrollTo().performClick()
        compose.onNodeWithText("Projects", substring = false).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Sprint 2 saved project").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Continue editing").onFirst().performClick()
        compose.onNodeWithText("Sprint 2 saved project").assertExists()
        compose.onNodeWithText("Saved caption").performScrollTo().assertExists()
        compose.onNodeWithText("Share current export").performScrollTo().assertIsNotEnabled()
    }
}
