package com.vidgravity.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun CreatorHelp(onBack:()->Unit,onCreate:()->Unit,onRecord:()->Unit,onImport:()->Unit) {
    BackHandler(onBack=onBack)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {TextButton(onClick=onBack) {Text("Back")};Text("Creator guide",style=MaterialTheme.typography.headlineLarge)}
        item {VgSection("1 · Start your story","Use your own video or record a new one. Photos and audio can be previewed/shared; photo-to-video timelines remain unsupported.") {
            Button(onClick=onCreate,modifier=Modifier.fillMaxWidth()) {Text("Create New Video")}
            OutlinedButton(onClick=onRecord) {Text("Record with Camera")};TextButton(onClick=onImport) {Text("Import Media")}
        }}
        item {VgSection("2 · Edit in Studio","Select a clip on the timeline. Split, trim or rearrange it, then open a contextual tool panel for color, sound or captions.") {Text("Undo/redo preserve editing history while the editor stays open. Original media is never overwritten.")}}
        item {VgSection("3 · Save and export","Projects autosave on this device. Tab navigation saves first; missing media can be relinked. Export creates a separate MP4 in Library.") {Text("Changing an edit invalidates the previous share action. Render again before sharing. Source access can be revoked, and uninstall removes local projects.")}}
        item {VgSection("4 · Creative tools, without an account","Social Creator provides templates, branding and queued formats. Creative Studio includes smart cuts, writing suggestions, captions and covers.") {Text("Writing and signal analysis are rule-based. Optional face framing and microphone dictation depend on device capabilities. Imported-video transcription is not installed.")}}
        item {VgSection("Troubleshooting","If a source moved, use Relink selected video. For encoder/storage failures, free space or use a smaller preset. Retry or cancel jobs from their queue.") {Text("Downloads require supported reachable public sources. Pause/resume support varies by provider. Camera permissions are requested only when used. Accessibility font size and system animator-duration settings are respected by Compose controls.")}}
    }
}
