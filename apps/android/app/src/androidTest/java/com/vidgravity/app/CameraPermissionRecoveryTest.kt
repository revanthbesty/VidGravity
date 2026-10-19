package com.vidgravity.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** CI runs this class on its own fresh install before the camera-granted suite. */
class CameraPermissionRecoveryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private fun awaitNode(text:String,timeoutMs:Long=15_000,ready:(AccessibilityNodeInfo)->Boolean={it.isVisibleToUser}):AccessibilityNodeInfo {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline=SystemClock.elapsedRealtime()+timeoutMs
        while(SystemClock.elapsedRealtime()<deadline) {
            automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull(ready)?.let {return it}
            SystemClock.sleep(100)
        }
        throw AssertionError("Android UI node unavailable: $text; active package=${automation.rootInActiveWindow?.packageName}")
    }

    @Test fun systemCameraDenialOffersRecoveryAndReturnsToCameraWithoutLosingNavigation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val service=instrumentation.uiAutomation.serviceInfo
        service.flags=service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        instrumentation.uiAutomation.serviceInfo=service
        assertEquals("Permission phase requires a fresh install",PackageManager.PERMISSION_DENIED,ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA))
        compose.awaitVidGravityFocus()
        compose.onNodeWithTag("nav_Camera").performClick();compose.onNodeWithTag("camera_open").performClick()
        assertTrue(awaitNode("Create with your camera").isVisibleToUser)
        // Espresso cannot wait for an app window to regain focus while a system
        // permission dialog deliberately owns it. Cross that boundary via accessibility.
        val continueButton=awaitNode("Continue") {it.isVisibleToUser && it.isClickable}
        assertTrue(continueButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val deadline=SystemClock.elapsedRealtime()+20_000
        var recovered=false;var denials=0
        while(SystemClock.elapsedRealtime()<deadline && !recovered) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            val pkg=root?.packageName?.toString()
            if(pkg!=null && pkg.endsWith("permissioncontroller")) {
                // Google's controller package and its resource namespace can differ.
                val button=listOf(pkg,"com.android.permissioncontroller","com.google.android.permissioncontroller").distinct()
                    .flatMap {namespace->listOf("permission_deny_button","permission_deny_and_dont_ask_again_button")
                        .flatMap {id->root?.findAccessibilityNodeInfosByViewId("$namespace:id/$id").orEmpty()}}
                    .firstOrNull()
                if(button!=null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) denials++
            }
            if(pkg==context.packageName) {
                recovered=root?.findAccessibilityNodeInfosByText("Camera unavailable")?.any {it.isVisibleToUser}==true
            }
            if(!recovered) SystemClock.sleep(100)
        }
        assertTrue("No Android permission denial was performed",denials>0)
        assertTrue("Camera denial did not reach recoverable UI",recovered)
        assertEquals(PackageManager.PERMISSION_DENIED,ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA))
        assertTrue(awaitNode("Retry").isVisibleToUser)
        assertTrue(awaitNode("App settings").isVisibleToUser)
        // Exercise the real Settings Activity round trip. The permission grant is
        // automated at the Android boundary; no provider/camera is mocked.
        val settingsButton=awaitNode("App settings") {it.isVisibleToUser && it.isClickable}
        assertTrue(settingsButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val settingsDeadline=SystemClock.elapsedRealtime()+10_000
        while(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()!="com.android.settings" && SystemClock.elapsedRealtime()<settingsDeadline) SystemClock.sleep(100)
        assertEquals("com.android.settings",instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        assertTrue(awaitNode("●  Record",30_000) {it.isVisibleToUser && it.isEnabled}.isEnabled)
        val cameraRoot=instrumentation.uiAutomation.rootInActiveWindow
        val close=cameraRoot.findAccessibilityNodeInfosByText("✕").single {it.contentDescription?.toString()=="Close camera"}
        assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("camera_workspace").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav_Camera").assertIsSelected()
        compose.onNodeWithTag("nav_Library").performClick().assertIsSelected()
        val evidence=File(context.getExternalFilesDir(null),"smoke-evidence").apply {mkdirs()}
        File(evidence,"camera-permission-recovery.json").writeText("""{"system_denials":$denials,"camera_permission_initial":"denied","camera_permission_after_settings":"granted","camera_ready":"passed","recovery_ui":"passed","return_navigation":"passed"}""")
        android.util.Log.i("VGSmoke","sprint9.system-permission-denial-recovery.passed denials=$denials")
    }
}
