package com.vidgravity.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class IdentifiedSoundtrack(
    val title: String,
    val artist: String,
    val spotifyTrackId: String? = null,
    val appleMusicUrl: String? = null
)

@Composable
fun SoundtrackBadge(track: IdentifiedSoundtrack) {
    val context = LocalContext.current
    val spotifyId = track.spotifyTrackId?.takeIf { it.matches(Regex("^[A-Za-z0-9]{22}$")) }
    val appleUrl = track.appleMusicUrl?.takeIf { it.startsWith("https://music.apple.com/") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = {
                val uri = spotifyId?.let { Uri.parse("spotify:track:$it") }
                    ?: appleUrl?.let(Uri::parse)
                    ?: return@AssistChip
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }.onFailure {
                    spotifyId?.let { id ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/track/$id")))
                    }
                }
            },
            label = { Text("Identified soundtrack · ${track.title} — ${track.artist}", maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
        )
        if (spotifyId != null) {
            AssistChip(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/track/$spotifyId")))
            }, label = { Text("Open Spotify") })
        }
    }
}
