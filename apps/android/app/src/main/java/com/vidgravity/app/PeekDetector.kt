package com.vidgravity.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Detects a short horizontal "peek" gesture only inside a VidGravity surface.
 *
 * Android apps cannot observe private touch gestures in Snapchat or other apps; this is
 * intentionally scoped to a surface that the app owns. It reports a gesture, not proof
 * that a message was read or that a particular person peeked.
 */
fun Modifier.detectVidGravityPeek(
    enabled: Boolean = true,
    threshold: Dp = 48.dp,
    onPeek: () -> Unit
): Modifier = pointerInput(enabled, threshold) {
    if (!enabled) return@pointerInput
    val thresholdPx = threshold.toPx()
    awaitEachGesture {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            var maxHorizontal = 0f
            var maxVertical = 0f
            var ended = false
            while (!ended) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - startX
                val dy = change.position.y - down.position.y
                maxHorizontal = maxOf(maxHorizontal, abs(dx))
                maxVertical = maxOf(maxVertical, abs(dy))
                if (!change.pressed) ended = true
                // Let scroll containers and buttons keep ownership of the gesture.
                if (maxVertical > thresholdPx || maxHorizontal > thresholdPx * 3f) break
            }
            // A restrained horizontal drag released before a full swipe can be treated as
            // a peek hint. Do not consume pointer events or block normal navigation.
            if (maxHorizontal >= thresholdPx && maxHorizontal < thresholdPx * 3f &&
                maxVertical < thresholdPx) {
                onPeek()
            }
        }
    }
}
