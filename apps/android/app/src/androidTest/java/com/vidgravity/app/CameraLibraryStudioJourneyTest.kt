package com.vidgravity.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed as viewDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled as viewEnabled
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.Description
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Uses the CI emulator's emulated CameraX camera; no injected capture result or renderer. */
class CameraLibraryStudioJourneyTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun awaitView(timeoutMs:Long=30_000,check:()->Unit) {
        val deadline=SystemClock.elapsedRealtime()+timeoutMs
        while(true) {
            try {check();return} catch(failure:AssertionError) {if(SystemClock.elapsedRealtime()>=deadline) throw failure}
            catch(failure:androidx.test.espresso.NoMatchingViewException) {if(SystemClock.elapsedRealtime()>=deadline) throw failure}
            SystemClock.sleep(100)
        }
    }

    @Test fun emulatedCameraRecordsToLibraryEditsReopensExportsPlaysAndPreparesAndroidShare() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        val repository=StudioRepository(context)
        val before=MediaLibraryIndexer.list(context).map {it.uri}.toSet()
        val title="Sprint 9 camera ${System.currentTimeMillis()}"
        var capture:LibraryMedia?=null;var exported:LibraryMedia?=null;var projectId:String?=null
        val output=File(context.cacheDir,"sprint9-camera-export-check.mp4")
        try {
            compose.awaitVidGravityFocus()
            compose.onNodeWithTag("nav_Camera").performClick()
            compose.onNodeWithTag("camera_open").performClick()
            awaitView {onView(withText("●  Record")).check(matches(viewEnabled()))}
            onView(withText("●  Record")).perform(click())
            awaitView {onView(withText(object:TypeSafeMatcher<String>() {
                override fun describeTo(description:Description) {description.appendText("recorded at least two seconds")}
                override fun matchesSafely(value:String)=Regex("Recording · ([0-9]+)s").matchEntire(value)?.groupValues?.get(1)?.toLongOrNull()?.let {it>=2} ?: false
            })).check(matches(viewDisplayed()))}
            onView(withText("■  Stop")).perform(click())
            awaitView {onView(withText("View capture in Library")).check(matches(viewEnabled()))}
            onView(withText("View capture in Library")).perform(click())
            compose.waitUntil(15_000) {compose.onAllNodesWithTag("capture_edit").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("nav_Library").assertIsSelected()
            capture=MediaLibraryIndexer.list(context).first {it.uri !in before && it.displayName.startsWith("vidgravity-") && it.mimeType.startsWith("video/")}
            val original=File(context.cacheDir,"sprint9-capture-hash.mp4")
            context.contentResolver.openInputStream(capture!!.uri)!!.use {input->original.outputStream().use {input.copyTo(it)}}
            val originalHash=DownloadApi.sha256(original);original.delete()
            compose.onNodeWithTag("capture_edit").performClick()
            compose.waitUntil(15_000) {compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty()}
            val name=compose.onNodeWithText("Project name");name.performTextReplacement(title);name.performImeAction()
            compose.clickStudioToolbar("Split at midpoint")
            compose.clickStudioToolbar("Duplicate clip")
            compose.onNodeWithText("Undo",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("Redo",substring=false).performClick()
            val overlay=compose.onNodeWithText("Centered text overlay").performScrollTo()
            overlay.performTextReplacement("CAPTURE TO CREATE");overlay.performImeAction()
            compose.clickStudioToolbar("Mono")
            compose.onNodeWithText("Save project").performScrollTo().performClick()
            compose.waitUntil(10_000) {repository.scan().projects.any {it.title==title && it.clips.size==3 && it.overlay=="CAPTURE TO CREATE"}}
            val saved=repository.scan().projects.first {it.title==title};projectId=saved.id
            assertEquals(StudioFilter.MONO,saved.filter)
            compose.onNodeWithTag("nav_Library").performClick()
            compose.waitUntil(10_000) {compose.onAllNodes(hasTestTag("nav_Library") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("nav_Studio").performClick()
            compose.waitUntil(15_000) {compose.onAllNodes(hasText("Project name") and hasText(title)).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("native_export").performScrollTo().performClick()
            compose.waitUntil(120_000) {repository.record(saved.id)?.uri!=null}
            val record=repository.record(saved.id)!!
            assertEquals(repository.load(saved.id).fingerprint(),record.fingerprint)
            val uri=Uri.parse(record.uri!!)
            exported=MediaLibraryIndexer.list(context).first {it.uri==uri}
            assertNotEquals(capture!!.uri,uri)
            context.contentResolver.openInputStream(uri)!!.use {input->output.outputStream().use {input.copyTo(it)}}
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(output.path)
                assertEquals("yes",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                assertEquals("720",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Edited duration $duration vs ${saved.durationMs}",kotlin.math.abs(duration-saved.durationMs)<600)
                val frame=reader.getFrameAtTime(500_000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                try {
                    var chroma=0L;var samples=0
                    for(y in 0 until frame.height step 24) for(x in 0 until frame.width step 24) {
                        val color=frame.getPixel(x,y)
                        chroma+=kotlin.math.abs(android.graphics.Color.red(color)-android.graphics.Color.green(color))+kotlin.math.abs(android.graphics.Color.green(color)-android.graphics.Color.blue(color));samples++
                    }
                    assertTrue("Mono missing from rendered camera video",chroma/samples<25)
                } finally {frame.recycle()}
            } finally {reader.release()}
            context.contentResolver.openInputStream(capture!!.uri)!!.use {input->output.outputStream().use {input.copyTo(it)}}
            assertEquals(originalHash,DownloadApi.sha256(output))
            compose.onNodeWithTag("native_play_export").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(15_000) {compose.onAllNodesWithTag("player_ready").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("player_share").assertIsEnabled()
            Intents.init()
            try {
                androidx.test.espresso.intent.Intents.intending(hasAction(Intent.ACTION_CHOOSER))
                    .respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null))
                compose.onNodeWithTag("player_share").performClick()
                val chooser=Intents.getIntents().last {it.action==Intent.ACTION_CHOOSER}
                @Suppress("DEPRECATION") val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND,send.action);assertEquals("video/mp4",send.type)
                @Suppress("DEPRECATION") val shared=send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                assertEquals(uri,shared);assertEquals(uri,send.clipData!!.getItemAt(0).uri)
                assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION !=0)
            } finally {Intents.release()}
            compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
            compose.waitUntil(15_000) {compose.onAllNodesWithText("Project name").fetchSemanticsNodes().isNotEmpty()}
            val renamed=compose.onNodeWithText("Project name");renamed.performTextReplacement("$title changed");renamed.performImeAction()
            compose.onNodeWithText("Share current export").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("native_play_export").assertIsNotEnabled()
            android.util.Log.i("VGSmoke","sprint9.camera-library-edit-reopen-real-export-play-share-intent.passed")
        } finally {
            projectId?.let {repository.delete(it)}
            exported?.let {MediaLibraryIndexer.delete(context,it)}
            capture?.let {MediaLibraryIndexer.delete(context,it)}
            output.delete()
        }
    }
}
