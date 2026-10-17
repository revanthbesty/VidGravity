package com.vidgravity.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

internal object StudioMediaImports {
    fun video(context: Context, uri: Uri): StudioClip {
        require(uri.scheme == "content" && context.contentResolver.getType(uri)?.startsWith("video/") == true) {
            "Select a local video file."
        }
        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally { retriever.release() }
        require(duration in 100..1_800_000) { "Select a video between 0.1 seconds and 30 minutes." }
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Video clip"
        return StudioClip(uri = uri.toString(), name = name, sourceDurationMs = duration)
    }
    fun retain(context: Context, uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

@Composable
internal fun NativeStudioHub(initialMedia: LibraryMedia? = null, onClose: () -> Unit, onBusyChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { StudioRepository(context) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(emptyList<StudioProject>()) }
    var trashed by remember { mutableStateOf(emptyList<StudioProject>()) }
    var editing by remember { mutableStateOf<StudioProject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun reload() {
        loading = true
        try {
            val listing = withContext(Dispatchers.IO) { repository.scan() }
            projects = listing.projects
            trashed = withContext(Dispatchers.IO) { repository.trashed() }
            message = if (listing.unreadableCount > 0) "${listing.unreadableCount} project file(s) could not be opened. Their files were preserved." else null
        }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; message = "Projects could not be read. Existing files were preserved." }
        finally { loading = false }
    }
    fun importVideo(uri: Uri) {
        scope.launch {
            loading = true
            try {
                val project = withContext(Dispatchers.IO) {
                    StudioMediaImports.retain(context, uri)
                    val clip = StudioMediaImports.video(context, uri)
                    StudioProject(title = clip.name.substringBeforeLast('.').take(100).ifBlank { "New project" }, clips = listOf(clip))
                        .also { repository.save(it) }
                }
                editing = project
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                message = failure.message ?: "This video could not be imported."
            } finally { loading = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importVideo) }
    LaunchedEffect(Unit) { reload(); initialMedia?.takeIf { it.mimeType.startsWith("video/") }?.let { importVideo(it.uri) } }
    val project = editing
    if (project != null) {
        NativeStudioEditor(project, repository, onClose = { editing = null; scope.launch { reload() } }, onBusyChanged = onBusyChanged)
        return
    }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("On-device Studio", style = MaterialTheme.typography.headlineLarge)
        Text("Your projects. Your device.", color = MaterialTheme.colorScheme.primary)
        Text("Trim, split and arrange clips, add text, color, music and voiceovers, then export without uploading your media.")
        Button(enabled = !loading, onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) { Text("New video project") }
        TextButton(onClick = onClose) { Text("Back to Studio tools") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Saved projects", style = MaterialTheme.typography.titleLarge)
        if (!loading && projects.isEmpty()) Text("Import a video to start. Projects save automatically and can be reopened here.")
        projects.forEach { saved ->
            OutlinedCard(Modifier.fillMaxWidth().animateContentSize()) {
                Column(Modifier.padding(16.dp)) {
                    Text(saved.title, style = MaterialTheme.typography.titleMedium)
                    Text("${saved.clips.size} clips · ${saved.durationMs / 1000f}s · ${saved.resolution.label}")
                    TextButton(onClick = { editing = saved }) { Text("Continue editing") }
                    Row {
                        TextButton(enabled=!loading,onClick={scope.launch {
                            try { withContext(Dispatchers.IO) { repository.duplicate(saved) }; reload() }
                            catch (failure: Exception) { if(failure is CancellationException) throw failure; message="Project could not be duplicated." }
                        }}) { Text("Duplicate project") }
                        TextButton(enabled=!loading,onClick={scope.launch {
                            try { withContext(Dispatchers.IO) {
                                check(WorkManager.getInstance(context).getWorkInfosForUniqueWork("studio-export-${saved.id}").get().none { !it.state.isFinished }) { "Cancel or finish this project's export before moving it to trash." }
                                repository.trash(saved.id)
                            }; reload() }
                            catch (failure: Exception) { if(failure is CancellationException) throw failure; message=failure.message ?: "Project was preserved." }
                        }}) { Text("Move to trash") }
                    }
                }
            }
        }
        if(trashed.isNotEmpty()) {
            Text("Recoverable trash",style=MaterialTheme.typography.titleLarge)
            Text("Original media is never deleted. Trashed projects can be restored here.")
            trashed.forEach { saved -> TextButton(onClick={scope.launch {
                try { withContext(Dispatchers.IO) { repository.restore(saved.id) }; reload() }
                catch (failure: Exception) { if(failure is CancellationException) throw failure; message="Restore failed; the saved copy was preserved." }
            }}) {Text("Restore ${saved.title}")} }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeStudioEditor(initial: StudioProject, repository: StudioRepository, onClose: () -> Unit, onBusyChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { WorkManager.getInstance(context) }
    var project by remember(initial.id) { mutableStateOf(initial) }
    var selected by remember(initial.id) { mutableIntStateOf(0) }
    var positionMs by remember(initial.id) { mutableLongStateOf(0) }
    var seekRequest by remember(initial.id) { mutableLongStateOf(0) }
    val history=remember(initial.id) { StudioHistory() }
    var canUndo by remember {mutableStateOf(false)}
    var canRedo by remember {mutableStateOf(false)}
    var message by remember { mutableStateOf<String?>(null) }
    var saveStatus by remember { mutableStateOf("Saving…") }
    var record by remember { mutableStateOf<StudioExportRecord?>(null) }
    var queueing by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val activeId = record?.workId
    val workLiveData = remember(activeId) { activeId?.let { manager.getWorkInfoByIdLiveData(UUID.fromString(it)) } }
    val work = workLiveData?.observeAsState()?.value
    val exporting = work?.state?.let { !it.isFinished } == true || queueing
    val busy = exporting || recorder != null
    fun edit(next: StudioProject, group: String? = null) {
        try {
            next.validate()
            if(next.copy(updatedAt=0)==project.copy(updatedAt=0)) return
            history.record(project,group); project = next.copy(updatedAt = System.currentTimeMillis()); saveStatus = "Saving…"
            canUndo=history.canUndo; canRedo=history.canRedo
            selected=selected.coerceIn(project.clips.indices); positionMs=positionMs.coerceIn(0,project.durationMs)
        } catch(failure: IllegalArgumentException) { message=failure.message ?: "This edit would create an invalid timeline." }
    }
    fun seek(value: Long) {
        val target=value.coerceIn(0,project.durationMs-1)
        var total=0L
        selected=project.clips.indexOfFirst { total+=it.playbackDurationMs; target<total }.coerceAtLeast(0)
        seekRequest=target; positionMs=target
    }
    suspend fun reloadRecord() {
        try { record = withContext(Dispatchers.IO) { repository.record(project.id) } }
        catch(failure: Exception) { if(failure is CancellationException) throw failure; record=null; message="Export history could not be read. Your project is preserved; export again to recover." }
    }
    LaunchedEffect(project.id) { reloadRecord() }
    LaunchedEffect(work?.state) { reloadRecord() }
    LaunchedEffect(project) {
        delay(400)
        try { withContext(Dispatchers.IO) { repository.save(project) }; saveStatus = "Saved on this device" }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; saveStatus = "Save failed — retry with Save project" }
    }
    DisposableEffect(busy) { onBusyChanged(busy); onDispose { } }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    fun close() {
        if (busy) { message = "Stop recording or wait for/cancel the export before leaving."; return }
        scope.launch {
            try { withContext(Dispatchers.IO) { repository.save(project) }; onClose() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; message = "Project could not be saved. Please retry." }
        }
    }
    BackHandler { close() }
    val clipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val clip = withContext(Dispatchers.IO) { StudioMediaImports.retain(context, uri); StudioMediaImports.video(context, uri) }
                val next = project.copy(clips = project.clips + clip)
                next.validate(); edit(next); selected = next.clips.lastIndex
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; message = failure.message }
        }
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { StudioMediaImports.retain(context, uri); edit(project.copy(musicUri = uri.toString())) }
    }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            try { val replacement=withContext(Dispatchers.IO) {StudioMediaImports.retain(context,uri); StudioMediaImports.video(context,uri)}
                edit(StudioEdits.relink(project,selected,replacement)); message="Selected clip relinked; existing trims were preserved."
            } catch(failure: Exception) {if(failure is CancellationException) throw failure; message=failure.message ?: "Relink failed; original reference was preserved."}
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            try { val lane=withContext(Dispatchers.IO) {
                StudioMediaImports.retain(context,uri)
                require(context.contentResolver.getType(uri)?.startsWith("audio/")==true) {"Choose an audio file."}
                val reader=MediaMetadataRetriever()
                val duration=try {reader.setDataSource(context,uri); reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0} finally {reader.release()}
                require(duration in 100..1_800_000) {"Audio must be between 0.1 seconds and 30 minutes."}
                val name=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null} ?: "Audio"
                StudioAudioLane(uri=uri.toString(),name=name,sourceDurationMs=duration)
            }; edit(project.copy(tools=project.tools.copy(audio=project.tools.audio+lane))) }
            catch(failure: Exception) {if(failure is CancellationException) throw failure; message=failure.message ?: "Audio could not be imported."}
        }
    }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            try { val captions=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                val buffer=CharArray(64_001); var count=0
                while(count<buffer.size) {val n=reader.read(buffer,count,buffer.size-count); if(n<0) break; count+=n}
                require(count<=64_000) {"SRT must be smaller than 64 KB."}; StudioSrt.parse(String(buffer,0,count),project.durationMs)
            }}; edit(project.copy(tools=project.tools.copy(captions=captions))); message="Imported ${captions.size} caption cues." }
            catch(failure: Exception) {if(failure is CancellationException) throw failure; message=failure.message ?: "SRT could not be read; existing cues were preserved."}
        }
    }
    val srtWriter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) {uri ->
        if(uri!=null) scope.launch {
            try { withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use {it.write(StudioSrt.encode(project.tools.captions))}}; message="SRT saved." }
            catch(failure: Exception) {if(failure is CancellationException) throw failure; message="SRT could not be saved. Retry to another location."}
        }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "Microphone allowed. Tap Record voiceover to start." else "Microphone permission was denied. You can still import music."
    }
    fun stopRecording() {
        val active = recorder ?: return
        try {
            active.stop()
            recordingFile?.let { file -> edit(project.copy(voiceUri = MediaLibraryIndexer.localUri(context, file).toString())) }
            message = "Voiceover saved to this project."
        } catch (failure: RuntimeException) { recordingFile?.delete(); message = "Recording was too short or could not be saved." }
        finally { active.release(); recorder = null; recordingFile = null }
    }
    DisposableEffect(recorder) { val active = recorder; val file = recordingFile; onDispose {
        // A controlled stop releases before state changes; unexpected disposal discards unfinished audio.
        if (active != null) { runCatching { active.release() }; if (recorder === active) file?.delete() }
    } }
    LaunchedEffect(recorder) {
        recordingSeconds = 0
        while (recorder != null) { delay(1000); recordingSeconds++; if (recordingSeconds >= 300) stopRecording() }
    }
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphone.launch(Manifest.permission.RECORD_AUDIO); return
        }
        val file = File(File(context.filesDir, "media/voiceovers").apply { mkdirs() }, "${UUID.randomUUID()}.m4a")
        val active = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setOutputFile(file.absolutePath)
            active.prepare(); active.start(); recordingFile = file; recorder = active
        } catch (failure: Exception) { active.release(); file.delete(); message = "Microphone recording could not start." }
    }
    fun export() {
        scope.launch {
            queueing = true
            try { withContext(Dispatchers.IO) { NativeStudioExportWorker.enqueue(context, project) }; reloadRecord() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; message = failure.message ?: "Export could not start." }
            finally { queueing = false }
        }
    }
    val clip = project.clips[selected.coerceIn(project.clips.indices)]
    val currentExport = record?.takeIf { it.uri != null && it.fingerprint == project.fingerprint() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { close() }) { Text("Projects") }
            Text(saveStatus, style = MaterialTheme.typography.labelSmall)
        }
        OutlinedTextField(project.title, { edit(project.copy(title = it.take(100))) }, enabled = !busy,
            label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
        Row {
            TextButton(enabled=!busy && canUndo,onClick={project=history.undo(project).copy(updatedAt=System.currentTimeMillis()); canUndo=history.canUndo; canRedo=history.canRedo; selected=selected.coerceIn(project.clips.indices)}) {Text("Undo")}
            TextButton(enabled=!busy && canRedo,onClick={project=history.redo(project).copy(updatedAt=System.currentTimeMillis()); canUndo=history.canUndo; canRedo=history.canRedo; selected=selected.coerceIn(project.clips.indices)}) {Text("Redo")}
        }
        NativeClipPreview(project, clip,seekRequest) {positionMs=it}
        StudioTimeline(project,selected,positionMs,!busy,{selected=it},::seek,::edit)
        StudioLongField("Seek timeline (ms)",positionMs,!busy,::seek)
        TextButton(enabled=!busy,onClick={relinkPicker.launch(arrayOf("video/*"))}) {Text("Relink selected video")}
        Text("Relink to a matching video if a source moved. The saved trim range must fit the replacement.",style=MaterialTheme.typography.labelSmall)
        Text("Selected clip preview · Music and voiceover are mixed during export", style = MaterialTheme.typography.labelSmall)
        Text("Timeline · ${project.durationMs / 1000f}s", style = MaterialTheme.typography.titleLarge)
        project.clips.forEachIndexed { index, item ->
            OutlinedCard(Modifier.fillMaxWidth().animateContentSize()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${index + 1}. ${item.name} · ${item.durationMs / 1000f}s")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(enabled = !busy, onClick = { selected = index }) { Text(if (index == selected) "Selected" else "Edit clip") }
                        TextButton(enabled = !busy && index > 0, onClick = {
                            val rows = project.clips.toMutableList(); rows.add(index - 1, rows.removeAt(index)); edit(project.copy(clips = rows)); selected = index - 1
                        }) { Text("Move left") }
                        TextButton(enabled = !busy && project.clips.size > 1, onClick = {
                            edit(project.copy(clips = project.clips.filterIndexed { i, _ -> i != index })); selected = 0
                        }) { Text("Remove") }
                    }
                }
            }
        }
        Text("Trim: ${clip.startMs / 1000f}s → ${clip.endMs / 1000f}s")
        StudioLongField("Video source in (ms)",clip.startMs,!busy) {value -> edit(project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(startMs=value) else it}))}
        StudioLongField("Video source out (ms)",clip.endMs,!busy) {value -> edit(project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(endMs=value) else it}))}
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(0.5f,1f,1.5f,2f).forEach {speed -> FilterChip(clip.speed==speed,{edit(project.copy(clips=project.clips.map {if(it.id==clip.id) it.copy(speed=speed) else it}))},enabled=!busy,label={Text("${speed}×")}) }
        }
        RangeSlider(value = clip.startMs / 1000f..clip.endMs / 1000f, enabled = !busy,
            valueRange = 0f..(clip.sourceDurationMs / 1000f), onValueChange = { range ->
                val start = (range.start * 1000).toLong().coerceIn(0, clip.sourceDurationMs - 100)
                val end = (range.endInclusive * 1000).toLong().coerceIn(start + 100, clip.sourceDurationMs)
                edit(project.copy(clips = project.clips.map { if (it.id == clip.id) it.copy(startMs = start, endMs = end) else it }))
            })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy && project.clips.size < 12, onClick = { clipPicker.launch(arrayOf("video/*")) }) { Text("Add clip") }
            OutlinedButton(enabled = !busy && clip.durationMs >= 200 && project.clips.size < 12, onClick = {
                val pieces = clip.split(clip.durationMs / 2)
                edit(project.copy(clips = project.clips.flatMap { if (it.id == clip.id) pieces else listOf(it) }))
            }) { Text("Split at midpoint") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(enabled=!busy && project.clips.size<12,onClick={try {edit(StudioEdits.duplicate(project,selected))} catch(failure:IllegalArgumentException) {message=failure.message}}) {Text("Duplicate clip")}
            TextButton(enabled=!busy && selected<project.clips.lastIndex,onClick={try {edit(StudioEdits.mergeNext(project,selected))} catch(failure:IllegalArgumentException) {message=failure.message}}) {Text("Merge adjacent source ranges")}
            TextButton(enabled=!busy && project.clips.size<12,onClick={
                try {val start=project.clips.take(selected).sumOf {it.playbackDurationMs}
                    val pieces=clip.split(((positionMs-start)*clip.speed).toLong())
                    edit(project.copy(clips=project.clips.flatMap {if(it.id==clip.id) pieces else listOf(it)}))
                } catch(failure:IllegalArgumentException) {message=failure.message}
            }) {Text("Split at playhead")}
        }
        OutlinedTextField(project.overlay, { edit(project.copy(overlay = it.take(160))) }, enabled = !busy,
            label = { Text("Centered text overlay") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioFilter.entries.forEach { filter -> FilterChip(selected = project.filter == filter, enabled = !busy,
                onClick = { edit(project.copy(filter = filter)) }, label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }
        Row { Switch(project.fadeEdges, { edit(project.copy(fadeEdges = it)) }, enabled = !busy); Text("Fade through black at clip edges") }
        Text("Original audio: ${(project.originalVolume * 100).toInt()}%")
        Slider(project.originalVolume, { edit(project.copy(originalVolume = it)) }, enabled = !busy)
        Button(enabled = !busy, onClick = { musicPicker.launch(arrayOf("audio/*")) }) { Text(if (project.musicUri == null) "Add music" else "Replace music") }
        if (project.musicUri != null) {
            Text("Music loops to the end · ${(project.musicVolume * 100).toInt()}%")
            Slider(project.musicVolume, { edit(project.copy(musicVolume = it)) }, enabled = !busy)
            TextButton(enabled = !busy, onClick = { edit(project.copy(musicUri = null)) }) { Text("Remove music") }
        }
        Button(enabled = !exporting, onClick = { if (recorder == null) startRecording() else stopRecording() }) {
            Text(if (recorder == null) "Record voiceover" else "Stop voiceover · ${recordingSeconds}s")
        }
        if (project.voiceUri != null) {
            Text("Voiceover: ${(project.voiceVolume * 100).toInt()}%")
            Slider(project.voiceVolume, { edit(project.copy(voiceVolume = it)) }, enabled = !busy)
            TextButton(enabled = !busy, onClick = { edit(project.copy(voiceUri = null)) }) { Text("Remove voiceover") }
        }
        StudioToolsPanel(project,!busy,::edit,{audioPicker.launch(arrayOf("audio/*"))},
            {srtPicker.launch(arrayOf("application/x-subrip","text/*","application/octet-stream"))},
            {srtWriter.launch("${project.title.take(60)}.srt")})
        Text("Export size", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioResolution.entries.forEach { resolution -> FilterChip(selected = project.resolution == resolution, enabled = !busy,
                onClick = { edit(project.copy(resolution = resolution)) }, label = { Text(resolution.label) }) }
        }
        Text("H.264 / AAC MP4 · Encoder support required. Unsupported sizes fail visibly; no silent quality fallback.", style = MaterialTheme.typography.labelSmall)
        OutlinedButton(enabled = !busy, onClick = { scope.launch {
            try { withContext(Dispatchers.IO) { repository.save(project) }; saveStatus = "Saved on this device" }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; message = "Save failed. Please retry." }
        } }) { Text("Save project") }
        Button(enabled = !busy, onClick = { export() }, modifier = Modifier.fillMaxWidth().testTag("native_export")) {
            Text(if (work?.state == WorkInfo.State.FAILED) "Retry device export" else "Export on device")
        }
        if (exporting) {
            val progress = work?.progress?.getInt("progress", -1) ?: -1
            if (progress >= 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text((work?.progress?.getString("stage") ?: "Preparing export") + if (progress >= 0) " · $progress%" else "")
            activeId?.let { TextButton(onClick = { manager.cancelWorkById(UUID.fromString(it)) }) { Text("Cancel export") } }
        }
        if (work?.state == WorkInfo.State.FAILED) Text(work.outputData.getString("error") ?: "Export failed. Retry with a smaller size.", color = MaterialTheme.colorScheme.error)
        if (work?.state == WorkInfo.State.CANCELLED) Text("Export cancelled. Your project and original media are preserved.")
        if (currentExport != null) Text("Saved to Library · ${currentExport.width} × ${currentExport.height}")
        Button(enabled = currentExport != null && !busy, onClick = {
            try {
                val uri = Uri.parse(currentExport!!.uri)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newUri(context.contentResolver, project.title, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share completed Studio video"))
            } catch (failure: Exception) { message = "The exported file is unavailable or sharing could not open. Export again to recover." }
        }) { Text("Share current export") }
        if (record?.uri != null && currentExport == null) Text("Project changed. Export again before sharing.")
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun NativeClipPreview(project: StudioProject, clip: StudioClip, seekRequest: Long, onPosition: (Long)->Unit) {
    val context = LocalContext.current
    val player = remember(clip.id) { ExoPlayer.Builder(context).build() }
    LaunchedEffect(clip, project.filter, project.overlay, project.fadeEdges, project.originalVolume, project.tools) {
        delay(150)
        player.setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build())
        player.setVideoEffects(NativeStudioRenderer.videoEffects(project, clip, resize = false))
        player.setPlaybackSpeed(clip.speed)
        player.volume = project.originalVolume
        player.prepare()
    }
    val positionCallback by rememberUpdatedState(onPosition)
    val offset=project.clips.takeWhile {it.id!=clip.id}.sumOf {it.playbackDurationMs}
    LaunchedEffect(player,seekRequest,clip) {
        delay(200) // Apply after the debounced media/effect preparation.
        player.seekTo(((seekRequest-offset).coerceIn(0,clip.playbackDurationMs)*clip.speed).toLong())
    }
    LaunchedEffect(player,offset,clip.speed) {while(true) {delay(200); positionCallback(offset+(player.currentPosition/clip.speed).toLong())}}
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } },
        update = { it.player = player }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
}
