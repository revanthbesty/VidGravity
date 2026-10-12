package com.vidgravity.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workId = intent.getStringExtra(EXTRA_WORK_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val controls = context.getSharedPreferences(CONTROLS, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_PAUSE -> controls.edit().putBoolean("paused:" + workId, true).apply()
            ACTION_RESUME -> controls.edit().putBoolean("paused:" + workId, false).apply()
            ACTION_CANCEL -> {
                controls.edit().remove("paused:" + workId).apply()
                WorkManager.getInstance(context).cancelWorkById(workId)
            }
        }
    }
    companion object {
        const val EXTRA_WORK_ID = "vg_work_id"
        const val ACTION_PAUSE = "com.vidgravity.action.PAUSE"
        const val ACTION_RESUME = "com.vidgravity.action.RESUME"
        const val ACTION_CANCEL = "com.vidgravity.action.CANCEL"
        const val CONTROLS = "vg_download_controls"
        fun isPaused(context: Context, workId: UUID) = context.getSharedPreferences(CONTROLS, 0).getBoolean("paused:" + workId, false)
        fun clear(context: Context, workId: UUID) = context.getSharedPreferences(CONTROLS, 0).edit().remove("paused:" + workId).apply()
    }
}
