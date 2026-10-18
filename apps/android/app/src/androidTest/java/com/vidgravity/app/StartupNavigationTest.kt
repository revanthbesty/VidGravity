package com.vidgravity.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.asAndroidBitmap
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StartupNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun startupDrawsHomeAndCoreTabsNavigate() {
        compose.awaitVidGravityFocus()

        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("YOUR CREATOR UNIVERSE").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("YOUR CREATOR UNIVERSE").assertIsDisplayed()
        compose.waitForIdle()
        onView(withText("Retry startup")).check(matches(not(isDisplayed())))

        // PixelCopy-backed window capture avoids opening a separate UiAutomation connection.
        val captured = compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val screenshot = captured.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        captured.recycle()
        assertTrue("Window pixel capture unavailable", screenshot != null)
        screenshot?.let { bitmap ->
            val directory = File(compose.activity.getExternalFilesDir(null), "smoke-evidence").apply { mkdirs() }
            File(directory, "startup.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            var colored = 0
            var samples = 0
            for (y in 0 until bitmap.height step 16) for (x in 0 until bitmap.width step 16) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) > 100) colored++
                samples++
            }
            assertTrue("Launch image is almost entirely black", colored > samples / 20)
            bitmap.recycle()
            android.util.Log.i("VGSmoke", "startup.nonblack-screenshot.passed")
        }

        for (tab in listOf("Downloads", "Library", "Studio", "Home")) {
            compose.onNodeWithTag("nav_$tab").performClick()
            compose.onNodeWithTag("nav_$tab").assertIsSelected()
            android.util.Log.i("VGSmoke", "navigation.$tab.passed")
        }
        compose.onNodeWithText("YOUR CREATOR UNIVERSE").assertIsDisplayed()
        compose.onNodeWithTag("nav_Studio").performClick()
        compose.onNodeWithText("Creator Studio").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("nav_Home").assertIsSelected()
        compose.activityRule.scenario.onActivity { activity ->
            // ActivityScenario matches lifecycle events against the original Intent filter.
            // Preserve action/categories while changing the inbox extra for singleTop delivery.
            activity.startActivity(android.content.Intent(activity.intent).apply {
                putExtra(ShareReceiverActivity.EXTRA_OPEN_INBOX, true)
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Share Inbox").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share Inbox").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue("Inbox request must reach the existing Activity",
                activity.intent.getBooleanExtra(ShareReceiverActivity.EXTRA_OPEN_INBOX, false))
        }
        android.util.Log.i("VGSmoke", "singleTop.inbox.passed")
        compose.onNodeWithTag("nav_Home").performClick()
        compose.onNodeWithText("YOUR CREATOR UNIVERSE").assertIsDisplayed()
        android.util.Log.i("VGSmoke", "startup.navigation.body.completed")
    }
}
