package com.vidgravity.app

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class StudioDerivativeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editedTitleCannotShareAnEarlierDerivative() {
        val completed = mutableStateOf(false)
        val media = LibraryMedia(Uri.parse("content://com.vidgravity.app.files/cache/test.mp4"),
            "test.mp4", 0, "video/mp4", 0)
        compose.setContent {
            MaterialTheme {
                StoryCardScreen(media, onClose = {},
                    onRenderStory = { _, _, _, _, _, _ -> completed.value = true },
                    hasDerivative = completed.value, onEdit = { completed.value = false })
            }
        }
        compose.onNodeWithText("WhatsApp Status").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Render 9:16 Story").performScrollTo().performClick()
        compose.onNodeWithText("WhatsApp Status").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Track / story title").performScrollTo().performTextReplacement("Edited title")
        compose.onNodeWithText("WhatsApp Status").performScrollTo().assertIsNotEnabled()
    }
}
