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
    isRendering: Boolean = false
) {
    val context = LocalContext.current
    var start by remember { mutableFloatStateOf(0f) }
    var length by remember { mutableFloatStateOf(15f) }
    var title by remember { mutableStateOf(media.displayName.substringBeforeLast(".")) }
    var artist by remember { mutableStateOf("") }
    var layout by remember { mutableStateOf("Cover Art") }
    var status by remember { mutableStateOf("") }
    val duration = 60f
    val end = (start + length).coerceAtMost(duration)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Story Card", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
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
        Slider(value = length, onValueChange = { length = it.coerceIn(15f, 30f) }, valueRange = 15f..30f, modifier = Modifier.heightIn(min = 48.dp))
        Text("Start point: ${start.roundToInt()}s", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = start, onValueChange = { start = it.coerceIn(0f, (duration - length).coerceAtLeast(0f)) },
            valueRange = 0f..(duration - length).coerceAtLeast(0f), modifier = Modifier.heightIn(min = 48.dp))
        OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text("Track / story title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = artist, onValueChange = { artist = it.take(100) }, label = { Text("Artist / caption") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = layout == "Cover Art", onClick = { layout = "Cover Art" }, label = { Text("Cover Art") })
            FilterChip(selected = layout == "Video Backdrop", onClick = { layout = "Video Backdrop" }, label = { Text("Video Backdrop") })
        }
        Button(enabled = !isRendering, onClick = { onRenderStory(media, start, end, title, artist, layout); status = "Story render requested." },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (isRendering) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Rendering story…") }
            else Text("Render 9:16 Story")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onShareDerivative("instagram") }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Instagram Story") }
            OutlinedButton(onClick = { onShareDerivative("whatsapp") }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("WhatsApp Status") }
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
