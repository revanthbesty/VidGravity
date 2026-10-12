package com.vidgravity.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.ConcurrentCamera
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recording
import androidx.camera.video.PendingRecording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * POV Snap capture shell. CameraX may reject simultaneous front/rear use on unsupported hardware.
 * This implementation falls back to a single rear-camera session and never claims dual capture
 * when the platform cannot bind both concurrently.
 */
class DualCameraCaptureActivity : ComponentActivity() {
    private lateinit var container: FrameLayout
    private lateinit var rearPreview: PreviewView
    private lateinit var frontPreview: PreviewView
    private lateinit var executor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var dualBound = false
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { granted -> granted }) startCameras() else finishWithMessage("Camera and microphone permissions are required.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = Executors.newSingleThreadExecutor()
        container = FrameLayout(this)
        rearPreview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        frontPreview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        container.addView(rearPreview, FrameLayout.LayoutParams(-1, -1))
        val pip = FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.29f).toInt(),
            (resources.displayMetrics.heightPixels * 0.22f).toInt()).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = 64
            marginEnd = 20
        }
        frontPreview.clipToOutline = true
        container.addView(frontPreview, pip)
        setContentView(container)
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) startCameras()
        else permissionRequest.launch(permissions)
    }

    private fun startCameras() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val rear = CameraSelector.DEFAULT_BACK_CAMERA
            val front = CameraSelector.DEFAULT_FRONT_CAMERA
            val rearPreviewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(rearPreview.surfaceProvider) }
            val frontPreviewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(frontPreview.surfaceProvider) }
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
            val video = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            try {
                // Requires device-level concurrent-camera support; check before concurrent binding.
                val concurrentPair = provider.availableConcurrentCameraInfos.firstOrNull { infos ->
                    val facings = infos.mapNotNull { info ->
                        runCatching { info.lensFacing }.getOrNull()
                    }.toSet()
                    CameraSelector.LENS_FACING_BACK in facings &&
                        CameraSelector.LENS_FACING_FRONT in facings
                }
                if (concurrentPair != null) {
                    // CameraX requires concurrent use cases to be bound as one atomic pair.
                    provider.bindToLifecycle(listOf(
                        ConcurrentCamera.SingleCameraConfig(
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                listOf(rearPreviewUseCase, capture, video),
                                this
                            ),
                        ConcurrentCamera.SingleCameraConfig(
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                listOf(frontPreviewUseCase),
                                this
                            )
                    ))
                    dualBound = true
                } else {
                    provider.bindToLifecycle(this, rear, rearPreviewUseCase, capture, video)
                    frontPreview.visibility = android.view.View.GONE
                    dualBound = false
                    Toast.makeText(this, "This device supports rear-camera capture only.", Toast.LENGTH_LONG).show()
                }
                imageCapture = capture
                videoCapture = video
                container.setOnClickListener { takePhoto() }
                container.setOnLongClickListener { toggleRecording(); true }
            } catch (error: Exception) {
                runCatching { provider.unbindAll(); provider.bindToLifecycle(this, rear, rearPreviewUseCase, capture, video) }
                frontPreview.visibility = android.view.View.GONE
                imageCapture = capture
                videoCapture = video
                Toast.makeText(this, "Concurrent cameras unavailable; using rear camera.", Toast.LENGTH_LONG).show()
                container.setOnClickListener { takePhoto() }
                container.setOnLongClickListener { toggleRecording(); true }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val target = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "pov-${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(target).build()
        imageCapture?.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) = runOnUiThread {
                Toast.makeText(this@DualCameraCaptureActivity, "Photo saved: ${target.name}", Toast.LENGTH_SHORT).show()
            }
            override fun onError(exception: ImageCaptureException) = runOnUiThread {
                Toast.makeText(this@DualCameraCaptureActivity, "Photo failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun toggleRecording() {
        val capture = videoCapture ?: return
        if (recording != null) {
            recording?.stop(); recording = null
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
            return
        }
        val target = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "pov-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(target).build()
        var pending: PendingRecording = capture.output.prepareRecording(this, options)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                Toast.makeText(this, if (event.hasError()) "Recording failed" else "Saved ${target.name}", Toast.LENGTH_LONG).show()
            }
        }
        Toast.makeText(this, if (dualBound) "Recording (dual-camera preview)" else "Recording rear camera", Toast.LENGTH_SHORT).show()
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        recording?.stop()
        executor.shutdown()
        super.onDestroy()
    }
}
