package com.vidgravity.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.MotionEvent
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Camera capture UI with visible controls. Concurrent front/rear preview is enabled only
 * when CameraX reports support. Dual mode uses rear Preview + ImageCapture and front Preview;
 * video recording switches to a supported single-camera session before starting.
 */
class DualCameraCaptureActivity : ComponentActivity() {
    private lateinit var container: FrameLayout
    private lateinit var rearPreview: PreviewView
    private lateinit var frontPreview: PreviewView
    private lateinit var gridOverlay: View
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
    private enum class FlashMode { AUTO, ON, OFF }
    private var flashMode = FlashMode.OFF
    private enum class DualPreviewSize { SMALL, MEDIUM, LARGE }
    private var dualPreviewSize = DualPreviewSize.MEDIUM
    private var videoQuality = Quality.FHD
    private var supportedVideoQualities: List<Quality> = emptyList()
    private var timerSeconds = 0
    private var pendingPhoto = false
    private var gridEnabled = false

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) startCameras()
        else finishWithMessage("Camera permission is required to capture media.")
    }

    private val audioPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        activateVideoMode(audioGranted = granted)
        if (!granted) {
            Toast.makeText(this, "Microphone permission denied. Video will record without audio.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = Executors.newSingleThreadExecutor()
        buildCameraUi()
        // Ask only for camera access on entry. Microphone permission is requested
        // when the user explicitly switches to video mode, so photo capture does not
        // require an unrelated permission grant.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameras()
        } else {
            permissionRequest.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildCameraUi() {
        container = FrameLayout(this).apply { setBackgroundColor(0xFF121E21.toInt()) }
        rearPreview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        frontPreview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            clipToOutline = true
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        container.addView(rearPreview, FrameLayout.LayoutParams(-1, -1))
        rearPreview.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                focusAt(event.x, event.y)
                view.performClick()
                true
            } else true
        }
        val pip = FrameLayout.LayoutParams(dp(118), dp(164), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(76)
            marginEnd = dp(16)
        }
        container.addView(frontPreview, pip)
        gridOverlay = object : View(this) {
            private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99FFFFFF.toInt()
                strokeWidth = dp(1).toFloat()
                style = Paint.Style.STROKE
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (!gridEnabled) return
                val thirdW = width / 3f
                val thirdH = height / 3f
                for (i in 1..2) {
                    canvas.drawLine(thirdW * i, 0f, thirdW * i, height.toFloat(), gridPaint)
                    canvas.drawLine(0f, thirdH * i, width.toFloat(), thirdH * i, gridPaint)
                }
            }
        }.apply { isClickable = false; visibility = View.GONE }
        container.addView(gridOverlay, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(0xE61A272B.toInt())
        }
        val close = controlButton("✕") { finish() }
        flashButton = controlButton("⚡ Off") { chooseFlashMode() }
        val flip = controlButton("Flip") { toggleFacing() }
        dualButton = controlButton("Dual off") { toggleDualMode() }
        val settings = controlButton("Settings") { showCameraSettings() }
        val previewSize = controlButton("PiP size") { chooseDualPreviewSize() }
        top.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        val topSpacer = View(this)
        top.addView(topSpacer, LinearLayout.LayoutParams(0, dp(48), 1f))
        container.addView(top, FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP))

        // Reachable right-side camera toolbar. The top bar now contains navigation only.
        val sideTools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(8), dp(5), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE61B2B30.toInt())
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0x667ECABD)
            }
            elevation = dp(6).toFloat()
        }
        listOf(flashButton, flip, dualButton, previewSize, settings).forEach { button ->
            button.textSize = 10f
            button.contentDescription = when (button) {
                flashButton -> "Flashlight mode"
                flip -> "Switch front and rear camera"
                dualButton -> "Toggle dual camera preview"
                previewSize -> "Adjust secondary preview size"
                else -> "Camera settings"
            }
            sideTools.addView(button, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                bottomMargin = dp(7)
            })
        }
        container.addView(sideTools, FrameLayout.LayoutParams(dp(64), -2, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(8)
        })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(0xE61A272B.toInt())
        }
        status = TextView(this).apply {
            text = "Preparing camera…"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }
        bottom.addView(status, LinearLayout.LayoutParams(-1, -2))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeButton = controlButton("PHOTO ✓") { toggleMode() }
        shutter = controlButton("●  Capture") { onShutterPressed() }.apply {
            textSize = 17f
            setTextColor(0xFF102124.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF80CDBF.toInt()))
        }
        controls.addView(modeButton, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(12) })
        controls.addView(shutter, LinearLayout.LayoutParams(0, dp(58), 1.3f))
        bottom.addView(controls, LinearLayout.LayoutParams(-1, -2))
        val hint = TextView(this).apply {
            text = "Tap preview to focus · Tap Capture for a photo. In Video mode, tap to start/stop recording."
            setTextColor(0xFFB8C9C8.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        bottom.addView(hint, LinearLayout.LayoutParams(-1, -2))
        container.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(container)
    }

    private fun controlButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2D4146.toInt()))
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
        val cameraInfo = runCatching { cameraProvider.getCameraInfo(selector) }.getOrNull()
        supportedVideoQualities = cameraInfo?.let { info ->
            runCatching { QualitySelector.getSupportedQualities(info) }.getOrNull()
        }.orEmpty()
        if (supportedVideoQualities.isNotEmpty() && videoQuality !in supportedVideoQualities) {
            videoQuality = supportedVideoQualities.firstOrNull { it == Quality.FHD }
                ?: supportedVideoQualities.firstOrNull { it == Quality.HD }
                ?: supportedVideoQualities.first()
        }
        cameraProvider.unbindAll()
        camera = null
        imageCapture = null
        videoCapture = null
        flashEnabled = false
        flashMode = FlashMode.OFF
        updateFlashLabel()

        val rearUseCase = Preview.Builder().build().also { it.setSurfaceProvider(rearPreview.surfaceProvider) }
        if (dualMode && !frontFacing && dualSupported) {
            val frontUseCase = Preview.Builder().build().also { it.setSurfaceProvider(frontPreview.surfaceProvider) }
            val stillCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
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
                applyDualPreviewSize()
                status.text = "Dual-camera preview · Photo mode"
                flashButton.isEnabled = false
                flashButton.text = "Flash n/a"
                shutter.text = "●  Capture"
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
            .setFlashMode(if (flashMode == FlashMode.AUTO) ImageCapture.FLASH_MODE_AUTO else ImageCapture.FLASH_MODE_OFF)
            .build()
        val recorder = Recorder.Builder().setQualitySelector(
            QualitySelector.from(videoQuality, androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
        ).build()
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
        camera?.cameraControl?.enableTorch(false)
        updateFlashLabel()
        status.text = if (frontFacing) "Front camera · ${if (videoMode) "Video" else "Photo"} mode"
            else "Rear camera · ${if (videoMode) "Video" else "Photo"} mode"
        shutter.text = if (videoMode) "●  Record" else "●  Capture"
    }

    /** Tap the preview to meter focus and exposure at the tapped point. CameraX reports the result. */
    private fun focusAt(x: Float, y: Float) {
        val activeCamera = camera
        if (dualMode || activeCamera == null || rearPreview.width <= 0 || rearPreview.height <= 0) return
        val point = rearPreview.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        activeCamera.cameraControl.startFocusAndMetering(action)
            .addListener({
                runOnUiThread {
                    status.text = if (dualMode) "Dual-camera preview · Photo mode" else "Focus and exposure updated"
                }
            }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleMode() {
        if (recording != null) {
            Toast.makeText(this, "Stop the current recording before changing mode.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!videoMode && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!videoMode) activateVideoMode(audioGranted = true) else {
            videoMode = false
            modeButton.text = "PHOTO ✓ · tap for VIDEO"
            modeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2D4146.toInt()))
            bindCameraSession()
        }
    }

    private fun activateVideoMode(audioGranted: Boolean) {
        videoMode = true
        modeButton.text = "VIDEO ✓ · tap for PHOTO"
        modeButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF80CDBF.toInt()))
        if (dualMode) {
            dualMode = false
            bindCameraSession()
            Toast.makeText(this, "Switched to single-camera video recording.", Toast.LENGTH_SHORT).show()
        } else {
            bindCameraSession()
        }
        if (!audioGranted) status.text = "Video mode · recording without microphone audio"
    }

    private fun onShutterPressed() {
        if (videoMode) toggleRecording() else takePhoto()
    }

    private fun takePhoto() {
        val capture = imageCapture
        if (capture == null) {
            Toast.makeText(this, "Photo capture is not available in this camera mode.", Toast.LENGTH_LONG).show()
            return
        }
        val captureNow = {
            val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
            if (!directory.exists()) directory.mkdirs()
            val target = File(directory, "vidgravity-${System.currentTimeMillis()}.jpg")
            val options = ImageCapture.OutputFileOptions.Builder(target).build()
            capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) = runOnUiThread {
                    try {
                        val published = com.vidgravity.app.MediaLibraryIndexer.publish(
                            this@DualCameraCaptureActivity, target, target.name, sha256(target)
                        )
                        target.delete()
                        status.text = "Photo saved to Library · ${published.displayName}"
                        Toast.makeText(this@DualCameraCaptureActivity, "Photo added to VidGravity Library", Toast.LENGTH_LONG).show()
                    } catch (error: Exception) {
                        status.text = "Photo captured · Library import failed"
                        Toast.makeText(this@DualCameraCaptureActivity, "Photo captured but could not be added to Library: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onError(exception: ImageCaptureException) = runOnUiThread {
                    Toast.makeText(this@DualCameraCaptureActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
        if (timerSeconds > 0) {
            status.text = "Photo timer · ${timerSeconds}s"
            shutter.postDelayed(captureNow, timerSeconds * 1000L)
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
        val directory = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        if (!directory.exists()) directory.mkdirs()
        val target = File(directory, "vidgravity-${System.currentTimeMillis()}.mp4")
        var pending = capture.output.prepareRecording(this, FileOutputOptions.Builder(target).build())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        shutter.isEnabled = true
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    shutter.text = "■  Stop"
                    status.text = "Recording video…"
                }
                is VideoRecordEvent.Status -> {
                    val seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                    status.text = "Recording · ${seconds}s"
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    shutter.isEnabled = true
                    shutter.text = "●  Record"
                    if (event.hasError()) {
                        status.text = "Recording failed"
                        Toast.makeText(this, "Recording failed. Please retry.", Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            val published = com.vidgravity.app.MediaLibraryIndexer.publish(
                                this@DualCameraCaptureActivity, target, target.name, sha256(target)
                            )
                            target.delete()
                            status.text = "Video saved to Library · ${published.displayName}"
                            Toast.makeText(this, "Video added to VidGravity Library", Toast.LENGTH_LONG).show()
                        } catch (error: Exception) {
                            status.text = "Video recorded · Library import failed"
                            Toast.makeText(this, "Video recorded but could not be added to Library: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
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

    private fun chooseFlashMode() {
        val activeCamera = camera
        if (dualMode || activeCamera == null || !activeCamera.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "Flash is not available in this camera configuration.", Toast.LENGTH_LONG).show()
            return
        }
        val choices = arrayOf("Auto (photo only)", "On (torch)", "Off")
        val checked = when (flashMode) { FlashMode.AUTO -> 0; FlashMode.ON -> 1; FlashMode.OFF -> 2 }
        AlertDialog.Builder(this)
            .setTitle("Flash")
            .setSingleChoiceItems(choices, checked) { dialog, which ->
                flashMode = when (which) { 0 -> FlashMode.AUTO; 1 -> FlashMode.ON; else -> FlashMode.OFF }
                val autoPhotoFlash = flashMode == FlashMode.AUTO && !videoMode
                imageCapture?.flashMode = if (autoPhotoFlash) ImageCapture.FLASH_MODE_AUTO else ImageCapture.FLASH_MODE_OFF
                activeCamera.cameraControl.enableTorch(flashMode == FlashMode.ON)
                flashEnabled = flashMode == FlashMode.ON
                updateFlashLabel()
                if (flashMode == FlashMode.AUTO && videoMode) {
                    Toast.makeText(this, "Auto flash applies to photos only. Video light remains off.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateFlashLabel() {
        flashButton.text = when (flashMode) {
            FlashMode.AUTO -> "⚡ Auto"
            FlashMode.ON -> "⚡ On"
            FlashMode.OFF -> "⚡ Off"
        }
    }

    private fun chooseDualPreviewSize() {
        if (!dualSupported) {
            Toast.makeText(this, "Picture-in-picture size is available when dual preview is supported.", Toast.LENGTH_SHORT).show()
            return
        }
        val choices = arrayOf("Small", "Medium", "Large")
        AlertDialog.Builder(this)
            .setTitle("Secondary preview size")
            .setSingleChoiceItems(choices, dualPreviewSize.ordinal) { dialog, which ->
                dualPreviewSize = DualPreviewSize.values()[which]
                applyDualPreviewSize()
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun applyDualPreviewSize() {
        val params = frontPreview.layoutParams as? FrameLayout.LayoutParams ?: return
        val (width, height) = when (dualPreviewSize) {
            DualPreviewSize.SMALL -> dp(88) to dp(124)
            DualPreviewSize.MEDIUM -> dp(118) to dp(164)
            DualPreviewSize.LARGE -> dp(148) to dp(204)
        }
        params.width = width
        params.height = height
        frontPreview.layoutParams = params
    }

    private fun showCameraSettings() {
        val items = arrayOf(
            "Video quality: ${videoQualityLabel(videoQuality)}",
            "Photo timer: ${if (timerSeconds == 0) "Off" else "${timerSeconds}s"}",
            "Composition grid: ${if (gridEnabled) "On" else "Off"}",
            "Zoom control",
            "Exposure compensation",
            "Dual-camera mode: ${if (dualSupported) "Supported" else "Not supported by device"}"
        )
        AlertDialog.Builder(this)
            .setTitle("Camera settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseVideoQuality()
                    1 -> chooseTimer()
                    2 -> {
                        gridEnabled = !gridEnabled
                        gridOverlay.visibility = if (gridEnabled) View.VISIBLE else View.GONE
                        gridOverlay.invalidate()
                        Toast.makeText(this, "Composition grid: ${if (gridEnabled) "On" else "Off"}", Toast.LENGTH_SHORT).show()
                        showCameraSettings()
                    }
                    3 -> chooseZoom()
                    4 -> chooseExposure()
                    5 -> Toast.makeText(this, if (dualSupported) "Use Dual on to enable front/rear preview. Video recording uses single-camera mode." else "This phone does not expose a supported front/rear concurrent camera pair.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun chooseZoom() {
        val activeCamera = camera
        if (dualMode || activeCamera == null) {
            Toast.makeText(this, "Zoom control is unavailable in dual-camera mode.", Toast.LENGTH_LONG).show()
            return
        }
        val zoomState = activeCamera.cameraInfo.zoomState.value
        if (zoomState == null || zoomState.maxZoomRatio <= zoomState.minZoomRatio) {
            Toast.makeText(this, "This camera does not expose adjustable zoom.", Toast.LENGTH_SHORT).show()
            return
        }
        val minZoom = zoomState.minZoomRatio
        val maxZoom = zoomState.maxZoomRatio
        val seek = SeekBar(this).apply {
            max = 100
            progress = (((zoomState.zoomRatio - minZoom) / (maxZoom - minZoom)) * 100f).toInt().coerceIn(0, 100)
        }
        val label = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            text = String.format(java.util.Locale.US, "%.1fx", zoomState.zoomRatio)
            setPadding(0, dp(8), 0, dp(8))
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val ratio = minZoom + (maxZoom - minZoom) * (progress / 100f)
                label.text = String.format(java.util.Locale.US, "%.1fx", ratio)
                activeCamera.cameraControl.setZoomRatio(ratio)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(seek, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle("Zoom · ${String.format(java.util.Locale.US, "%.1f", minZoom)}×–${String.format(java.util.Locale.US, "%.1f", maxZoom)}×")
            .setView(panel)
            .setPositiveButton("Done", null)
            .setNeutralButton("Reset") { _, _ -> activeCamera.cameraControl.setZoomRatio(minZoom) }
            .show()
    }

    private fun chooseExposure() {
        val activeCamera = camera
        if (dualMode || activeCamera == null) {
            Toast.makeText(this, "Exposure control is unavailable in dual-camera mode.", Toast.LENGTH_LONG).show()
            return
        }
        val exposureState = activeCamera.cameraInfo.exposureState
        val range = exposureState.exposureCompensationRange
        if (range.lower >= range.upper) {
            Toast.makeText(this, "This camera does not expose adjustable exposure compensation.", Toast.LENGTH_SHORT).show()
            return
        }
        val currentIndex = exposureState.exposureCompensationIndex.coerceIn(range.lower, range.upper)
        val seek = SeekBar(this).apply {
            max = range.upper - range.lower
            progress = currentIndex - range.lower
        }
        val label = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            text = "Exposure index: $currentIndex"
            setPadding(0, dp(8), 0, dp(8))
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val index = range.lower + progress
                label.text = "Exposure index: $index"
                activeCamera.cameraControl.setExposureCompensationIndex(index)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(seek, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle("Exposure compensation")
            .setView(panel)
            .setPositiveButton("Done", null)
            .setNeutralButton("Reset") { _, _ -> activeCamera.cameraControl.setExposureCompensationIndex(0.coerceIn(range.lower, range.upper)) }
            .show()
    }

    private fun chooseVideoQuality() {
        val cameraInfo = provider?.let {
            runCatching {
                it.getCameraInfo(if (frontFacing) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA)
            }.getOrNull()
        }
        val supported = cameraInfo?.let { info ->
            runCatching { QualitySelector.getSupportedQualities(info) }.getOrNull()
        }.orEmpty()
        if (supported.isEmpty()) {
            Toast.makeText(this, "This camera does not report selectable video quality profiles yet.", Toast.LENGTH_LONG).show()
            return
        }
        supportedVideoQualities = supported
        val choices = supported.map { videoQualityLabel(it) }.toTypedArray()
        val checked = supported.indexOf(videoQuality).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Supported video quality")
            .setSingleChoiceItems(choices, checked) { dialog, which ->
                videoQuality = supported[which]
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(128 * 1024).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        // Request finalization if the screen is closed during recording. Do not call
        // ProcessCameraProvider.unbindAll() here: the Activity lifecycle owns these use cases,
        // and forcing an immediate unbind can interrupt CameraX's recording Finalize event.
        // shutdown() allows already queued photo callbacks to finish.
        runCatching { recording?.stop() }
        runCatching { executor.shutdown() }
        super.onDestroy()
    }
}
