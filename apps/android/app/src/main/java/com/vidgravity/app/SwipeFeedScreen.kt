package com.vidgravity.app

import android.content.Intent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeFeedScreen(
    media: List<LibraryMedia>,
    onClose: () -> Unit,
    onFavorite: (LibraryMedia, Boolean) -> Unit,
    onMakeSticker: (LibraryMedia) -> Unit = {},
    onStatusSplit: (LibraryMedia) -> Unit = {}
) {
    val context = LocalContext.current
    val clips = remember(media) { media.filter { it.mimeType.startsWith("video/") && it.uri.scheme in setOf("content", "file") } }
    val pager = rememberPagerState(pageCount = { maxOf(1, clips.size) })
    var muted by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(0) }
    var liked by remember { mutableStateOf(setOf<String>()) }
    val speeds = listOf(1f, 1.5f, 2f)
    val players = remember {
        List(2) { slot ->
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = slot == 0
                volume = 1f
            }
        }
    }
    fun prepare(slot: Int, index: Int, play: Boolean) {
        if (clips.isEmpty()) return
        val item = clips[Math.floorMod(index, clips.size)]
        val player = players[slot]
        if (player.currentMediaItem?.localConfiguration?.uri != item.uri) {
            player.setMediaItem(MediaItem.fromUri(item.uri))
            player.prepare()
        }
        player.volume = if (muted) 0f else 1f
        player.setPlaybackSpeed(speeds[speedIndex])
        player.playWhenReady = play
    }
    LaunchedEffect(clips, pager.currentPage, muted, speedIndex) {
        if (clips.isNotEmpty()) {
            val currentSlot = pager.currentPage % 2
            val nextSlot = (currentSlot + 1) % 2
            prepare(currentSlot, pager.currentPage, true)
            players[nextSlot].pause()
            if (clips.size > 1) prepare(nextSlot, pager.currentPage + 1, false)
        }
    }
    DisposableEffect(players) { onDispose { players.forEach { it.release() } } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (clips.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No saved videos yet", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("Download a video to start your offline feed.", color = Color.LightGray)
                TextButton(onClick = onClose) { Text("Back to Library") }
            }
        } else {
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                val mediaItem = clips[page % clips.size]
                val player = players[page % 2]
                Box(Modifier.fillMaxSize().pointerInput(mediaItem.uri) {
                    detectTapGestures(onDoubleTap = {
                        val key = mediaItem.uri.toString()
                        val next = key !in liked
                        liked = if (next) liked + key else liked - key
                        onFavorite(mediaItem, next)
                    })
                }) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> PlayerView(ctx).apply {
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            this.player = player
                        } },
                        update = { it.player = player }
                    )
                    Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalIconButton(onClick = { muted = !muted; players.forEach { it.volume = if (muted) 0f else 1f } }) {
                            Icon(if (muted) Icons.Default.MusicOff else Icons.Default.MusicNote, "Mute or unmute")
                        }
                        FilledTonalIconButton(onClick = { speedIndex = (speedIndex + 1) % speeds.size; players.forEach { it.setPlaybackSpeed(speeds[speedIndex]) } }) {
                            Icon(Icons.Default.Speed, "Playback speed ${speeds[speedIndex]}x")
                        }
                        FilledTonalIconButton(onClick = {
                            val key = mediaItem.uri.toString()
                            val next = key !in liked
                            liked = if (next) liked + key else liked - key
                            onFavorite(mediaItem, next)
                        }) { Icon(Icons.Default.Favorite, "Favorite", tint = if (mediaItem.uri.toString() in liked || mediaItem.isFavorite) Color(0xFFFF6688) else Color.White) }
                        FilledTonalIconButton(onClick = { onMakeSticker(mediaItem) }) { Icon(Icons.Default.AutoAwesome, "Make sticker") }
                        FilledTonalIconButton(onClick = { onStatusSplit(mediaItem) }) { Text("Split", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalIconButton(onClick = {
                            runCatching {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = mediaItem.mimeType; putExtra(Intent.EXTRA_STREAM, mediaItem.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share clip"))
                            }
                        }) { Icon(Icons.Default.Share, "Share") }
                    }
                    Surface(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.78f).padding(start = 16.dp, bottom = 28.dp),
                        color = Color.Black.copy(alpha = 0.46f), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(12.dp)) {
                            Text(mediaItem.displayName, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text("${page + 1} / ${clips.size} · Offline feed", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
            Text("Close feed", color = Color.White)
        }
    }
}
