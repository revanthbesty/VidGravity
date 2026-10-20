package com.vidgravity.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

/** Per-clip editing workspace. Changes flow through Studio's existing history/autosave/export. */
@Composable
internal fun CinematicWorkspace(project:StudioProject,selected:Int,positionMs:Long,seekRequest:Long,
    canUndo:Boolean,canRedo:Boolean,onUndo:()->Unit,onRedo:()->Unit,onClose:()->Unit,
    onSelect:(Int)->Unit,onSeek:(Long)->Unit,onPosition:(Long)->Unit,onEdit:(StudioProject,String?)->Unit,
    onAddAudio:()->Unit,onRecordVoice:()->Unit,onSave:suspend ()->Unit,onExport:()->Unit,editorMessage:String?=null) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val focus=LocalFocusManager.current
    val clip=project.clips[selected];val style=clip.cinema
    val offset=project.clips.take(selected).sumOf {it.playbackDurationMs}
    val sourceTime=(clip.startMs+(positionMs-offset)*clip.speed).toLong().coerceIn(clip.startMs,clip.endMs)
    var tab by remember {mutableIntStateOf(0)}
    val panelScroll=rememberScrollState()
    LaunchedEffect(tab) {panelScroll.scrollTo(0)}
    var bypass by remember {mutableStateOf(false)}
    var clipboard by remember {mutableStateOf<CinemaStyle?>(null)}
    var message by remember {mutableStateOf<String?>(null)}
    var working by remember {mutableStateOf(false)}
    val latest by rememberUpdatedState(project);val editLatest by rememberUpdatedState(onEdit)
    fun color(next:CinemaStyle,group:String?=null) {bypass=false;onEdit(project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(cinema=next) else it}),group)}
    fun copyColor(target:StudioClip,from:CinemaStyle)=target.copy(cinema=target.cinema.copy(look=from.look,intensity=from.intensity,grade=from.grade))
    val presets=remember(context) {CinemaPresetRepository(context)}
    var saved by remember {mutableStateOf(CinemaPresetListing(emptyList(),0))}
    var presetName by remember {mutableStateOf("")}
    var deletePreset by remember {mutableStateOf<CinemaPreset?>(null)}
    LaunchedEffect(Unit) {try {saved=withContext(Dispatchers.IO) {presets.list()}} catch(failure:Exception) {if(failure is CancellationException) throw failure;message="Color presets could not be read; existing files were preserved."}}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) scope.launch {
        try {
            withContext(Dispatchers.IO) {StudioMediaImports.retain(context,uri);require(context.contentResolver.getType(uri)?.startsWith("image/")==true) {"Choose a local image."}}
            val p=latest;require(p.tools.graphics.size<8) {"Keep up to eight graphic overlays."}
            editLatest(p.copy(tools=p.tools.copy(graphics=p.tools.graphics+CinemaGraphic(kind=CinemaGraphicKind.IMAGE,uri=uri.toString(),endMs=minOf(4000,p.durationMs),y=0f))),null)
            message="Image added as an editable overlay. Source files are unchanged."
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(failure:Exception) {message=failure.message ?: "Image could not be imported; existing layers were preserved."}
    }}
    BackHandler {onClose()}
    val preview=if(bypass) project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(cinema=it.cinema.copy(look=CinemaLook.ORIGINAL,grade=CinemaGrade(),lookKeys=emptyList())) else it}) else project
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF075B68),Color(0xFF154D91))),RoundedCornerShape(20.dp)).padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.MovieFilter,null,tint=Color(0xFFFFD175))
            Column(Modifier.weight(1f)) {Text("Cinema Studio",style=MaterialTheme.typography.titleLarge,color=Color.White,modifier=Modifier.testTag("cinema_studio").semantics {heading()});Text("Color · Motion · Sound",color=Color.White.copy(alpha=0.9f),style=MaterialTheme.typography.labelMedium)}
            TextButton(onClick=onClose) {Text("Back to editor",color=Color.White)}
        }
        NativeClipPreview(preview,preview.clips[selected],seekRequest,compact=true,onPosition=onPosition)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(enabled=canUndo,onClick={focus.clearFocus();onUndo()}) {Text("Undo")}
            TextButton(enabled=canRedo,onClick={focus.clearFocus();onRedo()}) {Text("Redo")}
            FilterChip(bypass,{bypass=!bypass},label={Text(if(bypass) "Before · look/color bypassed" else "After · look/color applied")},modifier=Modifier.testTag("cinema_compare"))
            TextButton(enabled=!working,onClick={working=true;scope.launch {try {onSave();message="Project saved on this device."} catch(cancelled:CancellationException) {throw cancelled} catch(failure:Exception) {message="Project save failed; retry before leaving."} finally {working=false}}}) {Text("Save project")}
            Button(enabled=!working,onClick=onExport,modifier=Modifier.testTag("cinema_export")) {Text("Export")}
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {project.clips.forEachIndexed {i,c->FilterChip(selected==i,{focus.clearFocus();onSelect(i)},enabled=!working,label={Text("Clip ${i+1} · ${c.name.take(15)}")},modifier=Modifier.testTag("cinema_clip_$i"))}}
        val tabs=listOf("Looks","Color","Motion","Graphics","Mixer","Transitions","Speed")
        ScrollableTabRow(selectedTabIndex=tab,edgePadding=0.dp) {tabs.forEachIndexed {i,label->Tab(selected=tab==i,enabled=!working,onClick={focus.clearFocus();tab=i},text={Text(label)},modifier=Modifier.testTag("cinema_tab_${label.lowercase()}"))}}
        if(working) LinearProgressIndicator(Modifier.fillMaxWidth())
        editorMessage?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)}
        message?.let {Text(it,style=MaterialTheme.typography.labelSmall,modifier=Modifier.testTag("cinema_status"))}
        Column(Modifier.weight(1f).verticalScroll(panelScroll),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Selected clip preview · ${positionMs} ms on timeline",style=MaterialTheme.typography.labelSmall)
            StudioLongField("Cinema seek (ms)",positionMs,true,onSeek)
            AnimatedContent(targetState=tab,label="Cinema tool panel",modifier=Modifier.animateContentSize()) {panel->
              Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                when(panel) {
                    0 -> {
                        Text("Cinematic Looks",style=MaterialTheme.typography.headlineSmall)
                        Text("SDR video · your settings apply to preview and export.",style=MaterialTheme.typography.labelSmall)
                        CinemaLook.entries.chunked(2).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {row.forEach {look->
                            OutlinedCard(onClick={color(style.copy(look=look))},modifier=Modifier.weight(1f).heightIn(min=72.dp).animateContentSize().testTag("cinema_look_${look.name.lowercase()}"),colors=CardDefaults.outlinedCardColors(containerColor=if(style.look==look) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                                Text(look.label,Modifier.padding(14.dp),style=MaterialTheme.typography.titleSmall)
                            }
                        }}}
                        StudioAdjustment("Look intensity",style.intensity,0f..1f,true) {color(style.copy(intensity=it),"cinema-intensity-${clip.id}")}
                        if(style.lookKeys.isNotEmpty()) Text("Saved intensity keys override the base slider at their times.",style=MaterialTheme.typography.labelSmall)
                        VgDisclosure("Animate look intensity") {CinemaScalarEditor("cinema_look_keys_${clip.id}","Look intensity",style.lookKeys,clip.sourceDurationMs,sourceTime,style.intensity,0f..1f) {color(style.copy(lookKeys=it))}}
                        TextButton(onClick={color(style.copy(look=CinemaLook.ORIGINAL,intensity=1f,lookKeys=emptyList()))}) {Text("Reset look")}
                    }
                    1 -> {
                        Text("Color Studio",style=MaterialTheme.typography.headlineSmall)
                        val g=style.grade
                        fun grade(next:CinemaGrade,group:String) {color(style.copy(grade=next),"cinema-$group-${clip.id}")}
                        StudioAdjustment("Clip brightness",g.brightness,-1f..1f,true) {grade(g.copy(brightness=it),"brightness")}
                        StudioAdjustment("Clip contrast",g.contrast,-1f..1f,true) {grade(g.copy(contrast=it),"contrast")}
                        StudioAdjustment("Clip saturation",g.saturation,-1f..1f,true) {grade(g.copy(saturation=it),"saturation")}
                        StudioAdjustment("Exposure EV",g.exposure,-2f..2f,true) {grade(g.copy(exposure=it),"exposure")}
                        StudioAdjustment("Temperature",g.temperature,-1f..1f,true) {grade(g.copy(temperature=it),"temperature")}
                        StudioAdjustment("Tint",g.tint,-1f..1f,true) {grade(g.copy(tint=it),"tint")}
                        StudioAdjustment("Highlights",g.highlights,-1f..1f,true) {grade(g.copy(highlights=it),"highlights")}
                        StudioAdjustment("Shadows",g.shadows,-1f..1f,true) {grade(g.copy(shadows=it),"shadows")}
                        StudioAdjustment("Gamma",g.gamma,0.3f..3f,true) {grade(g.copy(gamma=it),"gamma")}
                        StudioAdjustment("Hue degrees",g.hue,-180f..180f,true) {grade(g.copy(hue=it),"hue")}
                        StudioAdjustment("Sharpness",g.sharpness,0f..1f,true) {grade(g.copy(sharpness=it),"sharpness")}
                        TextButton(onClick={color(style.copy(look=CinemaLook.ORIGINAL,intensity=1f,grade=CinemaGrade(),lookKeys=emptyList()))},modifier=Modifier.testTag("cinema_reset_color")) {Text("Reset look and grade")}
                        Row {
                            TextButton(onClick={clipboard=style.colorOnly();message="Color adjustments copied; motion/timing are not copied."}) {Text("Copy color")}
                            TextButton(enabled=clipboard!=null,onClick={clipboard?.let {from->bypass=false;onEdit(project.copy(clips=project.clips.map {if(it.id==clip.id) copyColor(it,from) else it}),null)}},modifier=Modifier.testTag("cinema_paste_color")) {Text("Paste color")}
                        }
                        TextButton(onClick={onEdit(project.copy(clips=project.clips.map {copyColor(it,style)}),null)}) {Text("Apply color to all clips")}
                        OutlinedTextField(presetName,{presetName=it.take(40)},label={Text("Color preset name")},modifier=Modifier.fillMaxWidth().testTag("cinema_preset_name"),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
                        Button(enabled=!working && presetName.isNotBlank(),onClick={val snapshot=CinemaPreset(name=presetName.trim(),style=style.colorOnly());working=true;scope.launch {
                            try {saved=withContext(Dispatchers.IO) {presets.save(snapshot);presets.list()};message="Color preset saved locally."}
                            catch(cancelled:CancellationException) {throw cancelled} catch(failure:Exception) {message=failure.message ?: "Color preset could not be saved."} finally {working=false}
                        }},modifier=Modifier.testTag("cinema_save_preset")) {Text("Save custom color preset")}
                        if(saved.unreadable>0) Text("${saved.unreadable} unreadable/newer presets preserved.",style=MaterialTheme.typography.labelSmall)
                        saved.presets.forEach {preset->Row {
                            TextButton(onClick={color(style.copy(look=preset.style.look,intensity=preset.style.intensity,grade=preset.style.grade));message="Preset applied; existing motion and timing retained."}) {Text("Apply preset: ${preset.name}")}
                            TextButton(onClick={deletePreset=preset}) {Text("Remove ${preset.name}")}
                        }}
                    }
                    2 -> {
                        Text("Clip Motion",style=MaterialTheme.typography.headlineSmall)
                        Text("Source-time keys survive trimming, splitting and speed changes. Position and scale can reveal black canvas around the video.",style=MaterialTheme.typography.labelSmall)
                        CinemaMotionEditor("cinema_clip_motion_${clip.id}",style.motion,clip.sourceDurationMs,sourceTime) {color(style.copy(motion=it))}
                    }
                    3 -> CinemaGraphicsPanel(project,positionMs,onEdit,{imagePicker.launch(arrayOf("image/*"))})
                    4 -> CinemaAudioPanel(project,true,onEdit,onAddAudio,positionMs,onRecordVoice,onProcessing={working=it})
                    5 -> {
                        Text("Clip-boundary transitions",style=MaterialTheme.typography.headlineSmall)
                        Text("Fade through black/white without overlapping video. Total project duration stays unchanged. Each half is limited to fit both neighboring clips.",style=MaterialTheme.typography.labelSmall)
                        if(selected==project.clips.lastIndex) Text("Add or select a clip with a following neighbor to edit its transition.")
                        else {
                            CinemaTransitionKind.entries.forEach {kind->FilterChip(style.exit.kind==kind,{bypass=false;onEdit(project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(cinema=style.copy(exit=style.exit.copy(kind=kind))) else it}),null)},label={Text(if(kind==CinemaTransitionKind.NONE && project.fadeEdges) "Default edge fade" else kind.label)},modifier=Modifier.testTag("cinema_transition_${kind.name.lowercase()}"))}
                            StudioAdjustment("Transition duration (ms)",style.exit.durationMs.toFloat(),100f..2000f,true) {color(style.copy(exit=style.exit.copy(durationMs=it.toLong())),"cinema-transition-${clip.id}")}
                            Text("Effective total fade: ${CinemaTransitions.half(project,selected)*2} ms")
                            TextButton(onClick={onSeek(offset+clip.playbackDurationMs-CinemaTransitions.half(project,selected))}) {Text("Preview outgoing side")}
                            TextButton(onClick={onSeek(offset+clip.playbackDurationMs)}) {Text("Preview incoming side")}
                            TextButton(onClick={color(style.copy(exit=CinemaTransition()))}) {Text("Remove transition")}
                        }
                        Text("Crossfade, slide, zoom, wipe and blur transitions between overlapping videos are unsupported by this sequential renderer. ",style=MaterialTheme.typography.labelSmall)
                    }
                    6 -> CinemaSpeedPanel(project,selected,onEdit)
                }
              }
            }
            StudioTimeline(project,selected,positionMs,!working,onSelect,onSeek,onEdit)
        }
    }
    deletePreset?.let {p->AlertDialog(onDismissRequest={deletePreset=null},title={Text("Remove color preset?")},text={Text("Remove ${p.name} from this device? Saved projects and media are preserved.")},
        dismissButton={TextButton(onClick={deletePreset=null}) {Text("Keep preset")}},confirmButton={TextButton(onClick={deletePreset=null;scope.launch {try {saved=withContext(Dispatchers.IO) {presets.delete(p.id);presets.list()}} catch(cancelled:CancellationException) {throw cancelled} catch(failure:Exception) {message="Preset could not be removed; retry."}}}) {Text("Remove preset")}})}
}

@Composable
private fun CinemaSpeedPanel(project:StudioProject,index:Int,onEdit:(StudioProject,String?)->Unit) {
    val c=project.clips[index]
    var first by remember(c.id,c.startMs,c.endMs) {mutableStateOf((c.startMs+c.durationMs/3).toString())}
    var second by remember(c.id,c.startMs,c.endMs) {mutableStateOf((c.startMs+c.durationMs*2/3).toString())}
    var rates by remember(c.id) {mutableStateOf(listOf(0.5f,1f,2f))}
    var confirm by remember {mutableStateOf<StudioProject?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    Text("Stepped Speed Sequence",style=MaterialTheme.typography.headlineSmall)
    Text("Three editable source ranges with supported constant speeds. This is discrete retiming, not a smooth interpolated ramp or optical-flow slow motion. Original audio follows each segment’s playback speed.",style=MaterialTheme.typography.labelSmall)
    CinemaKeyTime("First speed boundary (source ms)",first,{first=it},"cinema_speed_first")
    CinemaKeyTime("Second speed boundary (source ms)",second,{second=it},"cinema_speed_second")
    rates.forEachIndexed {i,current->Row(Modifier.horizontalScroll(rememberScrollState())) {listOf(0.5f,1f,1.5f,2f).forEach {rate->FilterChip(current==rate,{rates=rates.mapIndexed {j,v->if(i==j) rate else v}},label={Text("Part ${i+1}: ${rate}×")})}}}
    Button(enabled=first.toLongOrNull()!=null && second.toLongOrNull()!=null,onClick={
        try {confirm=CinemaSpeed.apply(project,index,listOf(CinemaSpeedKey(c.startMs,rates[0]),CinemaSpeedKey(first.toLong(),rates[1]),CinemaSpeedKey(second.toLong(),rates[2])));error=null}
        catch(failure:IllegalArgumentException) {error=failure.message ?: "Choose ordered boundaries at least 100 ms apart, with enough free clip slots."}
    },modifier=Modifier.testTag("cinema_speed_review")) {Text("Review stepped speed sequence")}
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    Text("Use this before adding timed music/titles. Existing timed layers cause safe rejection rather than silent misalignment. Constant speed and freeze-frame tools remain in the editor. Reverse and smooth speed-ramp keyframes are unsupported.",style=MaterialTheme.typography.labelSmall)
    confirm?.let {next->AlertDialog(onDismissRequest={confirm=null},title={Text("Apply stepped speed sequence?")},text={Text("Project duration changes from ${project.durationMs} to ${next.durationMs} ms. Three editable clips are created; source files stay untouched. Undo restores the prior project.")},dismissButton={TextButton(onClick={confirm=null}) {Text("Keep editing")}},confirmButton={TextButton(onClick={confirm=null;onEdit(next,null)},modifier=Modifier.testTag("cinema_speed_confirm")) {Text("Apply speed sequence")}})}
}
