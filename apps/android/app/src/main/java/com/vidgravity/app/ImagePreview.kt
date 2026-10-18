package com.vidgravity.app

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ImagePreview(media: LibraryMedia, onBack: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(media.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember(media.uri) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(media.uri) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(media.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                context.contentResolver.openInputStream(media.uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: throw IllegalStateException("Image could not be decoded.")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = "This image could not be opened." }
    }
    DisposableEffect(bitmap) {val b=bitmap;onDispose {b?.recycle()}}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(media.displayName, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("Back to workspace") }
            TextButton(onClick = {
                try {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = media.mimeType
                        putExtra(Intent.EXTRA_STREAM, media.uri)
                        clipData = android.content.ClipData.newUri(context.contentResolver, media.displayName, media.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Share image"))
                } catch (failure: Exception) { error = "No app could share this image." }
            }) { Text("Share image") }
        }
        bitmap?.let { Image(it.asImageBitmap(), media.displayName, Modifier.fillMaxWidth().weight(1f),
            contentScale = ContentScale.Fit) }
        if (bitmap == null && error == null) CircularProgressIndicator()
        error?.let { Text(it) }
    }
}
