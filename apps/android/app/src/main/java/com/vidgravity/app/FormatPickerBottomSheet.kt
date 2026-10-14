package com.vidgravity.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FormatPickerBottomSheet : BottomSheetDialogFragment() {
    interface Listener { fun onFormatChosen(url: String, option: FormatOption, remember: Boolean, title: String) }
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var remember: SwitchMaterial
    private var sharedUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUrl = arguments?.getString(ARG_URL).orEmpty()
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(resolveColor(android.R.attr.colorBackground, 0xff18181b.toInt()))
        }
        root.addView(TextView(context).apply {
            text = "Choose download quality"
            textSize = 20f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface, 0xfffafafa.toInt()))
        })
        root.addView(TextView(context).apply { text = sharedUrl; textSize = 12f; maxLines = 2 },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        status = TextView(context).apply { text = "Analyzing available formats…"; textSize = 13f }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        progress = ProgressBar(context)
        root.addView(progress, LinearLayout.LayoutParams(-2, dp(40)).apply { gravity = Gravity.CENTER })
        remember = SwitchMaterial(context).apply { text = "Always use this quality for automatic downloads" }
        root.addView(remember)
        root.post { analyze() }
        return ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun analyze() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { DownloadApi.analyzeSource(sharedUrl) }
                progress.visibility = View.GONE
                val choices = deduplicateFormats(result.formats)
                status.text = result.title + " · " + choices.size + " quality options"
                if (choices.isEmpty()) {
                    addChoice(FormatOption("bv*+ba/b", "best", "Best available", "mp4", "unknown", "unknown", null), result.title)
                } else choices.forEach { addChoice(it, result.title) }
            } catch (failure: Exception) {
                progress.visibility = View.GONE
                status.text = "Could not analyze provider: " + (failure.message ?: "").take(160)
                addChoice(FormatOption("bv*+ba/b", "best", "Best available", "mp4", "unknown", "unknown", null),
                    ShareReceiverActivity.friendlyTitle(sharedUrl))
            }
        }
    }

    private fun deduplicateFormats(formats: List<FormatOption>): List<FormatOption> {
        fun container(option: FormatOption) = option.container.trim().lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "mp4"
        fun key(option: FormatOption): String {
            val quality = option.height.takeIf { it > 0 }?.let { it.toString() + "p" }
                ?: option.resolution.lowercase().replace(Regex("[^a-z0-9]"), "")
            val fps = option.fps?.toInt()?.takeIf { it > 0 } ?: 0
            val hdr = option.dynamicRange.contains("hdr", true) ||
                option.dynamicRange.contains("hlg", true) || option.dynamicRange.contains("pq", true)
            return listOf(quality, container(option), option.hasVideo, option.hasAudio, fps, hdr).joinToString("|")
        }
        fun score(option: FormatOption): Int {
            var value = 0
            if (option.hasVideo && option.hasAudio) value += 100
            if (container(option) == "mp4") value += 30
            if ((option.sizeBytes ?: 0L) > 0L) value += 10
            if (option.videoCodec.isNotBlank() && option.videoCodec != "none" && option.videoCodec != "unknown") value += 2
            return value
        }
        return formats.asSequence()
            .filter { it.selector.isNotBlank() && (it.hasVideo || it.hasAudio) }
            .groupBy(::key)
            .values
            .mapNotNull { group -> group.maxWithOrNull(compareBy<FormatOption> { score(it) }.thenBy { it.sizeBytes ?: 0L }) }
            .sortedWith(compareByDescending<FormatOption> { it.height }
                .thenByDescending { it.fps ?: 0.0 }
                .thenByDescending { score(it) })
    }

    private fun addChoice(option: FormatOption, title: String) {
        val quality = option.height.takeIf { it > 0 }?.let { it.toString() + "p" } ?: option.resolution
        val ext = option.container.trim().uppercase().takeIf { it.matches(Regex("[A-Z0-9]{2,5}")) } ?: "MEDIA"
        val kind = when {
            option.hasVideo && option.hasAudio -> "Video + audio"
            option.hasVideo -> "Video (audio combined on download)"
            else -> "Audio only"
        }
        val details = buildList {
            add(ext)
            add(kind)
            option.sizeBytes?.takeIf { it > 0L }?.let { add(String.format("%.1f MB", it / 1_000_000.0)) }
            option.fps?.toInt()?.takeIf { it > 0 }?.let { add(it.toString() + " FPS") }
            if (option.dynamicRange.contains("hdr", true) || option.dynamicRange.contains("hlg", true) ||
                option.dynamicRange.contains("pq", true)) add("HDR")
        }.joinToString(" · ")
        root.addView(Button(requireContext()).apply {
            text = quality + " · " + details
            isAllCaps = false
            setOnClickListener {
                (activity as? Listener)?.onFormatChosen(sharedUrl, option, remember.isChecked, title)
                dismissAllowingStateLoss()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }

    private fun resolveColor(attribute: Int, fallback: Int): Int {
        val a = requireContext().obtainStyledAttributes(intArrayOf(attribute))
        return try { a.getColor(0, fallback) } finally { a.recycle() }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_URL = "url"
        fun newInstance(url: String) = FormatPickerBottomSheet().apply { arguments = Bundle().apply { putString(ARG_URL, url) } }
    }
}
