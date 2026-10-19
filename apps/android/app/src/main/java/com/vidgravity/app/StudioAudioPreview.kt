package com.vidgravity.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.math.abs

internal data class StudioPreviewAudio(val uri: String, val start: Long, val end: Long?, val offset: Long,
    val gain: Float, val fadeIn: Long=0, val fadeOut: Long=0, val loop: Boolean=false) {
    fun position(globalMs: Long, mediaDuration: Long, projectDuration: Long): Long? {
        val duration=if(end!=null) minOf(end-start,projectDuration-offset) else mediaDuration
        val local=globalMs-offset
        if(duration<=0 || local<0 || globalMs>=projectDuration || (!loop && local>=duration)) return null
        return if(loop) local%duration else local
    }
    fun volume(localMs: Long, durationMs: Long): Float = gain * minOf(1f,
        if(fadeIn>0) localMs.toFloat()/fadeIn else 1f,
        if(fadeOut>0) (durationMs-localMs).toFloat()/fadeOut else 1f).coerceIn(0f,1f)
}

/** Monitor mix for a selected video clip. Export remains the sample-accurate Media3 composition.
 * Players share the output-time clock, and resync after seek/buffering. This is not a DAW clock. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Composable
internal fun StudioAudioPreview(project: StudioProject, clip: StudioClip, master: ExoPlayer) {
    val context=LocalContext.current
    val sources=remember(project.musicUri,project.musicVolume,project.voiceUri,project.voiceVolume,project.tools.audio,project.durationMs) {
        buildList {
            project.musicUri?.let {add(StudioPreviewAudio(it,0,null,0,project.musicVolume,loop=true))}
            project.voiceUri?.let {add(StudioPreviewAudio(it,0,null,0,project.voiceVolume))}
            project.tools.audio.forEach {add(StudioPreviewAudio(it.uri,it.startMs,it.endMs,it.offsetMs,it.volume,it.fadeInMs,it.fadeOutMs))}
        }
    }
    val players=remember(sources) {sources.map {ExoPlayer.Builder(context).build()}}
    var failed by remember(sources) {mutableStateOf(false)}
    DisposableEffect(players) {
        val listener=object:Player.Listener {override fun onPlayerError(error: PlaybackException) {failed=true}}
        players.forEachIndexed {index,p ->
            val source=sources[index]
            p.addListener(listener)
            val clipping=MediaItem.ClippingConfiguration.Builder().setStartPositionMs(source.start).apply {
                source.end?.let {setEndPositionMs(minOf(it,source.start+project.durationMs-source.offset))}
            }.build()
            p.setMediaItem(MediaItem.Builder().setUri(source.uri).setClippingConfiguration(clipping).build())
            p.volume=0f; p.prepare()
        }
        onDispose {players.forEach {it.removeListener(listener);it.release()}}
    }
    val offset=project.clips.takeWhile {it.id!=clip.id}.sumOf {it.playbackDurationMs}
    LaunchedEffect(players,master,clip,offset,project.durationMs) {
        while(true) {
            val global=offset+(master.currentPosition/clip.speed).toLong()
            players.forEachIndexed {index,p ->
                val source=sources[index]
                val local=source.position(global,p.duration,project.durationMs)
                if(local==null || !master.isPlaying) {
                    p.pause()
                    if(local!=null && abs(p.currentPosition-local)>120) p.seekTo(local)
                } else {
                    if(abs(p.currentPosition-local)>120 || p.playbackState==Player.STATE_ENDED) p.seekTo(local)
                    val duration=source.end?.let {minOf(it-source.start,project.durationMs-source.offset)} ?: p.duration
                    p.volume=source.volume(local,duration)
                    p.play()
                }
            }
            delay(50)
        }
    }
    if(failed) Text("Some audio could not preview. Relink or replace the source before export.",color=MaterialTheme.colorScheme.error)
}
