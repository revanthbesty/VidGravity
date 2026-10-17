package com.vidgravity.app

import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

internal fun studioFixture(name: String): File {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val file = File(File(instrumentation.targetContext.cacheDir, "native-fixtures").apply { mkdirs() }, name)
    instrumentation.context.assets.open("generated/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
    return file
}

class NativeStudioIntegrationTest {
    @Test fun realEncoderRendersTrimmedTimelineEffectsAndMixedAudio() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = studioFixture("native-source.mp4")
        val music = studioFixture("native-music.wav")
        val uri = MediaLibraryIndexer.localUri(context, source).toString()
        val audio = MediaLibraryIndexer.localUri(context, music).toString()
        val project = StudioProject(title = "Native integration", clips = listOf(
            StudioClip(uri = uri, name = source.name, sourceDurationMs = 2000, endMs = 1000),
            StudioClip(uri = uri, name = source.name, sourceDurationMs = 2000, startMs = 1000, endMs = 2000)
        ), overlay = "SPRINT 2", filter = StudioFilter.MONO, fadeEdges = true,
            musicUri = audio, voiceUri = audio, voiceVolume = 0.2f)
        val output = File(context.cacheDir, "native-integration-${UUID.randomUUID()}.mp4")
        val repository = StudioRepository(context)
        try {
            repository.save(project)
            assertEquals(project, repository.load(project.id))
            assertEquals(project.fingerprint(), repository.load(project.id).fingerprint())
            var progressEvents = 0
            withTimeout(120_000) { NativeStudioRenderer.render(context, project, output) { progressEvents++ } }
            assertTrue(output.length() > 1000)
            assertTrue(progressEvents > 0)
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(output.absolutePath)
                assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                assertEquals("720", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Unexpected timeline duration: $duration", duration in 1700..2400)
                val frame = reader.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
                var chroma = 0L; var count = 0; var brightness = 0L
                for (y in 0 until frame.height step 16) for (x in 0 until frame.width step 16) {
                    val pixel = frame.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel); val g = android.graphics.Color.green(pixel); val b = android.graphics.Color.blue(pixel)
                    chroma += kotlin.math.abs(r - g) + kotlin.math.abs(g - b); brightness += r + g + b; count++
                }
                assertTrue("Monochrome effect missing", chroma / count < 25)
                assertTrue("Rendered output is black", brightness / count > 20)
                frame.recycle()
            } finally { reader.release() }
        } finally { repository.delete(project.id); output.delete() }
    }
}
