package com.vidgravity.app

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.math.sqrt

@Composable
internal fun CinemaAudioPanel(project:StudioProject,enabled:Boolean,onEdit:(StudioProject,String?)->Unit,onAddAudio:()->Unit,
    positionMs:Long=0,onRecordVoice:(()->Unit)?=null,onProcessing:(Boolean)->Unit={}) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val latest by rememberUpdatedState(project);val editLatest by rememberUpdatedState(onEdit)
    var processing by remember {mutableStateOf(false)};var message by remember {mutableStateOf<String?>(null)}
    val active=enabled && !processing
    fun change(a:StudioAudioLane,group:String?=null) {onEdit(project.copy(tools=project.tools.copy(audio=project.tools.audio.map {if(it.id==a.id) a else it})),group)}
    Text("Audio Mixer",style=MaterialTheme.typography.titleLarge)
    Text("Four independent tracks. Solo isolates selected tracks, including from original/legacy audio. Ducking follows Voice-lane timing, not speech detection.",style=MaterialTheme.typography.labelSmall)
    Button(enabled=active && project.tools.audio.size<4,onClick=onAddAudio) {Text("Import audio lane")}
    onRecordVoice?.let {action->OutlinedButton(enabled=active,onClick=action) {Text("Record voiceover in editor")}}
    if(project.voiceUri!=null) OutlinedButton(enabled=active && project.tools.audio.size<4,onClick={
        val source=project.voiceUri ?: return@OutlinedButton;processing=true;onProcessing(true)
        scope.launch {
            try {
                val duration=withContext(Dispatchers.IO) {val r=MediaMetadataRetriever();try {r.setDataSource(context,Uri.parse(source));r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()} finally {r.release()}}
                val current=latest;check(current.voiceUri==source) {"Voiceover changed; retry with the current recording."}
                require(duration>=100 && current.tools.audio.size<4)
                val offset=positionMs.coerceIn(0,current.durationMs-100)
                val lane=StudioAudioLane(uri=source,name="Recorded voiceover",sourceDurationMs=duration,endMs=minOf(duration,current.durationMs-offset),offsetMs=offset,volume=current.voiceVolume,role=CinemaAudioRole.VOICE)
                editLatest(current.copy(voiceUri=null,tools=current.tools.copy(audio=current.tools.audio+lane)),null)
                message="Recorded voiceover moved to a Voice track at the playhead. The original file is preserved."
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(failure:Exception) {message=failure.message ?: "Voiceover could not move to the mixer; existing audio was preserved."}
            finally {processing=false;onProcessing(false)}
        }
    }) {Text("Move recorded voiceover to mixer")}
    if(processing) LinearProgressIndicator(Modifier.fillMaxWidth())
    message?.let {Text(it,modifier=Modifier.testTag("cinema_audio_status"))}
    project.tools.audio.forEachIndexed {i,a->key(a.id) {
        OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("A${i+1} · ${a.name}",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(a.muted,{change(a.copy(muted=!a.muted))},enabled=active,label={Text("Mute A${i+1}")},modifier=Modifier.testTag("cinema_mute_${a.id}"))
                FilterChip(a.solo,{change(a.copy(solo=!a.solo))},enabled=active,label={Text("Solo A${i+1}")},modifier=Modifier.testTag("cinema_solo_${a.id}"))
                CinemaAudioRole.entries.forEach {role->FilterChip(a.role==role,{change(a.copy(role=role))},enabled=active,label={Text(role.name.lowercase().replaceFirstChar {it.uppercase()})})}
            }
            StudioWaveform(a,active,project)
            StudioAdjustment("Lane ${i+1} volume",a.volume,0f..1f,active) {change(a.copy(volume=it),"volume-${a.id}")}
            StudioAdjustment("Lane ${i+1} start on timeline (ms)",a.offsetMs.toFloat(),0f..(project.durationMs-1).toFloat(),active) {change(a.copy(offsetMs=it.toLong()),"offset-${a.id}")}
            StudioLongField("Audio source in (ms)",a.startMs,active) {change(a.copy(startMs=it,envelope=null))}
            StudioLongField("Audio source out (ms)",a.endMs,active) {change(a.copy(endMs=it,envelope=null))}
            StudioAdjustment("Audio fade in (ms)",a.fadeInMs.toFloat(),0f..a.durationMs.toFloat(),active) {change(a.copy(fadeInMs=it.toLong(),envelope=null),"fadein-${a.id}")}
            StudioAdjustment("Audio fade out (ms)",a.fadeOutMs.toFloat(),0f..a.durationMs.toFloat(),active) {change(a.copy(fadeOutMs=it.toLong(),envelope=null),"fadeout-${a.id}")}
            if(a.envelope!=null) Text("Source fades are preserved across cuts. Adjusting a fade re-anchors it to this fragment.",style=MaterialTheme.typography.labelSmall)
            VgDisclosure("Volume automation · A${i+1}") {
                CinemaScalarEditor("cinema_volume_${a.id}","Volume",a.volumeKeys,a.sourceDurationMs,
                    (a.startMs+positionMs-a.offsetMs).coerceIn(a.startMs,a.endMs),1f,0f..2f,enabled=active) {change(a.copy(volumeKeys=it))}
            }
            if(a.role!=CinemaAudioRole.VOICE) Row {Checkbox(a.duck,{change(a.copy(duck=it))},enabled=active);Text("Duck under timed Voice tracks")}
            if(a.duck && a.role!=CinemaAudioRole.VOICE) StudioAdjustment("Ducked music level",a.duckGain,0f..1f,active) {change(a.copy(duckGain=it),"duck-${a.id}")}
            Button(enabled=active,onClick={
                val source=a;processing=true;onProcessing(true)
                scope.launch {
                    try {
                        val bins=CreativeAnalyzer.waveform(context,source);require(bins.isNotEmpty()) {"No readable audio samples."}
                        val length=bins.sumOf {it.endMs-it.startMs}.coerceAtLeast(1)
                        val rms=sqrt(bins.sumOf {it.rms.toDouble()*it.rms*(it.endMs-it.startMs)}/length).toFloat()
                        val gain=SmartRanking.normalization(rms,bins.maxOf {it.peak})
                        val current=latest;val target=current.tools.audio.firstOrNull {it.id==source.id}
                        check(target!=null && target.uri==source.uri && target.startMs==source.startMs && target.endMs==source.endMs) {"The track changed. Analyze its current trim again."}
                        editLatest(current.copy(tools=current.tools.copy(audio=current.tools.audio.map {if(it.id==source.id) it.copy(normalization=gain) else it})),null)
                        message="Measured source gain ${"%.2f".format(gain)}× applied. Peak-aware RMS analysis covers the first 60 seconds; review longer audio. This is not LUFS normalization or noise reduction."
                    } catch(cancelled:CancellationException) {throw cancelled}
                    catch(failure:Exception) {message=failure.message ?: "Audio analysis failed; existing mix preserved."}
                    finally {processing=false;onProcessing(false)}
                }
            }) {Text("Measure and normalize A${i+1}")}
            Text("Normalization gain: ${"%.2f".format(a.normalization)}× · preview and export",style=MaterialTheme.typography.labelSmall)
            TextButton(enabled=active && a.normalization!=1f,onClick={change(a.copy(normalization=1f))}) {Text("Reset normalization A${i+1}")}
            var split by remember(a.id) {mutableStateOf(((a.startMs+a.endMs)/2).toString())}
            CinemaKeyTime("Split audio at source ms",split,{split=it},"cinema_audio_split_${a.id}",active)
            OutlinedButton(enabled=active && project.tools.audio.size<4 && split.toLongOrNull()?.let {it>=a.startMs+100 && it<=a.endMs-100}==true,onClick={
                try {onEdit(CinemaMix.split(project,a.id,split.toLong()),null)} catch(failure:IllegalArgumentException) {message=failure.message}
            },modifier=Modifier.testTag("cinema_audio_split_apply_${a.id}")) {Text("Split audio A${i+1}")}
            TextButton(enabled=active,onClick={onEdit(project.copy(tools=project.tools.copy(audio=project.tools.audio.filter {it.id!=a.id})),null)}) {Text("Remove lane ${i+1}")}
        }}
    }}
}
