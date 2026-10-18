package com.vidgravity.app

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry

/** CI-only recovery of a verified external launcher ANR; app ANRs remain failures. */
internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>,MainActivity>.awaitVidGravityFocus() {
    activityRule.scenario.onActivity { activity ->
        val keyguard=activity.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val power=activity.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        android.util.Log.i("VGSmoke","display.preflight locked=${keyguard.isKeyguardLocked} interactive=${power.isInteractive}")
        activity.setShowWhenLocked(true);activity.setTurnScreenOn(true)
        keyguard.requestDismissKeyguard(activity,null)
    }
    try {waitUntil(15_000) {activity.window.decorView.hasWindowFocus()}}
    catch(failure:ComposeTimeoutException) {
        fun shell(command:String):String {
            val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {it.readText()}
        }
        val windows=shell("dumpsys window windows")
        windows.lineSequence().filter {it.contains("Focus") || it.contains("Window{") || it.contains("mHasSurface") || it.contains("isOnScreen")}
            .forEach {android.util.Log.e("VGSmoke","focus.diagnostic $it")}
        if(windows.contains("Application Not Responding: com.google.android.apps.nexuslauncher") &&
            !windows.contains("Application Not Responding: com.vidgravity.app")) {
            android.util.Log.w("VGSmoke","focus.recover.external-launcher-anr")
            shell("am force-stop com.google.android.apps.nexuslauncher")
            waitUntil(15_000) {activity.window.decorView.hasWindowFocus()}
        } else throw failure
    }
}
