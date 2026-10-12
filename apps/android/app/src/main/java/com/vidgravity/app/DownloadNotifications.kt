package com.vidgravity.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import java.util.UUID

object DownloadNotifications {
    private const val CHANNEL = "vg-downloads"
    private const val BASE_ID = 1740

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "VidGravity downloads", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Download progress, actions, and completion"
                }
            )
        }
    }

    fun analyzing(context: Context, title: String) {
        ensureChannel(context)
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                BASE_ID,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("VidGravity")
                    .setContentText("Analyzing and downloading " + title + "…")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .build()
            )
        } catch (_: SecurityException) {}
    }

    fun foreground(context: Context, workId: UUID, title: String, message: String, percent: Int, paused: Boolean): ForegroundInfo {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VidGravity · " + title)
            .setContentText(if (paused) "Paused" else message.take(160))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(
                context, workId.hashCode(), Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        builder.addAction(android.R.drawable.ic_media_pause, if (paused) "Resume" else "Pause",
            actionIntent(context, workId, if (paused) DownloadActionReceiver.ACTION_RESUME else DownloadActionReceiver.ACTION_PAUSE))
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel",
            actionIntent(context, workId, DownloadActionReceiver.ACTION_CANCEL))
        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(BASE_ID + (workId.hashCode() and 0x3ff), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(BASE_ID + (workId.hashCode() and 0x3ff), notification)
    }

    fun terminal(context: Context, title: String, message: String, success: Boolean) {
        ensureChannel(context)
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                (System.nanoTime() and 0x7fffffff).toInt(),
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                    .setContentTitle(if (success) "VidGravity download complete" else "VidGravity download stopped")
                    .setContentText(title + " · " + message.take(120))
                    .setAutoCancel(true)
                    .build()
            )
        } catch (_: SecurityException) {}
    }

    private fun actionIntent(context: Context, workId: UUID, action: String): PendingIntent {
        val requestCode = workId.hashCode() xor action.hashCode()
        val intent = Intent(context, DownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(DownloadActionReceiver.EXTRA_WORK_ID, workId.toString())
        return PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
