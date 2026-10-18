package com.vidgravity.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable internal fun CreatorSettings(dark:Boolean,onDark:(Boolean)->Unit,onHelp:()->Unit) {
    val context=LocalContext.current
    var privacy by remember {mutableStateOf(false)}
    var accountFailure by remember {mutableStateOf(false)}
    val accounts=remember {runCatching {AccountRuntime.get(context)}.getOrElse {accountFailure=true;GuestAccounts}}
    var download by remember {mutableStateOf(SecureDownloadPreferences.load(context))}
    val exportPrefs=remember {context.getSharedPreferences("vg-export",0)}
    var resolution by remember {mutableStateOf(exportPrefs.getString("resolution","HD"))}
    if(privacy && true) {PrivacyDataScreen(accounts) {privacy=false};return}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Settings",style=MaterialTheme.typography.headlineMedium)
        Text("Make VidGravity work your way.")
        VgDisclosure("Account",initiallyOpen=false) {AccountPanel(accounts)}
        OutlinedButton(onClick={privacy=true},enabled=true,modifier=Modifier.fillMaxWidth()) {Text("Privacy & Data")}
        if(accountFailure) Text("Optional account services failed to initialize. Reopen Settings to retry; local editing is unchanged.")
        VgDisclosure("Storage",initiallyOpen=false) {Text("Project files are app-private. Public captures/exports use Android MediaStore. Use Privacy & Data for a measured storage overview and safe thumbnail cleanup.");OutlinedButton(enabled=true,onClick={privacy=true}) {Text("Manage local data")}}
        VgDisclosure("Export Preferences",initiallyOpen=false) {
            Text("Default resolution for new imported-video projects. Existing projects retain their own encoding settings. Device codec support still applies.")
            StudioResolution.values().forEach {preset ->FilterChip(selected=resolution==preset.name,onClick={resolution=preset.name;exportPrefs.edit().putString("resolution",preset.name).apply()},label={Text(preset.label)})}
        }
        VgDisclosure("Downloads",initiallyOpen=false) {
            Text("Default download quality")
            listOf("Best" to "bv*+ba/b","2160p" to "bestvideo[height<=2160]+bestaudio/best[height<=2160]","1080p" to "bestvideo[height<=1080]+bestaudio/best[height<=1080]","720p" to "bestvideo[height<=720]+bestaudio/best[height<=720]","Audio" to "bestaudio/best").forEach {(name,format)->FilterChip(download.preferredFormatSelector==format,{download=download.copy(preferredFormatSelector=format);SecureDownloadPreferences.save(context,download)},label={Text(name)})}
            Row {Checkbox(download.unmeteredOnly,{download=download.copy(unmeteredOnly=it);SecureDownloadPreferences.save(context,download)});Text("Wi-Fi only")}
            Row {Checkbox(download.autoDownloadWithPreferredPreset,{download=download.copy(autoDownloadWithPreferredPreset=it);SecureDownloadPreferences.save(context,download)});Text("Automatic share downloads")}
        }
        VgDisclosure("Notifications",initiallyOpen=false) {Text("Android controls download/export notification channels and permission. Foreground progress remains required for active long-running work.");OutlinedButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}) {Text("Manage notifications")}}
        VgDisclosure("Appearance",initiallyOpen=true) {Row {Switch(dark,onDark);Text("Dark appearance")}}
        VgDisclosure("Accessibility",initiallyOpen=false) {Text("VidGravity follows Android font scaling. Open Android accessibility settings for magnification, screen reader and display controls.");OutlinedButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))}) {Text("Android accessibility settings")}}
        OutlinedButton(onClick=onHelp) {Text("Help & getting started")}
        VgDisclosure("About VidGravity",initiallyOpen=false) {Text("VidGravity ${BuildConfig.VERSION_NAME}\nAll current tools are free. No ads, subscriptions or payment SDK. Optional accounts do not unlock or restrict editing.")}
    }
}
