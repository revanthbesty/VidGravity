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
import androidx.compose.ui.platform.testTag
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
fun PlayerScreen(media: LibraryMedia, onBack: () -> Unit) {
    if (media.mimeType.startsWith("image/")) {
        ImagePreview(media, onBack)
        return
    }
    val context = LocalContext.current
    val activity = context as? Activity
    var playbackError by remember(media.uri) {mutableStateOf<String?>(null)}
    var buffering by remember(media.uri) {mutableStateOf(true)}
    var firstFrame by remember(media.uri) {mutableStateOf(false)}
    var isPlaying by remember(media.uri) {mutableStateOf(false)}
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
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

    DisposableEffect(player,lifecycleOwner) {
        val listener=object:Player.Listener {
            override fun onPlayerError(error:PlaybackException) {buffering=false;playbackError="This media could not play. Check that the file is available and its codec is supported, then retry."}
            override fun onPlaybackStateChanged(state:Int) {buffering=state==Player.STATE_BUFFERING}
            override fun onRenderedFirstFrame() {firstFrame=true;buffering=false;playbackError=null}
            override fun onIsPlayingChanged(value:Boolean) {isPlaying=value}
        }
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) player.pause()}
        player.addListener(listener);lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            player.removeListener(listener);lifecycleOwner.lifecycle.removeObserver(observer)
            if (player.duration > 0L && player.currentPosition > 0L &&
                player.playbackState != androidx.media3.common.Player.STATE_ENDED) {
                progressPrefs.edit().putLong(progressKey, player.currentPosition).apply()
            }
            player.release()
            applyFullscreen(false)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).testTag(if(firstFrame && playbackError==null) "player_ready" else "player_loading")) {
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

        playbackError?.let {message ->
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(message,color=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.testTag("player_error"))
                Button(onClick={playbackError=null;buffering=true;firstFrame=false;player.prepare();player.play()}) {Text("Retry playback")}
            }
        }
        if(buffering && playbackError==null) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                    Text(if (isPlaying) "Pause" else "Play")
                }
                TextButton(enabled=firstFrame && playbackError==null,onClick={
                    runCatching {context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type=media.mimeType;putExtra(android.content.Intent.EXTRA_STREAM,media.uri)
                        clipData=android.content.ClipData.newUri(context.contentResolver,media.displayName,media.uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },"Share media"))}.onFailure {playbackError="Sharing could not open. Check that the file is still available."}
                },modifier=Modifier.testTag("player_share")) {Text("Share",color=Color.White)}
            }
        }
    }
}
