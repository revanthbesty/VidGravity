package com.vidgravity.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable internal fun PrivacyDataScreen(accounts:AccountService,onClose:()->Unit) {
    val context=LocalContext.current;val preferences=remember {PrivacyPreferences(context)}
    var choices by remember {mutableStateOf(preferences.load())}
    var storage by remember {mutableStateOf<StorageOverview?>(null)}
    var projects by remember {mutableStateOf<List<StudioProject>>(emptyList())}
    var recovery by remember {mutableStateOf<List<StudioProject>>(emptyList())}
    var unreadable by remember {mutableStateOf(0)}
    var pending by remember {mutableStateOf<StudioProject?>(null)}
    var message by remember {mutableStateOf("")};var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope();val repository=remember {StudioRepository(context)}
    suspend fun refresh() {recovery=withContext(Dispatchers.IO) {repository.recoverable()};withContext(Dispatchers.IO) {PrivacyStorage.overview(context) to repository.scan()}.let {(space,listing)->storage=space;projects=listing.projects;unreadable=listing.unreadableCount}}
    fun run(action:suspend ()->Unit) {scope.launch {busy=true;try {action();refresh()} catch(c:CancellationException){throw c} catch(e:Exception){message="Data action could not complete. Existing projects were preserved."} finally {busy=false}}}
    fun consent(value:PrivacyChoices) {run {withContext(Dispatchers.IO) {preferences.save(value,accounts)};choices=value;message="Privacy preferences saved."}}
    LaunchedEffect(Unit) {run {}}
    BackHandler(onBack=onClose)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("privacy_data"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick=onClose) {Text("Back to Settings")}
        Text("Privacy & Data",style=MaterialTheme.typography.headlineMedium)
        Text("Telemetry is off by default. Local editing never uploads your project. Optional account requests contact Firebase only in a configured build.")
        Row {Checkbox(choices.analytics,{consent(choices.copy(analytics=it))},enabled=accounts.available && !busy,modifier=Modifier.testTag("consent_analytics"));Text("Allow usage analytics")}
        Row {Checkbox(choices.crashes,{consent(choices.copy(crashes=it))},enabled=accounts.available && !busy,modifier=Modifier.testTag("consent_crashes"));Text("Allow crash reports")}
        if(!accounts.available) Text("Analytics and Crashlytics are absent from this APK. Consent is unavailable until an explicitly configured account build.")
        Text("Local storage",style=MaterialTheme.typography.titleLarge)
        storage?.let {Text("Project metadata: ${it.projectBytes} bytes\nPrivate app files: ${it.privateBytes} bytes\nDisposable disk thumbnails: ${it.cacheBytes} bytes\nAvailable app volume: ${it.availableBytes} bytes\nLibrary MediaStore files are separate and are not counted here.")}
        OutlinedButton(enabled=!busy,onClick={run {withContext(Dispatchers.IO) {PrivacyStorage.clearThumbnails(context)};message="In-memory and disposable thumbnails cleared. Projects, imports, active work and exports were kept."}}) {Text("Clear thumbnail cache")}
        Text("Projects",style=MaterialTheme.typography.titleLarge)
        Text("Move a project to recoverable trash in Studio. Originals and published exports remain untouched. No bulk delete or automatic cleanup.")
        if(unreadable>0) Text("$unreadable unreadable project file(s) were preserved. Do not clear application data; keep a backup for recovery.")
        recovery.forEach {p->OutlinedButton(enabled=!busy,onClick={run {withContext(Dispatchers.IO) {repository.recoverAsCopy(p.id)};message="Last good save recovered as a new project. Original files were preserved."}}) {Text("Recover last save: ${p.title}")}}
        projects.forEach {p->OutlinedButton(enabled=!busy,onClick={pending=p},modifier=Modifier.fillMaxWidth()) {Text("Move ${p.title} to trash")}}
        OutlinedButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}) {Text("Manage Android permissions")}
        Text("Cloud processing",style=MaterialTheme.typography.titleLarge)
        Text("Native Studio, saved captions, templates, local analysis and native export run on this device. Downloads/link inspection and legacy story, sticker or status cloud tools send the selected URL or media to the configured VidGravity API when you request them. They require internet; an account is not required. Optional speech dictation depends on the selected Android recognition provider and its availability. No automatic cloud backup is implemented.")
        Text("Account data controls are in Settings → Account. Signing out or deleting an account does not delete local media. Android app storage is protected by the OS sandbox; this is not end-to-end project encryption.")
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank()) Text(message,modifier=Modifier.testTag("privacy_message"))
    }
    pending?.let {p->AlertDialog(onDismissRequest={pending=null},title={Text("Move project to trash?")},text={Text("${p.title} can be restored from Studio trash. Its source media and exports stay unchanged.")},confirmButton={TextButton(enabled=!busy,onClick={pending=null;run {withContext(Dispatchers.IO) {repository.trash(p.id)};message="Project moved to recoverable trash."}}) {Text("Move to trash")}},dismissButton={TextButton(onClick={pending=null}) {Text("Cancel")}})}
}
