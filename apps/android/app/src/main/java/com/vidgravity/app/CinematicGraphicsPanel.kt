package com.vidgravity.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun CinemaGraphicsPanel(project:StudioProject,positionMs:Long,onEdit:(StudioProject,String?)->Unit,onImage:()->Unit) {
    var selectedTitle by remember(project.id) {mutableStateOf<String?>(null)}
    var selectedGraphic by remember(project.id) {mutableStateOf<String?>(null)}
    Text("Motion Graphics",style=MaterialTheme.typography.headlineSmall)
    Text("Reusable entrance/exit templates and editable keys. Graphics are drawn above video; text layers are drawn above graphics.",style=MaterialTheme.typography.labelSmall)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf("Cinematic title","Lower third","Zoom title").forEachIndexed {i,name->
            OutlinedButton(enabled=project.tools.textLayers.size<16,onClick={
                val layer=CinemaTitles.create(project.durationMs,name,lowerThird=i==1,zoom=i==2)
                selectedTitle=layer.id;onEdit(project.copy(tools=project.tools.copy(textLayers=project.tools.textLayers+layer)),null)
            },modifier=Modifier.testTag("cinema_title_template_$i")) {Text(name)}
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        CinemaGraphicKind.entries.filter {it!=CinemaGraphicKind.IMAGE}.forEach {kind->OutlinedButton(enabled=project.tools.graphics.size<8,onClick={
            val g=CinemaGraphic(kind=kind,endMs=minOf(4000,project.durationMs),y=0f)
            selectedGraphic=g.id;onEdit(project.copy(tools=project.tools.copy(graphics=project.tools.graphics+g)),null)
        },modifier=Modifier.testTag("cinema_add_${kind.name.lowercase()}")) {Text("Add ${kind.name.lowercase()}")}}
        OutlinedButton(enabled=project.tools.graphics.size<8,onClick=onImage) {Text("Import image overlay")}
    }
    if(project.tools.textLayers.isNotEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {project.tools.textLayers.forEachIndexed {i,t->FilterChip(selectedTitle==t.id,{selectedTitle=t.id},label={Text("Animate title ${i+1}")})}}
        val t=project.tools.textLayers.firstOrNull {it.id==selectedTitle} ?: project.tools.textLayers.last()
        VgDisclosure("Title animation keys") {
            CinemaMotionEditor("cinema_title_motion_${t.id}",t.motion,1_800_000,(positionMs-t.startMs+t.motionOffsetMs).coerceAtLeast(0)) {keys->
                onEdit(project.copy(tools=project.tools.copy(textLayers=project.tools.textLayers.map {if(it.id==t.id) it.copy(motion=keys) else it})),null)
            }
        }
        VgDisclosure("Text, font, colors and timing") {StudioTextPanel(project,true,onEdit)}
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {project.tools.graphics.forEachIndexed {i,g->FilterChip(selectedGraphic==g.id,{selectedGraphic=g.id},label={Text("Graphic ${i+1}: ${g.kind.name.lowercase()}")})}}
    val g=project.tools.graphics.firstOrNull {it.id==selectedGraphic} ?: project.tools.graphics.lastOrNull()
    if(g!=null) {
        fun change(next:CinemaGraphic,group:String?=null) {onEdit(project.copy(tools=project.tools.copy(graphics=project.tools.graphics.map {if(it.id==g.id) next else it})),group)}
        StudioLongField("Graphic start (ms)",g.startMs,true) {change(g.copy(startMs=it))}
        StudioLongField("Graphic end (ms)",g.endMs,true) {change(g.copy(endMs=it))}
        StudioAdjustment("Graphic X",g.x,-0.8f..0.8f,true) {change(g.copy(x=it),"graphic-x-${g.id}")}
        StudioAdjustment("Graphic Y",g.y,-0.8f..0.8f,true) {change(g.copy(y=it),"graphic-y-${g.id}")}
        if(g.kind!=CinemaGraphicKind.IMAGE) Row(Modifier.horizontalScroll(rememberScrollState())) {listOf(0xFF12B8C4.toInt() to "Teal",0xFFFFBF47.toInt() to "Amber",-1 to "White",0xFF2F72DB.toInt() to "Blue").forEach {(color,label)->FilterChip(g.color==color,{change(g.copy(color=color))},label={Text("Graphic $label")})}}
        CinemaMotionEditor("cinema_graphic_motion_${g.id}",g.motion,1_800_000,(positionMs-g.startMs+g.motionOffsetMs).coerceAtLeast(0)) {change(g.copy(motion=it))}
        val index=project.tools.graphics.indexOf(g)
        Row {
            TextButton(enabled=index>0,onClick={onEdit(project.copy(tools=project.tools.copy(graphics=project.tools.graphics.toMutableList().apply {add(index-1,removeAt(index))})),null)}) {Text("Send graphic backward")}
            TextButton(enabled=index<project.tools.graphics.lastIndex,onClick={onEdit(project.copy(tools=project.tools.copy(graphics=project.tools.graphics.toMutableList().apply {add(index+1,removeAt(index))})),null)}) {Text("Bring graphic forward")}
        }
        TextButton(onClick={onEdit(project.copy(tools=project.tools.copy(graphics=project.tools.graphics.filter {it.id!=g.id})),null)}) {Text("Delete selected graphic")}
    }
}
