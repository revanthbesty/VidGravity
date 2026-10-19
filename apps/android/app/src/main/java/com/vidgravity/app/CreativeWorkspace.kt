package com.vidgravity.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreativeWorkspace(project:StudioProject,selected:Int,onClose:()->Unit,onApply:(StudioProject)->Unit,
    onCreate:(StudioProject)->Unit,onBusy:(Boolean)->Unit,onSeek:(Long)->Unit={}) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    val clip=project.clips[selected.coerceIn(project.clips.indices)]
    var tab by remember {mutableIntStateOf(0)}
    var job by remember {mutableStateOf<Job?>(null)}
    var working by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    var progress by remember {mutableFloatStateOf(0f)}
    var stage by remember {mutableStateOf("")}
    var suggestedThumbnailAt by remember {mutableStateOf<Long?>(null)}
    var saveThumbnail by remember {mutableStateOf<(suspend ()->Unit)?>(null)}
    var dictating by remember {mutableStateOf(false)}
    var captionAt by remember {mutableLongStateOf(project.clips.take(selected).sumOf {it.playbackDurationMs})}
    val latestProject by rememberUpdatedState(project); val applyLatest by rememberUpdatedState(onApply)
    val speech=remember(context) {OfflineDictation(context,{message=it},{text ->
        val p=latestProject
        val start=captionAt.coerceIn(0,p.durationMs-1)
        val next=p.copy(tools=p.tools.copy(captions=p.tools.captions+StudioCaption(start,minOf(start+3000,p.durationMs),text)))
        try {next.validate(); applyLatest(next)} catch(failure:IllegalArgumentException) {message=failure.message}
        dictating=false
    },{dictating=it})}
    val available=remember {OfflineDictation.available(context)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        if(granted) {speech.start()} else {dictating=false; message="Microphone permission denied. Manual captions and SRT remain available."}
    }
    DisposableEffect(Unit) {
        val lifecycle=(context as? LifecycleOwner)?.lifecycle
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) {speech.close(); dictating=false; job?.cancel()}}
        lifecycle?.addObserver(observer)
        onDispose {lifecycle?.removeObserver(observer); job?.cancel(); speech.close(); onBusy(false)}
    }
    DisposableEffect(working,dictating) {onBusy(working || dictating); onDispose {}}
    // Published preview bitmaps remain owned by Compose/RenderThread until references are released.
    fun leave(next:()->Unit) {
        job?.cancel(); speech.close(); dictating=false
        val flush=saveThumbnail
        if(flush==null) {next(); return}
        job=scope.launch {
            working=true; stage="Saving thumbnail settings…"; progress=-1f
            try {flush(); next()}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(failure:Exception) {message="Thumbnail settings could not be saved. Correct the title or retry before leaving."}
            finally {working=false}
        }
    }
    fun close() {leave(onClose)}
    BackHandler {close()}
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Creative Studio",style=MaterialTheme.typography.headlineSmall)
            TextButton(onClick=::close) {Text("Back to editor")}
        }
        Text("Offline by default · review every suggestion",style=MaterialTheme.typography.labelMedium)
        val tabs=listOf("AI Tools","Templates","Assistant","Thumbnails","Captions")
        ScrollableTabRow(selectedTabIndex=tab,edgePadding=0.dp) {
            tabs.forEachIndexed {i,label -> Tab(selected=tab==i,onClick={leave {tab=i}},text={Text(label)})}
        }
        if(working) {
            if(progress>=0) LinearProgressIndicator(progress={progress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stage)
            if(job?.isActive==true) TextButton(onClick={job?.cancel()}) {Text("Cancel processing")}
        }
        message?.let {Text(it,color=MaterialTheme.colorScheme.primary)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(tab) {
                0 -> SmartToolsPanel(project,selected,onApply,onBusy={working=it},
                    onNavigate={time->leave {onSeek(time);onClose()}},onCaptions={tab=4},
                    onThumbnail={time->suggestedThumbnailAt=time;tab=3})
                1 -> CreativeTemplatePanel(project,!working,onCreate)
                2 -> CreativeAssistantPanel(project,!working,onApply)
                3 -> ThumbnailPanel(project,clip,onBusy={working=it},onStage={stage=it; progress=-1f},onSaveReady={saveThumbnail=it},initialTimeMs=suggestedThumbnailAt)
                4 -> {
                    Text("Caption tools",style=MaterialTheme.typography.titleLarge)
                    Text("The editor already supports timed captions, three fonts, colors, size, vertical position, SRT import/export and burned-in video captions.")
                    Text("Automatic video-file transcription is not installed. No language model is bundled and no paid API is required. Manual editing works offline.",modifier=Modifier.testTag("speech_fallback"))
                    Text(if(available) "This device offers optional on-device English microphone dictation." else "This device has no available on-device speech recognizer. Use manual captions or import SRT.")
                    StudioLongField("Dictated cue start (ms)",captionAt,!working) {captionAt=it.coerceIn(0,project.durationMs-1)}
                    Button(enabled=available && !working && !dictating && project.tools.captions.size<100,onClick={
                        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) speech.start()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    }) {Text("Dictate English caption · microphone")}
                    TextButton(onClick={speech.close(); dictating=false}) {Text("Stop dictation")}
                    if(Build.VERSION.SDK_INT>=34 && available) {
                        Text("Optional English model: download size and storage are chosen by your device's speech provider, not included in this APK. Download only when you choose; consider Wi-Fi. Projects do not require this model.")
                        OutlinedButton(enabled=!working && !dictating,onClick={speech.downloadEnglishModel()}) {Text("Request English model download")}
                    }
                    Button(onClick=::close) {Text("Return to manual caption editor")}
                    Text("Dictation listens to your microphone; it does NOT transcribe an existing video. Review words and cue times before export.",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CreativeTemplatePanel(project:StudioProject,enabled:Boolean,onCreate:(StudioProject)->Unit) {
    val context=LocalContext.current; val focus=LocalFocusManager.current
    var preview by remember {mutableStateOf<Bitmap?>(null)}
    var chosen by remember {mutableStateOf(CreativeTemplates.all.first())}
    var title by remember {mutableStateOf(project.title.take(100))}
    var seconds by remember {mutableIntStateOf(15)}
    var music by remember {mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null) {StudioMediaImports.retain(context,uri); music=uri.toString()}}
    LaunchedEffect(chosen,title) {
        delay(300)
        try {preview=withContext(Dispatchers.IO) {
            val clip=project.clips.first()
            val source=ThumbnailRenderer.frame(context,clip.uri,clip.startMs,640)
            try {
                val height=320; val width=(height*chosen.aspect.ratio).toInt(); val out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(out); canvas.drawColor(android.graphics.Color.BLACK)
                val scale=minOf(width.toFloat()/source.width,height.toFloat()/source.height)
                val w=source.width*scale; val h=source.height*scale
                canvas.drawBitmap(source,null,android.graphics.RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),null)
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.WHITE; textSize=width*0.065f; textAlign=android.graphics.Paint.Align.CENTER; setShadowLayer(2f,1f,1f,android.graphics.Color.BLACK)}
                canvas.drawText(title.take(40),width/2f,height*0.85f,paint); out
            } finally {source.recycle()}
        }} catch(cancelled:CancellationException) {throw cancelled} catch(failure:Exception) {preview=null}
    }
    // Do not recycle a bitmap still referenced by Compose display lists.

    Text("Editable templates",style=MaterialTheme.typography.titleLarge)
    Text("Built-in layouts and text only; no downloaded music or licensed stock footage. Every template uses your clips and exports through Studio.")
    CreativeTemplates.all.forEach {template ->
        OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
            Text(template.label,style=MaterialTheme.typography.titleMedium)
            Text("${template.aspect.label} · ${template.seconds}s target · ${template.opening}")
            TextButton(enabled=enabled,onClick={chosen=template; seconds=template.seconds}) {Text(if(chosen==template) "Selected template" else "Preview ${template.label}")}
        }}
    }
    preview?.let {Image(it.asImageBitmap(),"Template static layout preview",Modifier.fillMaxWidth().heightIn(max=240.dp))}
    Text("Static layout preview; play the complete project in the editor. Aspect fit may add black bars.",style=MaterialTheme.typography.labelSmall)
    Text("Preview: ${chosen.label} · ${chosen.aspect.label} · first ${minOf(seconds*1000L,project.durationMs)/1000f}s of your timeline · fades + opening title")
    OutlinedTextField(title,{title=it.take(160)},enabled=enabled,label={Text("Template opening title")},modifier=Modifier.fillMaxWidth(),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
    Slider(seconds.toFloat(),{seconds=it.toInt()},enabled=enabled,valueRange=1f..60f,modifier=Modifier.testTag("template_duration"))
    Text("Duration target: $seconds seconds; never extends your source clips")
    TextButton(enabled=enabled,onClick={picker.launch(arrayOf("audio/*"))}) {Text(if(music==null) "Choose your music (optional)" else "Replace template music")}
    if(music!=null) TextButton(enabled=enabled,onClick={music=null}) {Text("Remove template music")}
    Button(enabled=enabled && title.isNotBlank(),onClick={onCreate(CreativeTemplates.create(project,chosen,title,seconds,music))},modifier=Modifier.testTag("create_template")) {Text("Create editable template project")}
    Text("Source project is preserved. In the new project, use Relink selected video/Add clip to replace media, and the caption/music controls to customize it.",style=MaterialTheme.typography.labelSmall)
}

@Composable
private fun CreativeAssistantPanel(project:StudioProject,enabled:Boolean,onApply:(StudioProject)->Unit) {
    val context=LocalContext.current; val scope=rememberCoroutineScope(); val focus=LocalFocusManager.current
    val provider:CreativeTextProvider=remember {OfflineCreativeRules()}
    var topic by remember {mutableStateOf(project.title)}
    var audience by remember {mutableStateOf("viewers")}
    var output by remember {mutableStateOf<CreativeCopy?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    Text("Creative assistant",style=MaterialTheme.typography.titleLarge)
    Text(provider.label)
    Text("No generative model is installed. These editable writing suggestions come from local rules and templates, not AI inference. An independent provider interface allows a future optional local model.")
    OutlinedTextField(topic,{topic=it.take(100)},enabled=enabled,label={Text("Content topic")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
    OutlinedTextField(audience,{audience=it.take(60)},enabled=enabled,label={Text("Audience")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
    Button(enabled=enabled && topic.isNotBlank(),onClick={scope.launch {
        try {output=withContext(Dispatchers.Default) {provider.generate(CreativeBrief(topic,audience))}} catch(failure:Exception) {if(failure is CancellationException) throw failure; error=failure.message}
    }}) {Text("Generate offline writing suggestions")}
    output?.let {copy ->
        Text("Titles",style=MaterialTheme.typography.titleMedium)
        copy.titles.forEach {title -> Text(title); TextButton(enabled=enabled,onClick={onApply(project.copy(title=title.take(100)))}) {Text("Use title: ${title.take(35)}")}}
        Text("Description: ${copy.description}"); Text(copy.hashtags.joinToString(" ")); Text("Hook: ${copy.hook}")
        copy.script.forEach {Text(it)}; Text("Thumbnail text: ${copy.thumbnail}"); Text(copy.workflow); Text("Call to action: ${copy.cta}"); Text("Repurpose: ${copy.repurpose}")
        TextButton(enabled=enabled && project.tools.captions.size<100,onClick={onApply(project.copy(tools=project.tools.copy(captions=project.tools.captions+StudioCaption(0,minOf(3000,project.durationMs),copy.hook))))}) {Text("Add hook as editable caption")}
        TextButton(onClick={
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("VidGravity writing suggestions",(copy.titles+listOf(copy.description,copy.hashtags.joinToString(" "),copy.hook)+copy.script+listOf(copy.thumbnail,copy.workflow,copy.cta,copy.repurpose)).joinToString("\n\n")))
        }) {Text("Copy writing pack")}
    }
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
}
