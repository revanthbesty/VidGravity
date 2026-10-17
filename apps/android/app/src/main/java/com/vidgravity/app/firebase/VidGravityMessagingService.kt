package com.vidgravity.app.firebase

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vidgravity.app.BuildConfig
import com.vidgravity.app.R

/** Data-only notifications. Never execute payload URLs, commands, or downloads. */
class VidGravityMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Deliberately no logging or automatic account association. A trusted sender backend
        // must register tokens with verified ID tokens and remove associations on sign-out.
    }
    override fun onMessageReceived(message: RemoteMessage) {
        if (!BuildConfig.FIREBASE_ENABLED || !getSharedPreferences("vg-firebase", Context.MODE_PRIVATE)
                .getBoolean("notifications", false)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        // Only a generic signal is accepted; remote text may contain private/untrusted content.
        if (message.data["type"] != "workspace_update") return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("workspace_updates", "Workspace updates",
            NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(6401, NotificationCompat.Builder(this, "workspace_updates")
            .setSmallIcon(R.drawable.ic_vidgravity_logo).setContentTitle("VidGravity")
            .setContentText("Your workspace has an update.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).build())
    }
}
