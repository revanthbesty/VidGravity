package com.vidgravity.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable internal fun DownloadSourcePanel(url:String,onUrl:(String)->Unit,inspect:()->Unit,notify:(String)->Unit,openInbox:()->Unit,onQueued:()->Unit,openStudy:()->Unit) {
    val context=LocalContext.current;val clipboard=LocalClipboardManager.current;val scope=rememberCoroutineScope();var busy by remember {mutableStateOf(false)}
    VgDisclosure("Add download links",initiallyOpen=true) {
        Text("Inspect a public link to choose available quality. Multiple links use your saved best-quality preference.")
        OutlinedTextField(url,onUrl,label={Text("Media URL or links, one per line")},maxLines=4,modifier=Modifier.fillMaxWidth())
        Row {TextButton(onClick={clipboard.getText()?.text?.let(onUrl)}) {Text("Paste link")};TextButton(onClick=openInbox) {Text("Share Inbox")}}
        Button(enabled=!busy,onClick={if(ShareReceiverActivity.normalizeSharedUrl(url.trim())!=null) inspect() else notify("Enter one complete http or https media URL to inspect.")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Inspect link")}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        VgDisclosure("Batch links and audio") {
            OutlinedButton(enabled=!busy,onClick={scope.launch {
                busy=true
                try {
                    val count=withContext(Dispatchers.IO) {
                        val candidates=Regex("https?://[^\\s<>\"']+",RegexOption.IGNORE_CASE).findAll(url).mapNotNull {ShareReceiverActivity.normalizeSharedUrl(it.value.trimEnd(',', ';', ')', ']', '.'))}.distinctBy {it.lowercase()}.take(11).toList()
                        require(candidates.isNotEmpty() && candidates.size<=10) {"Paste 1–10 complete public links."}
                        val prefs=SecureDownloadPreferences.load(context)
                        val successes=candidates.count {link->runCatching {DownloadWorker.enqueue(context,link,prefs.preferredFormatSelector,ShareReceiverActivity.friendlyTitle(link),prefs.unmeteredOnly)}.isSuccess}
                        successes to candidates.size
                    }
                    notify("${count.first} of ${count.second} links queued.");if(count.first>0) onQueued()
                } catch(c:CancellationException) {throw c} catch(e:Exception) {notify(e.message ?: "Links could not be queued.")}
                finally {busy=false}
            }}) {Text("Queue multiple links · best quality")}
            TextButton(enabled=!busy,onClick={scope.launch {busy=true
                try {withContext(Dispatchers.IO) {
                    require(ShareReceiverActivity.normalizeSharedUrl(url.trim())!=null) {"Paste one complete media URL."}
                    val result=DownloadApi.analyzeSource(url.trim());val audio=result.formats.firstOrNull {it.hasAudio && !it.hasVideo} ?: error("No audio-only stream is available.")
                    val prefs=SecureDownloadPreferences.load(context);DownloadWorker.enqueue(context,url.trim(),audio.selector,result.title+" — Audio",prefs.unmeteredOnly)
                };notify("Audio download queued.");onQueued()}
                catch(c:CancellationException) {throw c} catch(e:Exception) {notify(e.message ?: "Audio could not be prepared.")}
                finally {busy=false}
            }}) {Text("Queue audio only")}
            TextButton(onClick=openStudy) {Text("StudyTok tools")}
        }
    }
}
