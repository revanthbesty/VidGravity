package com.vidgravity.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class HomeAccessibilityUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun creationRemainsVisibleAtLargeFontAndOnboardingCanBeSkipped() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("vg-ui",0);val previous=prefs.getBoolean("onboarding-complete",false)
        prefs.edit().putBoolean("onboarding-complete",false).commit()
        var createCount=0
        try {
            compose.setContent {val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    VgTheme(false) {HomeDashboard({createCount++},{},{},{},{},{},{},{},{})}
                }
            }
            compose.onNodeWithTag("home_create").assertIsDisplayed().performClick();assertEquals(1,createCount)
            compose.waitUntil(10_000) {compose.onAllNodesWithTag("home_loading").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("skip_onboarding"))
            compose.onNodeWithTag("skip_onboarding").performClick()
            compose.waitUntil(5000) {prefs.getBoolean("onboarding-complete",false)}
            compose.onNodeWithTag("skip_onboarding").assertDoesNotExist()
        } finally {prefs.edit().putBoolean("onboarding-complete",previous).commit()}
    }
}
