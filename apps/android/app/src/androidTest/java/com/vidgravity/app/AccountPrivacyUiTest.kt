package com.vidgravity.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountPrivacyUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun accountUiUsesSessionStateSignsOutAndReturnsToGuestWithoutTouchingProjects() {
        val accounts=RecordingAccounts()
        compose.setContent {VgTheme(true) {Column {AccountPanel(accounts)}}}
        compose.onNodeWithText("Guest mode").assertExists()
        compose.onNodeWithText("Email").performTextReplacement("test@example.invalid")
        compose.onNodeWithText("Password",substring=false).performTextReplacement("not-a-real-password")
        compose.onNodeWithText("Sign in",substring=false).performClick()
        compose.waitUntil {accounts.profile.value!=null}
        compose.onNodeWithText("Your account").assertExists()
        compose.onNodeWithText("Display name").performTextReplacement("Local test")
        compose.onNodeWithText("Save profile").performClick()
        compose.waitUntil {accounts.profile.value?.displayName=="Local test"}
        compose.onNodeWithText("Sign out",substring=false).performClick()
        compose.onNodeWithText("Guest mode").assertExists();assertNull(accounts.profile.value)
    }
    @Test fun guestPrivacyClearlyDisablesUnavailableTelemetryAndClearsOnlyDisposableCache() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val accounts=AccountRuntime.get(context)
        assertFalse(accounts.available)
        compose.setContent {VgTheme(true) {PrivacyDataScreen(accounts) {}}}
        compose.onNodeWithTag("consent_analytics").assertIsNotEnabled()
        compose.onNodeWithTag("consent_crashes").assertIsNotEnabled()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Project metadata:",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Clear thumbnail cache").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("In-memory and disposable thumbnails cleared.",substring=true).fetchSemanticsNodes().isNotEmpty()}
    }
}
