package com.vidgravity.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clipboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray

/** User-controlled staging area. Clipboard is read only after an explicit tap while this screen is open. */
class ClipboardInboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClipboardInboxScreen(onBack = { finish() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipboardInboxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vg_clipboard_inbox", Context.MODE_PRIVATE) }
    var urls by remember { mutableStateOf(loadUrls(prefs)) }
    var selected by remember { mutableStateOf(urls.toSet()) }
    var pasteText by remember { mutableStateOf("") }

    fun persist(next: List<String>) {
        urls = next.distinct().take(100)
        selected = selected.intersect(urls.toSet())
        prefs.edit().putString("urls", JSONArray(urls).toString()).apply()
    }
    fun importText(text: String) {
        val extracted = ShareReceiverActivity.extractUrlsFromText(text)
        if (extracted.isEmpty()) {
            Toast.makeText(context, "No supported public URLs found.", Toast.LENGTH_SHORT).show()
            return
        }
        val before = urls.size
        persist(urls + extracted)
        selected = selected + extracted
        val added = urls.size - before
        Toast.makeText(context, if (added > 0) "Added $added link(s) to Clipboard Inbox." else "These links are already in the inbox.", Toast.LENGTH_SHORT).show()
        pasteText = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clipboard Inbox") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Review links before downloading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Import the current clipboard or paste several links. Select only the URLs you want; unselected links stay in the inbox.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = clipboard.primaryClip?.let { clip ->
                        (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index).coerceToText(context)?.toString() }.joinToString("\n")
                    }.orEmpty()
                    importText(text)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) {
                Icon(Icons.Default.Clipboard, null); Spacer(Modifier.width(8.dp)); Text("Import current clipboard")
            }
            OutlinedTextField(
                value = pasteText, onValueChange = { pasteText = it.take(8192) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste one or more URLs") },
                minLines = 2, maxLines = 4
            )
            Button(onClick = { importText(pasteText) }, enabled = pasteText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add links to inbox") }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${urls.size} link(s)", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { selected = urls.toSet() }, enabled = urls.isNotEmpty()) { Text("Select all") }
                TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("Clear") }
            }
            if (urls.isEmpty()) {
                Text("Your inbox is empty. Import the current clipboard or paste links above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(urls, key = { it }) { url ->
                        Card {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = url in selected, onCheckedChange = { checked ->
                                    selected = if (checked) selected + url else selected - url
                                })
                                Text(url, modifier = Modifier.weight(1f), maxLines = 3, style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = { persist(urls - url) }) { Icon(Icons.Default.Delete, "Remove link") }
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val chosen = urls.filter { it in selected }
                        val preferences = SecureDownloadPreferences.load(context)
                        var queued = 0
                        chosen.forEach { url ->
                            runCatching {
                                DownloadWorker.enqueue(context, url, preferences.preferredFormatSelector,
                                    ShareReceiverActivity.friendlyTitle(url), preferences.unmeteredOnly)
                            }.onSuccess { queued++ }
                        }
                        val remaining = urls.filterNot { it in chosen }
                        persist(remaining)
                        selected = selected.intersect(remaining.toSet())
                        Toast.makeText(context, "Queued $queued of ${chosen.size} selected link(s). Unselected links remain in the inbox.", Toast.LENGTH_LONG).show()
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download Selected (${selected.size})")
                }
            }
        }
    }
}

private fun loadUrls(prefs: android.content.SharedPreferences): List<String> {
    val raw = prefs.getString("urls", null) ?: return emptyList()
    return runCatching {
        val json = JSONArray(raw)
        (0 until json.length()).mapNotNull { json.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList()).distinct().take(100)
}
