package com.vidgravity.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun CinemaKeyTime(label:String,value:String,onChange:(String)->Unit,tag:String,enabled:Boolean=true) {
    val focus=LocalFocusManager.current
    OutlinedTextField(value,{onChange(it.filter(Char::isDigit).take(9))},label={Text(label)},singleLine=true,enabled=enabled,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),modifier=Modifier.fillMaxWidth().testTag(tag))
}

@Composable
internal fun CinemaMotionEditor(id:String,keys:List<CinemaMotionKey>,limit:Long,playhead:Long,enabled:Boolean=true,onApply:(List<CinemaMotionKey>)->Unit) {
    var selected by remember(id) {mutableStateOf<Long?>(null)}
    var time by remember(id) {mutableStateOf(playhead.coerceIn(0,limit).toString())}
    var pose by remember(id) {mutableStateOf(CinemaCurves.motion(keys,playhead))}
    var ease by remember(id) {mutableStateOf(CinemaEase.LINEAR)}
    var error by remember(id) {mutableStateOf<String?>(null)}
    Text("Motion keyframes",style=MaterialTheme.typography.titleMedium)
    Text("Adjust values, then apply a key. The first and last keys hold outside their range. Easing belongs to the interval after each key.",style=MaterialTheme.typography.labelSmall)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        keys.forEach {k->FilterChip(selected==k.timeMs,{selected=k.timeMs;time=k.timeMs.toString();pose=k.pose;ease=k.ease},enabled=enabled,label={Text("${k.timeMs} ms")})}
    }
    CinemaKeyTime("Motion key time (ms)",time,{time=it},"${id}_time",enabled)
    TextButton(enabled=enabled,onClick={selected=null;time=playhead.coerceIn(0,limit).toString();pose=CinemaCurves.motion(keys,playhead)}) {Text("Use current playhead for motion key")}
    StudioAdjustment("Motion X",pose.x,-1f..1f,enabled) {pose=pose.copy(x=it)}
    StudioAdjustment("Motion Y",pose.y,-1f..1f,enabled) {pose=pose.copy(y=it)}
    StudioAdjustment("Motion scale",pose.scale,0.25f..3f,enabled) {pose=pose.copy(scale=it)}
    StudioAdjustment("Motion rotation",pose.rotation,-180f..180f,enabled) {pose=pose.copy(rotation=it)}
    StudioAdjustment("Motion opacity",pose.opacity,0f..1f,enabled) {pose=pose.copy(opacity=it)}
    Row(Modifier.horizontalScroll(rememberScrollState())) {CinemaEase.entries.forEach {e->FilterChip(ease==e,{ease=e},enabled=enabled,label={Text(e.label)})}}
    Button(enabled=enabled && time.toLongOrNull()?.let {it in 0..limit}==true && (keys.size<32 || keys.any {it.timeMs==selected}),onClick={
        val at=time.toLong()
        if(keys.any {it.timeMs==at && it.timeMs!=selected}) {error="A key already uses that time. Select it to modify it.";return@Button}
        onApply((keys.filter {it.timeMs!=selected}+CinemaMotionKey(at,pose,ease)).sortedBy {it.timeMs});selected=at;error=null
    },modifier=Modifier.testTag("${id}_apply")) {Text(if(selected==null) "Add motion keyframe" else "Apply or move motion keyframe")}
    TextButton(enabled=enabled && selected!=null,onClick={onApply(keys.filter {it.timeMs!=selected});selected=null},modifier=Modifier.testTag("${id}_delete")) {Text("Delete selected motion keyframe")}
    TextButton(enabled=enabled && keys.isNotEmpty(),onClick={onApply(emptyList());selected=null;pose=CinemaPose()}) {Text("Reset motion keyframes")}
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
}

@Composable
internal fun CinemaScalarEditor(id:String,label:String,keys:List<CinemaScalarKey>,limit:Long,playhead:Long,fallback:Float,
    range:ClosedFloatingPointRange<Float>,enabled:Boolean=true,onApply:(List<CinemaScalarKey>)->Unit) {
    var selected by remember(id) {mutableStateOf<Long?>(null)}
    var time by remember(id) {mutableStateOf(playhead.coerceIn(0,limit).toString())}
    var value by remember(id) {mutableFloatStateOf(CinemaCurves.scalar(keys,playhead,fallback))}
    var ease by remember(id) {mutableStateOf(CinemaEase.LINEAR)}
    var error by remember(id) {mutableStateOf<String?>(null)}
    Text(label,style=MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState())) {keys.forEach {k->FilterChip(selected==k.timeMs,{selected=k.timeMs;time=k.timeMs.toString();value=k.value;ease=k.ease},enabled=enabled,label={Text("${k.timeMs} ms · ${"%.2f".format(k.value)}")})}}
    CinemaKeyTime("$label time (ms)",time,{time=it},"${id}_time",enabled)
    TextButton(enabled=enabled,onClick={selected=null;time=playhead.coerceIn(0,limit).toString();value=CinemaCurves.scalar(keys,playhead,fallback)}) {Text("Use current playhead for $label")}
    StudioAdjustment("$label value",value,range,enabled) {value=it}
    Row(Modifier.horizontalScroll(rememberScrollState())) {CinemaEase.entries.forEach {e->FilterChip(ease==e,{ease=e},enabled=enabled,label={Text(e.label)})}}
    Button(enabled=enabled && time.toLongOrNull()?.let {it in 0..limit}==true && (keys.size<32 || keys.any {it.timeMs==selected}),onClick={
        val at=time.toLong()
        if(keys.any {it.timeMs==at && it.timeMs!=selected}) {error="A key already uses that time. Select it to modify it.";return@Button}
        onApply((keys.filter {it.timeMs!=selected}+CinemaScalarKey(at,value,ease)).sortedBy {it.timeMs});selected=at;error=null
    },modifier=Modifier.testTag("${id}_apply")) {Text(if(selected==null) "Add $label keyframe" else "Apply or move $label keyframe")}
    TextButton(enabled=enabled && selected!=null,onClick={onApply(keys.filter {it.timeMs!=selected});selected=null}) {Text("Delete selected $label keyframe")}
    TextButton(enabled=enabled && keys.isNotEmpty(),onClick={onApply(emptyList());selected=null}) {Text("Reset $label keyframes")}
    Text("Key values override the base $label amount; time uses source milliseconds.",style=MaterialTheme.typography.labelSmall)
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
}
