package com.vidgravity.app

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
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
}
