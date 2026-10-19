package com.vidgravity.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
internal fun StudioTimeline(project: StudioProject, selected: Int, positionMs: Long, enabled: Boolean,
    onSelect: (Int) -> Unit, onSeek: (Long) -> Unit, onEdit: (StudioProject, String?) -> Unit) {
    val latestProject by rememberUpdatedState(project)
    val latestEdit by rememberUpdatedState(onEdit)
    var zoom by remember { mutableFloatStateOf(28f) }
    var snapping by remember {mutableStateOf(true)}
    val density = LocalDensity.current.density
    val scale = minOf(zoom, 12000f / (project.durationMs / 1000f))
    val widths = project.clips.map { maxOf(64f, it.playbackDurationMs / 1000f * scale) }
    val totalWidth = widths.sum()
    // Short clips have a minimum touch target; map audio timing through the same
    // piecewise scale so lane boundaries still align with video boundaries.
    fun timelineX(timeMs: Long): Float {
        var start=0L; var x=0f
        project.clips.forEachIndexed { i,clip ->
            if(timeMs <= start+clip.playbackDurationMs) return x+widths[i]*((timeMs-start).coerceAtLeast(0).toFloat()/clip.playbackDurationMs)
            start+=clip.playbackDurationMs; x+=widths[i]
        }
        return totalWidth
    }
    fun timelineTime(x: Float): Long {
        var cursor=0f;var time=0L
        widths.forEachIndexed {i,w ->
            if(x<=cursor+w) return (time+((x-cursor).coerceAtLeast(0f)/w*project.clips[i].playbackDurationMs).toLong()).coerceIn(0,project.durationMs)
            time+=project.clips[i].playbackDurationMs;cursor+=w
        }
        return project.durationMs
    }
    val anchors=StudioTiming.boundaries(project)+positionMs
    fun seek(time: Long) = onSeek(StudioTiming.frame(if(snapping) StudioTiming.snap(time,anchors,project.durationMs) else time,project.tools.frameRate,project.durationMs))
    Text("Editing timeline", style=MaterialTheme.typography.titleLarge)
    Text("Long-press video to reorder. Drag audio or text directly to move timing. Video is sequential; audio lanes mix independently.",style=MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={zoom=(zoom/2).coerceAtLeast(8f)}) { Text("Zoom out") }
        TextButton(onClick={zoom=(zoom*2).coerceAtMost(128f)}) { Text("Zoom in") }
        FilterChip(snapping,{snapping=!snapping},label={Text("Snap")})
        Text("${positionMs} ms",modifier=Modifier.padding(top=12.dp))
    }
    // Bounded: at most 12 video clips, 4 audio lanes and 16 text layers. No media decode here.
    Box(Modifier.horizontalScroll(rememberScrollState())) {
      Column {
        Row {
            project.clips.forEachIndexed { index, clip ->
                val width = widths[index]
                var drag by remember(clip.id) { mutableFloatStateOf(0f) }
                val start = project.clips.take(index).sumOf { it.playbackDurationMs }
                Column(Modifier.width(width.dp).height(88.dp)
                    .background(if(index==selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp,MaterialTheme.colorScheme.outline)
                    .graphicsLayer {translationX=drag}
                    .clickable(enabled=enabled) { onSelect(index); onSeek(start) }
                    .pointerInput(project.clips,enabled,widths) {
                        if(enabled) detectDragGesturesAfterLongPress(onDragStart={drag=0f},onDragCancel={drag=0f},onDragEnd={
                            val center=widths.take(index).sum()+width/2+drag/density
                            var cursor=0f
                            val target=widths.indexOfFirst { cursor+=it; center<cursor }.let { if(it<0) widths.lastIndex else it }
                            if(abs(drag)>12*density && target!=index) latestEdit(StudioEdits.move(latestProject,index,target),null)
                            drag=0f
                        }) { change,amount -> change.consume(); drag+=amount.x }
                    }.padding(8.dp).semantics { contentDescription="Video clip ${index+1}: ${clip.name}" }) {
                    Text("V1 · ${index+1}"+if(project.fadeEdges) " ◀ Fade ${project.tools.fadeDurationMs}ms ▶" else "",style=MaterialTheme.typography.labelSmall)
                    Text(clip.name,maxLines=1)
                    Text("${start}–${start+clip.playbackDurationMs} ms",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        project.tools.audio.forEachIndexed { i,lane ->
            StudioTimedLane("A${i+1} · ${lane.name}",lane.offsetMs,minOf(project.durationMs,lane.offsetMs+lane.durationMs),
                totalWidth,timelineX(lane.offsetMs),timelineX(minOf(project.durationMs,lane.offsetMs+lane.durationMs)),enabled,
                MaterialTheme.colorScheme.secondaryContainer,{onSeek(lane.offsetMs)}) {delta ->
                val raw=timelineTime(timelineX(lane.offsetMs)+delta/density)
                val moved=if(snapping) StudioTiming.snap(raw,anchors,project.durationMs-1) else raw.coerceIn(0,project.durationMs-1)
                android.util.Log.i("VGStudio","timeline.audio.move fromMs=${lane.offsetMs} toMs=$moved")
                onEdit(project.copy(tools=project.tools.copy(audio=project.tools.audio.map {if(it.id==lane.id) it.copy(offsetMs=moved) else it})),null)
            }
        }
        project.tools.textLayers.forEachIndexed {i,layer ->
            StudioTimedLane("T${i+1} · ${layer.text.take(24)}",layer.startMs,layer.endMs,totalWidth,
                timelineX(layer.startMs),timelineX(layer.endMs),enabled,MaterialTheme.colorScheme.tertiaryContainer,{onSeek(layer.startMs)}) {delta ->
                val duration=layer.endMs-layer.startMs
                val raw=timelineTime(timelineX(layer.startMs)+delta/density)
                val moved=if(snapping) StudioTiming.snap(raw,anchors,project.durationMs-duration) else raw.coerceIn(0,project.durationMs-duration)
                android.util.Log.i("VGStudio","timeline.text.move fromMs=${layer.startMs} toMs=$moved durationMs=$duration")
                onEdit(project.copy(tools=project.tools.copy(textLayers=project.tools.textLayers.map {if(it.id==layer.id) it.copy(startMs=moved,endMs=moved+duration) else it})),null)
            }
        }
        if(project.tools.captions.isNotEmpty()) {
            val cueColor=MaterialTheme.colorScheme.tertiary
            val cueTextColor=MaterialTheme.colorScheme.onTertiary.toArgb()
            val cuePaint=remember(cueTextColor,density) {android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=cueTextColor;textSize=12*density}}
            val captionSeek by rememberUpdatedState<(Float)->Unit>({x ->seek(timelineTime(x/density))})
            Canvas(Modifier.width(totalWidth.dp).height(40.dp).semantics {contentDescription="Caption track: ${project.tools.captions.size} cues"}
                .pointerInput(enabled) {if(enabled) detectTapGestures {captionSeek(it.x)}}) {
                project.tools.captions.forEachIndexed {i,cue ->
                    val left=timelineX(cue.startMs)*density
                    val right=timelineX(cue.endMs)*density
                    drawRect(cueColor,Offset(left,2*density),androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(1f),36*density))
                    if(right-left>24*density) drawIntoCanvas {it.nativeCanvas.drawText("C${i+1}",left+3*density,24*density,cuePaint)}
                }
            }
        }
        if(project.clips.any {it.smart!=null}) {
            val sceneColor=MaterialTheme.colorScheme.tertiary
            val silenceColor=MaterialTheme.colorScheme.error.copy(alpha=0.35f)
            Text("Saved AI markers · lines: scenes · shaded: low energy",style=MaterialTheme.typography.labelSmall)
            Canvas(Modifier.width(totalWidth.dp).height(32.dp).semantics {contentDescription="Saved scene and silence analysis markers"}) {
                var offset=0L
                project.clips.forEach {clip->
                    fun x(source:Long)=timelineX(offset+((source-clip.startMs).coerceIn(0,clip.durationMs)/clip.speed).toLong())*density
                    clip.smart?.let {notes->
                        notes.silence.forEach {r->val a=maxOf(r.startMs,clip.startMs);val b=minOf(r.endMs,clip.endMs)
                            if(b>a) drawRect(silenceColor,Offset(x(a),0f),androidx.compose.ui.geometry.Size((x(b)-x(a)).coerceAtLeast(1f),size.height))}
                        notes.scenes.filter {it in clip.startMs until clip.endMs}.forEach {s->drawLine(sceneColor,Offset(x(s),0f),Offset(x(s),size.height),2*density)}
                    };offset+=clip.playbackDurationMs
                }
            }
        }
      }
      val playheadColor=MaterialTheme.colorScheme.primary
      Canvas(Modifier.matchParentSize()) {
          val x=timelineX(positionMs)*density
          drawLine(playheadColor,Offset(x,0f),Offset(x,size.height),2*density)
      }
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        val fps=project.tools.frameRate.takeIf {it>0} ?: 30
        TextButton(enabled=enabled,onClick={onSeek(StudioTiming.frame(positionMs-(1000.0/fps).toLong()-1,fps,project.durationMs))}) {Text("Previous frame step")}
        TextButton(enabled=enabled,onClick={onSeek(StudioTiming.frame(positionMs+(1000.0/fps).toLong()+1,fps,project.durationMs))}) {Text("Next frame step")}
    }
    Text("Seek steps use ${project.tools.frameRate.takeIf {it>0} ?: 30} fps; decoder frame precision depends on the source.",style=MaterialTheme.typography.labelSmall)
    Slider(positionMs.toFloat().coerceIn(0f,project.durationMs.toFloat()),{seek(it.toLong())},valueRange=0f..project.durationMs.toFloat(),enabled=enabled,
        modifier=Modifier.testTag("timeline_seek").semantics { contentDescription="Timeline playhead" })
}

@Composable
private fun StudioTimedLane(label: String,start: Long,end: Long,totalWidth: Float,left: Float,right: Float,
    enabled: Boolean,color: Color,onClick: ()->Unit,onMove: (Float)->Unit) {
    val density=LocalDensity.current.density
    val latestMove by rememberUpdatedState(onMove)
    var drag by remember {mutableFloatStateOf(0f)}
    Row(Modifier.width(totalWidth.dp).height(48.dp)) {
        Spacer(Modifier.width(left.dp))
        // Keep the visible bar time-proportional while giving short clips a usable touch target.
        Box(Modifier.width((right-left).coerceAtLeast(48f).dp).fillMaxHeight().graphicsLayer {translationX=drag}
            .clickable(enabled=enabled,onClick=onClick)
            .pointerInput(start,end,totalWidth,enabled) {
                if(enabled) detectHorizontalDragGestures(
                    onDragStart={drag=0f;android.util.Log.i("VGStudio","timeline.lane.drag.started")},
                    onDragCancel={drag=0f;android.util.Log.i("VGStudio","timeline.lane.drag.cancelled")},
                    onDragEnd={android.util.Log.i("VGStudio","timeline.lane.drag.end deltaPx=$drag");latestMove(drag);drag=0f}
                ) {change,amount ->change.consume();drag+=amount}
            }.semantics(mergeDescendants=true) {contentDescription="Timeline $label"}) {
            Canvas(Modifier.matchParentSize()) {drawRect(color,size=androidx.compose.ui.geometry.Size((right-left).coerceAtLeast(1f)*density,size.height))}
            Text("$label · $start–$end ms",maxLines=1,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(6.dp))
        }
    }
}
