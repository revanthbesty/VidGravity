package com.vidgravity.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Story builder UI. Rendering is delegated to a backend derivative-export callback. */
@Composable
fun StoryCardScreen(
    media: LibraryMedia,
    onClose: () -> Unit,
    onRenderStory: (LibraryMedia, Float, Float, String, String, String) -> Unit,
    onShareDerivative: (String) -> Unit = {},
    hasDerivative: Boolean = false,
    onEdit: () -> Unit = {},
    isRendering: Boolean = false
) {
    val context = LocalContext.current
    var start by remember(media.uri) { mutableFloatStateOf(0f) }
    var length by remember(media.uri) { mutableFloatStateOf(15f) }
    var title by remember(media.uri) { mutableStateOf(media.displayName.substringBeforeLast(".")) }
    var artist by remember(media.uri) { mutableStateOf("") }
    var layout by remember(media.uri) { mutableStateOf(if (media.mimeType.startsWith("audio/")) "Audio Visualizer" else "Cover Art") }
    var status by remember { mutableStateOf("") }
    var duration by remember(media.uri) { mutableFloatStateOf(60f) }
    var durationKnown by remember(media.uri) { mutableStateOf(false) }
    LaunchedEffect(media.uri, start, length, title, artist, layout) { onEdit() }
    LaunchedEffect(media.uri) {
        val measured = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, media.uri)
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.toFloat()?.div(1000f)
                } finally { retriever.release() }
            }.getOrNull()
        }
        if (measured != null && measured > 0f) {
            duration = measured
            length = minOf(length, measured, 30f).coerceAtLeast(1f)
            start = start.coerceIn(0f, (measured - length).coerceAtLeast(0f))
            durationKnown = true
        } else {
            status = "Could not read exact duration; trim range uses a 60-second fallback."
        }
    }
    val maxLength = minOf(duration, 120f).coerceAtLeast(1f)
    val end = (start + length).coerceAtMost(duration)
    val currentSettings = StoryEditSettings(media.uri.toString(), start, end, title, artist, layout)
    var renderedSettings by remember(media.uri) { mutableStateOf<StoryEditSettings?>(null) }
    val canShare = hasDerivative && !isRendering && renderedSettings == currentSettings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Story Card", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(enabled = !isRendering, onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
        }
        Box(Modifier.height(340.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxHeight(0.88f).aspectRatio(9f / 16f).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    Text(if (media.mimeType.startsWith("audio/")) "♫" else "▶", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(artist.ifBlank { "VidGravity • Now playing" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("〰  ♫  〰  ♫  〰", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("Clip length: ${length.roundToInt()} seconds", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 60, 90, 120).forEach { seconds ->
                FilterChip(
                    selected = length.roundToInt() == seconds,
                    enabled = seconds <= duration && seconds <= 120f && !isRendering,
                    onClick = {
                        length = seconds.toFloat().coerceAtMost(duration).coerceAtLeast(1f)
                        start = start.coerceIn(0f, (duration - length).coerceAtLeast(0f))
                    },
                    label = { Text("${seconds}s") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Slider(
            value = length.coerceIn(1f, maxLength),
            onValueChange = {
                length = it.coerceIn(1f, maxLength)
                start = start.coerceIn(0f, (duration - length).coerceAtLeast(0f))
            },
            valueRange = 1f..maxLength,
            enabled = !isRendering && maxLength > 1f,
            modifier = Modifier.heightIn(min = 48.dp)
        )
        Text("Start point: ${start.roundToInt()}s", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = start, onValueChange = { start = it.coerceIn(0f, (duration - length).coerceAtLeast(0f)) },
            valueRange = 0f..(duration - length).coerceAtLeast(0f), enabled = !isRendering,
            modifier = Modifier.heightIn(min = 48.dp))
        Text(
            if (length > 90f) "This clip is longer than WhatsApp's documented 90-second Status limit."
            else if (durationKnown) "Source duration: ${duration.roundToInt()}s"
            else "Source duration is estimated until media metadata is available.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(enabled = !isRendering, value = title, onValueChange = { title = it.take(100) }, label = { Text("Track / story title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(enabled = !isRendering, value = artist, onValueChange = { artist = it.take(100) }, label = { Text("Artist / caption") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
            "Title and caption are embedded in the exported video. Your original media stays unchanged.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (media.mimeType.startsWith("audio/")) {
            FilterChip(
                selected = layout == "Audio Visualizer",
                onClick = { layout = "Audio Visualizer" },
                label = { Text("Audio Visualizer · 9:16") },
                enabled = !isRendering
            )
            Text(
                "Creates a vertical MP4 with an animated waveform and your original audio.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(enabled = !isRendering, selected = layout == "Cover Art", onClick = { layout = "Cover Art" }, label = { Text("Fit / Letterbox") })
                FilterChip(enabled = !isRendering, selected = layout == "Video Backdrop", onClick = { layout = "Video Backdrop" }, label = { Text("Blurred Backdrop") })
            }
            FilterChip(
                selected = layout == "Privacy Blur",
                onClick = { layout = "Privacy Blur" },
                label = { Text("Privacy Blur · whole frame") },
                enabled = !isRendering
            )
            if (layout == "Privacy Blur") {
                Text(
                    "Blurs the entire video frame; this is not face detection or selective redaction.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Button(enabled = !isRendering, onClick = { renderedSettings = currentSettings; onRenderStory(media, start, end, title, artist, layout); status = "Story render requested." },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (isRendering) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Rendering story…") }
            else Text("Render 9:16 Story")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = canShare, onClick = { onShareDerivative("instagram") }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Instagram Story") }
            OutlinedButton(enabled = canShare, onClick = { onShareDerivative("whatsapp") }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("WhatsApp Status") }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
