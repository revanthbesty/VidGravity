package com.vidgravity.app.firebase

import android.app.Application
import com.google.firebase.FirebaseApp
import com.vidgravity.app.BuildConfig

class VidGravityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_ENABLED) {
            checkNotNull(FirebaseApp.initializeApp(this)) { "Firebase configuration is missing" }
        }
    }
}
