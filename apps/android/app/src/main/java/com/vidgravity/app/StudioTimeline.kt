package com.vidgravity.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var zoom by remember { mutableFloatStateOf(28f) }
    val density = LocalDensity.current.density
    val scale = minOf(zoom, 12000f / (project.durationMs / 1000f))
    val widths = project.clips.map { maxOf(64f, it.playbackDurationMs / 1000f * scale) }
    val totalWidth = widths.sum()
    Text("Editing timeline", style=MaterialTheme.typography.titleLarge)
    Text("Long-press a clip and drag to reorder. Video is sequential; audio lanes mix independently.",style=MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={zoom=(zoom/2).coerceAtLeast(8f)}) { Text("Zoom out") }
        TextButton(onClick={zoom=(zoom*2).coerceAtMost(128f)}) { Text("Zoom in") }
        Text("${positionMs} ms",modifier=Modifier.padding(top=12.dp))
    }
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            project.clips.forEachIndexed { index, clip ->
                val width = widths[index]
                var drag by remember(clip.id) { mutableFloatStateOf(0f) }
                val start = project.clips.take(index).sumOf { it.playbackDurationMs }
                Column(Modifier.width(width.dp).height(88.dp)
                    .background(if(index==selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp,MaterialTheme.colorScheme.outline)
                    .clickable(enabled=enabled) { onSelect(index); onSeek(start) }
                    .pointerInput(project.clips,enabled,widths) {
                        if(enabled) detectDragGesturesAfterLongPress(onDragStart={drag=0f},onDragCancel={drag=0f},onDragEnd={
                            val center=widths.take(index).sum()+width/2+drag/density
                            var cursor=0f
                            val target=widths.indexOfFirst { cursor+=it; center<cursor }.let { if(it<0) widths.lastIndex else it }
                            if(abs(drag)>12*density && target!=index) onEdit(StudioEdits.move(project,index,target),null)
                            drag=0f
                        }) { change,amount -> change.consume(); drag+=amount.x }
                    }.padding(8.dp).semantics { contentDescription="Video clip ${index+1}: ${clip.name}" }) {
                    Text("V1 · ${index+1}",style=MaterialTheme.typography.labelSmall)
                    Text(clip.name,maxLines=1)
                    Text("${start}–${start+clip.playbackDurationMs} ms",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        project.tools.audio.forEachIndexed { i,lane ->
            Row(Modifier.width(totalWidth.dp).height(36.dp)) {
                Spacer(Modifier.width((totalWidth*lane.offsetMs/project.durationMs).dp))
                Text("A${i+1} · ${lane.name}",maxLines=1,modifier=Modifier
                    .width((totalWidth*minOf(lane.durationMs,project.durationMs-lane.offsetMs)/project.durationMs).coerceAtLeast(1f).dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer).padding(6.dp))
            }
        }
    }
    Slider(positionMs.toFloat(),{onSeek(it.toLong())},valueRange=0f..project.durationMs.toFloat(),enabled=enabled,
        modifier=Modifier.testTag("timeline_seek").semantics { contentDescription="Timeline playhead" })
}
