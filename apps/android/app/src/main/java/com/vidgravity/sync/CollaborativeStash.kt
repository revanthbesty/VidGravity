package com.vidgravity.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class StashItem(val id: String, val url: String, val title: String, val addedAt: Long = System.currentTimeMillis())

/** URL parser for explicit user-shared links. It does not scrape private chat threads. */
object CollaborativeStash {
    private val allowedHosts = setOf(
        "youtube.com", "www.youtube.com", "youtu.be", "m.youtube.com",
        "instagram.com", "www.instagram.com", "tiktok.com", "www.tiktok.com",
        "vimeo.com", "www.vimeo.com", "soundcloud.com", "www.soundcloud.com"
    )
    fun parseSharedUrls(text: String): List<StashItem> {
        if (text.length > 20_000) return emptyList()
        val pattern = Regex("""https?://[^\s<>"']{1,2048}""", RegexOption.IGNORE_CASE)
        return pattern.findAll(text).mapNotNull { match ->
            val raw = match.value.trimEnd('.', ',', ';', ')', ']', '!')
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@mapNotNull null
            val host = uri.host?.lowercase() ?: return@mapNotNull null
            if (uri.scheme != "https" || host !in allowedHosts || uri.userInfo != null) return@mapNotNull null
            StashItem(UUID.randomUUID().toString(), uri.toString(), host)
        }.distinctBy { it.url }.take(50).toList()
    }
    fun createInviteLink(stashId: String, inviteToken: String): String {
        require(stashId.matches(Regex("^[A-Za-z0-9_-]{8,80}$"))) { "Invalid stash ID" }
        require(inviteToken.matches(Regex("^[A-Za-z0-9_-]{16,160}$"))) { "Invalid invite token" }
        return "https://vidgravity.onrender.com/stash/join?stash=" +
            URLEncoder.encode(stashId, StandardCharsets.UTF_8.name()) + "&invite=" +
            URLEncoder.encode(inviteToken, StandardCharsets.UTF_8.name())
    }
    fun enqueueUnmeteredTransfer(context: Context, url: String) {
        val parsed = parseSharedUrls(url).singleOrNull() ?: throw IllegalArgumentException("Unsupported or unsafe media URL")
        // A real Worker must be registered by the app; this request deliberately waits for Wi-Fi.
        val request = OneTimeWorkRequestBuilder<StashTransferWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .addTag("stash:${parsed.id}")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
    fun shareInvite(context: Context, inviteLink: String) {
        val uri = Uri.parse(inviteLink)
        require(uri.scheme == "https" && uri.host == "vidgravity.onrender.com")
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, inviteLink)
        }, "Invite to Group Stash"))
    }
}

/** Placeholder worker prevents silently pretending downloads are implemented. */
class StashTransferWorker(appContext: Context, params: androidx.work.WorkerParameters) :
    androidx.work.Worker(appContext, params) {
    override fun doWork(): Result {
        // URL storage, authorization, deduplication and media-download worker must be connected.
        return Result.failure()
    }
}
