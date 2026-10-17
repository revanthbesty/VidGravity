package com.vidgravity.app

import org.junit.Assert.*
import org.junit.Test

class StoryEditSettingsTest {
    @Test fun everyEditInvalidatesThePreviouslyRenderedSettings() {
        val rendered = StoryEditSettings("content://source/1", 0f, 15f, "Title", "Artist", "Cover Art")
        assertEquals(rendered, rendered.copy())
        for (changed in listOf(
            rendered.copy(source = "content://source/2"),
            rendered.copy(startSeconds = 1f), rendered.copy(endSeconds = 30f),
            rendered.copy(title = "Edited"), rendered.copy(artist = "Edited"),
            rendered.copy(layout = "Privacy Blur")
        )) assertNotEquals(rendered, changed)
    }
}
