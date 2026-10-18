package com.vidgravity.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun StudioProjectSummary(onOpenStudio: () -> Unit) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf<List<StudioProject>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try { projects = withContext(Dispatchers.IO) { StudioRepository(context).list() } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { failed = true }
    }
    Card(Modifier.fillMaxWidth().animateContentSize(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR CREATIVE WORKSPACE", style = MaterialTheme.typography.labelMedium)
            Text("Create without uploading", style = MaterialTheme.typography.titleLarge)
            when {
                failed -> Text("Open Studio to recover your saved projects.")
                projects == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                projects!!.isEmpty() -> Text("Trim, arrange, add sound and export on your device.")
                else -> Text("${projects!!.size} saved project(s) · Latest: ${projects!!.first().title}")
            }
            Text("Social Creator · brand kit · multi-format exports",style=MaterialTheme.typography.labelMedium)
            FilledTonalButton(onClick = onOpenStudio) { Text("Open Studio") }
        }
    }
}
