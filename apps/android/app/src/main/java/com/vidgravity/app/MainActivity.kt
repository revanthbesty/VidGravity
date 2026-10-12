package com.vidgravity.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VidGravity", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
                                catch (failure: Exception) { notice = "Could not inspect link: " + (failure.message ?: "Unknown error").take(180) }
                                finally { inspecting = false }
                            }
                        }
                    }, { notice = it })
                    VgTab.Downloads -> DownloadsContent()
                    VgTab.Library -> LibraryContent(onPlay = { playingMedia = it })
                    VgTab.Create -> SimpleCollectionScreen("Create studio", "Tools for your next post", Icons.Default.AutoAwesome,
                        listOf("WhatsApp Status · 30 seconds", "Extract audio", "Prepare a shareable clip"))
                    VgTab.Settings -> SettingsContent(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() })
                }
            }
        }
        if (inspecting) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Inspecting media") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("Checking available formats…") } })
        analysis?.let { result ->
            AlertDialog(onDismissRequest = { analysis = null },
                title = { Text(result.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(result.formats.size.toString() + " formats available")
                        if (result.formats.isEmpty()) Text("No selectable formats were returned.")
                        result.formats.take(14).forEach { option ->
                            OutlinedButton(onClick = {
                                val downloadPrefs = SecureDownloadPreferences.load(context)
                                DownloadWorker.enqueue(context, url.trim(), option.selector, result.title, downloadPrefs.unmeteredOnly)
                                analysis = null
                                selected = VgTab.Downloads
                                notice = "Download queued: " + result.title
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(option.displayName(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (result.formats.isEmpty()) Button(onClick = {
                            val downloadPrefs = SecureDownloadPreferences.load(context)
                            DownloadWorker.enqueue(context, url.trim(), "bv*+ba/b", result.title, downloadPrefs.unmeteredOnly)
                            analysis = null; selected = VgTab.Downloads; notice = "Best available download queued."
                        }) { Text("Queue best available") }
                    }
                },
                confirmButton = { TextButton(onClick = { analysis = null }) { Text("Close") } })
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
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Everything you want to keep.", fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                    Text("Paste a public media link to inspect formats and start a download.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    listOf("⚡ Best Video", "♫ Sound Only (MP3)", "✂ WhatsApp 30s Status", "✦ StudyTok AI").forEach { action ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (url.isBlank()) notify("Paste a media URL first, then choose " + action + ".")
                                else inspect()
                            }) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(action, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ChevronRight, null)
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
private fun DownloadsContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
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
                    val title = work.outputData.getString("filename") ?: work.progress.getString("title") ?: "Media download"
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(work.state.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(progress.toString() + "%", modifier = Modifier.weight(1f), fontSize = 12.sp)
                                if (work.state == WorkInfo.State.RUNNING || work.state == WorkInfo.State.ENQUEUED) {
                                    TextButton(onClick = { WorkManager.getInstance(context).cancelWorkById(work.id) }) { Text("Cancel") }
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
    LaunchedEffect(Unit) { media = withContext(Dispatchers.IO) { MediaLibraryIndexer.list(context) } }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your library", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Downloaded media saved by VidGravity", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (media.isEmpty()) {
            InfoCard("Your library is empty", "Completed downloads will appear here. Inspect a public link from Home to get started.", Icons.Default.VideoLibrary, Modifier.fillMaxWidth())
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(media, key = { it.uri.toString() }) { item ->
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
                            Icon(Icons.Default.PlayCircle, "Play", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
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
        SettingRow(Icons.Default.Wifi, "Network preference", "Wi-Fi-only setting is stored securely")
        Text("ACCOUNT & LICENSE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        SettingRow(Icons.Default.VerifiedUser, "License activation", "License activation UI is a future step")
        SettingRow(Icons.Default.Storage, "Storage", "Downloaded files stay on your device")
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
