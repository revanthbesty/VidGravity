package com.vidgravity.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import java.net.InetAddress
import java.util.Locale
import java.util.regex.Pattern

class ShareReceiverActivity : FragmentActivity(), FormatPickerBottomSheet.Listener {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); DownloadNotifications.ensureChannel(this); handleShare(intent) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handled = false; handleShare(intent) }

    private fun handleShare(intent: Intent?) {
        if (handled) return
        handled = true
        val raw = if (intent?.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() else null
        val url = normalizeSharedUrl(raw)
        if (url == null) {
            Toast.makeText(this, "VidGravity could not find a supported public URL.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val title = friendlyTitle(url)
        val preferences = SecureDownloadPreferences.load(this)

        // Handle URLs shared from any Android app through the standard Sharesheet.
        // Queue immediately and return to the source app so the user can share
        // additional URLs without opening VidGravity or pasting links.
        runCatching {
            DownloadNotifications.ensureChannel(this)
            DownloadNotifications.analyzing(this, title)
            DownloadWorker.enqueue(
                this,
                url,
                preferences.preferredFormatSelector,
                title,
                preferences.unmeteredOnly
            )
        }.onSuccess {
            Toast.makeText(
                this,
                "Added to VidGravity queue. Share another link; downloads continue in the background.",
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(this, "Could not queue this link: ${it.message ?: "please try again"}", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onFormatChosen(url: String, option: FormatOption, remember: Boolean, title: String) {
        val existing = SecureDownloadPreferences.load(this)
        if (remember) SecureDownloadPreferences.save(this, existing.copy(
            autoDownloadWithPreferredPreset = true,
            preferredFormatSelector = option.selector
        ))
        val preferences = SecureDownloadPreferences.load(this)
        DownloadNotifications.analyzing(this, title)
        DownloadWorker.enqueue(this, url, option.selector, title, preferences.unmeteredOnly)
        Toast.makeText(this, "VidGravity download queued", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_OPEN_INBOX = "com.vidgravity.app.EXTRA_OPEN_INBOX"
        private const val MAX_SHARED_TEXT = 8192
        private val URL_PATTERN = Pattern.compile("(?i)(https?://[^\\s<>\\\"']+|(?:www\\.)?[a-z0-9][a-z0-9.-]+\\.[a-z]{2,}(?:/[^\\s<>\\\"']*)?)")
        private val TRAILING = Regex("[),.;!?\\]}]+$")

        fun normalizeSharedUrl(raw: String?): String? {
            val text = raw?.take(MAX_SHARED_TEXT)?.replace("\\u0000", " ")?.trim().orEmpty()
            if (text.isBlank()) return null
            val match = URL_PATTERN.matcher(text)
            if (!match.find()) return null
            var candidate = match.group(1).trim().replace(TRAILING, "")
            if (candidate.startsWith("www.", true) || !candidate.contains("://")) candidate = "https://" + candidate
            val uri = Uri.parse(candidate)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
            if (scheme !in setOf("http", "https") || host.isBlank() || uri.userInfo != null || candidate.length > 8192 || uri.port !in -1..65535) return null
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal") || host == "metadata.google.internal") return null
            if (host.matches(Regex("[0-9.]+")) || host.contains(":")) {
                val ip = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
                if (ip.isAnyLocalAddress || ip.isLoopbackAddress || ip.isLinkLocalAddress || ip.isSiteLocalAddress || ip.isMulticastAddress) return null
            }
            val builder = uri.buildUpon().scheme("https").fragment(null)
            if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be") {
                // Tracking/share decoration is not needed for extraction and can confuse some providers.
                builder.clearQuery()
                uri.queryParameterNames
                    .filterNot { it.lowercase(Locale.ROOT) in setOf("si", "feature") }
                    .forEach { key -> uri.getQueryParameters(key).forEach { value -> builder.appendQueryParameter(key, value) } }
            }
            return builder.build().toString().takeIf { it.length <= 8192 }
        }

        fun friendlyTitle(url: String): String = Uri.parse(url).host?.removePrefix("www.")?.plus(" shared media") ?: "shared media"
    }
}
