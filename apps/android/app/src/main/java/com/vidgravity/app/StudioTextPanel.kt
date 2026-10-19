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
import androidx.compose.ui.unit.dp

@Composable
internal fun StudioTextPanel(project: StudioProject, enabled: Boolean, onEdit: (StudioProject,String?) -> Unit) {
    var selectedId by remember(project.id) {mutableStateOf<String?>(null)}
    val layers=project.tools.textLayers
    val selected=layers.firstOrNull {it.id==selectedId} ?: layers.lastOrNull()
    val focus=LocalFocusManager.current
    fun replace(rows: List<StudioTextLayer>, group: String?=null) = onEdit(project.copy(tools=project.tools.copy(textLayers=rows)),group)
    Text("Independent text layers",style=MaterialTheme.typography.titleLarge)
    Text("Layers are drawn back to front. Timing uses the complete timeline, including clip speed.",style=MaterialTheme.typography.labelSmall)
    Button(enabled=enabled && layers.size<16,onClick={
        val layer=StudioTextLayer(endMs=minOf(3000,project.durationMs))
        selectedId=layer.id; replace(layers+layer)
    },modifier=Modifier.testTag("studio_add_text_layer")) {Text("Add text layer")}
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        layers.forEachIndexed {i,l -> FilterChip(selected?.id==l.id,{focus.clearFocus();selectedId=l.id},
            label={Text("T${i+1} · ${l.text.take(18)}")},enabled=enabled) }
    }
    if(selected!=null) {
        val layer=selected
        fun change(next: StudioTextLayer,group: String?=null) = replace(layers.map {if(it.id==layer.id) next else it},group)
        OutlinedTextField(layer.text,{change(layer.copy(text=it.take(500)),"layer-text-${layer.id}")},enabled=enabled,
            label={Text("Layer text")},modifier=Modifier.fillMaxWidth().testTag("studio_layer_text"),
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
        StudioLongField("Text start (ms)",layer.startMs,enabled) {change(layer.copy(startMs=it))}
        StudioLongField("Text end (ms)",layer.endMs,enabled) {change(layer.copy(endMs=it))}
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("sans-serif","serif","monospace").forEach {font -> FilterChip(layer.font==font,{change(layer.copy(font=font))},enabled=enabled,label={Text("Layer font: $font")})}
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(-1 to "White",0xFFFFFF00.toInt() to "Yellow",0xFF00FFFF.toInt() to "Cyan",0xFFFF6699.toInt() to "Pink").forEach {(color,label) ->
                FilterChip(layer.color==color,{change(layer.copy(color=color))},enabled=enabled,label={Text("Text: $label")})
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(0 to "Clear",0xCC000000.toInt() to "Black",0xFF14245A.toInt() to "Navy").forEach {(color,label) ->
                FilterChip(layer.background==color,{change(layer.copy(background=color))},enabled=enabled,label={Text("Background: $label")})
            }
        }
        StudioAdjustment("Layer size",layer.size.toFloat(),16f..64f,enabled) {change(layer.copy(size=it.toInt()),"layer-size-${layer.id}")}
        StudioAdjustment("Layer horizontal position",layer.x,-0.8f..0.8f,enabled) {change(layer.copy(x=it),"layer-x-${layer.id}")}
        StudioAdjustment("Layer vertical position",layer.y,-0.8f..0.8f,enabled) {change(layer.copy(y=it),"layer-y-${layer.id}")}
        StudioAdjustment("Layer rotation",layer.rotation,-180f..180f,enabled) {change(layer.copy(rotation=it),"layer-rotation-${layer.id}")}
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            CreatorAnimation.entries.forEach {a -> FilterChip(layer.animation==a,{change(layer.copy(animation=a))},enabled=enabled,label={Text("Text animation: ${a.name.lowercase()}")})}
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            val index=layers.indexOf(layer)
            TextButton(enabled=enabled && index>0,onClick={replace(layers.toMutableList().apply {add(index-1,removeAt(index))})}) {Text("Send layer backward")}
            TextButton(enabled=enabled && index<layers.lastIndex,onClick={replace(layers.toMutableList().apply {add(index+1,removeAt(index))})}) {Text("Bring layer forward")}
            TextButton(enabled=enabled,onClick={replace(layers.filter {it.id!=layer.id})}) {Text("Delete text layer")}
        }
    }
}
