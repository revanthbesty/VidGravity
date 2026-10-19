package com.vidgravity.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var startup: StartupHost
    private var inboxRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.i(StartupHost.TAG, "onCreate.begin sdk=${android.os.Build.VERSION.SDK_INT} target=${applicationInfo.targetSdkVersion}")
        super.onCreate(savedInstanceState)
        startup = StartupHost(this)
        setContentView(startup)
        if (intent?.getBooleanExtra(ShareReceiverActivity.EXTRA_OPEN_INBOX, false) == true) inboxRequest++
        startup.start { VidGravityApp(inboxRequest = inboxRequest) }
        android.util.Log.i(StartupHost.TAG, "onCreate.end")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(ShareReceiverActivity.EXTRA_OPEN_INBOX, false)) inboxRequest++
        android.util.Log.i(StartupHost.TAG, "onNewIntent")
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.i(StartupHost.TAG, "onResume")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        android.util.Log.i(StartupHost.TAG, "window.focus=$hasFocus")
    }

    override fun onDestroy() {
        if (::startup.isInitialized) startup.close()
        super.onDestroy()
    }
}

private enum class VgTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Default.Home), Camera("Camera", Icons.Default.PhotoCamera),
    Library("Library", Icons.Default.VideoLibrary), Studio("Studio", Icons.Default.AutoAwesome),
    Settings("Settings", Icons.Default.Settings), Downloads("Downloads", Icons.Default.Download)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VidGravityApp(inboxRequest: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var prefs by remember { mutableStateOf<android.content.SharedPreferences?>(null) }
    var dark by remember { mutableStateOf(true) }
    // SharedPreferences may wait for disk loading. Never hold the first frame on it.
    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                val loadedPrefs = context.getSharedPreferences("vg-ui", 0)
                loadedPrefs to loadedPrefs.getBoolean("dark", true)
            }
            catch (failure: RuntimeException) {
                android.util.Log.w(StartupHost.TAG, "theme.preference.failed type=${failure.javaClass.simpleName}")
                null to true
            }
        }
        prefs = loaded.first
        dark = loaded.second
        android.util.Log.i(StartupHost.TAG, "theme.preference.loaded")
    }
    var selected by rememberSaveable { mutableStateOf(VgTab.Home) }
    val screenState=rememberSaveableStateHolder()
    var recentCaptureUri by rememberSaveable {mutableStateOf<String?>(null)}
    var verifyingCapture by remember {mutableStateOf(false)}
    var showInbox by rememberSaveable { mutableStateOf(inboxRequest > 0) }
    var showHelp by rememberSaveable {mutableStateOf(false)}
    var activeProjectId by rememberSaveable {mutableStateOf<String?>(null)}
    var studioLaunch by rememberSaveable {mutableStateOf("projects")}
    var studioRequest by rememberSaveable {mutableIntStateOf(0)}
    var saveStudio by remember {mutableStateOf<(suspend ()->Unit)?>(null)}
    var showStudyTok by remember { mutableStateOf(false) }
    var creatorInitialMedia by remember { mutableStateOf<LibraryMedia?>(null) }
    var playingMedia by remember { mutableStateOf<LibraryMedia?>(null) }
    var url by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var analysis by remember { mutableStateOf<SourceAnalysisResult?>(null) }
    var inspecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var studioBusy by remember { mutableStateOf(false) }
    LaunchedEffect(inboxRequest) {
        if (inboxRequest > 0) {
            if (studioBusy) notice = "Link saved to Share Inbox. Finish the current export before opening it."
            else try {saveStudio?.invoke();playingMedia=null;showStudyTok=false;showHelp=false;showInbox=true}
            catch(failure:Exception) {notice="Studio could not save. Retry saving before opening Inbox."}
        }
    }
    fun navigate(tab:VgTab) {
        if(studioBusy) {notice="Finish or cancel the active Studio operation before leaving.";return}
        scope.launch(Dispatchers.Main.immediate) {
            try {saveStudio?.invoke();playingMedia=null;showStudyTok=false;showInbox=false;showHelp=false;selected=tab}
            catch(c:kotlinx.coroutines.CancellationException) {throw c}
            catch(e:Exception) {notice="Studio could not save. Your editor stays open; retry Save project."}
        }
    }
    fun openStudio(mode:String,id:String?=activeProjectId) {
        studioLaunch=mode;studioRequest++;activeProjectId=if(mode=="new") null else id;creatorInitialMedia=null;navigate(VgTab.Studio)
    }
    val mediaImport=rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) scope.launch {
            try {val media=withContext(Dispatchers.IO) {
                StudioMediaImports.retain(context,uri)
                val name=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null} ?: "Imported media"
                LibraryMedia(uri,name,0,context.contentResolver.getType(uri) ?: "application/octet-stream",System.currentTimeMillis())
            };activeProjectId=null;studioLaunch="media";studioRequest++;creatorInitialMedia=media;navigate(VgTab.Studio)}
            catch(e:Exception) {notice="Media could not be opened. Choose a readable local file."}
        }
    }
    BackHandler(enabled = playingMedia != null || showHelp || showStudyTok || showInbox || selected != VgTab.Home) {
        when {
            playingMedia != null -> playingMedia = null
            showHelp -> showHelp=false
            showStudyTok -> showStudyTok = false
            showInbox -> showInbox = false
            else -> navigate(VgTab.Home)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val media=CameraResultMedia.read(result.resultCode,result.data)
        if(media!=null) scope.launch {
            verifyingCapture=true
            try {
                val verified=withContext(Dispatchers.IO) {CameraResultMedia.verify(context,media)}
                recentCaptureUri=verified.uri.toString()
                playingMedia=null;showInbox=false;showHelp=false;showStudyTok=false
                selected=VgTab.Library
                notice=null // The Library review card communicates success without blocking its actions.
            } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(failure:Exception) {notice="Capture could not be opened. Check Library or retry the camera; saved files were preserved."}
            finally {verifyingCapture=false}
        }

    }
    val openCamera: () -> Unit = camera@{
        if (studioBusy) { notice = "Wait for the Studio export to finish or cancel the clip split."; return@camera }
        scope.launch(Dispatchers.Main.immediate) {try {saveStudio?.invoke();cameraLauncher.launch(Intent(context,com.vidgravity.camera.DualCameraCaptureActivity::class.java).putExtra(com.vidgravity.camera.DualCameraCaptureActivity.EXTRA_DARK,dark))}
            catch(e:Exception) {notice="Camera could not open or Studio could not save. Retry."}}
    }
    val downloadsSelected=selected==VgTab.Downloads
    VgTheme(dark) {
        Scaffold(
            modifier=Modifier.testTag(if(dark) "theme_dark" else "theme_light"),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(9.dp).size(23.dp))
                        }
                        Column {
                            Text("VidGravity", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.4).sp)
                            Text("YOUR CREATOR UNIVERSE", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp, letterSpacing = 1.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(modifier=Modifier.testTag("nav_Downloads").semantics {this.selected=downloadsSelected}, onClick = { navigate(VgTab.Downloads) }) {
                        Icon(Icons.Default.Download, contentDescription = "Downloads")
                    }
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        IconButton(onClick = { dark = !dark; prefs?.edit()?.putBoolean("dark", dark)?.apply() }) {
                            Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.surface) {
                    VgTab.entries.filter { it != VgTab.Downloads }.forEach { tab ->
                        NavigationBarItem(modifier = Modifier.testTag("nav_${tab.name}"), selected = selected == tab, onClick = {
                            navigate(tab)
                        },
                            icon = { Icon(tab.icon, contentDescription = tab.title) }, label = { Text(tab.title, maxLines = 1) })
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (playingMedia != null) {
                    PlayerScreen(media = playingMedia!!, onBack = { playingMedia = null })
                } else if(showHelp) {
                    CreatorHelp({showHelp=false},{showHelp=false;openStudio("new",null)},openCamera,{mediaImport.launch(arrayOf("video/*","audio/*","image/*"))})
                } else if (showStudyTok) {
                    StudyTokScreen(
                        onBack = { showStudyTok = false },
                        notify = { notice = it }
                    )
                } else if (showInbox) {
                    ShareInboxContent(
                        onBack = { showInbox = false },
                        onInspect = {sharedUrl->url=sharedUrl;showInbox=false;selected=VgTab.Downloads},
                        onOpen = { sharedUrl ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sharedUrl))) }
                                .onFailure { notice = "Could not open this link in another app." }
                        }
                    )
                } else screenState.SaveableStateProvider(selected.name) { when (selected) {
                    VgTab.Home -> HomeDashboard(onCreate={openStudio("new",null)},onResume={openStudio("project",it)},onCamera=openCamera,
                        onImport={mediaImport.launch(arrayOf("video/*","audio/*","image/*"))},onTemplate={openStudio("template:$it")},
                        onLibrary={navigate(VgTab.Library)},onDownloads={navigate(VgTab.Downloads)},onPlay={playingMedia=it},onHelp={showHelp=true})
                    VgTab.Downloads -> DownloadsContent(onPlay={playingMedia=it},onEdit={creatorInitialMedia=it;activeProjectId=null;studioLaunch="media";studioRequest++;selected=VgTab.Studio},
                        sourcePanel={DownloadSourcePanel(url,{url=it},{if(url.isNotBlank()) {inspecting=true;scope.launch {try {analysis=withContext(Dispatchers.IO) {DownloadApi.analyzeSource(url.trim())}} catch(e:Exception) {notice=friendlyNetworkError(e)} finally {inspecting=false}}}},{notice=it},{showInbox=true},{},{showStudyTok=true})})
                    VgTab.Camera -> CameraWorkspace(openCamera,{navigate(VgTab.Library)})
                    VgTab.Library -> LibraryContent(recentCaptureUri=recentCaptureUri,
                        onPlay = { playingMedia = it },
                        onCreateWithMedia = {media -> creatorInitialMedia=media;activeProjectId=null;studioLaunch="media";studioRequest++;selected=VgTab.Studio}
                    )
                    VgTab.Studio -> key(studioRequest) {CreateStudioContent(
                        onPlay = { playingMedia = it }, notify = { notice = it }, openInbox = { showInbox = true },
                        initialMedia = creatorInitialMedia, openCamera = openCamera,
                        onBusyChanged={studioBusy=it},resumeProjectId=activeProjectId,launchMode=studioLaunch,
                        onActiveProject={activeProjectId=it},onSaveReady={saveStudio=it},onLaunchConsumed={studioLaunch="projects"}
                    )}
                    VgTab.Settings -> CreatorSettings(dark, {dark=it;prefs?.edit()?.putBoolean("dark",it)?.apply()},onHelp={showHelp=true})
                }}
            }
        }
        if(verifyingCapture) AlertDialog(onDismissRequest={},confirmButton={},title={Text("Opening saved capture")},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {LinearProgressIndicator(Modifier.fillMaxWidth());Text("Checking the file and refreshing Library…")}})
        if (inspecting) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Inspecting media") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("Checking available formats…") } })
        analysis?.let { result ->
            val usable = result.formats.filter { it.selector.isNotBlank() && (it.hasVideo || it.hasAudio) }
            val videoFormats = usable.filter { it.hasVideo }
            val audioFormats = usable.filter { it.hasAudio && !it.hasVideo }
            // Normalize portrait and landscape streams to the shorter pixel dimension:
            // 720x1280 is 720p, not 1280p. Keep one best compatible stream per quality.
            // Normalize every stream into a user-facing quality tier. Providers can return
            // portrait dimensions, partial dimensions, or several codec variants for the same tier.
            val rawQualityHeight: (FormatOption) -> Int = { option ->
                val w = option.width
                val h = option.height
                when {
                    w > 0 && h > 0 -> minOf(w, h)
                    h > 0 -> h
                    w > 0 -> w
                    else -> Regex("(\\d{3,4})\\s*x\\s*(\\d{3,4})")
                        .find(option.resolution)?.let { match ->
                            minOf(match.groupValues[1].toInt(), match.groupValues[2].toInt())
                        } ?: 0
                }
            }
            val qualityTier: (Int) -> Int = { height ->
                when {
                    height >= 7000 -> 4320
                    height >= 3800 -> 2160
                    height >= 2500 -> 1440
                    height >= 1800 -> 1080
                    height >= 1200 -> 720
                    height >= 800 -> 480
                    height >= 600 -> 360
                    height >= 360 -> height
                    else -> 0
                }
            }
            val qualityHeight: (FormatOption) -> Int = { option ->
                val w = option.width
                val h = option.height
                if (w > 0 && h > 0) {
                    val shortSide = minOf(w, h)
                    when {
                        shortSide >= 4320 -> 4320
                        shortSide >= 2160 -> 2160
                        shortSide >= 1440 -> 1440
                        shortSide >= 1080 -> 1080
                        shortSide >= 720 -> 720
                        shortSide >= 480 -> 480
                        shortSide >= 360 -> 360
                        else -> shortSide
                    }
                } else {
                    // A lone 1920/1280 dimension is commonly the long side of portrait media.
                    val raw = rawQualityHeight(option)
                    qualityTier(raw).takeIf { it > 0 } ?: raw
                }
            }
            val sortedVideo = videoFormats
                .filter { qualityHeight(it) > 0 && (it.selector.isNotBlank() || it.formatId.isNotBlank()) }
                .sortedWith(
                    compareByDescending<FormatOption> { qualityHeight(it) }
                        .thenByDescending { it.hasAudio }
                        .thenByDescending { it.container.equals("mp4", ignoreCase = true) }
                        .thenByDescending { it.sizeBytes ?: 0L }
                )
            val standardVideo = sortedVideo
                .groupBy { qualityHeight(it) }
                .toSortedMap(compareByDescending { it })
                .mapNotNull { (height, streams) ->
                    val option = streams.firstOrNull() ?: return@mapNotNull null
                    val resolution = when {
                        height >= 4320 -> "4320p · 8K UHD"
                        height >= 2160 -> "2160p · 4K UHD"
                        height >= 1440 -> "1440p · QHD"
                        height >= 1080 -> "1080p · Full HD"
                        height >= 720 -> "720p · HD"
                        height >= 480 -> "480p · SD"
                        height >= 360 -> "360p"
                        else -> "${height}p"
                    }
                    val selector = if (option.hasAudio) option.selector else option.selector + "+bestaudio/best"
                    Triple(resolution, selector, option)
                }
            val videoChoices = standardVideo
            var rememberQuality by remember(result.title) { mutableStateOf(false) }
            var qualityTab by remember(result.title) { mutableStateOf("Video & Audio") }
            ModalBottomSheet(
                onDismissRequest = { analysis = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 680.dp)
                            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Choose quality", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                            IconButton(onClick = { analysis = null }) { Icon(Icons.Default.Close, "Close") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = rememberQuality, onCheckedChange = { rememberQuality = it })
                            Text("Remember this resolution as default", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Video & Audio", "Audio Only").forEach { tab ->
                                FilterChip(
                                    selected = qualityTab == tab,
                                    onClick = { qualityTab = tab },
                                    label = { Text(tab) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (qualityTab == "Video & Audio") {
                            item {
                                Text("VIDEO · DOWNLOAD WITH SOUND", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            items(videoChoices, key = { it.first + ":" + (it.third?.formatId ?: it.second) }) { choice ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        try {
                                            val preferences = SecureDownloadPreferences.load(context)
                                            if (rememberQuality) {
                                                SecureDownloadPreferences.save(context, preferences.copy(
                                                    autoDownloadWithPreferredPreset = true,
                                                    preferredFormatSelector = choice.second
                                                ))
                                            }
                                            DownloadWorker.enqueue(context, url.trim(), choice.second, result.title, preferences.unmeteredOnly)
                                            analysis = null
                                            selected = VgTab.Downloads
                                            notice = "Download added to queue. Open Downloads to track progress."
                                        } catch (failure: Exception) {
                                            notice = "Could not queue this download: " + (failure.message ?: "Please try again.")
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(choice.first, fontWeight = FontWeight.SemiBold)
                                            val option = choice.third
                                            val sizeLabel = option?.sizeBytes?.let { "~" + String.format("%.1f MB", it / 1_000_000.0) } ?: "Size varies"
                                            val fpsLabel = option?.fps?.let { if (it >= 50) "60 FPS" else "${it.toInt()} FPS" }
                                            val hdrLabel = option?.dynamicRange?.takeIf { it.isNotBlank() && !it.equals("SDR", true) }?.let { "HDR" }
                                            val safeContainer = option?.container?.uppercase()
                                                ?.takeIf { it.matches(Regex("[A-Z0-9]{2,5}")) } ?: "MEDIA"
                                            Text(listOfNotNull(safeContainer, fpsLabel, hdrLabel, sizeLabel).joinToString(" • "),
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, null)
                                    }
                                }
                            }
                            }
                            if (qualityTab == "Audio Only" && audioFormats.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text("AUDIO ONLY", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                items(audioFormats, key = { "audio:" + it.formatId }) { audio ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            try {
                                                val preferences = SecureDownloadPreferences.load(context)
                                                DownloadWorker.enqueue(context, url.trim(), audio.selector, result.title + " — Audio", preferences.unmeteredOnly)
                                                analysis = null
                                                selected = VgTab.Downloads
                                                notice = "Audio download added to queue."
                                            } catch (failure: Exception) {
                                                notice = "Could not queue audio: " + (failure.message ?: "Please try again.")
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(audio.container.uppercase() + " · " + audio.audioCodec.takeIf { it != "none" } .orEmpty(), fontWeight = FontWeight.SemiBold)
                                                Text(audio.displayName(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(Icons.Default.Download, null)
                                        }
                                    }
                                }
                            }
                            if ((qualityTab == "Video & Audio" && videoChoices.isEmpty()) ||
                                (qualityTab == "Audio Only" && audioFormats.isEmpty())) {
                                item {
                                    Text(if (qualityTab == "Audio Only") "No audio-only streams were returned for this link."
                                        else "No standard video formats were returned for this link.")
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Choose one option to add it to Downloads. Transfer status and errors appear there.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
            }
        }
        notice?.let { msg ->
            AlertDialog(onDismissRequest = { notice = null }, confirmButton = {
                TextButton(onClick = { notice = null }) { Text("Got it") }
            }, title = { Text("VidGravity") }, text = { Text(msg) })
        }
    }
}

@Composable
private fun DownloadsContent(onPlay:(LibraryMedia)->Unit,onEdit:(LibraryMedia)->Unit,sourcePanel:@Composable ()->Unit={}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    var expandedErrors by remember { mutableStateOf(setOf<java.util.UUID>()) }
    var downloadFilter by remember { mutableStateOf("All") }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var downloadSearch by remember { mutableStateOf("") }
    var downloadSort by remember { mutableStateOf("Newest") }
    val liveWorks = remember(context) { WorkManager.getInstance(context).getWorkInfosByTagLiveData("vidgravity-download") }
    val allWorks by liveWorks.observeAsState(emptyList())
    val historyPrefs = remember(context) { context.getSharedPreferences("vg_downloads_ui", android.content.Context.MODE_PRIVATE) }
    var hiddenHistoryIds by remember(context) {
        mutableStateOf(historyPrefs.getStringSet("hidden_finished_ids", emptySet()).orEmpty().toSet())
    }
    val works = allWorks.filterNot { work ->
        work.state in setOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED) &&
            work.id.toString() in hiddenHistoryIds
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        sourcePanel()
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp))
                    Text("Downloads", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Your download queue, history and saved transfers in one place.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activeCount = works.count { it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                    val completedCount = works.count { it.state == WorkInfo.State.SUCCEEDED }
                    val failedCount = works.count { it.state == WorkInfo.State.FAILED ||
                        it.state == WorkInfo.State.CANCELLED }
                    listOf(Triple("ACTIVE", activeCount, MaterialTheme.colorScheme.primary),
                        Triple("SAVED", completedCount, MaterialTheme.colorScheme.secondary),
                        Triple("ISSUES", failedCount, MaterialTheme.colorScheme.error))
                        .forEach { (label, count, accent) ->
                            Surface(modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(count.toString(), fontSize = 21.sp,
                                        fontWeight = FontWeight.Bold, color = accent)
                                    Text(label, fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                }
            }
        }
        if (works.isEmpty()) {
            InfoCard("Nothing downloading yet", "Inspect a public link from Home to get started.", Icons.Default.Download, Modifier.fillMaxWidth())
        } else {
            val activeWorks = works.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            val failedWorks = works.filter { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }
            val retryableWorks = failedWorks.filter { work ->
                !(work.outputData.getString(DownloadWorker.RETRY_URL)
                    ?: work.progress.getString("media_url")).isNullOrBlank()
            }
            val completedWorks = works.filter { it.state == WorkInfo.State.SUCCEEDED }
            val statusFilteredWorks = when (downloadFilter) {
                "Active" -> activeWorks
                "Completed" -> completedWorks
                "Failed" -> failedWorks
                else -> works
            }
            val searchTerm = downloadSearch.trim()
            val matchedWorks = statusFilteredWorks.filter { work ->
                if (searchTerm.isBlank()) true else {
                    val title = work.outputData.getString("display_name")
                        ?: work.outputData.getString("filename")
                        ?: work.progress.getString("media_title")
                        ?: "Media download"
                    val source = work.outputData.getString(DownloadWorker.RETRY_URL)
                        ?: work.progress.getString("media_url").orEmpty()
                    val error = work.outputData.getString("error").orEmpty()
                    title.contains(searchTerm, ignoreCase = true) ||
                        source.contains(searchTerm, ignoreCase = true) ||
                        error.contains(searchTerm, ignoreCase = true) ||
                        work.state.name.contains(searchTerm, ignoreCase = true)
                }
            }
            val filteredWorks = when (downloadSort) {
                "Newest" -> matchedWorks
                "Oldest" -> matchedWorks.reversed()
                "Name A–Z" -> matchedWorks.sortedBy { work ->
                    (work.outputData.getString("display_name")
                        ?: work.outputData.getString("filename")
                        ?: work.progress.getString("media_title")
                        ?: "Media download").lowercase()
                }
                "Progress" -> matchedWorks.sortedByDescending { work ->
                    work.progress.getInt("progress", 0).coerceIn(0, 100)
                }
                "Status" -> matchedWorks.sortedWith(
                    compareBy<WorkInfo> { it.state.name }.thenBy { work ->
                        (work.outputData.getString("display_name")
                            ?: work.outputData.getString("filename")
                            ?: work.progress.getString("media_title")
                            ?: "Media download").lowercase()
                    }
                )
                "Failed first" -> matchedWorks.sortedWith(
                    compareBy<WorkInfo> {
                        when (it.state) {
                            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> 0
                            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> 1
                            WorkInfo.State.SUCCEEDED -> 2
                            else -> 3
                        }
                    }.thenBy { work ->
                        (work.outputData.getString("display_name")
                            ?: work.outputData.getString("filename")
                            ?: work.progress.getString("media_title")
                            ?: "Media download").lowercase()
                    }
                )
                else -> matchedWorks
            }
            val retryableFilteredWorks = filteredWorks.filter { work ->
                (work.state == WorkInfo.State.FAILED || work.state == WorkInfo.State.CANCELLED) &&
                    !(work.outputData.getString(DownloadWorker.RETRY_URL)
                        ?: work.progress.getString("media_url")).isNullOrBlank()
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Download overview", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${activeWorks.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Active", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("${completedWorks.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Completed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text("${failedWorks.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Failed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
                    val csv = buildString {
                        appendLine("Title,Status,Progress,Source URL,Error")
                        filteredWorks.forEach { work ->
                            val title = work.outputData.getString("display_name")
                                ?: work.progress.getString("media_title")
                                ?: work.progress.getString("title")
                                ?: "Media download"
                            val source = work.outputData.getString(DownloadWorker.RETRY_URL)
                                ?: work.progress.getString("media_url")
                                ?: ""
                            val percent = work.progress.getInt("progress", -1)
                            val error = work.outputData.getString("error") ?: ""
                            appendLine(listOf(title, work.state.name, if (percent >= 0) "$percent%" else "", source, error).joinToString(",") { csvCell(it) })
                        }
                    }
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(csv))
                    android.widget.Toast.makeText(context, "CSV download report copied.", android.widget.Toast.LENGTH_SHORT).show()
                },
                enabled = filteredWorks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            ) {
                Icon(Icons.Default.TableChart, null)
                Spacer(Modifier.width(8.dp))
                Text("Copy CSV report (${filteredWorks.size})")
            }
            OutlinedButton(
                onClick = {
                    val links = filteredWorks.mapNotNull { work ->
                        (work.outputData.getString(DownloadWorker.RETRY_URL)
                            ?: work.progress.getString("media_url"))
                            ?.takeIf { it.isNotBlank() }
                    }.distinct()
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(links.joinToString("\n")))
                    android.widget.Toast.makeText(context, "Copied ${links.size} source link(s).", android.widget.Toast.LENGTH_SHORT).show()
                },
                enabled = filteredWorks.any { work ->
                    !(work.outputData.getString(DownloadWorker.RETRY_URL)
                        ?: work.progress.getString("media_url")).isNullOrBlank()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            ) {
                Icon(Icons.Default.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("Copy visible source links")
            }
            OutlinedButton(
                onClick = {
                    val report = buildString {
                        appendLine("VidGravity download report")
                        appendLine("Filter: $downloadFilter")
                        appendLine("Listed: ${filteredWorks.size} | Active: ${activeWorks.size} | Completed: ${completedWorks.size} | Failed/cancelled: ${failedWorks.size}")
                        appendLine()
                        filteredWorks.forEachIndexed { index, work ->
                            val title = work.outputData.getString("display_name")
                                ?: work.progress.getString("media_title")
                                ?: work.progress.getString("title")
                                ?: "Media download"
                            val source = work.outputData.getString(DownloadWorker.RETRY_URL)
                                ?: work.progress.getString("media_url")
                                ?: "Not saved"
                            val percent = work.progress.getInt("progress", -1)
                            val error = work.outputData.getString("error")
                            appendLine("${index + 1}. $title")
                            appendLine("   Status: ${work.state.name}${if (percent >= 0) " | Progress: $percent%" else ""}")
                            appendLine("   Source: $source")
                            if (!error.isNullOrBlank()) appendLine("   Error: $error")
                        }
                    }
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                    android.widget.Toast.makeText(context, "Download report copied (${filteredWorks.size} item(s)).", android.widget.Toast.LENGTH_SHORT).show()
                },
                enabled = filteredWorks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(8.dp))
                Text("Copy download report (${filteredWorks.size})")
            }
            OutlinedButton(
                onClick = {
                    val report = buildString {
                        appendLine("VidGravity download report")
                        appendLine("Filter: $downloadFilter")
                        appendLine("Listed: ${filteredWorks.size} | Active: ${activeWorks.size} | Completed: ${completedWorks.size} | Failed/cancelled: ${failedWorks.size}")
                        appendLine()
                        filteredWorks.forEachIndexed { index, work ->
                            val title = work.outputData.getString("display_name")
                                ?: work.progress.getString("media_title")
                                ?: work.progress.getString("title")
                                ?: "Media download"
                            val source = work.outputData.getString(DownloadWorker.RETRY_URL)
                                ?: work.progress.getString("media_url")
                                ?: "Not saved"
                            val percent = work.progress.getInt("progress", -1)
                            val error = work.outputData.getString("error")
                            appendLine("${index + 1}. $title")
                            appendLine("Status: ${work.state.name}${if (percent >= 0) " | Progress: $percent%" else ""}")
                            appendLine("Source: $source")
                            if (!error.isNullOrBlank()) appendLine("Error: $error")
                            appendLine()
                        }
                    }
                    runCatching {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "VidGravity download report")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share download report"))
                    }.onFailure {
                        android.widget.Toast.makeText(context, "Could not open the sharing menu.", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                enabled = filteredWorks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Share report (${filteredWorks.size})")
            }
            OutlinedButton(
                onClick = { showClearHistoryConfirm = true },
                enabled = completedWorks.isNotEmpty() || failedWorks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear finished history")
            }
            if (failedWorks.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val report = failedWorks.joinToString("\n\n") { work ->
                            val title = work.outputData.getString("display_name")
                                ?: work.progress.getString("media_title") ?: "Media download"
                            val error = work.outputData.getString("error") ?: "No technical error was saved."
                            "${title}\nStatus: ${work.state.name}\nError: ${error}"
                        }
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                        android.widget.Toast.makeText(context, "Failure report copied (${failedWorks.size} item(s)).", android.widget.Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy failure report (${failedWorks.size})")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        activeWorks.forEach { work ->
                            DownloadActionReceiver.clear(context, work.id)
                            WorkManager.getInstance(context).cancelWorkById(work.id)
                        }
                    },
                    enabled = activeWorks.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.StopCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel active (${activeWorks.size})")
                }
                OutlinedButton(
                    onClick = {
                        var queued = 0
                        val preferences = SecureDownloadPreferences.load(context)
                        retryableFilteredWorks.forEach { work ->
                            val sourceUrl = work.outputData.getString(DownloadWorker.RETRY_URL)
                                ?: work.progress.getString("media_url")
                            val selector = work.outputData.getString(DownloadWorker.RETRY_FORMAT)
                                ?: work.progress.getString("format_selector")
                                ?: preferences.preferredFormatSelector
                            val retryTitle = work.outputData.getString(DownloadWorker.RETRY_TITLE)
                                ?: work.progress.getString("media_title")
                                ?: "Media download"
                            if (!sourceUrl.isNullOrBlank()) {
                                runCatching {
                                    DownloadWorker.enqueue(context, sourceUrl, selector, retryTitle, preferences.unmeteredOnly)
                                }.onSuccess { queued++ }
                            }
                        }
                        android.widget.Toast.makeText(
                            context,
                            if (queued > 0) "Queued ${queued} retry attempt(s)." else "No failed items had enough saved information to retry.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    },
                    enabled = retryableFilteredWorks.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Retry visible (${retryableFilteredWorks.size})")
                }
            }
            val runningWorks = works.filter { it.state == WorkInfo.State.RUNNING }
            val pausableWorks = runningWorks.filterNot { DownloadActionReceiver.isPaused(context, it.id) }
            val resumableWorks = runningWorks.filter { DownloadActionReceiver.isPaused(context, it.id) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        pausableWorks.forEach { work ->
                            context.sendBroadcast(Intent(context, DownloadActionReceiver::class.java)
                                .setAction(DownloadActionReceiver.ACTION_PAUSE)
                                .putExtra(DownloadActionReceiver.EXTRA_WORK_ID, work.id.toString()))
                        }
                    },
                    enabled = pausableWorks.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                ) {
                    Icon(Icons.Default.Pause, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pause running (${pausableWorks.size})")
                }
                OutlinedButton(
                    onClick = {
                        resumableWorks.forEach { work ->
                            context.sendBroadcast(Intent(context, DownloadActionReceiver::class.java)
                                .setAction(DownloadActionReceiver.ACTION_RESUME)
                                .putExtra(DownloadActionReceiver.EXTRA_WORK_ID, work.id.toString()))
                        }
                    },
                    enabled = resumableWorks.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Resume paused (${resumableWorks.size})")
                }
            }
            OutlinedTextField(
                value = downloadSearch,
                onValueChange = { downloadSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search download history") },
                placeholder = { Text("Filename, source URL, error, or status") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (downloadSearch.isNotBlank()) {
                        IconButton(onClick = { downloadSearch = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Newest", "Oldest", "Name A–Z", "Progress", "Status", "Failed first")) { sort ->
                    FilterChip(
                        selected = downloadSort == sort,
                        onClick = { downloadSort = sort },
                        label = { Text("Sort: $sort") }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${filteredWorks.size} matching download(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    enabled = downloadSearch.isNotBlank() || downloadFilter != "All" || downloadSort != "Newest",
                    onClick = {
                        downloadSearch = ""
                        downloadFilter = "All"
                        downloadSort = "Newest"
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear filters")
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Active", "Completed", "Failed")) { filter ->
                    val count = when (filter) {
                        "Active" -> activeWorks.size
                        "Completed" -> completedWorks.size
                        "Failed" -> failedWorks.size
                        else -> works.size
                    }
                    FilterChip(selected = downloadFilter == filter, onClick = { downloadFilter = filter },
                        label = { Text("$filter ($count)") })
                }
            }
            if (filteredWorks.isEmpty()) {
                InfoCard("No ${downloadFilter.lowercase()} downloads",
                    if (downloadFilter == "Active") "Your download queue is idle." else "Items will appear here when they match this filter.",
                    Icons.Default.FilterList, Modifier.fillMaxWidth())
            }
            // Use the screen's parent scroll container; nesting a LazyColumn here can
            // measure it with infinite height and crash on some Compose versions.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredWorks.forEach { work ->
                    val progress = work.progress.getInt("progress", 0).coerceIn(0, 100)
                    val paused = DownloadActionReceiver.isPaused(context, work.id)
                    val title = work.outputData.getString("display_name") ?: work.outputData.getString("filename") ?: work.progress.getString("media_title") ?: "Media download"
                    val downloaded = work.progress.getLong("bytes_downloaded", 0L).coerceAtLeast(0L)
                    val total = work.progress.getLong("total_bytes", -1L)
                    val speed = work.progress.getLong("bytes_per_second", 0L).coerceAtLeast(0L)
                    val eta = work.progress.getLong("eta_seconds", -1L)
                    val sourceUrl = work.progress.getString("media_url").orEmpty()
                    val platform = when { sourceUrl.contains("youtube", true) || sourceUrl.contains("youtu.be", true) -> "YouTube"; sourceUrl.contains("instagram", true) -> "Instagram"; else -> "Media" }
                    val selector = work.progress.getString("format_selector").orEmpty()
                    val quality = Regex("(4320|2160|1440|1080|720|480|360)").find(selector)?.value?.let { it + "p" } ?: "Auto"
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(platform + "  •  " + quality, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(work.state.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (sourceUrl.isNotBlank()) {
                                        IconButton(onClick = {
                                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(sourceUrl))
                                            android.widget.Toast.makeText(context, "Source link copied.", android.widget.Toast.LENGTH_SHORT).show()
                                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy source link") }
                                    }
                                }
                            }
                            if (work.state == WorkInfo.State.FAILED) {
                                val rawError = work.outputData.getString("error") ?: "Download failed. Try inspecting the link again."
                                val friendly = when {
                                    rawError.contains("SHA-256", true) || rawError.contains("checksum", true) -> "We couldn't verify this transfer. Retry the download; the incomplete file was not added to your library."
                                    rawError.contains("ForegroundServiceStartNotAllowedException", true) || rawError.contains("startForegroundService", true) -> "Android restricted background transfer. Retry with VidGravity open."
                                    rawError.contains("timeout", true) || rawError.contains("network", true) || rawError.contains("connect", true) -> "Connection interrupted. Check your network and retry."
                                    else -> "This transfer couldn't finish. Retry it or view troubleshooting details."
                                }
                                Text(friendly, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                TextButton(onClick = {
                                    expandedErrors = if (work.id in expandedErrors) expandedErrors - work.id else expandedErrors + work.id
                                }) { Text(if (work.id in expandedErrors) "Hide technical details" else "Troubleshooting details") }
                                if (work.id in expandedErrors) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(rawError, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            TextButton(onClick = {
                                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(rawError))
                                                android.widget.Toast.makeText(context, "Error details copied.", android.widget.Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Copy error details")
                                            }
                                        }
                                    }
                                }
                            }
                            if (work.state == WorkInfo.State.SUCCEEDED) {
                                Text("Saved to your device", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                val contentUri = work.outputData.getString("content_uri")?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                if (contentUri != null) {
                                    OutlinedButton(onClick = {
                                        val mime = MediaWorkflowRules.mimeForName(title) ?: "video/mp4"
                                        onEdit(LibraryMedia(contentUri, title,
                                            work.outputData.getLong("bytes_downloaded", downloaded), mime, System.currentTimeMillis()))
                                    }) { Text("Edit in Studio") }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(onClick = {
                                            val mime = if (title.substringAfterLast(".", "").lowercase() in listOf("mp3", "m4a", "aac", "flac", "wav", "opus")) "audio/*" else "video/*"
                                            onPlay(LibraryMedia(contentUri, title, work.outputData.getLong("bytes_downloaded", downloaded),
                                                mime, System.currentTimeMillis()))
                                        }) {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Play")
                                        }
                                        OutlinedButton(onClick = {
                                            val mime = if (title.substringAfterLast(".", "").lowercase() in listOf("mp3", "m4a", "aac", "flac", "wav", "opus")) "audio/*" else "video/*"
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = mime; putExtra(Intent.EXTRA_STREAM, contentUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(share, "Share media"))
                                        }) {
                                            Icon(Icons.Default.Share, null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Share")
                                        }
                                    }
                                }
                            }
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                            if (work.state == WorkInfo.State.RUNNING || work.state == WorkInfo.State.ENQUEUED) {
                                Text(String.format("%.1f MB", downloaded / 1_000_000.0) +
                                    (if (total > 0L) " / " + String.format("%.1f MB", total / 1_000_000.0) else " downloaded"),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format("%.2f MB/s", speed / 1_000_000.0) +
                                    (if (eta >= 0L) "  •  ~" + eta + "s remaining" else "  •  calculating ETA"),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(progress.toString() + "%", modifier = Modifier.weight(1f), fontSize = 12.sp)
                                if (work.state == WorkInfo.State.RUNNING && !paused) {
                                    TextButton(onClick = {
                                        context.sendBroadcast(Intent(context, DownloadActionReceiver::class.java)
                                            .setAction(DownloadActionReceiver.ACTION_PAUSE)
                                            .putExtra(DownloadActionReceiver.EXTRA_WORK_ID, work.id.toString()))
                                    }) { Text("Pause") }
                                }
                                if (work.state == WorkInfo.State.RUNNING && paused) {
                                    TextButton(onClick = {
                                        context.sendBroadcast(Intent(context, DownloadActionReceiver::class.java)
                                            .setAction(DownloadActionReceiver.ACTION_RESUME)
                                            .putExtra(DownloadActionReceiver.EXTRA_WORK_ID, work.id.toString()))
                                    }) { Text("Resume") }
                                }
                                if (work.state == WorkInfo.State.RUNNING || work.state == WorkInfo.State.ENQUEUED) {
                                    TextButton(onClick = { WorkManager.getInstance(context).cancelWorkById(work.id) }) { Text("Cancel") }
                                }
                                if (work.state == WorkInfo.State.FAILED || work.state == WorkInfo.State.CANCELLED) {
                                    TextButton(onClick = {
                                        val sourceUrl = work.outputData.getString(DownloadWorker.RETRY_URL)
                                            ?: work.progress.getString("media_url")
                                        val selector = work.outputData.getString(DownloadWorker.RETRY_FORMAT)
                                            ?: work.progress.getString("format_selector")
                                        val retryTitle = work.outputData.getString(DownloadWorker.RETRY_TITLE)
                                            ?: work.progress.getString("media_title") ?: title
                                        if (!sourceUrl.isNullOrBlank()) {
                                            val preferences = SecureDownloadPreferences.load(context)
                                            DownloadWorker.enqueue(context, sourceUrl, selector ?: preferences.preferredFormatSelector, retryTitle, preferences.unmeteredOnly)
                                        } else {
                                            android.widget.Toast.makeText(context,
                                                "Retry unavailable. Inspect the original link again from Home.",
                                                android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }) { Text("Retry") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear finished download history?") },
            text = { Text("This hides completed, failed, and cancelled items from this screen. Active downloads are not cancelled, and downloaded media files remain in your Library.") },
            confirmButton = {
                TextButton(onClick = {
                    val finishedIds = works.filter {
                        it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
                    }.map { it.id.toString() }.toSet()
                    hiddenHistoryIds = hiddenHistoryIds + finishedIds
                    historyPrefs.edit().putStringSet("hidden_finished_ids", hiddenHistoryIds.toSet()).apply()
                    showClearHistoryConfirm = false
                    android.widget.Toast.makeText(context, "Hidden ${finishedIds.size} finished item(s). Downloaded files are kept in Library.", android.widget.Toast.LENGTH_LONG).show()
                }) { Text("Clear history") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Keep history") }
            }
        )
    }
}

@Composable
private fun InfoCard(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShareInboxContent(onBack: () -> Unit, onInspect: (String) -> Unit, onOpen: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf(ShareInboxStore.list(context)) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) items = ShareInboxStore.list(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val visibleItems = items.filter {
        it.title.contains(searchQuery.trim(), ignoreCase = true) || it.url.contains(searchQuery.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Share Inbox", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Links you choose to save. Nothing downloads automatically.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (items.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it.take(160) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search saved links") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear search") } }
            )
        }
        if (items.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Inbox, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Your inbox is clear", fontWeight = FontWeight.Bold)
                    Text("Use Share in another app and choose VidGravity to save a public link here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (visibleItems.isEmpty()) {
            Text("No saved links match “${searchQuery.trim()}”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleItems, key = { it.id }) { item ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onInspect(item.url) }) { Text("Inspect") }
                                OutlinedButton(onClick = { onOpen(item.url) }) { Text("Open source") }
                                IconButton(onClick = { ShareInboxStore.remove(context, item.id); items = ShareInboxStore.list(context) }) {
                                    Icon(Icons.Default.DeleteOutline, "Remove saved link")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(onPlay: (LibraryMedia) -> Unit, onCreateWithMedia: (LibraryMedia) -> Unit, recentCaptureUri:String?=null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    var media by remember { mutableStateOf(emptyList<LibraryMedia>()) }
    var filter by rememberSaveable { mutableStateOf("All Media") }
    var sizeFilter by rememberSaveable { mutableStateOf("Any size") }
    var sortBy by rememberSaveable { mutableStateOf("Newest") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LibraryMedia?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LibraryMedia?>(null) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var swipeFeedOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled=swipeFeedOpen) {swipeFeedOpen=false}
    var libraryLoading by remember {mutableStateOf(true)}
    var seenCaptureUri by rememberSaveable {mutableStateOf<String?>(null)}
    val libraryListState=rememberLazyListState()
    val scope = rememberCoroutineScope()
    var libraryError by remember { mutableStateOf<String?>(null) }
    suspend fun refreshMedia() {
        libraryLoading=true
        try {
            media = withContext(Dispatchers.IO) { MediaLibraryIndexer.list(context) }
            libraryError = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { libraryError = "Library could not refresh. Your saved files are unchanged." }
        finally {libraryLoading=false}
    }
    LaunchedEffect(recentCaptureUri) {
        // A new capture must stay visible even when a previously saved search/filter excludes it.
        if(recentCaptureUri!=null && recentCaptureUri!=seenCaptureUri) {searchQuery="";filter="All Media";sizeFilter="Any size";sortBy="Newest";seenCaptureUri=recentCaptureUri;libraryListState.scrollToItem(0)}
        refreshMedia()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshMedia() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val filteredMedia = remember(media,filter,sizeFilter,searchQuery) {media.filter { item ->
        val matchesCategory = when (filter) {
            "Video" -> item.mimeType.startsWith("video/")
            "Audio" -> item.mimeType.startsWith("audio/")
            "Images" -> item.mimeType.startsWith("image/")
            "Other Files" -> !item.mimeType.startsWith("video/") && !item.mimeType.startsWith("audio/") && !item.mimeType.startsWith("image/")
            "Favorites" -> item.isFavorite
            else -> true
        }
        val matchesSize = when (sizeFilter) {
            "Under 10 MB" -> item.sizeBytes in 1L..9_999_999L
            "10–100 MB" -> item.sizeBytes in 10_000_000L..100_000_000L
            "Over 100 MB" -> item.sizeBytes > 100_000_000L
            "Unknown size" -> item.sizeBytes <= 0L
            else -> true
        }
        matchesCategory && matchesSize && item.displayName.contains(searchQuery.trim(), ignoreCase = true)
    }
    }
    val visibleMedia = remember(filteredMedia,sortBy) {when (sortBy) {
        "Oldest" -> filteredMedia.sortedBy { it.createdAtMs }
        "Name A-Z" -> filteredMedia.sortedBy { it.displayName.lowercase() }
        "Largest" -> filteredMedia.sortedByDescending { it.sizeBytes }
        "Smallest" -> filteredMedia.sortedBy { it.sizeBytes }
        "Type" -> filteredMedia.sortedWith(compareBy<LibraryMedia> { it.mimeType.substringBefore("/") }.thenBy { it.displayName.lowercase() })
        "Extension" -> filteredMedia.sortedWith(compareBy<LibraryMedia> { it.displayName.substringAfterLast('.', "").lowercase() }.thenBy { it.displayName.lowercase() })
        else -> filteredMedia.sortedByDescending { it.createdAtMs }
    }
    }
    // Drop selections for items that have been deleted or are no longer indexed.
    LaunchedEffect(media) {
        val existingUris = media.map { it.uri.toString() }.toSet()
        selectedUris = selectedUris.intersect(existingUris)
    }
    val visibleBytes = visibleMedia.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val favoriteCount = media.count { it.isFavorite }
    if (swipeFeedOpen) {
        SwipeFeedScreen(
            media = visibleMedia,
            onClose = { swipeFeedOpen = false },
            onFavorite = { item, favorite ->
                scope.launch {
                    withContext(Dispatchers.IO) { MediaLibraryIndexer.setFavorite(context, item.uri, favorite) }
                    refreshMedia()
                }
            },
            onMakeSticker = { onCreateWithMedia(it) },
            onStatusSplit = { onCreateWithMedia(it) }
        )
        return
    }
    var grid by rememberSaveable {mutableStateOf(false)}
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns=if(!grid || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) 1 else if(maxWidth>=840.dp) 3 else if(maxWidth>=360.dp) 2 else 1
    LazyColumn(Modifier.fillMaxSize().testTag("library_list"),state=libraryListState,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    item {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(libraryLoading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("library_loading"))
        media.firstOrNull {it.uri.toString()==recentCaptureUri}?.let {capture ->
            VgSection("Latest capture",capture.displayName) {
                Text("Saved on this device",color=MaterialTheme.colorScheme.primary)
                Button(onClick={onCreateWithMedia(capture)},modifier=Modifier.fillMaxWidth().testTag("capture_edit")) {Text("Open capture in Studio")}
                OutlinedButton(onClick={onPlay(capture)},modifier=Modifier.fillMaxWidth()) {Text("Preview capture")}
            }
        }
        libraryError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { scope.launch { refreshMedia() } }) { Text("Retry library") }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("My Library", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Captures, downloads and creator exports",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = { swipeFeedOpen = true },
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Swipe Feed")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("VIDEOS", media.count { it.mimeType.startsWith("video/") }, Icons.Default.Movie),
                        Triple("PHOTOS", media.count { it.mimeType.startsWith("image/") }, Icons.Default.Image),
                        Triple("AUDIO", media.count { it.mimeType.startsWith("audio/") }, Icons.Default.MusicNote)
                    ).forEach { (label, count, icon) ->
                        Surface(modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp))
                                Text(count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(label, fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = {Text("Search media")},
            shape = RoundedCornerShape(16.dp)
        )
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!grid,{grid=false},label={Text("List view")},modifier=Modifier.heightIn(min=48.dp))
            FilterChip(grid,{grid=true},label={Text("Grid view")},modifier=Modifier.heightIn(min=48.dp))
        }
        VgDisclosure("Library organization & bulk actions") {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${visibleMedia.size} shown", fontWeight = FontWeight.SemiBold)
                        Text(String.format("%.1f MB", visibleBytes / 1_000_000.0),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${media.size} total", fontWeight = FontWeight.SemiBold)
                        Text("${favoriteCount} favorites", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(
                    onClick = {
                        val report = buildString {
                            appendLine("VidGravity media library")
                            appendLine("Visible: ${visibleMedia.size} | Total indexed: ${media.size} | Favorites: ${favoriteCount}")
                            appendLine("Visible size: ${String.format("%.1f MB", visibleBytes / 1_000_000.0)}")
                            appendLine()
                            visibleMedia.forEachIndexed { index, item ->
                                appendLine("${index + 1}. ${item.displayName}")
                                appendLine("   Type: ${item.mimeType} | Size: ${if (item.sizeBytes > 0) String.format("%.1f MB", item.sizeBytes / 1_000_000.0) else "Unknown"}")
                            }
                        }
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                        android.widget.Toast.makeText(context, "Library report copied.", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    enabled = visibleMedia.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy visible library report")
                }
            }
        }
        OutlinedButton(
            onClick = {
                val report = buildString {
                    appendLine("VidGravity media library")
                    appendLine("Visible: ${visibleMedia.size} | Total indexed: ${media.size} | Favorites: ${favoriteCount}")
                    appendLine("Visible size: ${String.format("%.1f MB", visibleBytes / 1_000_000.0)}")
                    appendLine()
                    visibleMedia.forEachIndexed { index, item ->
                        appendLine("${index + 1}. ${item.displayName}")
                        appendLine("   Type: ${item.mimeType} | Size: ${if (item.sizeBytes > 0) String.format("%.1f MB", item.sizeBytes / 1_000_000.0) else "Unknown"}")
                    }
                }
                runCatching {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "VidGravity media library report")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share library report"))
                }.onFailure {
                    android.widget.Toast.makeText(context, "Could not open the sharing menu.", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            enabled = visibleMedia.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Share visible library report")
        }
        if (visibleMedia.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val allSelected = visibleMedia.all { it.uri.toString() in selectedUris }
                    selectedUris = if (allSelected) emptySet() else visibleMedia.map { it.uri.toString() }.toSet()
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Checklist, null); Spacer(Modifier.width(6.dp))
                    Text(if (visibleMedia.all { it.uri.toString() in selectedUris }) "Clear selection" else "Select all")
                }
                Button(enabled = visibleMedia.any { it.uri.toString() in selectedUris }, onClick = {
                    val chosen = visibleMedia.filter { it.uri.toString() in selectedUris }
                    runCatching {
                        val streams = ArrayList<Uri>()
                        chosen.forEach { streams.add(it.uri) }
                        val majorTypes = chosen.map { it.mimeType.substringBefore('/') }.distinct()
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = if (majorTypes.size == 1) majorTypes.first() + "/*" else "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            if (streams.isNotEmpty()) {
                                clipData = android.content.ClipData.newUri(context.contentResolver, "VidGravity media", streams.first())
                                streams.drop(1).forEach { clipData?.addItem(android.content.ClipData.Item(it)) }
                            }
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share selected media"))
                    }.onFailure {
                        android.widget.Toast.makeText(context, "Could not share selected media. Try fewer files.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share (${visibleMedia.count { it.uri.toString() in selectedUris }})")
                }
            }
            OutlinedButton(
                enabled = visibleMedia.isNotEmpty(),
                onClick = {
                    val visibleUris = visibleMedia.map { it.uri.toString() }.toSet()
                    selectedUris = selectedUris + visibleUris
                    android.widget.Toast.makeText(context, "Selected " + visibleUris.size + " visible item(s).", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.SelectAll, null)
                Spacer(Modifier.width(6.dp))
                Text("Select all visible (" + visibleMedia.size + ")")
            }
            OutlinedButton(
                enabled = visibleMedia.any { it.uri.toString() in selectedUris },
                onClick = {
                    val visibleUris = visibleMedia.map { it.uri.toString() }.toSet()
                    selectedUris = selectedUris - visibleUris
                    android.widget.Toast.makeText(context, "Cleared selection for " + visibleUris.size + " visible item(s).", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.Clear, null)
                Spacer(Modifier.width(6.dp))
                Text("Deselect visible items")
            }
            OutlinedButton(
                enabled = visibleMedia.isNotEmpty(),
                onClick = {
                    val visibleUris = visibleMedia.map { it.uri.toString() }.toSet()
                    val currentlySelected = selectedUris.intersect(visibleUris)
                    val invertVisible = visibleUris - currentlySelected
                    selectedUris = (selectedUris - visibleUris) + invertVisible
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.SwapVert, null)
                Spacer(Modifier.width(6.dp))
                Text("Invert visible selection")
            }
            OutlinedButton(
                enabled = visibleMedia.isNotEmpty(),
                onClick = {
                    val names = visibleMedia.joinToString("\n") { it.displayName }
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(names))
                    android.widget.Toast.makeText(context, "Copied " + visibleMedia.size + " visible filename(s).", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(6.dp))
                Text("Copy visible filenames (" + visibleMedia.size + ")")
            }
            OutlinedButton(
                enabled = visibleMedia.any { it.uri.toString() in selectedUris },
                onClick = {
                    val chosen = visibleMedia.filter { it.uri.toString() in selectedUris }
                    val names = chosen.joinToString("\n") { it.displayName }
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(names))
                    android.widget.Toast.makeText(context, "Copied ${chosen.size} filename(s).", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(6.dp))
                Text("Copy selected filenames")
            }
            OutlinedButton(
                enabled = visibleMedia.any { it.uri.toString() in selectedUris },
                onClick = {
                    val chosen = visibleMedia.filter { it.uri.toString() in selectedUris }
                    val report = buildString {
                        appendLine("VidGravity selected media report")
                        appendLine("Selected visible items: ${chosen.size}")
                        appendLine("Total size: ${String.format("%.1f MB", chosen.sumOf { it.sizeBytes.coerceAtLeast(0L) } / 1_000_000.0)}")
                        appendLine()
                        chosen.forEachIndexed { index, item ->
                            appendLine("${index + 1}. ${item.displayName}")
                            appendLine("   Type: ${item.mimeType} | Size: ${if (item.sizeBytes > 0) String.format("%.1f MB", item.sizeBytes / 1_000_000.0) else "Unknown"} | Favorite: ${if (item.isFavorite) "Yes" else "No"}")
                        }
                    }
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                    android.widget.Toast.makeText(context, "Selected media report copied.", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(6.dp))
                Text("Copy selected media report")
            }
            OutlinedButton(
                enabled = visibleMedia.any { it.uri.toString() in selectedUris },
                onClick = {
                    val chosen = visibleMedia.filter { it.uri.toString() in selectedUris }
                    val report = buildString {
                        appendLine("VidGravity selected media report")
                        appendLine("Selected visible items: ${chosen.size}")
                        appendLine("Total size: ${String.format("%.1f MB", chosen.sumOf { it.sizeBytes.coerceAtLeast(0L) } / 1_000_000.0)}")
                        appendLine()
                        chosen.forEachIndexed { index, item ->
                            appendLine("${index + 1}. ${item.displayName}")
                            appendLine("Type: ${item.mimeType} | Size: ${if (item.sizeBytes > 0) String.format("%.1f MB", item.sizeBytes / 1_000_000.0) else "Unknown"} | Favorite: ${if (item.isFavorite) "Yes" else "No"}")
                        }
                    }
                    runCatching {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "VidGravity selected media report")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share selected media report"))
                    }.onFailure {
                        android.widget.Toast.makeText(context, "Could not open the sharing menu.", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(6.dp))
                Text("Share selected media report")
            }
            OutlinedButton(
                enabled = visibleMedia.any { it.uri.toString() in selectedUris },
                onClick = {
                    val chosen = visibleMedia.filter { it.uri.toString() in selectedUris }
                    val markFavorite = chosen.any { !it.isFavorite }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            chosen.forEach { item -> MediaLibraryIndexer.setFavorite(context, item.uri, markFavorite) }
                        }
                        refreshMedia()
                        val count = chosen.size
                        val message = if (markFavorite) "Added $count item(s) to Favorites." else "Removed $count item(s) from Favorites."
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Star, null); Spacer(Modifier.width(6.dp))
                val chosenItems = visibleMedia.filter { it.uri.toString() in selectedUris }
                Text(if (chosenItems.isNotEmpty() && chosenItems.all { it.isFavorite }) "Remove selected from Favorites" else "Add selected to Favorites")
            }
            OutlinedButton(
                enabled = visibleMedia.any { it.uri.toString() in selectedUris },
                onClick = { showBulkDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(6.dp))
                Text("Delete selected (${visibleMedia.count { it.uri.toString() in selectedUris }})")
            }
        }
        }
        VgDisclosure("Library filters & sorting") {
        OutlinedButton(
            enabled = searchQuery.isNotBlank() || filter != "All Media" || sizeFilter != "Any size" || sortBy != "Newest",
            onClick = {
                searchQuery = ""
                filter = "All Media"
                sizeFilter = "Any size"
                sortBy = "Newest"
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(6.dp))
            Text("Reset library filters and sorting")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(listOf("All Media", "Video", "Audio", "Images", "Other Files", "Favorites")) { value ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(value) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Filter by file size", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(listOf("Any size", "Under 10 MB", "10–100 MB", "Over 100 MB", "Unknown size")) { value ->
                    FilterChip(
                        selected = sizeFilter == value,
                        onClick = { sizeFilter = value },
                        label = { Text(value) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sort by", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("Newest", "Oldest", "Name A-Z", "Largest", "Smallest", "Type", "Extension")) { value ->
                    FilterChip(
                        selected = sortBy == value,
                        onClick = { sortBy = value },
                        label = { Text(value) },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                }
            }
        }
        }
    }}
    if(!libraryLoading && visibleMedia.isEmpty()) item {
        InfoCard(if(searchQuery.isNotBlank()) "No matching media" else "Your library is empty","Import, record or finish a download. Missing files are removed from this index; relink affected Studio clips.",Icons.Default.VideoLibrary)
    } else items(visibleMedia.chunked(columns),key={row->row.first().uri.toString()}) {row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {row.forEach {item ->
            LibraryMediaCard(item,item.uri.toString() in selectedUris,grid,
                onSelected={checked->selectedUris=if(checked) selectedUris+item.uri.toString() else selectedUris-item.uri.toString()},
                onOpen={onPlay(item)},onEdit={onCreateWithMedia(item)},
                onFavorite={scope.launch {try {withContext(Dispatchers.IO) {MediaLibraryIndexer.setFavorite(context,item.uri,!item.isFavorite)};refreshMedia()} catch(e:Exception) {libraryError="Favorite could not be saved. Retry."}}},
                onRename={renameTarget=item;renameText=item.displayName.substringBeforeLast(".",item.displayName)},onDelete={deleteTarget=item},
                onShare={runCatching {context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type=item.mimeType;putExtra(Intent.EXTRA_STREAM,item.uri);clipData=android.content.ClipData.newUri(context.contentResolver,item.displayName,item.uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share media"))}.onFailure {libraryError="Sharing could not open. Check that the file is still available."}},
                modifier=Modifier.weight(1f))
        }}
    }
    }}
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename media") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("File name") }) },
            confirmButton = { TextButton(onClick = {
                val suffix = target.displayName.substringAfterLast(".", "")
                val finalName = renameText.trim() + if (suffix.isNotBlank() && !renameText.trim().endsWith(".$suffix")) ".$suffix" else ""
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { MediaLibraryIndexer.rename(context, target, finalName) }
                    refreshMedia()
                    if (!ok) android.widget.Toast.makeText(context, "Could not rename this media item.", android.widget.Toast.LENGTH_LONG).show()
                    renameTarget = null
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    if (showBulkDeleteConfirm) {
        val chosen = visibleMedia.filter { it.uri.toString() in selectedUris }
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete selected media?") },
            text = { Text("This permanently deletes ${chosen.size} selected item(s) from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkDeleteConfirm = false
                    scope.launch {
                        val results = withContext(Dispatchers.IO) {
                            chosen.map { item -> item.uri.toString() to MediaLibraryIndexer.delete(context, item) }
                        }
                        selectedUris = selectedUris - chosen.map { it.uri.toString() }.toSet()
                        refreshMedia()
                        val failed = results.count { !it.second }
                        val message = if (failed == 0) "Deleted ${results.size} item(s)." else "Deleted ${results.size - failed}; ${failed} could not be deleted."
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete download?") },
            text = { Text("This permanently deletes \"${target.displayName}\" from this device.") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { MediaLibraryIndexer.delete(context, target) }
                    refreshMedia()
                    if (!ok) android.widget.Toast.makeText(context, "Could not delete this media item.", android.widget.Toast.LENGTH_LONG).show()
                    deleteTarget = null
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CreateStudioContent(onPlay: (LibraryMedia) -> Unit, notify: (String) -> Unit, openInbox: () -> Unit, initialMedia: LibraryMedia? = null, openCamera: () -> Unit,onBusyChanged:(Boolean)->Unit,resumeProjectId:String?=null,launchMode:String="projects",onActiveProject:(String?)->Unit={},onSaveReady:((suspend ()->Unit)?)->Unit={},onLaunchConsumed:()->Unit={}) {
    val initialMode=remember {launchMode}
    var nativeStudio by remember {mutableStateOf(resumeProjectId!=null || initialMode=="new" || initialMode.startsWith("template:") || initialMedia?.mimeType?.startsWith("video/")==true)}
    var nativeInitial by remember {mutableStateOf<LibraryMedia?>(initialMedia)}
    if (nativeStudio) {
        NativeStudioHub(nativeInitial,onClose={nativeStudio=false;onActiveProject(null)},onBusyChanged=onBusyChanged,
            requestedProjectId=resumeProjectId,autoImport=initialMode=="new" || initialMode.startsWith("template:") && resumeProjectId==null,
            initialWorkspace=initialMode.takeIf {it.startsWith("template:")},onEditingChanged=onActiveProject,onSaveReady=onSaveReady,onPlayExport=onPlay)
        LaunchedEffect(Unit) {onLaunchConsumed()}
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMedia by remember(initialMedia) { mutableStateOf(initialMedia) }
    var storyEditorOpen by remember { mutableStateOf(false) }
    var isRendering by remember { mutableStateOf(false) }
    var segmentProgress by remember { mutableStateOf<String?>(null) }
    var segmentJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var statusClipSeconds by remember { mutableStateOf(90) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }
    var stickerUri by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(isRendering, segmentJob) {
        onBusyChanged(isRendering || segmentJob != null)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    val picker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val metadata = runCatching {
                context.contentResolver.query(uri, arrayOf(
                    android.provider.OpenableColumns.DISPLAY_NAME,
                    android.provider.OpenableColumns.SIZE
                ), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0) ?: "Selected media"
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                        name to size
                    } else "Selected media" to 0L
                } ?: ("Selected media" to 0L)
            }.getOrDefault("Selected media" to 0L)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            if (isRendering || segmentJob != null) {
                notify("Wait for the current export to finish or cancel the clip split before changing media.")
                return@rememberLauncherForActivityResult
            }
            selectedMedia = LibraryMedia(uri, metadata.first, metadata.second, mime, System.currentTimeMillis())
            storyEditorOpen = false
            exportedUri = null
            stickerUri = null
            notify(when {
                mime.startsWith("video/") -> "Video selected. Preview, share, edit a story, split status clips, or export a sticker."
                mime.startsWith("audio/") -> "Audio selected. Preview, share, or try a compatible story layout."
                mime.startsWith("image/") -> "Image selected. Preview or share the original; story rendering supports video and audio."
                else -> "File selected. Available Studio tools depend on the file type."
            })
        }
    }
    BackHandler(enabled = storyEditorOpen) {
        if (isRendering) notify("Wait for the export to finish before leaving the editor.")
        else storyEditorOpen = false
    }
    if (storyEditorOpen && selectedMedia != null) {
        StoryCardScreen(
            media = selectedMedia!!,
            onClose = { storyEditorOpen = false },
            isRendering = isRendering,
            hasDerivative = exportedUri != null,
            onEdit = { exportedUri = null },
            onRenderStory = { source, startSeconds, endSeconds, title, artist, layout ->
                if (!isRendering) {
                    scope.launch {
                        isRendering = true
                        exportedUri = null
                        try {
                            val mode = when (layout) {
                                "Audio Visualizer" -> "audio-story"
                                "Video Backdrop" -> "blurred-backdrop"
                                "Privacy Blur" -> "privacy-blur"
                                else -> "fit-letterbox"
                            }
                            val uri = withContext(Dispatchers.IO) {
                                DownloadApi.exportMedia(
                                    context, source.uri, "whatsapp-status", mode,
                                    startSeconds, (endSeconds - startSeconds).coerceIn(1f, 3600f),
                                    outputFormat = "mp4", title = title, artist = artist
                                )
                            }
                            exportedUri = uri
                            notify("Story exported, checksum verified, and saved to Movies/VidGravity. You can now share it.")
                        } catch (failure: Exception) {
                            notify("Story export failed: ${failure.message ?: "Please try again."}".take(240))
                        } finally {
                            isRendering = false
                        }
                    }
                }
            },
            onShareDerivative = { platform ->
                val uri = exportedUri
                if (uri == null) {
                    notify("Render the story first; the original file will not be replaced.")
                } else {
                    runCatching {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TITLE, "VidGravity story")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share story to ${platform.replaceFirstChar { it.uppercase() }}"))
                    }.onFailure { notify("Could not open sharing: ${it.message ?: "try again"}") }
                }
            }
        )
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(enabled = !isRendering && segmentJob == null, onClick = { nativeInitial = null; nativeStudio = true },
            modifier = Modifier.fillMaxWidth()) { Text("Projects & on-device editor") }
        OutlinedButton(onClick = openInbox, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Default.Inbox, null)
            Spacer(Modifier.width(8.dp))
            Text("Share Inbox · Watch / Learn Later")
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp))
                    Text("Creator Studio", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Turn your media into share-ready stories, status clips and stickers.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("STORIES", Icons.Default.Movie, Color(0xFF9C72FF)),
                        Triple("AUDIO", Icons.Default.MusicNote, Color(0xFF43D8CA)),
                        Triple("STICKERS", Icons.Default.AutoAwesome, Color(0xFFFF8195)))
                        .forEach { (label, icon, accent) ->
                            Surface(modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
                                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                }
                Text("Your original files stay unchanged.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Text("Start with your own media", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Choose a video, audio track or image from your device. You can preview and share it, or use supported Studio tools to create a separate export.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Button(onClick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) },
                    enabled = !isRendering && segmentJob == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose media")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Local file picker · Originals preserved · Export only when requested",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        selectedMedia?.let { media ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(media.displayName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (media.sizeBytes > 0) String.format("%.1f MB · %s", media.sizeBytes / 1_000_000.0, media.mimeType)
                        else media.mimeType, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface) {
                        Text(when {
                            media.mimeType.startsWith("video/") -> "VIDEO READY · Story editor, clip splitter and animated sticker"
                            media.mimeType.startsWith("audio/") -> "AUDIO READY · Preview, share and compatible story layouts"
                            media.mimeType.startsWith("image/") -> "IMAGE READY · Preview and share original"
                            else -> "FILE SELECTED · Available tools depend on file type"
                        }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (media.mimeType.startsWith("video/")) {
                        Button(enabled = !isRendering && segmentJob == null, onClick = { nativeInitial = media; nativeStudio = true },
                            modifier = Modifier.fillMaxWidth()) { Text("Edit this video on device") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onPlay(media) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Preview")
                        }
                        Button(onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = media.mimeType; putExtra(Intent.EXTRA_STREAM, media.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share original media"))
                            }.onFailure { notify("Could not open Android sharing: ${it.message ?: "try again"}") }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share original")
                        }
                    }
                    Button(enabled = MediaWorkflowRules.supportsStory(media.mimeType), onClick = { storyEditorOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Create 9:16 Story / Status")
                    }
                    if (!media.mimeType.startsWith("video/")) {
                        Surface(shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                                Text("Video-only tools such as clip splitting and animated WebP stickers require a video. You can preview and share images; story rendering currently supports video and audio.",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Text("STATUS CLIP LENGTH", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 90, 120).forEach { seconds ->
                            FilterChip(
                                selected = statusClipSeconds == seconds,
                                onClick = { statusClipSeconds = seconds },
                                enabled = segmentJob == null && !isRendering,
                                label = { Text("${seconds}s") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        if (statusClipSeconds > 90)
                            "120s is a custom split target; WhatsApp documents a 90-second maximum per Status video, so it may trim this clip. Use 90s or less for safer Status uploads."
                        else "Each exported clip targets ${statusClipSeconds} seconds. WhatsApp's current documented maximum is 90 seconds per Status video.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        enabled = media.mimeType.startsWith("video/") && !isRendering && segmentJob == null,
                        onClick = {
                            if (!media.mimeType.startsWith("video/")) {
                                notify("Status segmentation requires a video file.")
                            } else {
                                segmentJob = scope.launch {
                                    var completed = 0
                                    try {
                                        val durationSeconds = withContext(Dispatchers.IO) {
                                            val retriever = android.media.MediaMetadataRetriever()
                                            try {
                                                retriever.setDataSource(context, media.uri)
                                                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                                    ?.toLongOrNull()?.toFloat()?.div(1000f)
                                            } finally { retriever.release() }
                                        } ?: throw IllegalStateException("Could not read the video duration.")
                                        require(durationSeconds > 0f) { "The video duration is unavailable." }
                                        require(durationSeconds <= 600f) { "For safety and predictable processing, split videos up to 10 minutes per batch." }
                                        val clipTargetSeconds = statusClipSeconds.toFloat()
                                        val count = kotlin.math.ceil(durationSeconds / clipTargetSeconds).toInt()
                                        segmentProgress = "Preparing $count status clip(s)…"
                                        for (index in 0 until count) {
                                            if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == false) throw kotlinx.coroutines.CancellationException("Split cancelled")
                                            val startSeconds = index * clipTargetSeconds
                                            val clipSeconds = (durationSeconds - startSeconds).coerceAtMost(clipTargetSeconds)
                                            segmentProgress = "Rendering ${statusClipSeconds}s clip ${index + 1} of $count…"
                                            withContext(Dispatchers.IO) {
                                                DownloadApi.exportMedia(
                                                    context, media.uri, "whatsapp-status", "fit-letterbox",
                                                    startSeconds, clipSeconds
                                                )
                                            }
                                            completed++
                                            if (index < count - 1) kotlinx.coroutines.delay(21_000L)
                                        }
                                        notify("Created $completed of $count WhatsApp Status clips. Exports are saved in Movies/VidGravity.")
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        notify("Status split cancelled after $completed clip(s).")
                                    } catch (failure: Exception) {
                                        notify("Status splitting stopped after $completed clip(s): ${failure.message ?: "Please retry."}".take(240))
                                    } finally {
                                        segmentJob = null
                                        segmentProgress = null
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (segmentJob != null) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(segmentProgress ?: "Preparing status clips…")
                        } else {
                            Icon(Icons.Default.ContentCut, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Split into ${statusClipSeconds}-second clips")
                        }
                    }
                    if (segmentJob != null) {
                        TextButton(
                            onClick = { segmentJob?.cancel() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Close, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel split")
                        }
                    }
                    OutlinedButton(
                        enabled = media.mimeType.startsWith("video/") && !isRendering && segmentJob == null,
                        onClick = {
                            if (!media.mimeType.startsWith("video/")) {
                                notify("Animated WebP stickers require a video source.")
                            } else {
                                scope.launch {
                                    isRendering = true
                                    try {
                                        val durationSeconds = withContext(Dispatchers.IO) {
                                            val retriever = android.media.MediaMetadataRetriever()
                                            try {
                                                retriever.setDataSource(context, media.uri)
                                                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                                    ?.toLongOrNull()?.toFloat()?.div(1000f)
                                            } finally { retriever.release() }
                                        } ?: throw IllegalStateException("Could not read the video duration.")
                                        require(durationSeconds > 0f) { "The video duration is unavailable." }
                                        val clipSeconds = durationSeconds.coerceAtMost(10f)
                                        stickerUri = withContext(Dispatchers.IO) {
                                            DownloadApi.exportMedia(
                                                context, media.uri, "animated-sticker", "fit-letterbox",
                                                0f, clipSeconds, "webp"
                                            )
                                        }
                                        notify("Animated WebP sticker rendered, checksum verified, and saved to Pictures/VidGravity/Stickers.")
                                    } catch (failure: Exception) {
                                        notify("Sticker export failed: ${failure.message ?: "Please try again."}".take(240))
                                    } finally {
                                        isRendering = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (isRendering) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Rendering creator export…")
                        } else {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Make animated WebP sticker (up to 10s)")
                        }
                    }
                    stickerUri?.let { uri ->
                        OutlinedButton(onClick = {
                            runCatching {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/webp"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share animated sticker"))
                            }.onFailure { notify("Could not share sticker: ${it.message ?: "try again"}") }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share animated sticker")
                        }
                    }
                    exportedUri?.let { uri ->
                        Text("Latest story exported to Movies/VidGravity.",
                            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        OutlinedButton(
                            enabled = !isRendering && segmentJob == null,
                            onClick = {
                                runCatching {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TITLE, "VidGravity story")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share exported story"))
                                }.onFailure { notify("Could not share story: ${it.message ?: "try again"}") }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Share latest exported story")
                        }
                    }
                }
            }
        }
        Text("Capture", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedButton(
            onClick = {
                runCatching {
                    openCamera()
                }.onFailure { notify("Could not open camera capture: ${it.message ?: "try again"}") }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) {
            Icon(Icons.Default.PhotoCamera, null)
            Spacer(Modifier.width(8.dp))
            Text("Open POV camera")
        }
        Text("Creator workflow", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        InfoCard("Animated stickers", "Choose a video, export up to 10 seconds as animated WebP, then share the saved sticker.", Icons.Default.AutoAwesome, Modifier.fillMaxWidth())
        InfoCard("WhatsApp Status segments", "Choose a video, select a clip length, split into separate saved clips, and share from your library.", Icons.Default.ContentCut, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SimpleCollectionScreen(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, rows: List<String>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { row ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
                    Text(row, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun friendlyNetworkError(failure: Exception): String {
    val chain = generateSequence<Throwable>(failure) { it.cause }.toList()
    if (chain.any { it is java.net.UnknownHostException }) {
        return "VidGravity could not find its cloud API address. Check your internet connection and try again. If this keeps happening, the service hostname or DNS configuration needs to be fixed."
    }
    if (chain.any { it is java.net.SocketTimeoutException }) {
        return "The cloud service took too long to respond. It may be waking up from a free-tier idle state. Wait a moment and retry."
    }
    val message = failure.message.orEmpty()
    if (message.contains("Companion HTTP 502") || message.contains("Companion HTTP 503") || message.contains("Companion HTTP 504")) {
        return "The cloud service is temporarily unavailable. Wait a moment and retry."
    }
    if (message.isBlank()) return "VidGravity could not inspect this link. Check your connection and try again."
    return ("Could not inspect link: " + message).take(240)
}

