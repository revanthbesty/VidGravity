package com.vidgravity.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Snap Studio capture surface. Media is written to app-private cache only; unedited captures
 * are never published to MediaStore. A successful capture is handed back as a content URI.
 */
class CameraCaptureActivity : ComponentActivity() {
    private lateinit var controller: LifecycleCameraController
    private lateinit var preview: PreviewView
    private lateinit var hint: TextView
    private lateinit var progress: ProgressBar
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var recording: Recording? = null
    private var recordingStartedAt = 0L
    private var recordingFile: File? = null
    private var isFront = false
    private var finishingCapture = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.CAMERA] == true && grants[Manifest.permission.RECORD_AUDIO] == true) buildCameraUi()
        else {
            Toast.makeText(this, "Camera and microphone permissions are required to capture media.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    private val timer = object : Runnable {
        override fun run() {
            val active = recording != null
            if (!active) return
            val elapsed = System.currentTimeMillis() - recordingStartedAt
            progress.progress = elapsed.coerceAtMost(60_000L).toInt()
            hint.text = "Recording · ${(elapsed / 1000).coerceAtMost(60)}s / 60s"
            if (elapsed >= 60_000L) stopRecording() else handler.postDelayed(this, 80L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasPermissions()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            return
        }
        buildCameraUi()
    }

    private fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun buildCameraUi() {
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        controller = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            isTapToFocusEnabled = true
            isPinchToZoomEnabled = true
        }
        preview.controller = controller
        controller.bindToLifecycle(this)

        val root = FrameLayout(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        hint = TextView(this).apply {
            text = "Tap to capture · Hold to record · Double tap to flip"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(28, 18, 28, 18)
            setBackgroundColor(0x99000000.toInt())
        }
        val hintParams = FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP)
        root.addView(hint, hintParams)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 60_000
            progress = 0
            visibility = View.GONE
        }
        root.addView(progress, FrameLayout.LayoutParams(-1, 8, android.view.Gravity.TOP).apply { topMargin = 74 })
        setContentView(root)

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { capturePhoto(); return true }
            override fun onLongPress(e: MotionEvent) { startRecording() }
            override fun onDoubleTap(e: MotionEvent): Boolean { flipCamera(); return true }
        })
        preview.setOnTouchListener { _, event ->
            gestures.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (recording != null) stopRecording()
            }
            true
        }
    }

    private fun capturePhoto() {
        if (recording != null || finishingCapture) return
        val file = File(cacheDir, "snap-${System.currentTimeMillis()}.jpg")
        val options = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(file).build()
        controller.takePicture(options, executor, object : ImageCaptureCallback() {
            override fun saved() = handoff(file, "image/jpeg")
            override fun failed(message: String) = toast(message)
        })
        hint.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun startRecording() {
        if (recording != null || finishingCapture) return
        val file = File(cacheDir, "snap-${System.currentTimeMillis()}.mp4")
        recordingFile = file
        val output = FileOutputOptions.Builder(file).build()
        recording = controller.startRecording(output, executor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> runOnUiThread { hint.text = "Recording · 0s / 60s" }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    handler.removeCallbacks(timer)
                    runOnUiThread {
                        progress.visibility = View.GONE
                        if (!event.hasError() && file.exists() && file.length() > 0L) handoff(file, "video/mp4")
                        else hint.text = "Recording failed. Tap to try again."
                    }
                }
                is VideoRecordEvent.Status -> Unit
                is VideoRecordEvent.Pause -> Unit
                is VideoRecordEvent.Resume -> Unit
            }
        }
        recordingStartedAt = System.currentTimeMillis()
        progress.visibility = View.VISIBLE
        progress.progress = 0
        hint.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        handler.post(timer)
    }

    private fun stopRecording() {
        handler.removeCallbacks(timer)
        val active = recording ?: return
        recording = null
        active.stop()
        progress.visibility = View.GONE
        hint.text = "Finalizing video…"
        // VideoRecordEvent.Finalize owns the handoff; do not expose a file before muxing completes.
    }

    private fun flipCamera() {
        if (recording != null) return
        isFront = !isFront
        controller.cameraSelector = if (isFront) androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA else androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
        hint.text = if (isFront) "Front camera" else "Back camera"
        hint.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun handoff(file: File, mime: String) {
        if (finishingCapture || !file.exists() || file.length() == 0L) return
        finishingCapture = true
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        setResult(RESULT_OK, Intent().setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        finish()
    }

    private fun toast(message: String) = runOnUiThread {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timer)
        recording?.stop()
        recording = null
        executor.shutdown()
        super.onDestroy()
    }

    private abstract class ImageCaptureCallback : androidx.camera.core.ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) = saved()
        override fun onError(exception: androidx.camera.core.ImageCaptureException) = failed(exception.message ?: "Photo capture failed")
        abstract fun saved()
        abstract fun failed(message: String)
    }
}
