package com.vidgravity.app

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.*
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicit build opt-in, adapted from draft #64; no Firestore, Storage or Messaging access. */
object AccountRuntime {
    @Volatile private var service:AccountService?=null
    fun get(context:Context):AccountService=service ?: synchronized(this) {
        service ?: FirebaseAccounts(context.applicationContext).also {service=it}
    }
}
private suspend fun <T> Task<T>.awaitResult():T=suspendCancellableCoroutine { continuation ->
    addOnCompleteListener {task -> if(continuation.isActive) {
        if(task.isSuccessful) continuation.resume(task.result) else continuation.resumeWithException(task.exception ?: IllegalStateException("Account request failed"))
    }}
}
private class FirebaseAccounts(private val context:Context):AccountService {
    init {checkNotNull(FirebaseApp.initializeApp(context))}
    private val auth=FirebaseAuth.getInstance()
    override val available=true
    override val profile=MutableStateFlow<AccountProfile?>(null)
    init {
        auth.addAuthStateListener { profile.value=it.currentUser?.let {user -> AccountProfile(user.email.orEmpty(),user.displayName.orEmpty())} }
        val prefs=PrivacyPreferences(context).load();consent(prefs.analytics,prefs.crashes)
    }
    override suspend fun signIn(email:String,password:String,register:Boolean) {
        require(email.trim().length in 3..254 && password.length in 6..4096)
        if(register) auth.createUserWithEmailAndPassword(email.trim(),password).awaitResult()
        else auth.signInWithEmailAndPassword(email.trim(),password).awaitResult()
    }
    override suspend fun resetPassword(email:String) {auth.sendPasswordResetEmail(email.trim()).awaitResult()}
    override suspend fun saveProfile(name:String) {
        require(name.isNotBlank() && name.length<=80)
        val user=checkNotNull(auth.currentUser)
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).awaitResult()
        profile.value=AccountProfile(user.email.orEmpty(),user.displayName.orEmpty())
    }
    override suspend fun deleteAccount(password:String) {
        val user=checkNotNull(auth.currentUser)
        user.reauthenticate(EmailAuthProvider.getCredential(checkNotNull(user.email),password)).awaitResult()
        user.delete().awaitResult();profile.value=null
        // This build never uploads projects, Firestore profiles or Storage files.
    }
    override fun signOut() {auth.signOut();profile.value=null}
    override fun consent(analytics:Boolean,crashes:Boolean) {
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(analytics)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(crashes)
        if(!analytics) FirebaseAnalytics.getInstance(context).resetAnalyticsData()
        if(!crashes) FirebaseCrashlytics.getInstance().deleteUnsentReports()
    }
}
