package com.vidgravity.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IntegratedCreatorUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun focused() {compose.waitUntil(15_000) {compose.activity.window.decorView.hasWindowFocus()}}
    @Test fun homeContinueSavesBeforeTabNavigationAndReopensTheActiveProject() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=studioFixture("native-source.mp4")
        val p=StudioProject(title="Sprint 6 continue",clips=listOf(StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,source))))
        val repository=StudioRepository(context);repository.save(p)
        try {
            focused();compose.onNodeWithTag("nav_Library").performClick();compose.onNodeWithTag("nav_Home").performClick()
            compose.onNodeWithTag("home_create").assertIsDisplayed()
            compose.waitUntil(10_000) {compose.onAllNodesWithTag("home_loading").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithTag("home_dashboard").performScrollToNode(hasText("Continue Sprint 6 continue"))
            compose.onNodeWithText("Continue Sprint 6 continue").performClick()
            compose.waitUntil(10_000) {compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty()}
            val field=compose.onNodeWithText("Project name");field.performTextReplacement("Sprint 6 preserved");field.performImeAction()
            compose.onNodeWithTag("nav_Library").performClick()
            compose.waitUntil(10_000) {repository.load(p.id).title=="Sprint 6 preserved"}
            compose.onNodeWithTag("nav_Library").assertIsSelected()
            compose.onNodeWithTag("nav_Studio").performClick()
            compose.waitUntil(10_000) {compose.onAllNodes(hasText("Project name") and hasText("Sprint 6 preserved")).fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasText("Project name") and hasText("Sprint 6 preserved")).assertExists()
        } finally {repository.delete(p.id)}
    }
    @Test fun populatedLibraryGridSearchPreviewAndThemeToggleWork() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.cacheDir,"sprint6-library.png")
        val image=android.graphics.Bitmap.createBitmap(640,360,android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.CYAN)
        try {file.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {image.recycle()}
        val uri=MediaLibraryIndexer.localUri(context,file)
        MediaLibraryIndexer.register(context,uri,"Sprint 6 library image.png","image/png",file.length())
        try {
            focused();compose.onNodeWithTag("nav_Library").performClick()
            compose.onNodeWithTag("library_list").performScrollToNode(hasText("Grid view"))
            compose.onNodeWithText("Grid view").performClick()
            compose.onNodeWithTag("library_list").performScrollToNode(hasText("Search media"))
            val search=compose.onNodeWithText("Search media");search.performTextReplacement("Sprint 6 library image")
            compose.onNodeWithTag("library_list").performScrollToNode(hasText("Sprint 6 library image.png"))
            compose.onNodeWithText("Sprint 6 library image.png").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) {compose.onAllNodesWithText("Share image").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Back to workspace").performClick()
            val wasDark=compose.onAllNodesWithTag("theme_dark").fetchSemanticsNodes().isNotEmpty()
            compose.onNodeWithContentDescription("Toggle theme").performClick()
            compose.onNodeWithTag(if(wasDark) "theme_light" else "theme_dark").assertExists()
            compose.onNodeWithContentDescription("Toggle theme").performClick()
            compose.onNodeWithTag(if(wasDark) "theme_dark" else "theme_light").assertExists()
        } finally {MediaLibraryIndexer.delete(context,LibraryMedia(uri,"Sprint 6 library image.png",file.length(),"image/png",0));file.delete()}
    }
}
