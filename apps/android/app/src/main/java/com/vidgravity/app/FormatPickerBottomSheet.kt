package com.vidgravity.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
        return root
    }

    private fun analyze() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { DownloadApi.analyzeSource(sharedUrl) }
                progress.visibility = View.GONE
                status.text = result.title + " · " + result.formats.size + " formats"
                if (result.formats.isEmpty()) {
                    addChoice(FormatOption("bv*+ba/b", "best", "Best available", "mp4", "unknown", "unknown", null), result.title)
                } else result.formats.take(32).forEach { addChoice(it, result.title) }
            } catch (failure: Exception) {
                progress.visibility = View.GONE
                status.text = "Could not analyze provider: " + (failure.message ?: "").take(160)
                addChoice(FormatOption("bv*+ba/b", "best", "Best available", "mp4", "unknown", "unknown", null),
                    ShareReceiverActivity.friendlyTitle(sharedUrl))
            }
        }
    }

    private fun addChoice(option: FormatOption, title: String) {
        root.addView(Button(requireContext()).apply {
            text = option.displayName()
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
