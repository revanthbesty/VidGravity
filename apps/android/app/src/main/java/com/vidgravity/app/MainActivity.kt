package com.vidgravity.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VidGravityApp() }
    }
}

private enum class VgTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Default.Home), Downloads("Downloads", Icons.Default.Download),
    Library("Library", Icons.Default.VideoLibrary), Create("Create", Icons.Default.AutoAwesome),
    Settings("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VidGravityApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("vg-ui", 0) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var selected by remember { mutableStateOf(VgTab.Home) }
    var playingMedia by remember { mutableStateOf<LibraryMedia?>(null) }
    var url by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var analysis by remember { mutableStateOf<SourceAnalysisResult?>(null) }
    var inspecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFB8A5FF), secondary = Color(0xFF8B7BFF),
        background = Color(0xFF101014), surface = Color(0xFF19191F),
        surfaceVariant = Color(0xFF25252D), onBackground = Color(0xFFF4F2FA),
        onSurface = Color(0xFFF4F2FA)
    ) else lightColorScheme(
        primary = Color(0xFF5B43D6), secondary = Color(0xFF735FE3),
        background = Color(0xFFF7F7FB), surface = Color.White,
        surfaceVariant = Color(0xFFECECF4)
    )
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VidGravity", fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
                        Text("Your media, in one place", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        IconButton(onClick = { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() }) {
                            Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.surface) {
                    VgTab.values().forEach { tab ->
                        NavigationBarItem(selected = selected == tab, onClick = { selected = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title) }, label = { Text(tab.title, maxLines = 1) })
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (playingMedia != null) {
                    PlayerScreen(media = playingMedia!!, onBack = { playingMedia = null })
                } else when (selected) {
                    VgTab.Home -> HomeContent(url, { url = it }, {
                        if (url.isBlank()) notice = "Paste a public media URL first."
                        else {
                            inspecting = true
                            scope.launch {
                                try { analysis = withContext(Dispatchers.IO) { DownloadApi.analyzeSource(url.trim()) } }
                                catch (failure: Exception) { notice = friendlyNetworkError(failure) }
                                finally { inspecting = false }
                            }
                        }
                    }, { notice = it })
                    VgTab.Downloads -> DownloadsContent(onPlay = { playingMedia = it })
                    VgTab.Library -> LibraryContent(onPlay = { playingMedia = it })
                    VgTab.Create -> CreateStudioContent(onPlay = { playingMedia = it }, notify = { notice = it })
                    VgTab.Settings -> SettingsContent(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() })
                }
            }
        }
        if (inspecting) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Inspecting media") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("Checking available formats…") } })
        analysis?.let { result ->
            val usable = result.formats.filter { it.selector.isNotBlank() && (it.hasVideo || it.hasAudio) }
            val videoFormats = usable.filter { it.hasVideo }
            val audioFormats = usable.filter { it.hasAudio && !it.hasVideo }
            val standardHeights = listOf(4320, 2160, 1440, 1080, 720, 480, 360)
            // Keep every extractor stream available in the picker, not just exact standard heights.
            // Many vertical/social sources expose heights like 640 or 854 rather than 720/1080.
            val sortedVideo = videoFormats.sortedWith(compareByDescending<FormatOption> { it.height }
                .thenByDescending { it.sizeBytes ?: 0L })
            val standardVideo = sortedVideo.distinctBy { listOf(it.height, it.container.lowercase(), it.videoCodec, it.audioCodec, it.formatId).joinToString("|") }
                .map { option ->
                    val h = option.height
                    val resolution = when {
                        h >= 4320 -> "8K UHD · ${h}p"
                        h >= 2160 -> "4K UHD · ${h}p"
                        h >= 1440 -> "2K · ${h}p"
                        h >= 1080 -> "FHD · ${h}p"
                        h >= 720 -> "HD · ${h}p"
                        h > 0 -> "${h}p"
                        else -> option.resolution.take(40).ifBlank { "Video stream" }
                    }
                    val selector = if (option.hasAudio) option.selector else option.selector + "+bestaudio/best"
                    Triple(resolution, selector, option)
                }
            val videoChoices = listOf(Triple("Highest available", "bv*+ba/b", videoFormats.maxByOrNull { it.height } ?: usable.firstOrNull()))
                .filter { it.third != null } + standardVideo
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
                                            if (rememberQuality && choice.first != "Highest available") {
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
                                            Text(if (choice.first == "Highest available") "Best compatible quality for this source"
                                                else listOfNotNull(option?.container?.uppercase(), option?.videoCodec?.takeIf { it != "none" && it != "unknown" }, fpsLabel, hdrLabel, sizeLabel).joinToString(" • "),
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
private fun HomeContent(url: String, onUrl: (String) -> Unit, inspect: () -> Unit, notify: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? ComponentActivity
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }
    fun detectClipboardUrl() {
        val raw = runCatching {
            val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (manager.hasPrimaryClip() && manager.primaryClipDescription?.hasMimeType("text/plain") == true) {
                manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            } else null
        }.getOrNull()?.trim().orEmpty()
        val candidate = raw.lineSequence().firstOrNull { it.startsWith("https://", true) || it.startsWith("http://", true) }
            ?.takeIf { it.length <= 4096 && (it.contains("://youtu") || it.contains("instagram.com") || it.contains("tiktok.com") || it.contains("facebook.com") || it.contains("twitter.com") || it.contains("x.com") || it.contains("vimeo.com") || it.contains("soundcloud.com") || it.contains("youtube.com") || it.contains("youtu.be")) }
        clipboardSuggestion = candidate?.takeIf { it != url }
    }
    DisposableEffect(activity) {
        detectClipboardUrl()
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) detectClipboardUrl()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (clipboardSuggestion != null && url.isBlank()) item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    onUrl(clipboardSuggestion.orEmpty())
                    clipboardSuggestion = null
                    notify("Clipboard link pasted. Tap Inspect link to view available formats.")
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentPaste, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Shared URL found in clipboard", fontWeight = FontWeight.SemiBold)
                        Text("Tap to paste and inspect", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Text("YOUR MEDIA WORKSPACE", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text("Your media, all in one place.", fontSize = 28.sp, lineHeight = 33.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                    Text("Paste a public video or audio link. Inspect available quality, then choose exactly what to save.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = url, onValueChange = onUrl, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste video or audio URL") }, leadingIcon = { Icon(Icons.Default.Link, null) },
                        trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.let(onUrl) }) { Icon(Icons.Default.ContentPaste, "Paste") } },
                        shape = RoundedCornerShape(16.dp), singleLine = true)
                    Button(onClick = { if (url.startsWith("http://") || url.startsWith("https://")) inspect() else notify("Enter a complete public URL starting with https://") },
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Inspect link")
                    }
                    Text("QUICK ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(end = 4.dp)
                        ) {
                            items(listOf("⚡ Grab Best Video", "♫ Sound Only", "✂ WhatsApp 30s", "✦ StudyTok AI")) { action ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(18.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.width(220.dp).heightIn(min = 112.dp).clickable {
                                        if (url.isBlank()) notify("Paste a media URL first, then choose " + action + ".")
                                        else if (action.contains("Sound")) {
                                            scope.launch {
                                                try {
                                                    val result = withContext(Dispatchers.IO) { DownloadApi.analyzeSource(url.trim()) }
                                                    val audio = result.formats.firstOrNull { it.hasAudio && !it.hasVideo }
                                                    if (audio == null) notify("No audio-only stream was found for this link.")
                                                    else {
                                                        val prefs = SecureDownloadPreferences.load(context)
                                                        DownloadWorker.enqueue(context, url.trim(), audio.selector, result.title + " — Audio", prefs.unmeteredOnly)
                                                        notify("Audio download added to queue. Open Downloads to track progress.")
                                                    }
                                                } catch (failure: Exception) {
                                                    notify("Could not prepare audio: " + (failure.message ?: "Please try again."))
                                                }
                                            }
                                        } else if (action.contains("WhatsApp") || action.contains("StudyTok")) {
                                            notify(if (action.contains("WhatsApp")) "Status splitting is not available in this build yet." else "StudyTok processing is not available in this build yet.")
                                        } else inspect()
                                    }
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(action.substringBefore(" "), fontSize = 24.sp)
                                        Text(action.substringAfter(" "), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("Tap to start", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard("Downloads", "Real transfer status", Icons.Default.Download, Modifier.weight(1f))
                InfoCard("On this device", "Your media library", Icons.Default.PhoneAndroid, Modifier.weight(1f))
            }
        }
        item {
            Text("Getting started", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("1. Paste a public link · 2. Inspect formats · 3. Choose and download",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DownloadsContent(onPlay: (LibraryMedia) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expandedErrors by remember { mutableStateOf(setOf<java.util.UUID>()) }
    val liveWorks = remember(context) { WorkManager.getInstance(context).getWorkInfosByTagLiveData("vidgravity-download") }
    val works by liveWorks.observeAsState(emptyList())
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Downloads", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Track active and completed transfers on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (works.isEmpty()) {
            InfoCard("Nothing downloading yet", "Inspect a public link from Home to get started.", Icons.Default.Download, Modifier.fillMaxWidth())
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(works, key = { it.id.toString() }) { work ->
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
                                Text(work.state.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        Text(rawError, Modifier.fillMaxWidth().padding(12.dp), fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (work.state == WorkInfo.State.SUCCEEDED) {
                                Text("Saved to your device", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                val contentUri = work.outputData.getString("content_uri")?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                if (contentUri != null) {
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
                                        val selector = work.outputData.getString(DownloadWorker.RETRY_FORMAT)
                                        val retryTitle = work.outputData.getString(DownloadWorker.RETRY_TITLE) ?: title
                                        if (sourceUrl != null) {
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
private fun LibraryContent(onPlay: (LibraryMedia) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var media by remember { mutableStateOf(emptyList<LibraryMedia>()) }
    var filter by remember { mutableStateOf("All Media") }
    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LibraryMedia?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LibraryMedia?>(null) }
    var swipeFeedOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refreshMedia() { media = withContext(Dispatchers.IO) { MediaLibraryIndexer.list(context) } }
    LaunchedEffect(Unit) { refreshMedia() }
    val visibleMedia = media.filter { item ->
        val matchesCategory = when (filter) {
            "Video" -> item.mimeType.startsWith("video/")
            "Audio" -> item.mimeType.startsWith("audio/")
            "Favorites" -> item.isFavorite
            else -> true
        }
        matchesCategory && item.displayName.contains(searchQuery.trim(), ignoreCase = true)
    }
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
            onMakeSticker = { android.widget.Toast.makeText(context, "Sticker renderer integration is not available yet.", android.widget.Toast.LENGTH_LONG).show() },
            onStatusSplit = { android.widget.Toast.makeText(context, "30-second status splitting is not available yet.", android.widget.Toast.LENGTH_LONG).show() }
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your library", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Downloaded media saved by VidGravity", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { swipeFeedOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Swipe Feed")
            }
        }
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search your downloads") },
            shape = RoundedCornerShape(16.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(listOf("All Media", "Video", "Audio", "Favorites")) { value ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(value) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
        if (visibleMedia.isEmpty()) {
            InfoCard(
                if (searchQuery.isNotBlank()) "No matching media" else if (filter == "Favorites") "No favorites yet" else "Your library is empty",
                if (searchQuery.isNotBlank()) "Try another title or clear the search." else "Completed downloads saved by VidGravity will appear here. Use Downloads to check failed transfers.",
                Icons.Default.VideoLibrary, Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleMedia, key = { it.uri.toString() }) { item ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onPlay(item) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                                Icon(if (item.mimeType.startsWith("audio/")) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                    null, modifier = Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.displayName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(String.format("%.1f MB", item.sizeBytes / 1_000_000.0), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { MediaLibraryIndexer.setFavorite(context, item.uri, !item.isFavorite) }
                                        refreshMedia()
                                    }
                                }) { Icon(if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite", tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                                IconButton(onClick = { renameTarget = item; renameText = item.displayName.substringBeforeLast(".", item.displayName) }) { Icon(Icons.Default.Edit, "Rename") }
                                IconButton(onClick = { deleteTarget = item }) { Icon(Icons.Default.Delete, "Delete") }
                                IconButton(onClick = {
                                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = item.mimeType; putExtra(android.content.Intent.EXTRA_STREAM, item.uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                    context.startActivity(android.content.Intent.createChooser(share, "Share media"))
                                }) { Icon(Icons.Default.Share, "Share") }
                                IconButton(onClick = { onPlay(item) }) { Icon(Icons.Default.PlayCircle, "Play", tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
        }
    }
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
private fun CreateStudioContent(onPlay: (LibraryMedia) -> Unit, notify: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedMedia by remember { mutableStateOf<LibraryMedia?>(null) }
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
            val mime = context.contentResolver.getType(uri) ?: "video/*"
            selectedMedia = LibraryMedia(uri, metadata.first, metadata.second, mime, System.currentTimeMillis())
            notify("Media selected. Choose Preview or Share to continue.")
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Create studio", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Choose a local video or audio file to preview and share. Your original stays unchanged.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Text("Start with your own media", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Select a file from this device. VidGravity will keep the selected source intact while you preview or share it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Button(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose media")
                }
            }
        }
        selectedMedia?.let { media ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(media.displayName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (media.sizeBytes > 0) String.format("%.1f MB · %s", media.sizeBytes / 1_000_000.0, media.mimeType)
                        else media.mimeType, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                context.startActivity(Intent.createChooser(intent, "Share media"))
                            }.onFailure { notify("Could not open Android sharing: ${it.message ?: "try again"}") }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share")
                        }
                    }
                }
            }
        }
        Text("Capture", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(context, com.vidgravity.camera.DualCameraCaptureActivity::class.java))
                }.onFailure { notify("Could not open camera capture: ${it.message ?: "try again"}") }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) {
            Icon(Icons.Default.PhotoCamera, null)
            Spacer(Modifier.width(8.dp))
            Text("Open POV camera")
        }
        Text("Creator tools", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        InfoCard("WhatsApp Status", "Story segmentation/export is not connected to the native render pipeline yet.", Icons.Default.ContentCut, Modifier.fillMaxWidth())
        InfoCard("Sticker maker", "Animated WebP rendering requires the media renderer integration.", Icons.Default.AutoAwesome, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SimpleCollectionScreen(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, rows: List<String>) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

@Composable
private fun SettingsContent(dark: Boolean, onDark: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val deviceId = remember {
        val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        "VG-" + androidId.takeLast(8).uppercase()
    }
    var activationKey by remember { mutableStateOf("") }
    var showActivation by remember { mutableStateOf(false) }
    var mediaBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        mediaBytes = withContext(Dispatchers.IO) { MediaLibraryIndexer.list(context).sumOf { it.sizeBytes } }
    }
    var unmeteredOnly by remember { mutableStateOf(SecureDownloadPreferences.load(context).unmeteredOnly) }
    var autoPreset by remember { mutableStateOf(SecureDownloadPreferences.load(context).autoDownloadWithPreferredPreset) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Make VidGravity work your way.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("APPEARANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (dark) Icons.Default.DarkMode else Icons.Default.LightMode, null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dark appearance", fontWeight = FontWeight.SemiBold)
                    Text(if (dark) "Dark theme enabled" else "Light theme enabled", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = dark, onCheckedChange = onDark)
            }
        }
        Text("DOWNLOADS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        SettingRow(Icons.Default.Folder, "Save location", "Managed by Android app storage")
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wi-Fi only", fontWeight = FontWeight.SemiBold)
                    Text("Queue downloads only on unmetered networks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = unmeteredOnly, onCheckedChange = {
                    unmeteredOnly = it
                    SecureDownloadPreferences.save(context, SecureDownloadPreferences.load(context).copy(unmeteredOnly = it))
                })
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Automatic share downloads", fontWeight = FontWeight.SemiBold)
                    Text("Use the saved format preset when sharing a link", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoPreset, onCheckedChange = {
                    autoPreset = it
                    SecureDownloadPreferences.save(context, SecureDownloadPreferences.load(context).copy(autoDownloadWithPreferredPreset = it))
                })
            }
        }
        Text("ACCOUNT & LICENSE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Device ID", fontWeight = FontWeight.SemiBold)
                        Text(deviceId, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(deviceId)) }) { Text("Copy") }
                }
                OutlinedButton(onClick = { showActivation = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enter license key")
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Library storage", fontWeight = FontWeight.SemiBold)
                        Text(String.format("%.1f MB indexed", mediaBytes / 1_000_000.0), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LinearProgressIndicator(progress = { (mediaBytes.toFloat() / (2L * 1024 * 1024 * 1024)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("Gauge is relative to 2 GB; it does not represent total device capacity.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showActivation) AlertDialog(
            onDismissRequest = { showActivation = false },
            title = { Text("License activation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the license key issued for your account.")
                    OutlinedTextField(value = activationKey, onValueChange = { activationKey = it.take(160) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("License key") })
                    Text("Activation requires the licensing API integration, which is not yet connected in this build.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showActivation = false; activationKey = "" }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(activationKey)) }) { Text("Copy key") } }
        )
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

