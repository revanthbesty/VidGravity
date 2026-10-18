package com.vidgravity.camera

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vidgravity.app.MediaLibraryIndexer
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Camera capture UI with visible controls. Concurrent front/rear preview is enabled only
 * when CameraX reports support. Dual mode uses rear Preview + ImageCapture and front Preview;
 * video recording switches to a supported single-camera session before starting.
 */
class DualCameraCaptureActivity : ComponentActivity() {
    private lateinit var container: FrameLayout
    private lateinit var rearPreview: PreviewView
    private lateinit var frontPreview: PreviewView
    private lateinit var pauseRecording: Button
    private lateinit var zoom: SeekBar
    private lateinit var zoomLabel: TextView
    private var recordingPaused = false
    private var photoTimer: Runnable? = null
    private lateinit var useCapture: Button
    private lateinit var status: TextView
    private lateinit var shutter: Button
    private lateinit var modeButton: Button
    private lateinit var dualButton: Button
    private lateinit var flashButton: Button
    private lateinit var executor: ExecutorService

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var dualSupported = false
    private var dualMode = false
    private var videoMode = false
    private var frontFacing = false
    private var flashEnabled = false
    private var videoQuality = Quality.FHD
    private var timerSeconds = 0
    private var pendingPhoto = false

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) startCameras()
        else finishWithMessage("Camera permission is required to capture media.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = Executors.newSingleThreadExecutor()
        buildCameraUi()
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameras()
        } else {
            AlertDialog.Builder(this).setTitle("Create with your camera")
                .setMessage("Camera access is needed to capture photos and videos. Microphone access is optional for recorded sound. Captures are saved to Library and can open in Studio.")
                .setPositiveButton("Continue") {_,_->permissionRequest.launch(permissions)}
                .setNegativeButton("Not now") {_,_->finish()}.show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildCameraUi() {
        container = FrameLayout(this).apply { setBackgroundColor(0xFF08080B.toInt()) }
        rearPreview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        frontPreview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            clipToOutline = true
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        container.addView(rearPreview, FrameLayout.LayoutParams(-1, -1))
        val pip = FrameLayout.LayoutParams(dp(118), dp(164), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(76)
            marginEnd = dp(16)
        }
        container.addView(frontPreview, pip)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(0xAA101521.toInt())
        }
        val close = controlButton("✕") { finish() }.apply {contentDescription="Close camera"}
        flashButton = controlButton("Flash off") { toggleFlash() }
        val flip = controlButton("Flip") { toggleFacing() }
        dualButton = controlButton("Dual off") { toggleDualMode() }
        val settings = controlButton("Settings") { showCameraSettings() }
        listOf(close, flashButton, flip, dualButton, settings).forEach { button ->
            top.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(4)
            })
        }
        container.addView(top, FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(0xCC101521.toInt())
        }
        status = TextView(this).apply {
            text = "Preparing camera…"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }
        bottom.addView(status, LinearLayout.LayoutParams(-1, -2))
        zoomLabel = TextView(this).apply { text = "Zoom 1.0×"; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER }
        bottom.addView(zoomLabel)
        zoom = SeekBar(this).apply {
            max = 1000
            contentDescription = "Camera zoom"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val state = camera?.cameraInfo?.zoomState?.value ?: return
                    val ratio = state.minZoomRatio + (state.maxZoomRatio - state.minZoomRatio) * progress / 1000f
                    camera?.cameraControl?.setZoomRatio(ratio)
                }
                override fun onStartTrackingTouch(bar: SeekBar) { }
                override fun onStopTrackingTouch(bar: SeekBar) { }
            })
        }
        bottom.addView(zoom, LinearLayout.LayoutParams(-1, dp(48)))
        pauseRecording = controlButton("Pause recording") {
            runCatching {
                if (recordingPaused) recording?.resume() else recording?.pause()
                recordingPaused = !recordingPaused
                pauseRecording.text = if (recordingPaused) "Resume recording" else "Pause recording"
            }.onFailure { Toast.makeText(this, "Recording could not change state.", Toast.LENGTH_SHORT).show() }
        }.apply { isEnabled = false }
        bottom.addView(pauseRecording, LinearLayout.LayoutParams(-1, dp(48)))
        useCapture = controlButton("Use capture in Studio") { finish() }.apply { isEnabled = false }
        bottom.addView(useCapture, LinearLayout.LayoutParams(-1, dp(48)))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeButton = controlButton("PHOTO  |  VIDEO") { toggleMode() }
        shutter = controlButton("●  Capture") { onShutterPressed() }.apply {
            isEnabled=false
            contentDescription="Capture photo or start and stop video recording"
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF7657D8.toInt()))
        }
        controls.addView(modeButton, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(12) })
        controls.addView(shutter, LinearLayout.LayoutParams(0, dp(58), 1.3f))
        bottom.addView(controls, LinearLayout.LayoutParams(-1, -2))
        val hint = TextView(this).apply {
            text = "Tap Capture for a photo. In Video mode, tap to start/stop recording."
            setTextColor(0xFFCBC7D5.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        bottom.addView(hint, LinearLayout.LayoutParams(-1, -2))
        container.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(container)
    }

    private fun controlButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xCC293449.toInt()))
            setOnClickListener { action() }
        }

    private fun startCameras() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                provider = future.get()
                dualSupported = provider?.availableConcurrentCameraInfos?.any { infos ->
                    val facings = infos.mapNotNull { info -> runCatching { info.lensFacing }.getOrNull() }.toSet()
                    CameraSelector.LENS_FACING_BACK in facings && CameraSelector.LENS_FACING_FRONT in facings
                } == true
                dualButton.isEnabled = dualSupported
                dualButton.alpha = if (dualSupported) 1f else 0.45f
                bindCameraSession()
            } catch (error: Exception) {
                finishWithMessage("Could not initialize the camera: ${error.message ?: "unknown error"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraSession() {
        val cameraProvider = provider ?: return
        val selector = if (frontFacing) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        camera?.cameraInfo?.zoomState?.removeObservers(this)
        cameraProvider.unbindAll()
        camera = null
        imageCapture = null
        videoCapture = null
        flashEnabled = false
        updateFlashLabel()

        val rearUseCase = Preview.Builder().build().also { it.setSurfaceProvider(rearPreview.surfaceProvider) }
        if (dualMode && !frontFacing && dualSupported) {
            val frontUseCase = Preview.Builder().build().also { it.setSurfaceProvider(frontPreview.surfaceProvider) }
            val stillCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                val concurrent = cameraProvider.bindToLifecycle(listOf(
                    ConcurrentCamera.SingleCameraConfig(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        androidx.camera.core.UseCaseGroup.Builder()
                            .addUseCase(rearUseCase)
                            .addUseCase(stillCapture)
                            .build(),
                        this
                    ),
                    ConcurrentCamera.SingleCameraConfig(
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        androidx.camera.core.UseCaseGroup.Builder().addUseCase(frontUseCase).build(),
                        this
                    )
                ))
                imageCapture = stillCapture
                dualMode = true
                dualButton.text = "Dual on"
                frontPreview.visibility = View.VISIBLE
                status.text = "Dual-camera preview · Photo mode"
                flashButton.isEnabled = false
                flashButton.text = "Flash n/a"
                shutter.isEnabled=true
                shutter.text = "●  Capture"
                zoom.isEnabled = false
                zoomLabel.text = "Zoom unavailable in dual preview"
                return
            } catch (error: Exception) {
                runCatching { cameraProvider.unbindAll() }
                dualMode = false
                dualButton.text = "Dual off"
                frontPreview.visibility = View.GONE
                Toast.makeText(this, "Dual camera could not start on this device. Using single camera.", Toast.LENGTH_LONG).show()
            }
        }

        dualButton.text = "Dual off"
        frontPreview.visibility = View.GONE
        val stillCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(videoQuality)).build()
        val video = VideoCapture.withOutput(recorder)
        try {
            camera = cameraProvider.bindToLifecycle(this, selector, rearUseCase, stillCapture, video)
            imageCapture = stillCapture
            videoCapture = video
        } catch (error: Exception) {
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, selector, rearUseCase, stillCapture)
                imageCapture = stillCapture
                videoCapture = null
                Toast.makeText(this, "Photo mode is available; video capture is not supported in this camera configuration.", Toast.LENGTH_LONG).show()
            } catch (fallback: Exception) {
                finishWithMessage("Could not start camera preview: ${fallback.message ?: "unsupported camera configuration"}")
                return
            }
        }
        camera?.cameraInfo?.zoomState?.observe(this) { state ->
            zoom.isEnabled = state.maxZoomRatio > state.minZoomRatio
            zoomLabel.text = String.format(java.util.Locale.US, "Zoom %.1f×", state.zoomRatio)
            zoom.progress = if (zoom.isEnabled) (((state.zoomRatio - state.minZoomRatio) /
                (state.maxZoomRatio - state.minZoomRatio)) * 1000).toInt() else 0
        }
        camera?.cameraControl?.enableTorch(false)
        updateFlashLabel()
        status.text = if (frontFacing) "Front camera · ${if (videoMode) "Video" else "Photo"} mode"
            else "Rear camera · ${if (videoMode) "Video" else "Photo"} mode"
        shutter.isEnabled=true
        shutter.text = if (videoMode) "●  Record" else "●  Capture"
    }

    private fun toggleMode() {
        if (recording != null) {
            Toast.makeText(this, "Stop the current recording before changing mode.", Toast.LENGTH_SHORT).show()
            return
        }
        videoMode = !videoMode
        modeButton.text = if (videoMode) "VIDEO  |  PHOTO" else "PHOTO  |  VIDEO"
        if (videoMode && dualMode) {
            dualMode = false
            bindCameraSession()
            Toast.makeText(this, "Switched to single-camera video recording.", Toast.LENGTH_SHORT).show()
        } else {
            bindCameraSession()
        }
    }

    private fun onShutterPressed() {
        if (videoMode) toggleRecording() else takePhoto()
    }

    private fun takePhoto() {
        if (pendingPhoto) return
        val capture = imageCapture
        if (capture == null) {
            Toast.makeText(this, "Photo capture is not available in this camera mode.", Toast.LENGTH_LONG).show()
            return
        }
        pendingPhoto = true
        shutter.isEnabled = false
        useCapture.isEnabled = false
        val captureNow = {
            val fileName = "vidgravity-${System.currentTimeMillis()}.jpg"
            // Publish photos to the same MediaStore folder the library indexes.
            // Preserve app-specific storage on Android 8–9.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/VidGravity/")
                }
                ImageCapture.OutputFileOptions.Builder(
                    contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ).build()
            } else {
                val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(filesDir, "media")
                if (!directory.exists()) directory.mkdirs()
                ImageCapture.OutputFileOptions.Builder(
                    File(directory, fileName)
                ).build()
            }
            capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) = runOnUiThread {
                    pendingPhoto = false; shutter.isEnabled = true
                    completedCapture(result.savedUri,
                        File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(filesDir, "media"), fileName),
                        fileName, "image/jpeg")
                    status.text = "Photo saved · ${fileName}"
                    Toast.makeText(this@DualCameraCaptureActivity, "Photo saved to VidGravity: ${fileName}", Toast.LENGTH_LONG).show()
                }
                override fun onError(exception: ImageCaptureException) = runOnUiThread {
                    pendingPhoto = false; shutter.isEnabled = true
                    Toast.makeText(this@DualCameraCaptureActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
        if (timerSeconds > 0) {
            status.text = "Photo timer · ${timerSeconds}s"
            photoTimer = Runnable { captureNow() }
            shutter.postDelayed(photoTimer!!, timerSeconds * 1000L)
        } else captureNow()
    }

    private fun toggleRecording() {
        if (recording != null) {
            recording?.stop()
            shutter.isEnabled = false
            shutter.text = "Saving…"
            return
        }
        if (dualMode) {
            dualMode = false
            bindCameraSession()
        }
        val capture = videoCapture
        if (capture == null) {
            Toast.makeText(this, "Video capture is unavailable for this camera. Try another camera or resolution.", Toast.LENGTH_LONG).show()
            return
        }
        val fileName = "vidgravity-${System.currentTimeMillis()}.mp4"
        // Android 10+: publish finished recordings in Movies/VidGravity so the
        // MediaStore-backed library can discover them on its next refresh.
        // Older devices retain the existing app-specific file fallback.
        // CameraX overloads are typed by output destination; prepare each branch
        // separately instead of passing a common OutputOptions supertype.
        var pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/VidGravity/")
            }
            val options = MediaStoreOutputOptions.Builder(
                contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(values).build()
            capture.output.prepareRecording(this, options)
        } else {
            val directory = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(filesDir, "media")
            if (!directory.exists()) directory.mkdirs()
            val options = FileOutputOptions.Builder(File(directory, fileName)).build()
            capture.output.prepareRecording(this, options)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        shutter.isEnabled = true
        useCapture.isEnabled = false
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordingPaused = false; pauseRecording.isEnabled = true; pauseRecording.text = "Pause recording"
                    shutter.text = "■  Stop"
                    status.text = "Recording video…"
                }
                is VideoRecordEvent.Status -> {
                    val seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                    status.text = if (recordingPaused) "Paused · ${seconds}s" else "Recording · ${seconds}s"
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    pauseRecording.isEnabled = false; recordingPaused = false
                    shutter.isEnabled = true
                    shutter.text = "●  Record"
                    if (event.hasError()) {
                        status.text = "Recording failed"
                        Toast.makeText(this, "Recording failed. Please retry.", Toast.LENGTH_LONG).show()
                    } else {
                        completedCapture(event.outputResults.outputUri.takeIf { it != Uri.EMPTY },
                            File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(filesDir, "media"), fileName),
                            fileName, "video/mp4")
                        status.text = "Video saved · ${fileName}"
                        Toast.makeText(this, "Video saved to VidGravity: ${fileName}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun completedCapture(savedUri: Uri?, file: File, name: String, mime: String) {
        lifecycleScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val readable = savedUri ?: MediaLibraryIndexer.localUri(this@DualCameraCaptureActivity, file)
                    val size = contentResolver.openAssetFileDescriptor(readable, "r")?.use { it.length } ?: 0L
                    MediaLibraryIndexer.register(this@DualCameraCaptureActivity, readable, name, mime, size)
                    readable
                }
                setResult(RESULT_OK, Intent().setDataAndType(uri, mime).apply {
                    putExtra(Intent.EXTRA_TITLE, name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                useCapture.isEnabled = true
                status.text = "Saved to Library · Ready for Studio"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) {
                status.text = "Capture saved. Library indexing could not finish; reopen Library to retry."
            }
        }
    }

    private fun toggleFacing() {
        if (recording != null) {
            Toast.makeText(this, "Stop recording before switching cameras.", Toast.LENGTH_SHORT).show()
            return
        }
        if (dualMode) {
            dualMode = false
            Toast.makeText(this, "Dual mode disabled to switch the main camera.", Toast.LENGTH_SHORT).show()
        }
        frontFacing = !frontFacing
        bindCameraSession()
    }

    private fun toggleDualMode() {
        if (!dualSupported) {
            Toast.makeText(this, "This device does not report front/rear concurrent camera support.", Toast.LENGTH_LONG).show()
            return
        }
        if (recording != null) {
            Toast.makeText(this, "Stop recording before changing camera mode.", Toast.LENGTH_SHORT).show()
            return
        }
        frontFacing = false
        dualMode = !dualMode
        dualButton.text = if (dualMode) "Dual on" else "Dual off"
        bindCameraSession()
    }

    private fun toggleFlash() {
        if (dualMode || frontFacing || camera == null) {
            Toast.makeText(this, "Flash is available only on a supported single rear camera.", Toast.LENGTH_SHORT).show()
            return
        }
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
        if (!hasFlash) {
            Toast.makeText(this, "This camera has no flash unit.", Toast.LENGTH_SHORT).show()
            return
        }
        flashEnabled = !flashEnabled
        camera?.cameraControl?.enableTorch(flashEnabled)
        updateFlashLabel()
    }

    private fun updateFlashLabel() {
        flashButton.text = if (flashEnabled) "Flash on" else "Flash off"
    }

    private fun showCameraSettings() {
        val items = arrayOf(
            "Video quality: ${videoQualityLabel(videoQuality)}",
            "Photo timer: ${if (timerSeconds == 0) "Off" else "${timerSeconds}s"}",
            "Dual-camera mode: ${if (dualSupported) "Supported" else "Not supported by device"}"
        )
        AlertDialog.Builder(this)
            .setTitle("Camera settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseVideoQuality()
                    1 -> chooseTimer()
                    2 -> Toast.makeText(this, if (dualSupported) "Use Dual on to enable front/rear preview. Video recording uses single-camera mode." else "This phone does not expose a supported front/rear concurrent camera pair.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun chooseVideoQuality() {
        val qualities = arrayOf("SD · 480p", "HD · 720p", "Full HD · 1080p", "Highest available")
        AlertDialog.Builder(this)
            .setTitle("Video quality")
            .setSingleChoiceItems(qualities, when (videoQuality) {
                Quality.SD -> 0
                Quality.HD -> 1
                Quality.FHD -> 2
                else -> 3
            }) { dialog, which ->
                videoQuality = when (which) {
                    0 -> Quality.SD
                    1 -> Quality.HD
                    2 -> Quality.FHD
                    else -> Quality.HIGHEST
                }
                dialog.dismiss()
                if (recording == null) bindCameraSession()
                Toast.makeText(this, "Video quality: ${videoQualityLabel(videoQuality)}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseTimer() {
        val choices = arrayOf("Off", "3 seconds", "5 seconds", "10 seconds")
        AlertDialog.Builder(this)
            .setTitle("Photo timer")
            .setSingleChoiceItems(choices, when (timerSeconds) { 3 -> 1; 5 -> 2; 10 -> 3; else -> 0 }) { dialog, which ->
                timerSeconds = when (which) { 1 -> 3; 2 -> 5; 3 -> 10; else -> 0 }
                dialog.dismiss()
                Toast.makeText(this, "Photo timer: ${if (timerSeconds == 0) "Off" else "${timerSeconds}s"}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun videoQualityLabel(quality: Quality): String = when (quality) {
        Quality.SD -> "480p"
        Quality.HD -> "720p"
        Quality.FHD -> "1080p"
        else -> "Highest available"
    }

    private fun finishWithMessage(message: String) {
        status.text=message
        shutter.isEnabled=false
        AlertDialog.Builder(this).setTitle("Camera unavailable").setMessage(message+" Your saved media is unchanged.")
            .setPositiveButton("Retry") {_,_->if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCameras() else permissionRequest.launch(arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO))}
            .setNegativeButton("Back") {_,_->finish()}.show()
    }

    override fun onStop() {
        photoTimer?.let { shutter.removeCallbacks(it) }
        photoTimer = null
        pendingPhoto = false
        shutter.isEnabled=imageCapture!=null || videoCapture!=null
        recording?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        recording?.stop()
        executor.shutdown()
        super.onDestroy()
    }
}
