package com.vidgravity.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.os.Handler
import org.json.JSONArray
import org.json.JSONObject
import android.os.Looper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan2

private val cameraStyles = listOf("Natural", "Rich Contrast", "Warm", "Cool", "Vibrant", "Soft", "Cinematic", "Monochrome")
private data class CameraCreatorPreset(val name: String, val style: Int, val intensity: Float, val framing: String)

private fun readCreatorPresets(raw: String?): List<CameraCreatorPreset> = runCatching {
    val json = JSONArray(raw ?: "[]")
    (0 until json.length()).mapNotNull { index ->
        val item = json.optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString("name").trim()
        val style = item.optInt("style", 0)
        val intensity = item.optDouble("intensity", 0.75).toFloat()
        val framing = item.optString("framing", "Off")
        if (name.isBlank() || style !in cameraStyles.indices || framing !in listOf("Off", "1:1", "4:5", "16:9")) null
        else CameraCreatorPreset(name.take(32), style, intensity.coerceIn(0f, 1f), framing)
    }
}.getOrDefault(emptyList())

private fun writeCreatorPresets(presets: List<CameraCreatorPreset>): String {
    val json = JSONArray()
    presets.forEach { preset -> json.put(JSONObject().put("name", preset.name).put("style", preset.style).put("intensity", preset.intensity.toDouble()).put("framing", preset.framing)) }
    return json.toString()
}


private fun renderCameraStyle(sourceFile: File, outputFile: File, style: String, intensity: Float) {
    if (style == "Natural" || intensity <= 0.001f) {
        sourceFile.copyTo(outputFile, overwrite = true)
        return
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Captured photo could not be decoded." }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    var decoded = BitmapFactory.decodeFile(sourceFile.absolutePath, options) ?: error("Captured photo could not be decoded.")
    val orientation = runCatching { ExifInterface(sourceFile.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotation != 0f) {
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        decoded = rotated
    }
    val a = intensity.coerceIn(0f, 1f)
    val saturation = when (style) {
        "Monochrome" -> 1f - a
        "Vibrant" -> 1f + .32f * a
        "Soft" -> 1f - .12f * a
        "Cinematic" -> 1f - .08f * a
        else -> 1f
    }
    val contrast = when (style) {
        "Rich Contrast" -> 1f + .24f * a
        "Cinematic" -> 1f + .15f * a
        "Soft" -> 1f - .10f * a
        else -> 1f
    }
    val warm = when (style) {
        "Warm" -> a
        "Cool" -> -a
        "Vibrant" -> .12f * a
        "Cinematic" -> -.10f * a
        else -> 0f
    }
    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    val offset = 128f * (1f - contrast)
    matrix.postConcat(ColorMatrix(floatArrayOf(
        contrast,0f,0f,0f,offset + 22f * warm,
        0f,contrast,0f,0f,offset + 3f * warm,
        0f,0f,contrast,0f,offset - 22f * warm,
        0f,0f,0f,1f,0f
    )))
    val result = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
    try {
        Canvas(result).drawBitmap(decoded, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        outputFile.outputStream().buffered().use { out -> check(result.compress(Bitmap.CompressFormat.JPEG, 94, out)) { "Styled photo encoding failed." } }
    } finally {
        result.recycle()
        decoded.recycle()
    }
}

@Composable
internal fun CameraStudioScreen(onClose: () -> Unit, onNotice: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("vg-camera-studio", 0) }
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(prefs.getBoolean("front", false)) }
    var grid by remember { mutableStateOf(prefs.getBoolean("grid", true)) }
    var levelEnabled by remember { mutableStateOf(prefs.getBoolean("level-enabled", true)) }
    var horizonAngle by remember { mutableFloatStateOf(0f) }
    var levelSensorAvailable by remember { mutableStateOf(false) }
    val framingGuides = listOf("Off", "1:1", "4:5", "16:9")
    var framingGuide by remember { mutableStateOf(prefs.getString("framing-guide", "Off")?.takeIf { it in framingGuides } ?: "Off") }
    var flash by remember { mutableIntStateOf(prefs.getInt("flash", ImageCapture.FLASH_MODE_OFF)) }
    var style by remember { mutableIntStateOf(prefs.getInt("style", 0).coerceIn(0, cameraStyles.lastIndex)) }
    var favoriteStyles by remember { mutableStateOf(prefs.getStringSet("favorite-styles", emptySet()).orEmpty().toSet()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val creatorPresets = remember { mutableStateListOf<CameraCreatorPreset>().apply { addAll(readCreatorPresets(prefs.getString("creator-presets", "[]"))) } }
    var savePresetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var styleIntensity by remember { mutableFloatStateOf(prefs.getFloat("style-intensity", 0.75f).coerceIn(0f, 1f)) }
    var exposure by remember { mutableFloatStateOf(prefs.getFloat("exposure", 0f).coerceIn(-2f, 2f)) }
    var activeCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var exposureRange by remember { mutableStateOf(-2f..2f) }
    var busy by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(prefs.getInt("timer-seconds", 0).coerceIn(0, 10)) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permitted = granted; denied = !granted }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
    }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    DisposableEffect(levelEnabled) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        levelSensorAvailable = sensor != null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size >= 2) {
                    val degrees = (atan2(event.values[0].toDouble(), event.values[1].toDouble()) * 180.0 / PI).toFloat()
                    horizonAngle = if (degrees > 90f) degrees - 180f else if (degrees < -90f) degrees + 180f else degrees
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (levelEnabled && sensor != null) sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { providerFuture.get().unbindAll() }; executor.shutdown() }
    }
    LaunchedEffect(permitted, front, flash) {
        if (!permitted) return@LaunchedEffect
        try {
            val provider = withContext(Dispatchers.IO) { providerFuture.get() }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (!provider.hasCamera(selector)) { error = "Selected camera is unavailable on this device."; return@LaunchedEffect }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture.flashMode = flash
            provider.unbindAll()
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            activeCamera = camera
            val exposureState = camera.cameraInfo.exposureState
            val stepEv = exposureState.exposureCompensationStep.toFloat()
            val supportedRange = exposureState.exposureCompensationRange
            exposureRange = if (stepEv > 0f) (supportedRange.lower * stepEv)..(supportedRange.upper * stepEv) else 0f..0f
            exposure = exposure.coerceIn(exposureRange.start, exposureRange.endInclusive)
            error = null
        } catch (t: Exception) { error = "Camera startup failed: " + (t.message ?: "unknown error") }
    }

    LaunchedEffect(activeCamera, exposure, permitted) {
        val camera = activeCamera ?: return@LaunchedEffect
        if (!permitted) return@LaunchedEffect
        val state = camera.cameraInfo.exposureState
        val range = state.exposureCompensationRange
        val step = state.exposureCompensationStep.toFloat()
        val index = if (step > 0f) (exposure / step).toInt().coerceIn(range.lower, range.upper) else 0
        runCatching { camera.cameraControl.setExposureCompensationIndex(index) }
    }

    fun capturePhoto() {
        if (busy || error != null) return
        busy = true
        val capturedStyle = cameraStyles[style]
        val capturedIntensity = styleIntensity
        val file = File(context.cacheDir, "vg-camera-" + System.currentTimeMillis() + ".jpg")
        imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                try {
                    val output = File(context.cacheDir, "vg-camera-styled-" + System.currentTimeMillis() + ".jpg")
                    try {
                        renderCameraStyle(file, output, capturedStyle, capturedIntensity)
                        val digest = MessageDigest.getInstance("SHA-256").digest(output.readBytes()).joinToString("") { "%02x".format(it) }
                        val suffix = if (capturedStyle == "Natural" || capturedIntensity <= 0.001f) "" else "-" + capturedStyle.lowercase().replace(" ", "-")
                        val saved = MediaLibraryIndexer.publish(context, output, "vidgravity-camera-" + System.currentTimeMillis() + suffix + ".jpg", digest)
                        Handler(Looper.getMainLooper()).post { busy = false; onNotice("Photo saved to Library: " + saved.displayName) }
                    } finally { file.delete(); output.delete() }
                } catch (t: Exception) {
                    file.delete()
                    Handler(Looper.getMainLooper()).post { busy = false; onNotice("Photo save failed: " + (t.message ?: "unknown error")) }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                file.delete()
                Handler(Looper.getMainLooper()).post { busy = false; onNotice("Capture failed: " + (exception.message ?: "please retry")) }
            }
        })
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Close camera") }
            Column(Modifier.weight(1f)) {
                Text("Camera Studio", style = MaterialTheme.typography.titleLarge)
                Text("Smart photo capture · " + cameraStyles[style], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Camera preferences") }
        }
        if (!permitted) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Camera permission is required to take photos.", modifier = Modifier.padding(16.dp))
                Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                if (denied) Text("Enable camera access in Android Settings to continue.", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().pointerInput(activeCamera) { detectTapGestures { tap -> val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y); val action = FocusMeteringAction.Builder(point).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build(); runCatching { activeCamera?.cameraControl?.startFocusAndMetering(action) } } })
                if (levelEnabled && levelSensorAvailable) Canvas(Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val lineHalfWidth = size.width * .12f
                    val isLevel = kotlin.math.abs(horizonAngle) <= 2.0f
                    val levelColor = if (isLevel) ComposeColor(0xFF55E0C0) else ComposeColor.White.copy(alpha = .92f)
                    rotate(-horizonAngle, pivot = Offset(centerX, centerY)) {
                        drawLine(levelColor, Offset(centerX - lineHalfWidth, centerY), Offset(centerX + lineHalfWidth, centerY), strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawCircle(levelColor, radius = 4.dp.toPx(), center = Offset(centerX, centerY))
                    }
                }
                if (grid || framingGuide != "Off") Canvas(Modifier.fillMaxSize()) {
                    val guideColor = ComposeColor.White.copy(alpha = .82f)
                    if (grid) {
                        for (n in 1..2) {
                            drawLine(guideColor.copy(alpha = .55f), Offset(size.width * n / 3f, 0f), Offset(size.width * n / 3f, size.height), 1.dp.toPx())
                            drawLine(guideColor.copy(alpha = .55f), Offset(0f, size.height * n / 3f), Offset(size.width, size.height * n / 3f), 1.dp.toPx())
                        }
                    }
                    if (framingGuide != "Off") {
                        val ratio = when (framingGuide) { "1:1" -> 1f; "4:5" -> 4f / 5f; else -> 16f / 9f }
                        val frameWidth = minOf(size.width, size.height * ratio)
                        val frameHeight = frameWidth / ratio
                        drawRect(guideColor, Offset((size.width - frameWidth) / 2f, (size.height - frameHeight) / 2f), androidx.compose.ui.geometry.Size(frameWidth, frameHeight), style = Stroke(2.dp.toPx()))
                    }
                }
                Column(Modifier.align(Alignment.CenterEnd).padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = ComposeColor.Black.copy(alpha = .58f)) {
                        IconButton(onClick = { grid = !grid; prefs.edit().putBoolean("grid", grid).apply() }) { Icon(Icons.Default.GridOn, "Toggle grid", tint = ComposeColor.White) }
                    }
                    Surface(shape = CircleShape, color = ComposeColor.Black.copy(alpha = .58f)) {
                        IconButton(onClick = { flash = when (flash) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO; ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON; else -> ImageCapture.FLASH_MODE_OFF }; prefs.edit().putInt("flash", flash).apply() }) {
                            Icon(when (flash) { ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto; ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn; else -> Icons.Default.FlashOff }, "Cycle flash", tint = ComposeColor.White)
                        }
                    }
                    Surface(shape = CircleShape, color = ComposeColor.Black.copy(alpha = .58f)) {
                        IconButton(onClick = { front = !front; prefs.edit().putBoolean("front", front).apply() }) { Icon(Icons.Default.SwitchCamera, "Switch camera", tint = ComposeColor.White) }
                    }
                }
                error?.let { Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), color = ComposeColor.Black.copy(alpha = .78f), shape = RoundedCornerShape(12.dp)) { Text(it, color = ComposeColor.White, modifier = Modifier.padding(10.dp)) } }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Exposure compensation", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text((if (exposure >= 0) "+" else "") + "%.1f EV".format(exposure), style = MaterialTheme.typography.labelMedium)
                }
                Slider(value = exposure.coerceIn(exposureRange.start, exposureRange.endInclusive), onValueChange = { exposure = it; prefs.edit().putFloat("exposure", it).apply() }, valueRange = exposureRange, enabled = exposureRange.start < exposureRange.endInclusive)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Level / horizon guide", style = MaterialTheme.typography.labelMedium)
                        Text(if (levelSensorAvailable) "Turns teal when level (±2°)" else "Accelerometer unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = levelEnabled, onCheckedChange = { levelEnabled = it; prefs.edit().putBoolean("level-enabled", it).apply() }, enabled = levelSensorAvailable)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Social framing guide", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { val next = (framingGuides.indexOf(framingGuide) + 1) % framingGuides.size; framingGuide = framingGuides[next]; prefs.edit().putString("framing-guide", framingGuide).apply() }) { Text(framingGuide) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Creator presets", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { presetName = ""; savePresetDialog = true }, enabled = creatorPresets.size < 12) { Text("Save current") }
                }
                if (creatorPresets.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        creatorPresets.forEach { preset ->
                            AssistChip(onClick = {
                                style = preset.style
                                styleIntensity = preset.intensity
                                framingGuide = preset.framing
                                prefs.edit().putInt("style", style).putFloat("style-intensity", styleIntensity).putString("framing-guide", framingGuide).apply()
                                onNotice("Applied creator preset: " + preset.name)
                            }, label = { Text(preset.name) }, trailingIcon = { IconButton(onClick = { creatorPresets.remove(preset); prefs.edit().putString("creator-presets", writeCreatorPresets(creatorPresets)).apply() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Delete " + preset.name, modifier = Modifier.size(16.dp)) } })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { val candidates = if (favoritesOnly && favoriteStyles.isNotEmpty()) cameraStyles.indices.filter { cameraStyles[it] in favoriteStyles } else cameraStyles.indices.toList(); val currentPosition = candidates.indexOf(style).let { if (it < 0) 0 else it }; style = candidates[(currentPosition - 1 + candidates.size) % candidates.size]; prefs.edit().putInt("style", style).apply() }) { Text("‹ Style") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cameraStyles[style], style = MaterialTheme.typography.labelLarge)
                        Text(if (cameraStyles[style] in favoriteStyles) "★ Favorite" else "Photographic style", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { favoriteStyles = if (cameraStyles[style] in favoriteStyles) favoriteStyles - cameraStyles[style] else favoriteStyles + cameraStyles[style]; prefs.edit().putStringSet("favorite-styles", favoriteStyles).apply() }) {
                        Icon(if (cameraStyles[style] in favoriteStyles) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Toggle favorite style", tint = if (cameraStyles[style] in favoriteStyles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { val candidates = if (favoritesOnly && favoriteStyles.isNotEmpty()) cameraStyles.indices.filter { cameraStyles[it] in favoriteStyles } else cameraStyles.indices.toList(); val currentPosition = candidates.indexOf(style).let { if (it < 0) 0 else it }; style = candidates[(currentPosition + 1) % candidates.size]; prefs.edit().putInt("style", style).apply() }) { Text("Style ›") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Style favorites", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { if (!favoritesOnly && favoriteStyles.isEmpty()) onNotice("Mark a style as favorite first."); else { favoritesOnly = !favoritesOnly; if (favoritesOnly && cameraStyles[style] !in favoriteStyles) { style = cameraStyles.indexOfFirst { it in favoriteStyles }.coerceAtLeast(0); prefs.edit().putInt("style", style).apply() } } }) {
                        Text(if (favoritesOnly) "Show all" else "Favorites only (" + favoriteStyles.size + ")")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Style intensity", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text((styleIntensity * 100).toInt().toString() + "%", style = MaterialTheme.typography.labelMedium)
                }
                Slider(value = styleIntensity, onValueChange = { styleIntensity = it; prefs.edit().putFloat("style-intensity", it).apply() }, valueRange = 0f..1f)
                Text("Tap the preview to focus. Exposure range follows the selected camera hardware. Styles are applied to saved photos; live preview remains ungraded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Self-timer", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 3, 5, 10).forEach { seconds ->
                            FilterChip(selected = timerSeconds == seconds, onClick = { timerSeconds = seconds; prefs.edit().putInt("timer-seconds", seconds).apply() }, label = { Text(if (seconds == 0) "Off" else seconds.toString() + "s") }, enabled = !busy && countdownRemaining == 0)
                        }
                    }
                }
                Button(onClick = {
                    if (busy || error != null || countdownRemaining > 0) return@Button
                    if (timerSeconds == 0) capturePhoto()
                    else {
                        countdownJob?.cancel()
                        countdownJob = scope.launch {
                            for (remaining in timerSeconds downTo 1) {
                                countdownRemaining = remaining
                                delay(1000)
                            }
                            countdownRemaining = 0
                            capturePhoto()
                        }
                    }
                }, enabled = !busy && error == null, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else if (countdownRemaining > 0) Text(countdownRemaining.toString(), style = MaterialTheme.typography.headlineSmall)
                    else Icon(Icons.Default.PhotoCamera, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (busy) "Saving photo…" else if (countdownRemaining > 0) "Get ready…" else if (timerSeconds > 0) "Start " + timerSeconds + "s timer" else "Capture photo")
                }
            }
        }
    }

    if (savePresetDialog) AlertDialog(
        onDismissRequest = { savePresetDialog = false },
        title = { Text("Save creator preset") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Save the current photographic style, intensity and framing guide for quick reuse.")
            OutlinedTextField(value = presetName, onValueChange = { presetName = it.take(32) }, label = { Text("Preset name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(creatorPresets.size.toString() + " of 12 presets saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = {
            val name = presetName.trim()
            if (name.isNotEmpty() && creatorPresets.size < 12 && creatorPresets.none { it.name.equals(name, ignoreCase = true) }) {
                creatorPresets.add(CameraCreatorPreset(name, style, styleIntensity, framingGuide))
                prefs.edit().putString("creator-presets", writeCreatorPresets(creatorPresets)).apply()
                savePresetDialog = false
                onNotice("Saved creator preset: " + name)
            } else onNotice(if (name.isEmpty()) "Enter a preset name." else if (creatorPresets.size >= 12) "Preset limit reached." else "That preset name already exists.")
        }) { Text("Save preset") } },
        dismissButton = { TextButton(onClick = { savePresetDialog = false }) { Text("Cancel") } }
    )
    if (settings) AlertDialog(
        onDismissRequest = { settings = false },
        title = { Text("Camera preferences") },
        text = { Text("Front/rear camera, flash, exposure, composition grid, photographic style and style intensity are remembered on this device. Styles are applied to exported photos; the live preview is not color-graded.") },
        confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { front = false; grid = true; framingGuide = "Off"; flash = ImageCapture.FLASH_MODE_OFF; style = 0; favoriteStyles = emptySet(); favoritesOnly = false; creatorPresets.clear(); savePresetDialog = false; presetName = ""; styleIntensity = 0.75f; exposure = 0f; activeCamera = null; prefs.edit().clear().apply(); settings = false }) { Text("Reset defaults") } }
    )
}
