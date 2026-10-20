package com.vidgravity.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Actual decoded PCM RMS, in 200ms bins. Loading is explicit and bounded, never on the UI thread. */
@Composable
internal fun StudioWaveform(lane: StudioAudioLane, enabled: Boolean,project:StudioProject?=null) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var bins by remember(lane.uri,lane.startMs,lane.endMs) {mutableStateOf<List<AudioObservation>?>(null)}
    var loading by remember {mutableStateOf(false)}
    var failed by remember(lane.uri,lane.startMs,lane.endMs) {mutableStateOf(false)}
    val currentKey="${lane.uri}:${lane.startMs}:${lane.endMs}"
    val latestKey by rememberUpdatedState(currentKey)
    TextButton(enabled=enabled && !loading,onClick={
        val key=currentKey
        loading=true;failed=false
        scope.launch {
            try {val result=CreativeAnalyzer.waveform(context,lane);if(key==latestKey) bins=result}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(failure:Exception) {if(key==latestKey) failed=true}
            finally {loading=false}
        }
    }) {Text(if(loading) "Decoding waveform…" else "Analyze waveform")}
    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(failed) Text("Audio waveform unavailable. The source and edits are preserved.",color=MaterialTheme.colorScheme.error)
    bins?.let {values ->
        if(values.isEmpty()) Text("No decodable audio samples found.")
        else {
            val color=MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(64.dp).testTag("studio_audio_waveform")) {
                val shown=minOf(lane.durationMs,60_000L)
                drawLine(color.copy(alpha=0.3f),Offset(0f,size.height/2),Offset(size.width,size.height/2))
                val monitor=StudioPreviewAudio(lane.uri,lane.startMs,lane.endMs,lane.offsetMs,lane.volume,lane.fadeInMs,lane.fadeOutMs)
                values.forEach {bin ->
                    val x=((bin.startMs+bin.endMs)/2f/shown)*size.width
                    val gain=if(project==null) monitor.volume((bin.startMs+bin.endMs)/2,lane.durationMs) else CinemaMix.gain(project,lane,lane.startMs+(bin.startMs+bin.endMs)/2)
                    val height=(bin.rms*gain*3).coerceIn(0f,1f)*size.height/2
                    drawLine(color,Offset(x,size.height/2-height),Offset(x,size.height/2+height),maxOf(1f,size.width*160/shown))
                }
            }
            Text("Decoded RMS · 200 ms bins · gain/fades shown"+if(lane.durationMs>60_000) " · first 60 seconds" else "",style=MaterialTheme.typography.labelSmall)
        }
    }
}
