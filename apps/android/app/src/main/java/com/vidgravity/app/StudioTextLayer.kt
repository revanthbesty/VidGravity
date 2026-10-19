package com.vidgravity.app

import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import org.json.JSONObject
import java.util.UUID

/** Independent output-time layers. List order is back-to-front, shared by preview and export. */
data class StudioTextLayer(
    val id: String = UUID.randomUUID().toString(), val text: String = "Your title",
    val startMs: Long = 0, val endMs: Long,
    val font: String = "sans-serif", val color: Int = -1, val background: Int = 0,
    val size: Int = 32, val x: Float = 0f, val y: Float = 0f, val rotation: Float = 0f,
    val animation: CreatorAnimation = CreatorAnimation.NONE
) {
    fun validate(duration: Long) {
        require(id.matches(Regex("[a-fA-F0-9-]{36}")))
        require(text.isNotBlank() && text.length <= 500 && startMs >= 0 && endMs > startMs && endMs <= duration) { "Text layers must fit the timeline." }
        require(font in listOf("sans-serif","serif","monospace") && size in 16..64)
        require(x.isFinite() && x in -0.8f..0.8f && y.isFinite() && y in -0.8f..0.8f)
        require(rotation.isFinite() && rotation in -180f..180f)
    }
    fun json() = JSONObject().put("id",id).put("text",text).put("start",startMs).put("end",endMs)
        .put("font",font).put("color",color).put("background",background).put("size",size)
        .put("x",x).put("y",y).put("rotation",rotation).put("animation",animation.name)
    companion object {
        fun decode(j: JSONObject) = StudioTextLayer(j.getString("id"),j.getString("text"),j.getLong("start"),j.getLong("end"),
            j.getString("font"),j.getInt("color"),j.getInt("background"),j.getInt("size"),j.getDouble("x").toFloat(),
            j.getDouble("y").toFloat(),j.getDouble("rotation").toFloat(),CreatorAnimation.valueOf(j.getString("animation")))
    }
}

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal object StudioTextOverlays {
    fun overlays(layers: List<StudioTextLayer>, offset: Long, speed: Float): List<TextOverlay> = layers.map { layer ->
        object: TextOverlay() {
            private fun time(us: Long) = offset + (us/1000/speed).toLong()
            private val styled = SpannableString(layer.text).apply {
                setSpan(ForegroundColorSpan(layer.color),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(BackgroundColorSpan(layer.background),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(AbsoluteSizeSpan(layer.size,true),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(TypefaceSpan(layer.font),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            override fun getText(presentationTimeUs: Long) = styled
            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                val t=time(presentationTimeUs)
                val active=t in layer.startMs until layer.endMs
                val phase=((t-layer.startMs)/250f).coerceIn(0f,1f)
                val alpha=if(!active) 0f else if(layer.animation==CreatorAnimation.FADE) phase else 1f
                val scale=if(layer.animation==CreatorAnimation.POP) 0.75f+0.25f*phase else 1f
                return OverlaySettings.Builder().setAlphaScale(alpha).setScale(scale,scale)
                    .setBackgroundFrameAnchor(layer.x,layer.y+if(layer.animation==CreatorAnimation.SLIDE) (1-phase)*0.15f else 0f)
                    .setOverlayFrameAnchor(0f,0f).setRotationDegrees(layer.rotation).build()
            }
        }
    }
}

/** Quantize absolute positions rather than accumulating a rounded frame duration. */
internal object StudioTiming {
    fun frame(timeMs: Long, frameRate: Int, durationMs: Long): Long {
        val fps=frameRate.takeIf {it>0} ?: 30
        val index=kotlin.math.round(timeMs*fps/1000.0).toLong()
        return (index*1000.0/fps).toLong().coerceIn(0,durationMs)
    }
    fun snap(timeMs: Long, anchors: List<Long>, limit: Long, thresholdMs: Long = 100): Long {
        val bounded=timeMs.coerceIn(0,limit)
        val nearest=anchors.filter {it in 0..limit}.minByOrNull {kotlin.math.abs(it-bounded)}
        return if(nearest!=null && kotlin.math.abs(nearest-bounded)<=thresholdMs) nearest else bounded
    }
    fun boundaries(p: StudioProject): List<Long> = listOf(0L) + p.clips.runningFold(0L) {a,c -> a+c.playbackDurationMs}.drop(1)
}
