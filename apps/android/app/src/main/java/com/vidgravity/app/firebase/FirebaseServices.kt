package com.vidgravity.app.firebase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.AuthResult
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.vidgravity.app.BuildConfig

/** Opt-in SDK boundary. No backend license, payment, or download authorization is changed. */
class FirebaseServices(context: Context) {
    private val context = context.applicationContext
    init { check(BuildConfig.FIREBASE_ENABLED) { "Firebase is disabled in this build" } }
    private val auth = FirebaseAuth.getInstance()
    private val db get() = database
    companion object {
        private val database by lazy {
            FirebaseFirestore.getInstance().apply {
                // Avoid persistent disk caching of private account data.
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            }
        }
    }
    private val storage by lazy { FirebaseStorage.getInstance() }

    fun signIn(email: String, password: String): Task<AuthResult> =
        auth.signInWithEmailAndPassword(email.trim(), password)
    fun register(email: String, password: String): Task<AuthResult> =
        auth.createUserWithEmailAndPassword(email.trim(), password)
    fun sendVerification(): Task<Void> = checkNotNull(auth.currentUser).sendEmailVerification()
    fun resetPassword(email: String): Task<Void> = auth.sendPasswordResetEmail(email.trim())
    fun signOut() { auth.signOut() }

    private fun uid(): String = checkNotNull(auth.currentUser) { "Sign in first" }.uid
    fun saveProfile(displayName: String): Task<Void> {
        require(FirebasePolicy.validDisplayName(displayName))
        return db.collection("users").document(uid()).set(mapOf(
            "displayName" to displayName, "updatedAt" to FieldValue.serverTimestamp()
        ))
    }
    fun loadProfile() = db.collection("users").document(uid()).get()
    fun uploadImage(id: String, bytes: ByteArray, contentType: String) = run {
        require(FirebasePolicy.validId(id))
        require(FirebasePolicy.validUpload(bytes.size.toLong(), contentType))
        storage.reference.child("users/${uid()}/images/$id").putBytes(bytes,
            StorageMetadata.Builder().setContentType(contentType).build())
    }
    // No download URLs: bearer download links would bypass private authenticated reads.
    fun downloadImage(id: String): Task<ByteArray> {
        require(FirebasePolicy.validId(id))
        return storage.reference.child("users/${uid()}/images/$id").getBytes(FirebasePolicy.MAX_UPLOAD_BYTES)
    }

    /** Call only after an explicit user choice; these SDK choices persist across restarts. */
    fun setTelemetryConsent(analytics: Boolean, crashReports: Boolean) {
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(analytics)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(crashReports)
        if (!analytics) FirebaseAnalytics.getInstance(context).resetAnalyticsData()
        if (!crashReports) FirebaseCrashlytics.getInstance().deleteUnsentReports()
    }

    /** Caller requests Android 13+ permission in an Activity before invoking this API. */
    fun enableNotifications(): Task<String> {
        check(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        context.getSharedPreferences("vg-firebase", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications", true).apply()
        return FirebaseMessaging.getInstance().token
    }
    fun disableNotifications(): Task<Void> {
        context.getSharedPreferences("vg-firebase", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications", false).apply()
        FirebaseMessaging.getInstance().isAutoInitEnabled = false
        return FirebaseMessaging.getInstance().deleteToken()
    }
}
