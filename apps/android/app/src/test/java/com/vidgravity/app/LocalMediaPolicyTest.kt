package com.vidgravity.app
import org.junit.Assert.*
import org.junit.Test
class LocalMediaPolicyTest {
    @Test fun onlyBoundedUnambiguousLocalUrisAreAccepted() {
        assertTrue(LocalMediaPolicy.validUri("content://media/external/video/media/12"))
        assertTrue(LocalMediaPolicy.validUri("content://com.android.providers.media.documents/document/video%3A12"))
        assertTrue(LocalMediaPolicy.validUri("content://custom_media.provider/video/12"))
        listOf("file:///data/data/token","https://example.test/a.mp4","content://","content://user@media/file","content://media:80/file","content://media/file#hidden","content://media/"+"x".repeat(8192)).forEach {assertFalse(it,LocalMediaPolicy.validUri(it))}
    }
    @Test fun allExistingFeaturesRemainFree() {listOf("edit","camera","export4k","creative","social").forEach {assertTrue(FreeEntitlements.canUse(it))}}
    @Test fun errorMessagesNeverEchoSensitiveProviderDetails() {
        assertFalse(accountError(IllegalStateException("secret token=123 email=user@example.com")).contains("123"))
        assertTrue(accountError(java.io.IOException()).contains("local editing"))
    }
}
