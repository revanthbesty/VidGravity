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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        val openInbox = intent?.getBooleanExtra(ShareReceiverActivity.EXTRA_OPEN_INBOX, false) == true
        setContent { VidGravityApp(openInboxInitially = openInbox) }
    }
}

private enum class VgTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Default.Home), Downloads("Downloads", Icons.Default.Download),
    Library("Library", Icons.Default.VideoLibrary), Create("Create", Icons.Default.AutoAwesome),
    Settings("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VidGravityApp(openInboxInitially: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("vg-ui", 0) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var selected by remember { mutableStateOf(VgTab.Home) }
    var showInbox by remember { mutableStateOf(openInboxInitially) }
    var creatorInitialMedia by remember { mutableStateOf<LibraryMedia?>(null) }
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
                } else if (showInbox) {
                    ShareInboxContent(
                        onBack = { showInbox = false },
                        onInspect = { sharedUrl -> url = sharedUrl; showInbox = false; selected = VgTab.Home },
                        onOpen = { sharedUrl ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sharedUrl))) }
                                .onFailure { notice = "Could not open this link in another app." }
                        }
                    )
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
                    }, { notice = it }, { showInbox = true }, { selected = VgTab.Downloads }, { selected = VgTab.Create })
                    VgTab.Downloads -> DownloadsContent(onPlay = { playingMedia = it })
                    VgTab.Library -> LibraryContent(
                        onPlay = { playingMedia = it },
                        onCreateWithMedia = { media -> creatorInitialMedia = media; selected = VgTab.Create }
                    )
                    VgTab.Create -> CreateStudioContent(
                        onPlay = { playingMedia = it }, notify = { notice = it }, openInbox = { showInbox = true },
                        initialMedia = creatorInitialMedia
                    )
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
            val videoChoices = listOf(
                Triple("Best available", "bv*+ba/b", videoFormats.maxByOrNull { qualityHeight(it) } ?: usable.firstOrNull())
            ).filter { it.third != null } + standardVideo
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
                                            val safeContainer = option?.container?.uppercase()
                                                ?.takeIf { it.matches(Regex("[A-Z0-9]{2,5}")) } ?: "MEDIA"
                                            Text(if (choice.first == "Highest available") "Best compatible quality for this source"
                                                else listOfNotNull(safeContainer, fpsLabel, hdrLabel, sizeLabel).joinToString(" • "),
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, null)
                                    }
                                }
                            }
                            }
                            if (qualityTab == "Audio Only") {
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text("AUDIO ONLY", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                if (audioFormats.isEmpty()) {
                                    item {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                try {
                                                    val preferences = SecureDownloadPreferences.load(context)
                                                    DownloadWorker.enqueue(
                                                        context, url.trim(), "bestaudio/best",
                                                        result.title + " — Audio", preferences.unmeteredOnly, audioOnly = true
                                                    )
                                                    analysis = null
                                                    selected = VgTab.Downloads
                                                    notice = "Sound Only queued using the provider's best audio stream."
                                                } catch (failure: Exception) {
                                                    notice = "Could not queue Sound Only: " + (failure.message ?: "Please try again.")
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("Best available audio", fontWeight = FontWeight.SemiBold)
                                                    Text("Let yt-dlp select and convert the audio stream",
                                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Icon(Icons.Default.ChevronRight, null)
                                            }
                                        }
                                    }
                                }
                                items(audioFormats, key = { "audio:" + it.formatId }) { audio ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            try {
                                                val preferences = SecureDownloadPreferences.load(context)
                                                DownloadWorker.enqueue(context, url.trim(), audio.selector, result.title + " — Audio", preferences.unmeteredOnly, audioOnly = true)
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
private fun HomeContent(url: String, onUrl: (String) -> Unit, inspect: () -> Unit, notify: (String) -> Unit, openInbox: () -> Unit, onBatchQueued: () -> Unit, openCreate: () -> Unit) {
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
                        placeholder = { Text("Paste one link or multiple links (one per line)") }, leadingIcon = { Icon(Icons.Default.Link, null) },
                        trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.let(onUrl) }) { Icon(Icons.Default.ContentPaste, "Paste") } },
                        shape = RoundedCornerShape(16.dp), minLines = 1, maxLines = 4)
                    OutlinedButton(
                        onClick = {
                            val candidates = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
                                .findAll(url)
                                .mapNotNull { ShareReceiverActivity.normalizeSharedUrl(it.value.trimEnd(',', ';', ')', ']', '.')) }
                                .distinctBy { it.lowercase() }
                                .take(11)
                                .toList()
                            if (candidates.isEmpty()) {
                                notify("Paste at least one complete public URL. Put each link on a new line.")
                            } else if (candidates.size > 10) {
                                notify("Please queue up to 10 links at a time.")
                            } else {
                                var queued = 0
                                val preferences = SecureDownloadPreferences.load(context)
                                candidates.forEach { link ->
                                    runCatching {
                                        DownloadWorker.enqueue(context, link, preferences.preferredFormatSelector,
                                            ShareReceiverActivity.friendlyTitle(link), preferences.unmeteredOnly)
                                    }.onSuccess { queued++ }
                                }
                                if (queued > 0) {
                                    onBatchQueued()
                                    notify("${queued} of ${candidates.size} links added to Downloads using best available quality.")
                                } else notify("No links could be queued. Please check the URLs and try again.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Queue multiple links · best quality")
                    }
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
                            items(listOf("♫ Sound Only", "✂ Status Clip Splitter")) { action ->
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
                                                    // Some providers omit or mislabel the audio-only format metadata.
                                                    // Ask yt-dlp for its best audio stream as a reliable fallback.
                                                    val audio = result.formats.firstOrNull { it.hasAudio && !it.hasVideo }
                                                    val selector = audio?.selector?.takeIf { it.isNotBlank() } ?: "bestaudio/best"
                                                    val prefs = SecureDownloadPreferences.load(context)
                                                    DownloadWorker.enqueue(
                                                        context, url.trim(), selector, result.title + " — Audio",
                                                        prefs.unmeteredOnly, audioOnly = true
                                                    )
                                                    notify("Sound Only queued. Audio will be converted to M4A when supported; check Downloads for status.")
                                                } catch (failure: Exception) {
                                                    notify("Could not prepare audio: " + (failure.message ?: "Please try again."))
                                                }
                                            }
                                        } else if (action.contains("Status Clip")) {
                                            openCreate()
                                            notify("In Create Studio, choose a local video and select a 30, 60, 90, or 120-second clip target.")
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
    val clipboard = LocalClipboardManager.current
    var expandedErrors by remember { mutableStateOf(setOf<java.util.UUID>()) }
    var downloadFilter by remember { mutableStateOf("All") }
    var showDownloadFilters by remember { mutableStateOf(false) }
    var showDownloadSort by remember { mutableStateOf(false) }
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
        Text("Downloads", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Track active and completed transfers on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val sortedMatchedWorks = when (downloadSort) {
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
            // Keep active downloads pinned first regardless of the selected secondary sort.
            // Apply the selected sort inside each group so completed/failed history stays useful.
            val filteredWorks = sortedMatchedWorks.filter { work ->
                work.state == WorkInfo.State.RUNNING || work.state == WorkInfo.State.ENQUEUED || work.state == WorkInfo.State.BLOCKED
            } + sortedMatchedWorks.filterNot { work ->
                work.state == WorkInfo.State.RUNNING || work.state == WorkInfo.State.ENQUEUED || work.state == WorkInfo.State.BLOCKED
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
                                    DownloadWorker.enqueue(context, sourceUrl, selector, retryTitle, preferences.unmeteredOnly, work.outputData.getBoolean(DownloadWorker.RETRY_AUDIO_ONLY, work.progress.getBoolean(DownloadWorker.KEY_AUDIO_ONLY, false)))
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
                label = { Text("Search downloads") },
                placeholder = { Text("Search title, source, or error") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (downloadSearch.isNotBlank()) IconButton(onClick = { downloadSearch = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showDownloadFilters = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.FilterList, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Filter")
                    if (downloadFilter != "All") Text(" · $downloadFilter")
                }
                OutlinedButton(
                    onClick = { showDownloadSort = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Sort, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sort")
                    Text(" · $downloadSort")
                }
            }
            Text("${filteredWorks.size} downloads shown", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showDownloadFilters) AlertDialog(
                onDismissRequest = { showDownloadFilters = false },
                title = { Text("Filter downloads") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("All", "Active", "Completed", "Failed").forEach { value ->
                            Row(
                                Modifier.fillMaxWidth().clickable { downloadFilter = value },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = downloadFilter == value, onClick = { downloadFilter = value })
                                Text(value)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDownloadFilters = false }) { Text("Apply") } },
                dismissButton = { TextButton(onClick = { downloadFilter = "All"; showDownloadFilters = false }) { Text("Clear filter") } }
            )
            if (showDownloadSort) AlertDialog(
                onDismissRequest = { showDownloadSort = false },
                title = { Text("Sort downloads") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Newest", "Oldest", "Name A–Z", "Progress", "Status", "Failed first").forEach { value ->
                            Row(
                                Modifier.fillMaxWidth().clickable { downloadSort = value },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = downloadSort == value, onClick = { downloadSort = value })
                                Text(value)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDownloadSort = false }) { Text("Apply") } },
                dismissButton = { TextButton(onClick = { downloadSort = "Newest"; showDownloadSort = false }) { Text("Newest first") } }
            )
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
                                            DownloadWorker.enqueue(context, sourceUrl, selector ?: preferences.preferredFormatSelector, retryTitle, preferences.unmeteredOnly, work.outputData.getBoolean(DownloadWorker.RETRY_AUDIO_ONLY, work.progress.getBoolean(DownloadWorker.KEY_AUDIO_ONLY, false)))
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
    var searchQuery by remember { mutableStateOf("") }
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
private fun LibraryContent(onPlay: (LibraryMedia) -> Unit, onCreateWithMedia: (LibraryMedia) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    var media by remember { mutableStateOf(emptyList<LibraryMedia>()) }
    var filter by remember { mutableStateOf("All Media") }
    var sizeFilter by remember { mutableStateOf("Any size") }
    var sortBy by remember { mutableStateOf("Newest") }
    var showLibraryFilters by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LibraryMedia?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LibraryMedia?>(null) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var swipeFeedOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refreshMedia() { media = withContext(Dispatchers.IO) { MediaLibraryIndexer.list(context) } }
    LaunchedEffect(Unit) { refreshMedia() }
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
    val filteredMedia = media.filter { item ->
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
    val visibleMedia = when (sortBy) {
        "Oldest" -> filteredMedia.sortedBy { it.createdAtMs }
        "Name A-Z" -> filteredMedia.sortedBy { it.displayName.lowercase() }
        "Largest" -> filteredMedia.sortedByDescending { it.sizeBytes }
        "Smallest" -> filteredMedia.sortedBy { it.sizeBytes }
        "Type" -> filteredMedia.sortedWith(compareBy<LibraryMedia> { it.mimeType.substringBefore("/") }.thenBy { it.displayName.lowercase() })
        "Extension" -> filteredMedia.sortedWith(compareBy<LibraryMedia> { it.displayName.substringAfterLast('.', "").lowercase() }.thenBy { it.displayName.lowercase() })
        else -> filteredMedia.sortedByDescending { it.createdAtMs }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your library", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Downloaded media saved by VidGravity", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { swipeFeedOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Swipe Feed")
            }
        }
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
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search your media") },
            shape = RoundedCornerShape(16.dp)
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showLibraryFilters = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.FilterList, null); Spacer(Modifier.width(6.dp)); Text("Filters & sorting")
            }
            Text("${visibleMedia.size} shown", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                enabled = searchQuery.isNotBlank() || filter != "All Media" || sizeFilter != "Any size" || sortBy != "Newest",
                onClick = { searchQuery = ""; filter = "All Media"; sizeFilter = "Any size"; sortBy = "Newest" }
            ) { Text("Reset") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = filter == "All Media", onClick = { filter = "All Media" }, label = { Text("All") })
            FilterChip(selected = filter == "Video", onClick = { filter = "Video" }, label = { Text("Video") })
            FilterChip(selected = filter == "Audio", onClick = { filter = "Audio" }, label = { Text("Audio") })
            FilterChip(selected = filter == "Images", onClick = { filter = "Images" }, label = { Text("Images") })
        }
        if (showLibraryFilters) AlertDialog(
            onDismissRequest = { showLibraryFilters = false },
            title = { Text("Library filters") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Media type", fontWeight = FontWeight.SemiBold)
                    listOf("All Media", "Video", "Audio", "Images", "Other Files", "Favorites").forEach { value ->
                        Row(Modifier.fillMaxWidth().clickable { filter = value }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = filter == value, onClick = { filter = value }); Text(value)
                        }
                    }
                    Text("File size", fontWeight = FontWeight.SemiBold)
                    listOf("Any size", "Under 10 MB", "10–100 MB", "Over 100 MB", "Unknown size").forEach { value ->
                        Row(Modifier.fillMaxWidth().clickable { sizeFilter = value }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sizeFilter == value, onClick = { sizeFilter = value }); Text(value)
                        }
                    }
                    Text("Sort by", fontWeight = FontWeight.SemiBold)
                    listOf("Newest", "Oldest", "Name A-Z", "Largest", "Smallest", "Type", "Extension").forEach { value ->
                        Row(Modifier.fillMaxWidth().clickable { sortBy = value }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sortBy == value, onClick = { sortBy = value }); Text(value)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLibraryFilters = false }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { filter = "All Media"; sizeFilter = "Any size"; sortBy = "Newest"; searchQuery = ""; showLibraryFilters = false }) { Text("Reset all") } }
        )
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
                            Checkbox(
                                checked = item.uri.toString() in selectedUris,
                                onCheckedChange = { checked ->
                                    selectedUris = if (checked) selectedUris + item.uri.toString() else selectedUris - item.uri.toString()
                                }
                            )
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
private fun CreateStudioContent(onPlay: (LibraryMedia) -> Unit, notify: (String) -> Unit, openInbox: () -> Unit, initialMedia: LibraryMedia? = null) {
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
            exportedUri = null
            stickerUri = null
            notify("Media selected. Preview, share, or create a 9:16 story or animated sticker.")
        }
    }
    if (storyEditorOpen && selectedMedia != null) {
        StoryCardScreen(
            media = selectedMedia!!,
            onClose = { storyEditorOpen = false },
            isRendering = isRendering,
            onRenderStory = { source, startSeconds, endSeconds, title, artist, layout ->
                if (!isRendering) {
                    scope.launch {
                        isRendering = true
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
        OutlinedButton(onClick = openInbox, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Default.Inbox, null)
            Spacer(Modifier.width(8.dp))
            Text("Share Inbox · Watch / Learn Later")
        }
        Text("Create studio", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Choose local video, preview it, or create a share-ready 9:16 derivative. Originals remain unchanged.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Text("Start with your own media", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Select a video from this device. Rendering uses the configured VidGravity companion and never overwrites the selected source.",
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
                                context.startActivity(Intent.createChooser(intent, "Share original media"))
                            }.onFailure { notify("Could not open Android sharing: ${it.message ?: "try again"}") }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share original")
                        }
                    }
                    Button(onClick = { storyEditorOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Create 9:16 Story / Status")
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
                        enabled = !isRendering && segmentJob == null,
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
                                        for (index in 0 until count) {
                                            val startSeconds = index * clipTargetSeconds
                                            val clipSeconds = (durationSeconds - startSeconds).coerceAtMost(clipTargetSeconds)
                                            segmentProgress = "Rendering ${statusClipSeconds}s clip ${index + 1} of $count…"
                                            val cloudExport = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    DownloadApi.exportMedia(
                                                        context, media.uri, "whatsapp-status", "fit-letterbox",
                                                        startSeconds, clipSeconds
                                                    )
                                                }
                                            }
                                            if (cloudExport.isSuccess) {
                                                completed++
                                                if (index < count - 1) kotlinx.coroutines.delay(21_000L)
                                            } else {
                                                val cloudFailure = cloudExport.exceptionOrNull()
                                                segmentProgress = "Cloud export unavailable; switching to on-device splitting…"
                                                val localFiles = LocalStatusSplitter.split(
                                                    context = context,
                                                    sourceUri = media.uri,
                                                    splitSeconds = statusClipSeconds,
                                                    durationSeconds = durationSeconds,
                                                    startIndex = index,
                                                    onProgress = { current, total ->
                                                        segmentProgress = "Splitting locally: clip $current of $total…"
                                                    }
                                                )
                                                try {
                                                    localFiles.forEachIndexed { localIndex, file ->
                                                        withContext(Dispatchers.IO) {
                                                            MediaLibraryIndexer.publish(
                                                                context,
                                                                file,
                                                                "VidGravity_Status_${String.format(java.util.Locale.US, "%02d", index + localIndex + 1)}.mp4",
                                                                DownloadApi.sha256(file)
                                                            )
                                                        }
                                                        completed++
                                                    }
                                                } finally {
                                                    localFiles.forEach { it.delete() }
                                                }
                                                if (localFiles.isEmpty()) {
                                                    throw IllegalStateException(
                                                        "Cloud export failed and local splitting returned no clips: ${cloudFailure?.message ?: "retry the source"}"
                                                    )
                                                }
                                                break
                                            }
                                        }
                                        notify("Created $completed WhatsApp Status clips. Verified clips are saved in Movies/VidGravity.")
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
                        enabled = !isRendering && segmentJob == null,
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
                    exportedUri?.let {
                        Text("Latest story saved in Movies/VidGravity. Use the story share buttons to send the verified derivative.",
                            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
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
        Text("More creator tools", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        InfoCard("Animated stickers", "Next pipeline batch: render short source clips as animated WebP with size and duration limits.", Icons.Default.AutoAwesome, Modifier.fillMaxWidth())
        InfoCard("WhatsApp Status segments", "Next pipeline batch: create multiple bounded segments and verify every output before sharing.", Icons.Default.ContentCut, Modifier.fillMaxWidth())
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
    var savedPreferences by remember { mutableStateOf(SecureDownloadPreferences.load(context)) }
    var unmeteredOnly by remember { mutableStateOf(savedPreferences.unmeteredOnly) }
    var autoPreset by remember { mutableStateOf(savedPreferences.autoDownloadWithPreferredPreset) }
    val formatPresets = listOf(
        "Best" to "bv*+ba/b",
        "2160p" to "bestvideo[height<=2160]+bestaudio/best[height<=2160]",
        "1080p" to "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
        "720p" to "bestvideo[height<=720]+bestaudio/best[height<=720]",
        "Audio" to "bestaudio/best"
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        Text("Default download quality", fontWeight = FontWeight.SemiBold)
        Text("Used by bulk queueing and automatic share downloads. You can still choose a different format when inspecting a link.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(formatPresets) { preset ->
                FilterChip(
                    selected = savedPreferences.preferredFormatSelector == preset.second,
                    onClick = {
                        val updated = savedPreferences.copy(preferredFormatSelector = preset.second)
                        savedPreferences = updated
                        SecureDownloadPreferences.save(context, updated)
                    },
                    label = { Text(preset.first) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
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

