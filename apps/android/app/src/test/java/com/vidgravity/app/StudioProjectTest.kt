package com.vidgravity.app

import org.junit.Assert.*
import org.junit.Test

class StudioProjectTest {
    private fun project() = StudioProject(title = "Test", clips = listOf(
        StudioClip(uri = "content://media/video/1", name = "source.mp4", sourceDurationMs = 10_000)))

    @Test fun projectRoundTripsEverySettingAndStableFingerprint() {
        val source = project().copy(overlay = "Hello", filter = StudioFilter.MONO, fadeEdges = true,
            resolution = StudioResolution.UHD, originalVolume = 0.8f,
            musicUri = "content://audio/music", musicVolume = 0.4f, voiceUri = "content://audio/voice", voiceVolume = 0.7f)
        val decoded = StudioProjectCodec.decode(StudioProjectCodec.encode(source))
        assertEquals(source, decoded)
        assertEquals(source.fingerprint(), decoded.fingerprint())
        assertEquals(source.fingerprint(), source.copy(updatedAt = source.updatedAt + 1000).fingerprint())
    }
    @Test fun editsInvalidateExportIdentity() {
        val source = project()
        for (changed in listOf(source.copy(overlay = "Edited"), source.copy(filter = StudioFilter.MONO),
            source.copy(resolution = StudioResolution.FULL_HD), source.copy(fadeEdges = true),
            source.copy(originalVolume = 0f), source.copy(musicUri = "content://audio/music"),
            source.copy(voiceUri = "content://audio/voice"), source.copy(musicVolume = 0.5f),
            source.copy(voiceVolume = 0.5f), source.copy(clips = source.clips.map { it.copy(startMs = 1000) }))) {
            assertNotEquals(source.fingerprint(), changed.fingerprint())
        }
    }
    @Test fun splitPreservesSourceRangesDurationAndDistinctIds() {
        val clip = project().clips.single().copy(startMs = 1000, endMs = 9000)
        val pieces = clip.split(3000)
        assertEquals(4000L, pieces[0].endMs)
        assertEquals(4000L, pieces[1].startMs)
        assertEquals(clip.durationMs, pieces.sumOf { it.durationMs })
        assertNotEquals(pieces[0].id, pieces[1].id)
    }
    @Test fun rejectsBadRangesRemoteSourcesAndUnknownVersions() {
        fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (expected: IllegalArgumentException) { } }
        val source = project()
        rejected { source.copy(clips = source.clips.map { it.copy(startMs = -1) }).validate() }
        rejected { source.copy(clips = source.clips.map { it.copy(endMs = 11_000) }).validate() }
        rejected { source.copy(clips = source.clips.map { it.copy(uri = "https://example.com/video.mp4") }).validate() }
        rejected { source.copy(originalVolume = Float.NaN).validate() }
        rejected { source.clips.single().split(0) }
        rejected { StudioProjectCodec.decode(StudioProjectCodec.encode(source).replace("\"schema\":4", "\"schema\":99")) }
    }
    @Test fun fadeIsBoundedAndReachesBlackAtBothEdges() {
        assertEquals(0f, StudioFade.gain(0, 1_000_000), 0.0001f)
        assertEquals(1f, StudioFade.gain(500_000, 1_000_000), 0.0001f)
        assertEquals(0f, StudioFade.gain(1_000_000, 1_000_000), 0.0001f)
        assertEquals(0f, StudioFade.gain(-1, 1_000_000), 0.0001f)
    }
}
