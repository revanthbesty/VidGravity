package com.vidgravity.app

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*

private enum class SmartTool(val title:String,val detail:String) {
    TRIM("Smart Trim","Review ranked ranges; keep the moments you choose"),
    SILENCE("Silence Detection","Find low-energy pauses; approve every removal"),
    SCENES("Scene Detection","Sample visual changes and split at reviewed markers"),
    HIGHLIGHTS("Highlight Suggestions","Rank visual and audio signals, not meaning"),
    CAPTIONS("Smart Captions","Manual captions and SRT; model capability reporting"),
    REFRAME("Auto Reframe","Aspect presets, manual pans and detected face samples"),
    AUDIO("Audio Enhancement","Measured gain, headroom and before/after listening"),
    THUMBNAILS("Smart Thumbnails","Rank real source frames; edit and export a PNG")
}

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@Composable
internal fun SmartToolsPanel(project:StudioProject,selected:Int,onApply:(StudioProject)->Unit,onBusy:(Boolean)->Unit,
    onNavigate:(Long)->Unit,onCaptions:()->Unit,onThumbnail:(Long)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val lifecycle=LocalLifecycleOwner.current.lifecycle
    val clip=project.clips[selected.coerceIn(project.clips.indices)]
    val latestProject by rememberUpdatedState(project)
    val applyLatest by rememberUpdatedState(onApply)
    var tool by remember {mutableStateOf(SmartTool.TRIM)}
    var notes by remember(clip.id,clip.startMs,clip.endMs) {mutableStateOf(clip.smart)}
    var threshold by remember(clip.id) {mutableFloatStateOf(clip.smart?.threshold ?: 0.008f)}
    var job by remember {mutableStateOf<Job?>(null)}
    var working by remember {mutableStateOf(false)}
    var progress by remember {mutableFloatStateOf(0f)}
    var status by remember {mutableStateOf("Available · offline, review before applying")}
    var selection by remember(clip.id,notes) {mutableStateOf(emptySet<Int>())}
    var dismissedHighlights by remember(clip.id,notes) {mutableStateOf(emptySet<Int>())}
    var pending by remember {mutableStateOf<List<CreativeRange>?>(null)}
    var frame by remember {mutableStateOf<Bitmap?>(null)}
    var frameTime by remember {mutableLongStateOf(clip.startMs)}
    var preview by remember {mutableStateOf<ExoPlayer?>(null)}
    fun stopPreview() {preview?.release();preview=null}
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) {job?.cancel();stopPreview()}}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);job?.cancel();stopPreview();onBusy(false)}
    }
    DisposableEffect(working) {onBusy(working);onDispose {}}
    fun runTask(action:suspend ()->Unit) {
        if(working) return
        stopPreview();working=true;progress=0f;status="Processing locally…"
        job=scope.launch {
            try {action()}
            catch(cancelled:CancellationException) {status="Cancelled · project and source media preserved";throw cancelled}
            catch(failure:Exception) {status=failure.message ?: "Operation failed; saved media was preserved. Retry or edit manually."}
            finally {working=false}
        }
    }
    fun applyChange(action:()->StudioProject) {
        try {val next=action();next.validate();onApply(next);status="Completed · saved through Studio; undo/redo is available in the editor"}
        catch(failure:IllegalArgumentException) {status=failure.message ?: "This edit could not be applied. Existing project preserved."}
    }
    fun audition(gain:Float) {
        stopPreview()
        val player=ExoPlayer.Builder(context,SmartAudioRenderers(context,gain)).build();preview=player
        player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true).build()
        player.addListener(object:Player.Listener {override fun onPlayerError(error:androidx.media3.common.PlaybackException) {status="Audio preview unavailable; source preserved. Try another clip."}})
        player.volume=project.originalVolume
        player.setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.startMs).setEndPositionMs(minOf(clip.endMs,clip.startMs+3000)).build()).build())
        player.prepare();player.playWhenReady=true;status="Previewing three seconds of selected-source audio · other music/voice lanes are not included"
    }
    Text("AI Tools",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.testTag("ai_tools"))
    Text("On-device signals. Your creative decisions.",color=MaterialTheme.colorScheme.primary)
    Text("No cloud AI or video upload. Scene, silence, highlight and thumbnail results are deterministic suggestions, not semantic video understanding.",style=MaterialTheme.typography.bodySmall)
    SmartTool.entries.chunked(2).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {row.forEach {entry->
        OutlinedCard(onClick={if(!working) {tool=entry;stopPreview()}},modifier=Modifier.weight(1f).animateContentSize().testTag("ai_tool_${entry.name.lowercase()}"),
            colors=CardDefaults.outlinedCardColors(containerColor=if(tool==entry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(12.dp)) {
                Icon(when(entry) {SmartTool.AUDIO->Icons.Default.GraphicEq;SmartTool.SCENES->Icons.Default.Movie;SmartTool.CAPTIONS->Icons.Default.Subtitles;else->Icons.Default.AutoAwesome},contentDescription=null)
                Text(entry.title,style=MaterialTheme.typography.titleSmall)
                Text(when {
                    entry==SmartTool.CAPTIONS -> "Manual available · video transcription unsupported"
                    android.os.Build.VERSION.SDK_INT<27 -> "Unsupported · analysis needs API27+"
                    working -> "Processing"
                    entry in listOf(SmartTool.SILENCE,SmartTool.AUDIO) && notes?.audio?.isEmpty()==true -> "Unsupported · no readable source audio"
                    notes!=null -> "Completed · review saved analysis"
                    else -> "Available"
                },style=MaterialTheme.typography.labelSmall)
            }
        }
    }}}
    Text(tool.detail,style=MaterialTheme.typography.titleMedium)
    if(tool!=SmartTool.CAPTIONS) {
        if(tool==SmartTool.SILENCE) StudioAdjustment("Silence sensitivity",threshold,0.001f..0.05f,!working) {threshold=it}
        Button(enabled=!working && android.os.Build.VERSION.SDK_INT>=27,onClick={runTask {
            val source=clip
            val result=SmartAnalyzer.analyze(context,source,threshold,force=notes!=null) {label,value->scope.launch {status=label;progress=value}}
            val current=latestProject;val target=current.clips.firstOrNull {it.id==source.id}
            check(target!=null && target.uri==source.uri && target.startMs==source.startMs && target.endMs==source.endMs) {"The source changed during analysis. Analyze the current clip again."}
            val next=current.copy(clips=current.clips.map {if(it.id==source.id) it.copy(smart=result) else it});next.validate()
            applyLatest(next);notes=result;status="Completed · analysis saved locally. Select suggestions to review; nothing has been cut."
        }},modifier=Modifier.testTag("ai_analyze")) {Text(if(notes==null) "Analyze selected media" else "Reanalyze selected media")}
        if(android.os.Build.VERSION.SDK_INT<27) Text("Unsupported on API26: frame analysis requires API27. Manual editing remains available.")
    }
    if(working) {LinearProgressIndicator(progress={progress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());TextButton(onClick={job?.cancel()}) {Text("Cancel analysis")}}
    Text(status,modifier=Modifier.testTag("ai_status"),style=MaterialTheme.typography.bodySmall)
    val r=notes
    if(r!=null && tool!=SmartTool.CAPTIONS) {
        Text("Saved source range ${r.startMs}–${r.endMs} ms · 2-second visual samples · 200 ms silence windows",style=MaterialTheme.typography.labelSmall)
        Text("Analysis is limited to 60 seconds, or 15 on low-RAM devices. Cuts preserve all content outside the approved ranges. Reanalyze after replacing a source file.",style=MaterialTheme.typography.bodySmall)
    }
    when(tool) {
        SmartTool.SILENCE -> if(r!=null) {
            Text(r.audioStatus)
            val ranges=r.silence.map {CreativeRange(maxOf(it.startMs,clip.startMs),minOf(it.endMs,clip.endMs))}.filter {it.durationMs>=100}
            if(ranges.isEmpty()) Text("Completed · no removable silence found at this sensitivity. This is audio energy, not speech detection.")
            ranges.forEachIndexed {i,range->Row {
                Checkbox(i in selection,{checked->selection=if(checked) selection+i else selection-i},enabled=!working,modifier=Modifier.testTag("ai_silence_$i"))
                Text("Source ${range.startMs}–${range.endMs} ms · ${range.durationMs} ms low energy",modifier=Modifier.padding(top=10.dp))
            }}
            Button(enabled=!working && selection.isNotEmpty(),onClick={pending=ranges.filterIndexed {i,_->i in selection}},modifier=Modifier.testTag("ai_review_cuts")) {Text("Review selected cuts")}
        }
        SmartTool.SCENES -> if(r!=null) {
            val scenes=r.scenes.filter {it in (clip.startMs+100)..(clip.endMs-100)}
            if(scenes.isEmpty()) Text("Completed · no internal scene candidate at this sampling rate.")
            scenes.forEachIndexed {i,time->
                Text("Scene ${i+1} · source $time ms")
                Row {TextButton(enabled=!working,onClick={onNavigate(project.clips.take(selected).sumOf {it.playbackDurationMs}+((time-clip.startMs)/clip.speed).toLong())}) {Text("Go to scene ${i+1}")}
                    OutlinedButton(enabled=!working && project.clips.size<12,onClick={applyChange {SmartCuts.split(project,selected,time)}},modifier=Modifier.testTag("ai_split_$i")) {Text("Split at scene ${i+1}")}}
            }
        }
        SmartTool.TRIM,SmartTool.HIGHLIGHTS -> if(r!=null) {
            Text("Ranked heuristic suggestions · choose and adjust a range. No edit is applied until confirmation.")
            r.highlights.filter {it.startMs>=clip.startMs && it.endMs<=clip.endMs}.forEachIndexed {i,suggestion->
              if(i !in dismissedHighlights) {
                var start by remember(suggestion) {mutableLongStateOf(suggestion.startMs)};var end by remember(suggestion) {mutableLongStateOf(suggestion.endMs)}
                Text("${i+1}. ${suggestion.startMs}–${suggestion.endMs} ms · score ${"%.2f".format(suggestion.score)}")
                Text(suggestion.reason,style=MaterialTheme.typography.bodySmall)
                StudioLongField("Highlight ${i+1} start (source ms)",start,!working) {start=it.coerceIn(clip.startMs,clip.endMs-100)}
                StudioLongField("Highlight ${i+1} end (source ms)",end,!working) {end=it.coerceIn(clip.startMs+100,clip.endMs)}
                Button(enabled=!working && end-start>=100 && (start>clip.startMs || end<clip.endMs),onClick={pending=buildList {
                    if(start>clip.startMs) add(CreativeRange(clip.startMs,start));if(end<clip.endMs) add(CreativeRange(end,clip.endMs))
                }}) {Text("Review keeping highlight ${i+1}")}
                TextButton(enabled=!working,onClick={dismissedHighlights=dismissedHighlights+i}) {Text("Reject highlight ${i+1}")}
              }
            }
        }
        SmartTool.CAPTIONS -> {
            Text("Video-file transcription is unsupported: no offline ASR weights are bundled. No multilingual recognition is claimed.")
            Text(if(OfflineDictation.available(context)) "An on-device microphone recognizer is available on this device; English model readiness depends on the installed provider." else "No on-device microphone recognizer is available here.")
            Button(onClick=onCaptions) {Text("Open caption editing and SRT tools")}
        }
        SmartTool.REFRAME -> if(r!=null) {
            var aspect by remember {mutableStateOf(StudioAspect.PORTRAIT)}
            var x by remember {mutableFloatStateOf(0.5f)};var y by remember {mutableFloatStateOf(0.5f)}
            var time by remember(clip.id) {mutableLongStateOf(clip.startMs)}
            Row(Modifier.horizontalScroll(rememberScrollState())) {listOf(StudioAspect.WIDE,StudioAspect.PORTRAIT,StudioAspect.SQUARE,StudioAspect.FEED).forEach {a->FilterChip(aspect==a,{aspect=a},enabled=!working,label={Text(a.label)})}}
            StudioAdjustment("Framing center X",x,0f..1f,!working) {x=it};StudioAdjustment("Framing center Y",y,0f..1f,!working) {y=it}
            StudioLongField("Pan keyframe (source ms)",time,!working) {time=it.coerceIn(clip.startMs,clip.endMs)}
            fun reframed(keys:List<SmartFrameKey>):StudioProject {
                val crop=SmartFraming.frame(r.aspect,aspect)
                val next=clip.copy(frame=crop,framingKeys=keys.map {SmartFraming.bounded(crop,it)}.distinctBy {it.timeMs}.sortedBy {it.timeMs})
                return project.copy(clips=project.clips.map {if(it.id==clip.id) next else it},tools=project.tools.copy(aspect=aspect))
            }
            Button(enabled=!working,onClick={applyChange {val crop=SmartFraming.frame(r.aspect,aspect,x,y)
                project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(frame=crop,framingKeys=emptyList()) else it},tools=project.tools.copy(aspect=aspect))}}) {Text("Apply static manual framing")}
            OutlinedButton(enabled=!working && clip.framingKeys.size<32,onClick={applyChange {reframed(clip.framingKeys.filter {it.timeMs!=time}+SmartFrameKey(time,x,y))}}) {Text("Apply pan keyframe")}
            Text("${clip.framingKeys.size} pan keys saved · linear interpolation; the first/last key holds outside their range.")
            Text(r.faceStatus)
            Button(enabled=!working && r.faces.size>=2,onClick={applyChange {reframed(r.faces)}}) {Text("Apply detected face pan samples")}
            Text("Face-guided pans use sparse frontal-face samples, not identity tracking or segmentation. No tested subject model is bundled. Preview the full motion, especially outside the analyzed range.",style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!working && clip.framingKeys.isNotEmpty(),onClick={applyChange {project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(framingKeys=emptyList()) else it})}}) {Text("Clear pan keyframes")}
        }
        SmartTool.AUDIO -> if(r!=null) {
            val gain=SmartRanking.normalization(r.rms,r.peak)
            Text(r.audioStatus)
            Text("Suggested source gain ${"%.2f".format(gain)}× · peak-aware RMS target, not a loudness/LUFS standard or denoiser")
            val before=MaterialTheme.colorScheme.secondary;val after=MaterialTheme.colorScheme.tertiary
            Canvas(Modifier.fillMaxWidth().height(80.dp)) {r.audio.forEachIndexed {i,a->val cx=(i+0.5f)*size.width/r.audio.size.coerceAtLeast(1)
                drawLine(before,Offset(cx,size.height/2),Offset(cx,size.height/2-a.rms*size.height/2),3f)
                drawLine(after,Offset(cx,size.height/2),Offset(cx,size.height/2+(a.rms*gain).coerceAtMost(1f)*size.height/2),3f)}}
            Text("Before (top) / proposed gain (bottom) · 1-second energy overview. Peak protection covers the analyzed samples; review unanalyzed audio.",style=MaterialTheme.typography.labelSmall)
            Row {TextButton(enabled=!working && r.audio.isNotEmpty(),onClick={audition(1f)}) {Text("Listen before")};TextButton(enabled=!working && r.audio.isNotEmpty(),onClick={audition(gain)}) {Text("Listen after")}}
            TextButton(onClick={stopPreview()}) {Text("Stop audio preview")}
            Button(enabled=!working && r.audio.isNotEmpty(),onClick={applyChange {project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(audioGain=gain) else it})}},modifier=Modifier.testTag("ai_apply_gain")) {Text("Apply measured normalization")}
            Text("Noise reduction is unsupported. Existing music/voice balance, fades and detailed PCM waveforms remain in Studio audio tools.")
        }
        SmartTool.THUMBNAILS -> if(r!=null) {
            r.thumbnails.filter {it.startMs in clip.startMs until clip.endMs}.forEachIndexed {i,s->
                Text("Frame ${i+1} · ${s.startMs} ms · ${s.reason}")
                Button(enabled=!working,onClick={runTask {val next=withContext(Dispatchers.IO) {ThumbnailRenderer.frame(context,clip.uri,s.startMs,480)};frame=next;frameTime=s.startMs;status="Completed · previewing actual source frame"}}) {Text("Preview suggested frame ${i+1}")}
            }
            frame?.let {Image(it.asImageBitmap(),"Suggested source frame",Modifier.fillMaxWidth().heightIn(max=240.dp))
                Button(enabled=!working,onClick={onThumbnail(frameTime)}) {Text("Edit this frame as a thumbnail")}}
            Text("The thumbnail editor supports manual frame time, crop, aspect, titles, badges, local projects and PNG export to Library.")
        }
    }
    pending?.let {cuts->AlertDialog(onDismissRequest={pending=null},title={Text("Apply reviewed cuts?")},text={Text("Remove ${cuts.sumOf {it.durationMs}} source ms from the selected clip. Timed layers are retimed where supported. Originals remain untouched; undo is available in Studio.")},
        dismissButton={TextButton(onClick={pending=null}) {Text("Keep editing")}},confirmButton={TextButton(onClick={pending=null;applyChange {SmartCuts.remove(project,selected,cuts)}},modifier=Modifier.testTag("ai_confirm_cuts")) {Text("Apply approved cuts")}})}
}
