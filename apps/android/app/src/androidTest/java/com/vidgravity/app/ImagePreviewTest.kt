package com.vidgravity.app

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImagePreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun contentUriImageDecodesAndOffersSharing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "preview-smoke.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF43D8CA.toInt()) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val media = LibraryMedia(MediaLibraryIndexer.localUri(context, source), source.name,
                source.length(), "image/png", System.currentTimeMillis())
            compose.setContent { MaterialTheme { ImagePreview(media) { } } }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(source.name).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(source.name).assertIsDisplayed()
            compose.onNodeWithText("Share image").assertIsDisplayed()
        } finally { source.delete() }
    }
    @Test fun previewCanBeClosedAndReopenedWithoutRecyclingRenderedBitmaps() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File(context.cacheDir,"preview-lifecycle.png")
        val original=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        try {source.outputStream().use {original.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {original.recycle()}
        val media=LibraryMedia(MediaLibraryIndexer.localUri(context,source),source.name,source.length(),"image/png",0)
        val showing=mutableStateOf(true)
        try {
            compose.setContent {MaterialTheme {if(showing.value) ImagePreview(media) {showing.value=false} else Button(onClick={showing.value=true}) {Text("Reopen preview")}}}
            repeat(4) {index ->
                compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription(source.name).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithContentDescription(source.name).assertIsDisplayed().captureToImage()
                compose.onNodeWithText("Share image").assertIsDisplayed()
                compose.onNodeWithText("Back to workspace").performClick()
                compose.onNodeWithText("Reopen preview").assertIsDisplayed()
                if(index<3) compose.onNodeWithText("Reopen preview").performClick()
            }
        } finally {source.delete()}
    }

}
