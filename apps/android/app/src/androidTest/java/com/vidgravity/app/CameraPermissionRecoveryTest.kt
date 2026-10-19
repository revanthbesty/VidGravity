package com.vidgravity.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed as viewDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled as viewEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** CI runs this class on its own fresh install before the camera-granted suite. */
class CameraPermissionRecoveryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun systemCameraDenialOffersRecoveryAndReturnsToCameraWithoutLosingNavigation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val service=instrumentation.uiAutomation.serviceInfo
        service.flags=service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        instrumentation.uiAutomation.serviceInfo=service
        assertEquals("Permission phase requires a fresh install",PackageManager.PERMISSION_DENIED,ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA))
        compose.awaitVidGravityFocus()
        compose.onNodeWithTag("nav_Camera").performClick();compose.onNodeWithTag("camera_open").performClick()
        onView(withText("Create with your camera")).check(matches(viewDisplayed()))
        onView(withText("Continue")).perform(click())
        val deadline=SystemClock.elapsedRealtime()+20_000
        var recovered=false;var denials=0
        while(SystemClock.elapsedRealtime()<deadline && !recovered) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            val pkg=root?.packageName?.toString()
            if(pkg!=null && pkg.endsWith("permissioncontroller")) {
                val button=root?.findAccessibilityNodeInfosByViewId("$pkg:id/permission_deny_button")?.firstOrNull()
                    ?: root?.findAccessibilityNodeInfosByViewId("$pkg:id/permission_deny_and_dont_ask_again_button")?.firstOrNull()
                if(button!=null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) denials++
            }
            if(pkg==context.packageName) {
                recovered=runCatching {onView(withText("Camera unavailable")).check(matches(viewDisplayed()))}.isSuccess
            }
            if(!recovered) SystemClock.sleep(100)
        }
        assertTrue("No Android permission denial was performed",denials>0)
        assertTrue("Camera denial did not reach recoverable UI",recovered)
        assertEquals(PackageManager.PERMISSION_DENIED,ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA))
        onView(withText("Retry")).check(matches(viewDisplayed()))
        onView(withText("App settings")).check(matches(viewDisplayed()))
        // Exercise the real Settings Activity round trip. The permission grant is
        // automated at the Android boundary; no provider/camera is mocked.
        onView(withText("App settings")).perform(click())
        val settingsDeadline=SystemClock.elapsedRealtime()+10_000
        while(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()!="com.android.settings" && SystemClock.elapsedRealtime()<settingsDeadline) SystemClock.sleep(100)
        assertEquals("com.android.settings",instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        val readyDeadline=SystemClock.elapsedRealtime()+30_000
        var ready=false
        while(SystemClock.elapsedRealtime()<readyDeadline && !ready) {
            ready=runCatching {onView(withText("●  Record")).check(matches(viewEnabled()))}.isSuccess
            if(!ready) SystemClock.sleep(100)
        }
        assertTrue("Camera did not initialize after permission was granted in Settings",ready)
        onView(withContentDescription("Close camera")).perform(click())
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("camera_workspace").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav_Camera").assertIsSelected()
        compose.onNodeWithTag("nav_Library").performClick().assertIsSelected()
        val evidence=File(context.getExternalFilesDir(null),"smoke-evidence").apply {mkdirs()}
        File(evidence,"camera-permission-recovery.json").writeText("""{"system_denials":$denials,"camera_permission_initial":"denied","camera_permission_after_settings":"granted","camera_ready":"passed","recovery_ui":"passed","return_navigation":"passed"}""")
        android.util.Log.i("VGSmoke","sprint9.system-permission-denial-recovery.passed denials=$denials")
    }
}
