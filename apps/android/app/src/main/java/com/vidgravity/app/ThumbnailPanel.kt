package com.vidgravity.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
internal fun ThumbnailPanel(project:StudioProject,clip:StudioClip,onBusy:(Boolean)->Unit,onStage:(String)->Unit,onSaveReady:((suspend ()->Unit)?)->Unit) {
    val context=LocalContext.current; val scope=rememberCoroutineScope(); val focus=LocalFocusManager.current
    val repository=remember(context) {ThumbnailRepository(context)}
    var thumb by remember {mutableStateOf(StudioThumbnail(projectId=project.id,source=clip.uri,durationMs=clip.sourceDurationMs,timeMs=clip.startMs,
        title=project.title.take(100),frame=clip.frame,rotation=project.tools.rotation,brightness=project.tools.brightness,contrast=project.tools.contrast))}
    var guides by remember {mutableStateOf(false)}
    var saved by remember {mutableStateOf(emptyList<StudioThumbnail>())}
    var bitmap by remember {mutableStateOf<Bitmap?>(null)}
    var message by remember {mutableStateOf<String?>(null)}
    var loading by remember {mutableStateOf(false)}
    var exporting by remember {mutableStateOf(false)}
    var exportJob by remember {mutableStateOf<Job?>(null)}
    var currentExport by remember {mutableStateOf<Pair<StudioThumbnail,String>?>(null)}
    fun edit(next:StudioThumbnail) {thumb=next.copy(updatedAt=maxOf(System.currentTimeMillis(),thumb.updatedAt+1))}
    suspend fun reload() {saved=withContext(Dispatchers.IO) {repository.list(project.id)}}
    LaunchedEffect(Unit) {try {reload(); saved.firstOrNull()?.let {thumb=it}} catch(failure:Exception) {if(failure is CancellationException) throw failure; message="Saved thumbnails could not be read; existing files were preserved."}}
    LaunchedEffect(thumb) {
        delay(350); loading=true
        try {
            thumb.validate()
            val next=withContext(Dispatchers.IO) {repository.save(thumb); ThumbnailRenderer.render(context,thumb,preview=true)}
            bitmap=next; message="Thumbnail autosaved on this device."
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(failure:Exception) {message=failure.message ?: "Preview could not open. Saved settings were preserved."}
        finally {loading=false}
    }
    val latestThumb by rememberUpdatedState(thumb)
    DisposableEffect(Unit) {
        onSaveReady {withContext(Dispatchers.IO) {repository.save(latestThumb)}}
        onDispose {onSaveReady(null)}
    }
    // Published preview bitmaps remain owned by Compose/RenderThread until references are released.
    DisposableEffect(exporting) {onBusy(exporting); onDispose {}}
    DisposableEffect(Unit) {onDispose {exportJob?.cancel(); onBusy(false)}}
    Text("Thumbnail creator",style=MaterialTheme.typography.titleLarge)
    Text("Your latest saved thumbnail opens automatically. Start a separate thumbnail to use the currently selected clip.",style=MaterialTheme.typography.labelSmall)
    TextButton(enabled=!exporting,onClick={thumb=StudioThumbnail(projectId=project.id,source=clip.uri,durationMs=clip.sourceDurationMs,timeMs=clip.startMs,
        title=project.title.take(100),frame=clip.frame,rotation=project.tools.rotation,brightness=project.tools.brightness,contrast=project.tools.contrast)}) {Text("New thumbnail from selected clip")}
    Text("Extract a real video frame, crop it, add your title and a vector badge, then save an editable image project. Original videos stay untouched.")
    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    bitmap?.let {Image(it.asImageBitmap(),"Editable thumbnail preview",Modifier.fillMaxWidth().heightIn(max=300.dp).drawWithContent {drawContent();if(guides) drawRect(Color.Yellow,Offset(size.width*0.1f,size.height*0.15f),Size(size.width*0.8f,size.height*0.65f),style=Stroke(2f))})}
    Row {Checkbox(guides,{guides=it});Text("Cover safe-area guide · preview only")}
    Text("Conservative framing guide, not a platform UI guarantee.",style=MaterialTheme.typography.labelSmall)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        TextButton(enabled=!exporting,onClick={edit(thumb.copy(textY=0.32f,badge="NEW",background=0xCC1E1B4B.toInt()))}) {Text("Cover: announcement")}
        TextButton(enabled=!exporting,onClick={edit(thumb.copy(textY=0.72f,badge="NONE",background=0x99000000.toInt()))}) {Text("Cover: minimal")}
        TextButton(enabled=!exporting,onClick={scope.launch {try {val b=withContext(Dispatchers.IO) {CreatorBrandRepository(context).load()};edit(thumb.copy(color=b.color,font=b.font,subtitle=b.name))} catch(e:Exception) {if(e is CancellationException) throw e;message="Brand kit unavailable."}}}) {Text("Apply cover brand style")}
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf(0xCC1E1B4B.toInt() to "Indigo",0xCC000000.toInt() to "Black",0 to "Clear").forEach {(color,name)->TextButton(enabled=!exporting,onClick={edit(thumb.copy(background=color))}) {Text("Text background: $name")}}
    }
    Text("Frame time: ${thumb.timeMs} ms")
    Slider(thumb.timeMs.toFloat(),{edit(thumb.copy(timeMs=it.toLong()))},enabled=!exporting,valueRange=0f..(thumb.durationMs-1).toFloat())
    StudioLongField("Thumbnail frame (ms)",thumb.timeMs,!exporting) {edit(thumb.copy(timeMs=it.coerceIn(0,thumb.durationMs-1)))}
    OutlinedTextField(thumb.title,{edit(thumb.copy(title=it.take(100)))},enabled=!exporting,label={Text("Thumbnail title")},modifier=Modifier.fillMaxWidth(),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
    OutlinedTextField(thumb.subtitle,{edit(thumb.copy(subtitle=it.take(120)))},enabled=!exporting,label={Text("Thumbnail subtitle")},modifier=Modifier.fillMaxWidth(),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        StudioAspect.entries.filter {it!=StudioAspect.SOURCE}.forEach {aspect -> FilterChip(thumb.aspect==aspect,{edit(thumb.copy(aspect=aspect))},enabled=!exporting,label={Text(aspect.label)})}
    }
    StudioAdjustment("Thumbnail crop zoom",thumb.zoom,0f..0.4f,!exporting) {edit(thumb.copy(zoom=it))}
    StudioAdjustment("Thumbnail brightness",thumb.brightness,-1f..1f,!exporting) {edit(thumb.copy(brightness=it))}
    StudioAdjustment("Thumbnail contrast",thumb.contrast,-0.9f..0.9f,!exporting) {edit(thumb.copy(contrast=it))}
    StudioAdjustment("Thumbnail text position",thumb.textY,0.2f..0.85f,!exporting) {edit(thumb.copy(textY=it))}
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf(-1 to "White",0xFFFFFF00.toInt() to "Yellow",0xFF00FFFF.toInt() to "Cyan").forEach {(color,label) -> TextButton(enabled=!exporting,onClick={edit(thumb.copy(color=color))}) {Text(label)}}
        listOf("sans-serif","serif","monospace").forEach {font -> TextButton(enabled=!exporting,onClick={edit(thumb.copy(font=font))}) {Text(font)}}
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf("PLAY","NEW","TIP","NONE").forEach {badge -> TextButton(enabled=!exporting,onClick={edit(thumb.copy(badge=badge))}) {Text("Badge $badge")}}
    }
    OutlinedButton(enabled=!exporting && thumb.title.isNotBlank(),onClick={scope.launch {
        try {withContext(Dispatchers.IO) {repository.save(thumb)}; reload(); message="Thumbnail project saved."}
        catch(failure:Exception) {if(failure is CancellationException) throw failure; message="Thumbnail settings could not be saved. Retry."}
    }}) {Text("Save thumbnail project")}
    Button(enabled=!exporting && thumb.title.isNotBlank(),onClick={
        val snapshot=thumb
        exportJob=scope.launch {
            exporting=true; onStage("Rendering and publishing PNG thumbnail…")
            try {
                val published=withContext(Dispatchers.IO) {repository.save(snapshot); ThumbnailRenderer.publish(context,snapshot)}
                currentExport=snapshot to published.uri.toString(); reload(); message="PNG saved to Android Library."
            } catch(cancelled:CancellationException) {message="Thumbnail operation cancelled. Source media was preserved."; throw cancelled}
            catch(failure:Exception) {message=failure.message ?: "Thumbnail export failed. Retry or choose another frame."}
            finally {exporting=false}
        }
    }) {Text("Export PNG to Library")}
    if(exporting) TextButton(onClick={exportJob?.cancel()}) {Text("Cancel thumbnail export")}
    val export=currentExport?.takeIf {it.first==thumb}
    Button(enabled=export!=null && !exporting,onClick={
        try {val uri=Uri.parse(export!!.second)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="image/png"; putExtra(Intent.EXTRA_STREAM,uri)
                clipData=android.content.ClipData.newUri(context.contentResolver,thumb.title,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share current thumbnail"))
        } catch(failure:Exception) {message="The exported PNG is unavailable. Export again before sharing."}
    }) {Text("Share current thumbnail")}
    if(currentExport!=null && export==null) Text("Thumbnail changed. Export again before sharing.")
    Text("Saved thumbnail projects",style=MaterialTheme.typography.titleMedium)
    TextButton(enabled=!exporting,onClick={scope.launch {
        try {reload()} catch(failure:Exception) {if(failure is CancellationException) throw failure; message="Saved thumbnails could not be refreshed; files were preserved."}
    }}) {Text("Refresh saved thumbnails")}
    saved.forEach {item -> TextButton(enabled=!exporting,onClick={scope.launch {
        try {thumb=withContext(Dispatchers.IO) {repository.load(item.id)}}
        catch(failure:Exception) {if(failure is CancellationException) throw failure; message="Thumbnail could not be reopened; its file was preserved."}
    }}) {Text("Reopen thumbnail: ${item.title}")}}
    message?.let {Text(it,color=MaterialTheme.colorScheme.primary)}
}
