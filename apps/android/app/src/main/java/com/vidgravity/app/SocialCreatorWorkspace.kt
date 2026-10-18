package com.vidgravity.app

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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import android.graphics.Bitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SocialCreatorWorkspace(project:StudioProject,selected:Int,onClose:()->Unit,onApply:(StudioProject)->Unit,onCreate:(StudioProject)->Unit,onBusy:(Boolean)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val focus=LocalFocusManager.current
    val brands=remember {CreatorBrandRepository(context)};val repository=remember {StudioRepository(context)}
    var tab by remember {mutableIntStateOf(0)};var busy by remember {mutableStateOf(false)};var message by remember {mutableStateOf<String?>(null)}
    var brand by remember {mutableStateOf(CreatorBrand())};var brandLoaded by remember {mutableStateOf(false)}
    var recipe by remember {mutableStateOf(SocialCreator.workflows.first())};var title by remember {mutableStateOf(project.title)}
    var seconds by remember {mutableIntStateOf(30)};var useBrand by remember {mutableStateOf(false)}
    var fit by remember {mutableStateOf(CreatorFit.FIT)};var targets by remember {mutableStateOf(setOf(StudioAspect.PORTRAIT))}
    var resolution by remember {mutableStateOf(project.resolution)};var fps by remember {mutableIntStateOf(if(project.tools.frameRate==0) 30 else project.tools.frameRate)}
    var bitrate by remember {mutableIntStateOf(if(project.tools.bitrate==0) 5_000_000 else project.tools.bitrate)}
    var bpm by remember {mutableLongStateOf(120)};var safe by remember {mutableStateOf(true)}
    var variantTitles by remember {mutableStateOf(emptyMap<StudioAspect,String>())}
    var previewAspect by remember {mutableStateOf(StudioAspect.PORTRAIT)};var preview by remember {mutableStateOf<Bitmap?>(null)}
    val clip=project.clips[selected.coerceIn(project.clips.indices)]
    val manager=remember {WorkManager.getInstance(context)}
    val jobs by remember {manager.getWorkInfosByTagLiveData("vidgravity-studio-export")}.observeAsState(emptyList())
    var queueProjects by remember {mutableStateOf(emptyMap<String,StudioProject>())}
    fun task(block:suspend ()->Unit) {scope.launch {busy=true;message=null;try {block()} catch(c:CancellationException) {throw c} catch(e:Exception) {message=e.message ?: "Operation failed. Saved projects are preserved."} finally {busy=false}}}
    fun settings(p:StudioProject)=p.copy(resolution=resolution,tools=p.tools.copy(frameRate=fps,bitrate=bitrate,social=p.tools.social.copy(fit=fit)))
    LaunchedEffect(Unit) {try {brand=withContext(Dispatchers.IO) {brands.load()};brandLoaded=true} catch(e:Exception) {if(e is CancellationException) throw e;message="Brand kit could not be read. Its file was preserved."}}
    LaunchedEffect(jobs) {try {queueProjects=withContext(Dispatchers.IO) {repository.list().mapNotNull {p -> repository.record(p.id)?.let {it.workId to p}}.toMap()}} catch(e:Exception) {if(e is CancellationException) throw e;message="Export history could not be read. Saved projects are preserved."}}
    DisposableEffect(busy) {onBusy(busy);onDispose {}}
    DisposableEffect(Unit) {onDispose {onBusy(false)}}
    BackHandler(enabled=!busy,onBack=onClose)
    val logoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null) task {val logo=withContext(Dispatchers.IO) {brands.importLogo(uri)};brand=brand.copy(logo=logo);message="Logo imported privately. Save the kit to reuse it."}}
    LaunchedEffect(clip,fit,previewAspect,safe) {
        delay(200)
        try {preview=withContext(Dispatchers.IO) {val source=ThumbnailRenderer.frame(context,clip.uri,clip.startMs,640)
            try {SocialPreview.draw(source,clip.frame,previewAspect,fit,safe)} finally {source.recycle()}}}
        catch(c:CancellationException) {throw c} catch(e:Exception) {preview=null;message="Preview unavailable. Relink missing media before export."}
    }
    DisposableEffect(preview) {val b=preview;onDispose {b?.recycle()}}
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("Social Creator",style=MaterialTheme.typography.headlineSmall);TextButton(enabled=!busy,onClick=onClose) {Text("Back to editor")}}
        Text("Create once. Adapt with care.",style=MaterialTheme.typography.labelMedium)
        ScrollableTabRow(tab,edgePadding=0.dp) {listOf("Create","Brand kit","Repurpose","Short tools").forEachIndexed {i,t -> Tab(tab==i,{if(!busy) tab=i},text={Text(t)})}}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {Text(it,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("social_message"))}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(tab) {
                0 -> {
                    Text("Choose a format or an original video template. Your current project stays intact; creation opens a separate editable project.")
                    Text("Guided workflows",style=MaterialTheme.typography.titleLarge)
                    SocialCreator.workflows.forEach {r -> SocialRecipeCard(r,recipe,!busy) {recipe=r;seconds=r.seconds;previewAspect=r.aspect}}
                    Text("Original template library",style=MaterialTheme.typography.titleLarge)
                    Text("Trending-style means an original short-video layout, not live trend data. Use Studio to replace/relink clips and choose music.",style=MaterialTheme.typography.labelSmall)
                    SocialCreator.templates.forEach {r -> SocialRecipeCard(r,recipe,!busy) {recipe=r;seconds=r.seconds;previewAspect=r.aspect}}
                    preview?.let {Image(it.asImageBitmap(),"Social framing preview",Modifier.fillMaxWidth().heightIn(max=260.dp))}
                    Text("Preview ${recipe.label} · ${recipe.aspect.label} · ${minOf(seconds*1000L,project.durationMs)/1000f}s. Frame only; play the saved template for animation/audio.")
                    CreatorField("Social opening title",title,!busy) {title=it.take(100)}
                    StudioLongField("Social duration target (seconds)",seconds.toLong(),!busy) {seconds=it.toInt().coerceIn(1,1800)}
                    Row {Checkbox(useBrand,{useBrand=it},enabled=brandLoaded && !busy);Text("Apply saved brand kit")}
                    Text("Timed captions and independent audio stay in the source project. New templates start with their own editable titles and original audio.",style=MaterialTheme.typography.labelSmall)
                    Button(enabled=!busy && title.isNotBlank(),onClick={task {var next=SocialCreator.create(project,recipe,title,seconds);if(useBrand) next=brand.apply(next);onCreate(next)}},modifier=Modifier.testTag("social_create")) {Text("Create social project")}
                }
                1 -> {
                    Text("Your local brand kit",style=MaterialTheme.typography.titleLarge)
                    Text("Save once, apply to multiple projects. Applying copies a snapshot; changing the kit does not alter older projects.")
                    CreatorField("Brand name",brand.name,!busy && brandLoaded) {brand=brand.copy(name=it.take(80))}
                    TextButton(enabled=!busy && brandLoaded,onClick={logoPicker.launch(arrayOf("image/*"))}) {Text(if(brand.logo==null) "Import logo" else "Replace logo")}
                    if(brand.logo!=null) TextButton(enabled=!busy,onClick={brand=brand.copy(logo=null)}) {Text("Remove kit logo")}
                    Row(Modifier.horizontalScroll(rememberScrollState())) {listOf(-1 to "White",0xFFFFCC00.toInt() to "Gold",0xFF00FFFF.toInt() to "Cyan").forEach {(c,n)->FilterChip(brand.color==c,{brand=brand.copy(color=c)},label={Text(n)})}}
                    Row(Modifier.horizontalScroll(rememberScrollState())) {listOf("sans-serif","serif","monospace").forEach {f -> FilterChip(brand.font==f,{brand=brand.copy(font=f)},label={Text(f)})}}
                    StudioAdjustment("Brand horizontal position",brand.x,-0.8f..0.8f,!busy) {brand=brand.copy(x=it)}
                    StudioAdjustment("Brand vertical position",brand.y,-0.8f..0.8f,!busy) {brand=brand.copy(y=it)}
                    CreatorField("Brand intro text",brand.intro,!busy) {brand=brand.copy(intro=it.take(100))}
                    CreatorField("Brand outro text",brand.outro,!busy) {brand=brand.copy(outro=it.take(100))}
                    CreatorExportOptions(brand.resolution,brand.fps,brand.bitrate,!busy,{brand=brand.copy(resolution=it)},{brand=brand.copy(fps=it)},{brand=brand.copy(bitrate=it)})
                    Button(enabled=!busy && brandLoaded,onClick={task {withContext(Dispatchers.IO) {brands.save(brand)};message="Brand kit saved on this device."}},modifier=Modifier.testTag("brand_save")) {Text("Save brand kit")}
                    OutlinedButton(enabled=!busy && brandLoaded,onClick={task {onApply(brand.apply(project));message="Brand snapshot applied. Undo is available in Studio."}}) {Text("Apply brand to current project")}
                    TextButton(enabled=!busy,onClick={onApply(project.copy(tools=project.tools.copy(social=project.tools.social.copy(watermark="",logo=null))));message="Watermark removed; editable intro/outro captions can be removed in Studio."}) {Text("Remove project watermark")}
                    TextButton(enabled=!busy,onClick={task {brand=withContext(Dispatchers.IO) {brands.load()};message="Brand kit reopened."}}) {Text("Reload brand kit")}
                }
                2 -> {
                    Text("Multi-format export",style=MaterialTheme.typography.titleLarge)
                    Text("Select up to five formats. Each creates a separate editable project and immutable export request. Jobs render serially, survive app restarts and publish to Library.")
                    CreatorAspects(targets,!busy) {a -> targets=if(a in targets) targets-a else targets+a;previewAspect=a}
                    Row {CreatorFit.entries.forEach {f -> FilterChip(fit==f,{fit=f},enabled=!busy,label={Text(if(f==CreatorFit.FIT) "Fit · preserve edges" else "Fill · crop edges")})}}
                    Text(if(fit==CreatorFit.FIT) "Fit preserves the selected source crop and may add bars." else "Fill crops edges. Review important faces/text in every format before queueing.")
                    preview?.let {Image(it.asImageBitmap(),"${previewAspect.label} framing and safe area preview",Modifier.fillMaxWidth().heightIn(max=280.dp))}
                    Row {Checkbox(safe,{safe=it});Text("Show conservative safe-area guide")}
                    Text("Guide is preview-only, not a platform guarantee. Platform UI and upload limits vary; verify current publishing requirements.",style=MaterialTheme.typography.labelSmall)
                    targets.forEach {a -> CreatorField("${a.label} output title",variantTitles[a] ?: (project.title.take(85)+" · "+a.label),!busy) {value->variantTitles=variantTitles+(a to value.take(100))}}
                    Text("After saving variants, use each project’s existing caption and cover editor for individual styling. Queueing captures its current settings.",style=MaterialTheme.typography.labelSmall)
                    CreatorExportOptions(resolution,fps,bitrate,!busy,{resolution=it},{fps=it},{bitrate=it})
                    Text("Approx. ${SocialCreator.estimatedBytes(settings(project))/1_000_000} MB per output; codec, audio and source rate affect actual size. Frame rate is capped by source; no frame interpolation.")
                    Button(enabled=!busy && targets.isNotEmpty(),onClick={task {
                        val variants=SocialCreator.variants(settings(project),targets.toList(),fit).map {v->v.copy(title=variantTitles[v.tools.aspect]?.takeIf {it.isNotBlank()} ?: v.title)}
                        withContext(Dispatchers.IO) {SocialCreator.requireSpace(android.os.StatFs(context.cacheDir.path).availableBytes,variants.sumOf {SocialCreator.estimatedBytes(it)})
                            variants.forEach {repository.save(it);NativeStudioExportWorker.enqueue(context,it)}}
                        message="${variants.size} editable exports queued. Find projects in Studio and completed files in Library."
                    }},modifier=Modifier.testTag("social_queue")) {Text("Queue selected formats")}
                    OutlinedButton(enabled=!busy && targets.isNotEmpty(),onClick={task {
                        val variants=SocialCreator.variants(settings(project),targets.toList(),fit).map {v->v.copy(title=variantTitles[v.tools.aspect]?.takeIf {it.isNotBlank()} ?: v.title)}
                        withContext(Dispatchers.IO) {variants.forEach {repository.save(it)}}
                        message="Editable variants saved. Back to Projects to customize captions, music and covers before exporting."
                    }}) {Text("Save variants for individual editing")}
                    Text("Export queue",style=MaterialTheme.typography.titleLarge)
                    jobs.sortedBy {it.id.toString()}.forEach {w -> val p=queueProjects[w.id.toString()]
                        OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                            Text(p?.title ?: "Studio export");Text("${w.state} · ${w.progress.getString("stage") ?: "Waiting for renderer"}")
                            val progress=w.progress.getInt("progress",-1);if(progress>=0 && !w.state.isFinished) LinearProgressIndicator(progress={progress/100f},modifier=Modifier.fillMaxWidth())
                            if(!w.state.isFinished) TextButton(onClick={manager.cancelWorkById(w.id)}) {Text("Cancel queued export")}
                            if(w.state==WorkInfo.State.FAILED || w.state==WorkInfo.State.CANCELLED) {
                                Text(w.outputData.getString("error") ?: "Cancelled or stopped. Your editable project is preserved.")
                                if(p!=null) TextButton(enabled=!busy,onClick={task {withContext(Dispatchers.IO) {NativeStudioExportWorker.enqueue(context,p)};message="Retry queued."}}) {Text("Retry ${p.title}")}
                            }
                            if(w.state==WorkInfo.State.SUCCEEDED) Text("Saved to Android Library")
                        }}
                    }
                }
                3 -> {
                    Text("Short-form tools",style=MaterialTheme.typography.titleLarge)
                    Text("Beat grid uses your BPM, not automatic music analysis. Cuts start at the selected clip’s in-point. New cut projects preserve source media and leave timed captions/music in the original.")
                    StudioLongField("Beat grid BPM",bpm,!busy) {bpm=it.coerceIn(40,240)}
                    OutlinedButton(enabled=!busy,onClick={task {onCreate(SocialCreator.cutGrid(project,selected,bpm.toInt()))}}) {Text("Create beat-grid cut project")}
                    OutlinedButton(enabled=!busy,onClick={task {onCreate(SocialCreator.cutGrid(project,selected,bpm.toInt(),true))}}) {Text("Create alternating jump cuts")}
                    OutlinedButton(enabled=!busy,onClick={task {onCreate(SocialCreator.speedRamp(project,selected))}}) {Text("Create stepped speed · 0.5× → 1× → 2×")}
                    Text("Stepped speeds are hard boundaries, not a continuous speed ramp. Studio’s existing split, reorder, trim and audio controls remain available.")
                    Text("Rendered caption motion",style=MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {CreatorAnimation.entries.forEach {a -> FilterChip(project.tools.social.animation==a,{onApply(project.copy(tools=project.tools.copy(social=project.tools.social.copy(animation=a))))},enabled=!busy,label={Text(a.name)})}}
                    CreatorField("Lower third (first five seconds)",project.tools.social.lowerThird,!busy) {onApply(project.copy(tools=project.tools.copy(social=project.tools.social.copy(lowerThird=it.take(100)))))}
                    Row {Checkbox(project.tools.social.progress,{onApply(project.copy(tools=project.tools.copy(social=project.tools.social.copy(progress=it))))},enabled=!busy);Text("Render timeline progress bar")}
                    TextButton(enabled=!busy && project.tools.captions.size<100,onClick={onApply(project.copy(tools=project.tools.copy(captions=project.tools.captions+StudioCaption(maxOf(0,project.durationMs-2000),project.durationMs,"Save this idea • Follow for more"))))}) {Text("Add editable call to action")}
                    Text("Freeze frames, picture-in-picture, split-screen, photo montages, timed word highlighting and arbitrary sticker/image layers are not supported in this build. Use timed text (including supported emoji glyphs) in the existing caption editor.",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable private fun SocialRecipeCard(r:SocialRecipe,chosen:SocialRecipe,enabled:Boolean,onSelect:()->Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {Text(r.label,style=MaterialTheme.typography.titleMedium);Text("${r.aspect.label} · ${r.seconds}s target · ${r.opening} → ${r.closing}")
        TextButton(enabled=enabled,onClick=onSelect) {Text(if(r==chosen) "Selected: ${r.label}" else "Preview: ${r.label}")}}}
}
@Composable internal fun CreatorField(label:String,value:String,enabled:Boolean=true,onChange:(String)->Unit) {
    val focus=LocalFocusManager.current
    OutlinedTextField(value,onChange,enabled=enabled,label={Text(label)},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}))
}
@Composable private fun CreatorAspects(selected:Set<StudioAspect>,enabled:Boolean,onClick:(StudioAspect)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {StudioAspect.entries.filter {it!=StudioAspect.SOURCE}.forEach {a->FilterChip(a in selected,{onClick(a)},enabled=enabled,label={Text(a.label)})}}
}
@Composable internal fun CreatorExportOptions(resolution:StudioResolution,fps:Int,bitrate:Int,enabled:Boolean,onResolution:(StudioResolution)->Unit,onFps:(Int)->Unit,onBitrate:(Int)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {StudioResolution.entries.forEach {r->FilterChip(resolution==r,{onResolution(r)},enabled=enabled,label={Text(r.label)})}}
    Row(Modifier.horizontalScroll(rememberScrollState())) {listOf(15,24,30).forEach {f->FilterChip(fps==f,{onFps(f)},enabled=enabled,label={Text("$f fps")})}}
    Row(Modifier.horizontalScroll(rememberScrollState())) {listOf(2_000_000,5_000_000,10_000_000,20_000_000).forEach {b->FilterChip(bitrate==b,{onBitrate(b)},enabled=enabled,label={Text("${b/1_000_000} Mbps")})}}
}
