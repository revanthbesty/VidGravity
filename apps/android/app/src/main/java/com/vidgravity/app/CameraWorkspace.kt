package com.vidgravity.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Stable navigation destination; cancelling the capture Activity returns here. */
@Composable internal fun CameraWorkspace(onOpen:()->Unit,onLibrary:()->Unit) {
    Column(Modifier.fillMaxSize().testTag("camera_workspace").verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Capture your next story",style=MaterialTheme.typography.headlineLarge)
        Text("Record, review and create",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
        VgSection("Your camera. Your Library.","Photos and videos stay on your device. Completed captures appear in Library, ready to preview or open in Studio.") {
            Button(onClick=onOpen,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("camera_open")) {
                Icon(Icons.Default.PhotoCamera,null);Spacer(Modifier.width(8.dp));Text("Open camera")
            }
            OutlinedButton(onClick=onLibrary,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Browse captures in Library")}
        }
        VgSection("Before you record") {
            Text("Camera permission is required. Microphone permission is optional; without it, videos are silent.")
            Text("Switch Photo / Video inside the camera. Resolution, flash and dual preview depend on the available cameras.")
            Text("Stop recording and wait for Saved to Library before leaving. Leaving during recording stops it; check Library for the completed file.")
        }
    }
}
