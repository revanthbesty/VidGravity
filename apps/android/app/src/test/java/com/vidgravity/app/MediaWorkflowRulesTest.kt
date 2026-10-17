package com.vidgravity.app

import org.junit.Assert.*
import org.junit.Test

class MediaWorkflowRulesTest {
    @Test fun recognizesLegacyCapturesAndExportsCaseInsensitively() {
        assertEquals("image/jpeg", MediaWorkflowRules.mimeForName("capture.JPG"))
        assertEquals("video/mp4", MediaWorkflowRules.mimeForName("clip.mp4"))
        assertEquals("image/webp", MediaWorkflowRules.mimeForName("sticker.webp"))
        assertEquals("audio/mp4", MediaWorkflowRules.mimeForName("track.m4a"))
        assertNull(MediaWorkflowRules.mimeForName("partial.tmp"))
        assertNull(MediaWorkflowRules.mimeForName("unknown"))
    }

    @Test fun storyEditorOnlyOffersRendererSupportedSources() {
        assertTrue(MediaWorkflowRules.supportsStory("video/mp4"))
        assertTrue(MediaWorkflowRules.supportsStory("audio/mpeg"))
        assertFalse(MediaWorkflowRules.supportsStory("image/jpeg"))
        assertFalse(MediaWorkflowRules.supportsStory("application/octet-stream"))
    }
}
