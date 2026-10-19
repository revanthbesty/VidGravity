package com.vidgravity.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioToolsPanel(project: StudioProject, enabled: Boolean, onEdit: (StudioProject,String?) -> Unit,
    onAddAudio: () -> Unit, onImportSrt: () -> Unit, onExportSrt: () -> Unit) {
    var section by remember {mutableStateOf("Color")}
    val t=project.tools
    val focus=LocalFocusManager.current
    fun tools(next: StudioTools, group: String?=null) = onEdit(project.copy(tools=next),group)
    Text("Editor tools",style=MaterialTheme.typography.titleLarge)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf("Color","Audio lanes","Captions","Text layers","Encoding").forEach {name -> FilterChip(section==name,{focus.clearFocus();section=name},label={Text("Tools: $name")},modifier=Modifier.heightIn(min=48.dp))}
    }
    if(section=="Text layers") StudioTextPanel(project,enabled,onEdit)
    if(section=="Color") {
    Text("Color & framing",style=MaterialTheme.typography.titleLarge)
    StudioAdjustment("Brightness",t.brightness,-1f..1f,enabled) { tools(t.copy(brightness=it),"brightness") }
    StudioAdjustment("Contrast",t.contrast,-0.9f..0.9f,enabled) { tools(t.copy(contrast=it),"contrast") }
    StudioAdjustment("Saturation",t.saturation,-100f..100f,enabled) { tools(t.copy(saturation=it),"saturation") }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        TextButton(enabled=enabled,onClick={tools(t.copy(brightness=0f,contrast=0f,saturation=0f))}) { Text("Neutral") }
        TextButton(enabled=enabled,onClick={tools(t.copy(brightness=0.05f,contrast=0.15f,saturation=15f))}) { Text("Vivid") }
        TextButton(enabled=enabled,onClick={tools(t.copy(brightness=0.08f,contrast=-0.1f,saturation=-20f))}) { Text("Soft") }
        TextButton(enabled=enabled,onClick={tools(t.copy(rotation=(t.rotation+90)%360))}) { Text("Rotate 90° · ${t.rotation}°") }
    }
    StudioAdjustment("Center crop",t.crop,0f..0.4f,enabled) { tools(t.copy(crop=it),"crop") }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        StudioAspect.entries.forEach { a -> FilterChip(t.aspect==a,{tools(t.copy(aspect=a))},enabled=enabled,label={Text(a.label)}) }
    }
    }
    if(section=="Audio lanes") {
    Text("Independent audio lanes",style=MaterialTheme.typography.titleLarge)
    Button(enabled=enabled && t.audio.size<4,onClick=onAddAudio) { Text("Import audio lane") }
    t.audio.forEachIndexed { index,lane ->
        fun laneEdit(next: StudioAudioLane, group: String?=null) = tools(t.copy(audio=t.audio.mapIndexed { i,a -> if(i==index) next else a }),group)
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("A${index+1} · ${lane.name}")
            StudioWaveform(lane,enabled)
            StudioAdjustment("Lane ${index+1} volume",lane.volume,0f..1f,enabled) {laneEdit(lane.copy(volume=it),"volume-${lane.id}")}
            StudioAdjustment("Lane ${index+1} start on timeline (ms)",lane.offsetMs.toFloat(),0f..(project.durationMs-1).toFloat(),enabled) {laneEdit(lane.copy(offsetMs=it.toLong()),"offset-${lane.id}")}
            StudioLongField("Audio source in (ms)",lane.startMs,enabled) {laneEdit(lane.copy(startMs=it))}
            StudioLongField("Audio source out (ms)",lane.endMs,enabled) {laneEdit(lane.copy(endMs=it))}
            StudioAdjustment("Audio fade in (ms)",lane.fadeInMs.toFloat(),0f..lane.durationMs.toFloat(),enabled) {laneEdit(lane.copy(fadeInMs=it.toLong()),"fadein-${lane.id}")}
            StudioAdjustment("Audio fade out (ms)",lane.fadeOutMs.toFloat(),0f..lane.durationMs.toFloat(),enabled) {laneEdit(lane.copy(fadeOutMs=it.toLong()),"fadeout-${lane.id}")}
            TextButton(enabled=enabled,onClick={tools(t.copy(audio=t.audio.filterIndexed { i,_ -> i!=index }))}) {Text("Remove lane ${index+1}")}
        } }
    }
    }
    if(section=="Captions") {
    Text("Captions & titles",style=MaterialTheme.typography.titleLarge)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        TextButton(enabled=enabled,onClick=onImportSrt) {Text("Import SRT")}
        TextButton(enabled=enabled && t.captions.isNotEmpty(),onClick=onExportSrt) {Text("Export SRT")}
        TextButton(enabled=enabled && t.captions.size<100,onClick={tools(t.copy(captions=t.captions+StudioCaption(0,minOf(3000,project.durationMs),"Your title")))}) {Text("Add title cue")}
    }
    Text("Cue times use the final video timeline. Overlapping cues are displayed together.",style=MaterialTheme.typography.labelSmall)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf("sans-serif","serif","monospace").forEach { font -> FilterChip(t.captionFont==font,{tools(t.copy(captionFont=font))},enabled=enabled,label={Text(font)}) }
        listOf(-1 to "White",0xFFFFFF00.toInt() to "Yellow",0xFF00FFFF.toInt() to "Cyan").forEach { (color,name) -> TextButton(enabled=enabled,onClick={tools(t.copy(captionColor=color))}) {Text(name)} }
    }
    StudioAdjustment("Caption size",t.captionSize.toFloat(),16f..64f,enabled) {tools(t.copy(captionSize=it.toInt()),"caption-size")}
    StudioAdjustment("Caption vertical position",t.captionY,-0.8f..0.8f,enabled) {tools(t.copy(captionY=it),"caption-position")}
    t.captions.forEachIndexed { index,cue ->
        fun cueEdit(next: StudioCaption,group:String?=null) = tools(t.copy(captions=t.captions.mapIndexed { i,c -> if(i==index) next else c }),group)
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            OutlinedTextField(cue.text,{cueEdit(cue.copy(text=it.take(500)),"caption-$index")},enabled=enabled,label={Text("Caption ${index+1}")},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),modifier=Modifier.fillMaxWidth())
            StudioLongField("Cue ${index+1} in (ms)",cue.startMs,enabled) {cueEdit(cue.copy(startMs=it))}
            StudioLongField("Cue ${index+1} out (ms)",cue.endMs,enabled) {cueEdit(cue.copy(endMs=it))}
            TextButton(enabled=enabled,onClick={tools(t.copy(captions=t.captions.filterIndexed { i,_ -> i!=index }))}) {Text("Delete cue ${index+1}")}
        } }
    }
    }
    if(section=="Encoding") {
    Text("Export encoding",style=MaterialTheme.typography.titleLarge)
    Text("Maximum frame rate · frame dropping only; lower-rate sources are not interpolated",style=MaterialTheme.typography.labelSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(0,15,24,30).forEach { fps -> FilterChip(t.frameRate==fps,{tools(t.copy(frameRate=fps))},enabled=enabled,label={Text(if(fps==0) "Source FPS" else "$fps fps cap")}) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(0,2,5,10,20).forEach { mbps -> FilterChip(t.bitrate==mbps*1_000_000,{tools(t.copy(bitrate=mbps*1_000_000))},enabled=enabled,label={Text(if(mbps==0) "Auto bitrate" else "$mbps Mbps")}) }
    }
    }
}

@Composable
internal fun StudioAdjustment(label:String,value:Float,range:ClosedFloatingPointRange<Float>,enabled:Boolean,onChange:(Float)->Unit) {
    Text("$label · ${java.lang.String.format(java.util.Locale.ROOT,"%.2f",value)}")
    Slider(value.coerceIn(range),onChange,valueRange=range,enabled=enabled,modifier=Modifier.semantics {contentDescription=label})
}

@Composable
internal fun StudioLongField(label:String,value:Long,enabled:Boolean,onApply:(Long)->Unit) {
    var text by remember(value) {mutableStateOf(value.toString())}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text,{text=it.filter(Char::isDigit).take(9)},enabled=enabled,label={Text(label)},singleLine=true,
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number),modifier=Modifier.weight(1f))
        TextButton(enabled=enabled && text.toLongOrNull()!=null,onClick={text.toLongOrNull()?.let(onApply)}) {Text("Apply $label")}
    }
}
