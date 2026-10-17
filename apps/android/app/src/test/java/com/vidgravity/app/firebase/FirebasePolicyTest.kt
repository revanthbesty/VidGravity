package com.vidgravity.app.firebase

import org.junit.Assert.*
import org.junit.Test

class FirebasePolicyTest {
    @Test fun idsCannotEscapeUserStorage() {
        assertTrue(FirebasePolicy.validId("photo_123"))
        listOf("", "../other", "a/b", "a".repeat(81)).forEach {
            assertFalse(FirebasePolicy.validId(it))
        }
    }
    @Test fun uploadsAreBoundedImages() {
        assertTrue(FirebasePolicy.validUpload(FirebasePolicy.MAX_UPLOAD_BYTES, "image/png"))
        assertFalse(FirebasePolicy.validUpload(FirebasePolicy.MAX_UPLOAD_BYTES + 1, "image/png"))
        assertFalse(FirebasePolicy.validUpload(0, "image/png"))
        assertFalse(FirebasePolicy.validUpload(1, "text/html"))
        assertFalse(FirebasePolicy.validUpload(1, "image/svg+xml"))
    }
    @Test fun profileNamesAreBounded() {
        assertTrue(FirebasePolicy.validDisplayName("VidGravity"))
        assertFalse(FirebasePolicy.validDisplayName("  "))
        assertFalse(FirebasePolicy.validDisplayName("a".repeat(81)))
    }
}
