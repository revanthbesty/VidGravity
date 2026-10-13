package com.vidgravity.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Actions are callbacks so UI does not claim export/share succeeded before the service completes. */
@Composable
fun CompletedCard(
    title: String,
    subtitle: String,
    onTrimSticker: () -> Unit,
    onWhatsAppSplit: () -> Unit,
    onShareToStash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onTrimSticker, label = { Text("Remix Reply") }, modifier = Modifier.heightIn(min = 48.dp))
                AssistChip(onClick = onWhatsAppSplit, label = { Text("WhatsApp Split") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            Button(onClick = onShareToStash, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Share to Group Stash")
            }
        }
    }
}
