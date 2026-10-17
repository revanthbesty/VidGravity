package com.vidgravity.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(media: LibraryMedia, onBack: () -> Unit) {
    if (media.mimeType.startsWith("image/")) {
        ImagePreview(media, onBack)
        return
    }
    val context = LocalContext.current
    val activity = context as? Activity
    val progressPrefs = remember(context) { context.getSharedPreferences("vg_player_progress", android.content.Context.MODE_PRIVATE) }
    val progressKey = remember(media.uri) { "position_" + media.uri.toString().hashCode().toUInt().toString(16) }
    val player = remember(media.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(media.uri))
            val resumeAt = progressPrefs.getLong(progressKey, 0L).coerceAtLeast(0L)
            if (resumeAt > 3_000L) seekTo(resumeAt)
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player, progressKey) {
        while (true) {
            delay(2_000)
            if (player.duration > 0L && player.currentPosition > 0L) {
                progressPrefs.edit().putLong(progressKey, player.currentPosition).apply()
            }
            if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                progressPrefs.edit().remove(progressKey).apply()
                break
            }
        }
    }
    var speed by remember { mutableFloatStateOf(1f) }
    var fullscreen by remember { mutableStateOf(false) }

    fun applyFullscreen(enabled: Boolean) {
        val window = activity?.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(player) {
        onDispose {
            if (player.duration > 0L && player.currentPosition > 0L &&
                player.playbackState != androidx.media3.common.Player.STATE_ENDED) {
                progressPrefs.edit().putLong(progressKey, player.currentPosition).apply()
            }
            player.release()
            applyFullscreen(false)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (!fullscreen) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF15151B)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(media.displayName, color = Color.White, maxLines = 1)
                    Text("VidGravity Player", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = {
                    fullscreen = true
                    applyFullscreen(true)
                }) {
                    Icon(Icons.Default.Fullscreen, "Enter fullscreen landscape", tint = Color.White)
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { menu = true }) { Text("${speed}×", color = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                            DropdownMenuItem(text = { Text("${value}×") }, onClick = {
                                speed = value
                                player.setPlaybackSpeed(value)
                                menu = false
                            })
                        }
                    }
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setPlayer(player)
                    useController = true
                    controllerAutoShow = true
                    setShowSubtitleButton(true)
                }
            },
            update = { it.player = player }
        )

        if (fullscreen) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xCC15151B)).padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    fullscreen = false
                    applyFullscreen(false)
                }) {
                    Icon(Icons.Default.FullscreenExit, "Exit fullscreen", tint = Color.White)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF15151B)).padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FilledTonalButton(
                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (player.isPlaying) "Pause" else "Play")
                }
            }
        }
    }
}
