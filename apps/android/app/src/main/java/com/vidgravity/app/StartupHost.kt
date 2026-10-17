package com.vidgravity.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** Native recovery UI stays above Compose until commit, layout and draw all occur.
 * This is a startup watchdog, not an exception boundary for asynchronous Compose errors.
 * A blocked main thread or a broken system renderer also prevents native UI from drawing.
 */
internal class StartupHost(private val activity: ComponentActivity) : FrameLayout(activity) {
    companion object { const val TAG = "VGStartup" }
    private val started = SystemClock.elapsedRealtime()
    private val gate = StartupGate()
    private val compose = ComposeView(activity)
    private val message = TextView(activity)
    private val recovery = LinearLayout(activity)
    private var completed = false
    private var loggedLayout = false
    private var loggedCommit = false
    private val timeout = Runnable { fail("first-draw.timeout") }
    private val drawListener = ViewTreeObserver.OnDrawListener {
        if (gate.drawn()) {
            milestone("compose.draw.dispatched")
            // Never alter the view hierarchy while dispatching a draw.
            post {
                if (!completed) {
                    completed = true
                    removeCallbacks(timeout)
                    recovery.visibility = View.GONE
                }
            }
        }
    }

    init {
        setBackgroundColor(Color.rgb(20, 26, 43))
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        recovery.orientation = LinearLayout.VERTICAL
        recovery.gravity = Gravity.CENTER
        recovery.setPadding(48, 48, 48, 48)
        recovery.setBackgroundColor(Color.rgb(20, 26, 43))
        message.text = "VidGravity\nStarting your workspace…"
        message.setTextColor(Color.WHITE)
        message.textSize = 22f
        message.gravity = Gravity.CENTER
        recovery.addView(message)
        recovery.addView(Button(activity).apply {
            text = "Retry startup"
            setOnClickListener { activity.recreate() }
        })
        recovery.addView(Button(activity).apply {
            text = "App settings"
            setOnClickListener {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")))
            }
        })
        addView(recovery, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun start(content: @Composable () -> Unit) {
        milestone("native.recovery.installed hardwareAccelerated=$isHardwareAccelerated")
        compose.viewTreeObserver.addOnDrawListener(drawListener)
        postDelayed(timeout, 10_000L)
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                    gate.laidOut(coordinates.size.width, coordinates.size.height)
                    if (!loggedLayout && coordinates.size.width > 0 && coordinates.size.height > 0) {
                        loggedLayout = true
                        milestone("compose.layout ${coordinates.size.width}x${coordinates.size.height}")
                    }
                }) {
                    content()
                    SideEffect {
                        gate.committed()
                        if (!loggedCommit) {
                            loggedCommit = true
                            milestone("compose.committed")
                        }
                    }
                }
            }
            milestone("compose.content.installed")
        } catch (failure: RuntimeException) {
            // setContent may fail synchronously; later composition errors still use Android's crash reporting.
            fail("compose.install.failed type=${failure.javaClass.simpleName}")
        }
    }

    private fun fail(reason: String) {
        if (completed) return
        completed = true
        gate.fail()
        removeCallbacks(timeout)
        Log.e(TAG, "$reason elapsedMs=${SystemClock.elapsedRealtime() - started}")
        compose.visibility = View.GONE
        compose.disposeComposition()
        message.text = "VidGravity couldn't open the workspace.\nRetry startup or open app settings."
        recovery.visibility = View.VISIBLE
    }

    private fun milestone(event: String) {
        Log.i(TAG, "$event elapsedMs=${SystemClock.elapsedRealtime() - started}")
    }

    fun close() {
        completed = true
        gate.fail()
        removeCallbacks(timeout)
        if (compose.viewTreeObserver.isAlive) compose.viewTreeObserver.removeOnDrawListener(drawListener)
        compose.disposeComposition()
    }
}
