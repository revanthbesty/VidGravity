package com.vidgravity.app.firebase

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.google.firebase.auth.FirebaseAuth
import com.vidgravity.app.BuildConfig

@Composable
fun FirebaseAccountPanel() {
    if (!BuildConfig.FIREBASE_ENABLED) return
    val context = LocalContext.current
    val services = remember { FirebaseServices(context) }
    val prefs = remember { context.getSharedPreferences("vg-firebase", 0) }
    var signedIn by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var analytics by remember { mutableStateOf(prefs.getBoolean("analytics", false)) }
    var crashes by remember { mutableStateOf(prefs.getBoolean("crashes", false)) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { signedIn = it.currentUser != null }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }
    fun complete(task: com.google.android.gms.tasks.Task<*>) {
        busy = true
        task.addOnCompleteListener {
            busy = false
            password = ""
            // Do not expose raw provider exceptions, credentials, tokens or account existence.
            status = if (it.isSuccessful) "Request completed." else "Request failed. Check your details and connection."
        }
    }
    fun consent() {
        services.setTelemetryConsent(analytics, crashes)
        prefs.edit().putBoolean("analytics", analytics).putBoolean("crashes", crashes).apply()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) complete(services.enableNotifications())
        else status = "Notification permission was not granted."
    }
    Text("FIREBASE ACCOUNT")
    if (!signedIn) {
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text("Password") },
            singleLine = true, visualTransformation = PasswordVisualTransformation())
        Row {
            Button(enabled = !busy && email.isNotBlank() && password.isNotEmpty(), onClick = {
                complete(services.signIn(email, password))
            }) { Text("Sign in") }
            TextButton(enabled = !busy && email.isNotBlank() && password.isNotEmpty(), onClick = {
                complete(services.register(email, password))
            }) { Text("Create account") }
        }
        TextButton(enabled = !busy && email.isNotBlank(), onClick = {
            complete(services.resetPassword(email))
        }) { Text("Reset password") }
    } else {
        Text("Signed in to Firebase")
        OutlinedTextField(name, { name = it }, label = { Text("Private display name") }, singleLine = true)
        Button(enabled = !busy && FirebasePolicy.validDisplayName(name), onClick = {
            complete(services.saveProfile(name))
        }) { Text("Save private profile") }
        TextButton(enabled = !busy, onClick = { complete(services.sendVerification()) }) { Text("Send verification email") }
        TextButton(enabled = !busy, onClick = { services.signOut(); name = ""; status = "Signed out." }) { Text("Sign out") }
    }
    Row { Checkbox(analytics, { analytics = it; consent() }); Text("Allow usage analytics") }
    Row { Checkbox(crashes, { crashes = it; consent() }); Text("Allow crash reports") }
    TextButton(enabled = !busy, onClick = {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else complete(services.enableNotifications())
    }) { Text("Enable workspace notifications") }
    TextButton(enabled = !busy, onClick = { complete(services.disableNotifications()) }) { Text("Disable workspace notifications") }
    if (status.isNotEmpty()) Text(status)
}
