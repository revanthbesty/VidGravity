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
        val repository=StudioRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val project=StudioProject(title="Kept after logout",clips=listOf(StudioClip(uri="content://media/video/12",name="Local",sourceDurationMs=1000)))
        repository.save(project)
        try {
        compose.setContent {VgTheme(true) {Column {AccountPanel(accounts)}}}
        compose.onNodeWithText("Guest mode").assertExists()
        compose.onNodeWithText("Email").performTextReplacement("test@example.invalid")
        compose.onNodeWithText("Password",substring=false).performTextReplacement("not-a-real-password")
        compose.onNodeWithText("Password",substring=false).performImeAction()
        compose.onNodeWithText("Sign in",substring=false).performClick()
        compose.waitUntil {accounts.profile.value!=null}
        compose.onNodeWithText("Your account").assertExists()
        compose.onNodeWithText("Display name").performTextReplacement("Local test")
        compose.onNodeWithText("Display name").performImeAction()
        compose.onNodeWithText("Save profile").performClick()
        compose.waitUntil {accounts.profile.value?.displayName=="Local test"}
        compose.onNodeWithText("Sign out",substring=false).performClick()
        compose.onNodeWithText("Guest mode").assertExists();assertNull(accounts.profile.value)
        assertEquals(project,repository.load(project.id))
        } finally {repository.delete(project.id)}
    }
    @Test fun guestPrivacyClearlyDisablesUnavailableTelemetryAndClearsOnlyDisposableCache() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val accounts=GuestAccounts
        assertFalse(accounts.available)
        compose.setContent {VgTheme(true) {PrivacyDataScreen(accounts) {}}}
        compose.onNodeWithTag("consent_analytics").assertIsNotEnabled()
        compose.onNodeWithTag("consent_crashes").assertIsNotEnabled()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Project metadata:",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Clear thumbnail cache").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("In-memory and disposable thumbnails cleared.",substring=true).fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun restoredSessionAndDeletionNetworkFailureRemainRecoverable() {
        val accounts=object:RecordingAccounts() {override suspend fun deleteAccount(password:String) {throw java.io.IOException("private-provider-detail")}}
        accounts.profile.value=AccountProfile("restored@example.invalid","Restored session")
        compose.setContent {VgTheme(true) {Column {AccountPanel(accounts)}}}
        compose.onNodeWithText("Your account").assertExists()
        compose.onNodeWithText("Display name").assertTextContains("Restored session")
        compose.onNodeWithText("Delete account",substring=false).performClick()
        compose.onNodeWithText("Confirm password").performTextReplacement("test-password")
        compose.onNodeWithText("Confirm password").performImeAction()
        compose.onNodeWithText("Confirm account deletion").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("account_delete_error").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("account_delete_error").assertTextContains("Account service is unavailable.",substring=true)
        assertNotNull(accounts.profile.value)
        compose.onNodeWithText("Cancel",substring=false).performClick()
        compose.onNodeWithText("Sign out",substring=false).performClick()
        compose.onNodeWithText("Guest mode").assertExists()
    }

}
