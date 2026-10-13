package com.vidgravity.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.File

/**
 * Secure local-session player scaffold.
 *
 * FLAG_SECURE blocks ordinary Android screenshots/recordings. This player only accepts
 * an app-private, already-decrypted temporary file; production Ghost Send must pass an
 * authenticated in-memory decrypting DataSource instead of a plaintext file.
 */
class GhostPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val mediaPath = intent.getStringExtra(EXTRA_TEMP_MEDIA_PATH).orEmpty()
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 15).coerceIn(5, 30)
        if (sessionId.isBlank() || mediaPath.isBlank() ||
            !ReplayTelemetry.openOrRecordReplayBlocked(this, sessionId)) {
            finish()
            return
        }
        ReplayTelemetry.record(this, sessionId, ReplayTelemetry.EventType.CAPTURE_PROTECTION_ENABLED)
        // Malformed or inaccessible paths fail closed.
        val file = runCatching { File(mediaPath).canonicalFile }.getOrNull()
        val cacheRoot = runCatching { cacheDir.canonicalFile }.getOrNull()
        if (file == null || cacheRoot == null || !runCatching {
                file.isFile && file.canonicalPath.startsWith(cacheRoot.path + File.separator)
            }.getOrDefault(false)) {
            finish()
            return
        }
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    GhostPlayerContent(
                        activity = this,
                        file = file,
                        seconds = seconds,
                        onExpired = {
                            ReplayTelemetry.record(this, sessionId, ReplayTelemetry.EventType.EXPIRED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    companion object {
        const val EXTRA_SESSION_ID = "ghost_session_id"
        const val EXTRA_TEMP_MEDIA_PATH = "ghost_temp_media_path"
        const val EXTRA_SECONDS = "ghost_seconds"
    }
}

@Composable
private fun GhostPlayerContent(
    activity: Activity,
    file: File,
    seconds: Int,
    onExpired: () -> Unit
) {
    var remaining by remember { mutableIntStateOf(seconds) }
    val player = remember {
        ExoPlayer.Builder(activity).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
            // Best-effort cache cleanup. Secure deletion cannot be guaranteed on flash storage.
            runCatching { file.writeBytes(ByteArray(0)) }
            file.delete()
        }
    }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
        onExpired()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("View once", style = MaterialTheme.typography.titleLarge)
        Text("Closes in $remaining seconds", style = MaterialTheme.typography.bodyMedium)
        Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> PlayerView(context).apply { this.player = player; useController = true } },
                update = { it.player = player }
            )
        }
        Button(onClick = onExpired, modifier = Modifier.align(Alignment.End)) {
            Text("Close now")
        }
    }
}
