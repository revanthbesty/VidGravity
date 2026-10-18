package com.vidgravity.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable internal fun HomeDashboard(onCreate:()->Unit,onResume:(String)->Unit,onCamera:()->Unit,onImport:()->Unit,onTemplate:(String)->Unit,
    onLibrary:()->Unit,onDownloads:()->Unit,onPlay:(LibraryMedia)->Unit,onHelp:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var projects by remember {mutableStateOf(emptyList<StudioProject>())};var exports by remember {mutableStateOf(emptyList<LibraryMedia>())}
    var loading by remember {mutableStateOf(true)};var error by remember {mutableStateOf<String?>(null)}
    var welcome by remember {mutableStateOf(false)}
    suspend fun refresh() {
        try {val data=withContext(Dispatchers.IO) {
            val repository=StudioRepository(context);val p=repository.list();val uris=p.mapNotNull {repository.record(it.id)?.uri}.toSet()
            Triple(p.take(5),MediaLibraryIndexer.list(context).filter {it.uri.toString() in uris}.take(6),context.getSharedPreferences("vg-ui",0).getBoolean("onboarding-complete",false))
        };projects=data.first;exports=data.second;welcome=!data.third;error=null}
        catch(c:CancellationException) {throw c} catch(e:Exception) {error="Recent work could not load. Open Studio or Library to recover it."}
        finally {loading=false}
    }
    LaunchedEffect(Unit) {refresh()}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {val observer=LifecycleEventObserver {_,e ->if(e==Lifecycle.Event.ON_RESUME) scope.launch {refresh()}};lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}}
    fun dismiss() {welcome=false;scope.launch(Dispatchers.IO) {context.getSharedPreferences("vg-ui",0).edit().putBoolean("onboarding-complete",true).apply()}}
    LazyColumn(Modifier.fillMaxSize().testTag("home_dashboard"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {VgSection("Make your next story","Create and edit on your device. Your source files stay yours.") {
            Button(onClick=onCreate,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("home_create")) {Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Create New Video")}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onCamera,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Icon(Icons.Default.PhotoCamera,null);Text("Quick Camera")}
                OutlinedButton(onClick=onImport,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Icon(Icons.Default.FileOpen,null);Text("Import Media")}
            }
        }}
        if(loading) item {LinearProgressIndicator(Modifier.fillMaxWidth().testTag("home_loading"))}
        error?.let {item {Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={scope.launch {refresh()}}) {Text("Retry recent work")}}}
        if(welcome) item {VgSection("Welcome to VidGravity","Create, Record or Import. Trim in Studio, then export a separate file to Library. You can start now and return to the guide anytime.") {
            Row {TextButton(onClick={dismiss();onHelp()}) {Text("Show me the basics")};TextButton(onClick=::dismiss,modifier=Modifier.testTag("skip_onboarding")) {Text("Skip onboarding")}}
        }}
        item {Text("Continue Editing",style=MaterialTheme.typography.titleLarge)}
        if(!loading && projects.isEmpty()) item {VgSection("Your first project starts here","Tap Create New Video to import footage. Saved projects will appear here, ready to reopen.") {}}
        items(projects,key={it.id}) {p -> OutlinedCard(Modifier.fillMaxWidth().clickable {onResume(p.id)}) {Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            val c=p.clips.first();MediaThumbnail(LibraryMedia(android.net.Uri.parse(c.uri),c.name,0,"video/mp4",p.updatedAt),Modifier.size(72.dp))
            Column(Modifier.weight(1f)) {Text(p.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);Text("${p.clips.size} clips · ${p.durationMs/1000}s",style=MaterialTheme.typography.bodySmall);TextButton(onClick={onResume(p.id)}) {Text("Continue ${p.title.take(35)}")}}
        }}}
        item {VgSection("Template picks","Original editable layouts from Social Creator; no live popularity ranking.") {
            listOf("short" to "Short video","explain" to "Educational explainer","promo" to "Product promotion").forEach {(id,label)->TextButton(onClick={onTemplate(id)},modifier=Modifier.heightIn(min=48.dp)) {Text(label)}}
        }}
        item {Text("Recent Exports",style=MaterialTheme.typography.titleLarge)}
        if(exports.isEmpty()) item {Text("Export a Studio project to see its finished file here.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        else item {LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {items(exports,key={it.uri.toString()}) {m->OutlinedCard(Modifier.width(176.dp).clickable {onPlay(m)}) {Column(Modifier.padding(10.dp)) {MediaThumbnail(m,Modifier.fillMaxWidth().height(100.dp));Text(m.displayName,maxLines=2,overflow=TextOverflow.Ellipsis)}}}}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {TextButton(onClick=onLibrary) {Text("Open Library")};TextButton(onClick=onDownloads) {Text("Download links")};TextButton(onClick=onHelp) {Text("Help")}}}
    }
}
