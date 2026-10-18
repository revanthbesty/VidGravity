package com.vidgravity.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable internal fun AccountPanel(accounts:AccountService) {
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    val done=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()})
    val profile by accounts.profile.collectAsState()
    var email by remember {mutableStateOf("")};var password by remember {mutableStateOf("")}
    var name by remember(profile) {mutableStateOf(profile?.displayName.orEmpty())}
    var status by remember {mutableStateOf("")};var busy by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun request(success:String,action:suspend ()->Unit) {scope.launch {
        busy=true;status=""
        try {action();status=success} catch(c:CancellationException) {throw c} catch(e:Exception) {status=accountError(e)}
        finally {password="";busy=false}
    }}
    Text(if(profile==null) "Guest mode" else "Your account",modifier=Modifier.testTag("account_status"))
    Text("Editing, camera, local Creative tools and export never require an account. Projects stay on this installation when you sign out or delete an account.")
    if(!accounts.available) {
        Text("Accounts are not configured in this APK. No Firebase connection or telemetry is initialized.")
    } else if(profile==null) {
        OutlinedTextField(email,{email=it.take(254)},label={Text("Email")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it.take(4096)},label={Text("Password")},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=done,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy && email.isNotBlank() && password.length>=6,onClick={request("Signed in.") {accounts.signIn(email,password)}}) {Text("Sign in")}
        OutlinedButton(enabled=!busy && email.isNotBlank() && password.length>=6,onClick={request("Account created.") {accounts.signIn(email,password,true)}}) {Text("Create account")}
        TextButton(enabled=!busy && email.isNotBlank(),onClick={request("If eligible, password reset instructions have been requested. Check your inbox.") {accounts.resetPassword(email)}}) {Text("Reset password")}
    } else {
        Text(profile!!.email)
        OutlinedTextField(name,{name=it.take(80)},label={Text("Display name")},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=done,modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy && name.isNotBlank(),onClick={request("Profile saved.") {accounts.saveProfile(name)}}) {Text("Save profile")}
        OutlinedButton(enabled=!busy,onClick={accounts.signOut();email="";password="";name="";status="Signed out. Local projects remain available."}) {Text("Sign out")}
        TextButton(enabled=!busy,onClick={password="";status="";deleting=true}) {Text("Delete account")}
    }
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(status.isNotBlank() && !deleting) Text(status,modifier=Modifier.testTag("account_message"))
    if(deleting) AlertDialog(onDismissRequest={if(!busy){deleting=false;password=""}},title={Text("Delete Firebase account?")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("This permanently deletes the sign-in identity and Auth profile. This build does not upload projects or cloud files. Local projects and exports are kept. Re-enter your password to confirm.")
        OutlinedTextField(password,{password=it.take(4096)},label={Text("Confirm password")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=done,visualTransformation=PasswordVisualTransformation(),singleLine=true)
        if(status.isNotBlank()) Text(status,modifier=Modifier.testTag("account_delete_error"))
    }},confirmButton={TextButton(enabled=!busy && password.isNotEmpty(),onClick={request("Account deleted. Guest mode is active.") {accounts.deleteAccount(password);deleting=false}}) {Text("Confirm account deletion")}},dismissButton={TextButton(enabled=!busy,onClick={deleting=false;password=""}) {Text("Cancel")}})
}
