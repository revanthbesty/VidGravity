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
    val animation: CreatorAnimation = CreatorAnimation.NONE,
    val motion:List<CinemaMotionKey> = emptyList(),val motionOffsetMs:Long=0
) {
    fun validate(duration: Long) {
        require(id.matches(Regex("[a-fA-F0-9-]{36}")))
        require(text.isNotBlank() && text.length <= 500 && startMs >= 0 && endMs > startMs && endMs <= duration) { "Text layers must fit the timeline." }
        require(font in listOf("sans-serif","serif","monospace") && size in 16..64)
        require(x.isFinite() && x in -0.8f..0.8f && y.isFinite() && y in -0.8f..0.8f)
        CinemaCurves.validateMotion(motion,1_800_000);require(motionOffsetMs in 0..1_800_000)
        require(rotation.isFinite() && rotation in -180f..180f)
    }
    fun json() = JSONObject().put("id",id).put("text",text).put("start",startMs).put("end",endMs)
        .put("font",font).put("color",color).put("background",background).put("size",size)
        .put("x",x).put("y",y).put("rotation",rotation).put("animation",animation.name).put("motion",CinemaCurves.motionJson(motion)).put("motionOffset",motionOffsetMs)
    companion object {
        fun decode(j: JSONObject) = StudioTextLayer(j.getString("id"),j.getString("text"),j.getLong("start"),j.getLong("end"),
            j.getString("font"),j.getInt("color"),j.getInt("background"),j.getInt("size"),j.getDouble("x").toFloat(),
            j.getDouble("y").toFloat(),j.getDouble("rotation").toFloat(),CreatorAnimation.valueOf(j.getString("animation")),CinemaCurves.motionDecode(j.optJSONArray("motion")),j.optLong("motionOffset",0))
    }
}

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal object StudioTextOverlays {
    fun overlays(layers: List<StudioTextLayer>, clock: StudioEffectClock): List<TextOverlay> = layers.map { layer ->
        object: TextOverlay() {
            private var lastActive:Boolean?=null
            private var canvasScale=1f
            override fun configure(videoSize: androidx.media3.common.util.Size) {
                super.configure(videoSize)
                // Reference size is 720 high: the same title retains its relative size
                // for low-resolution imports, portrait video and higher-resolution exports.
                canvasScale=videoSize.height/720f
            }
            private fun time(us: Long) = clock.outputMs(us)
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
                if(active!=lastActive) {android.util.Log.i("VGStudio","text.layer.active=$active timeMs=$t exporting=${clock.exporting}");lastActive=active}
                val phase=((t-layer.startMs)/250f).coerceIn(0f,1f)
                val alpha=if(!active) 0f else if(layer.animation==CreatorAnimation.FADE) phase else 1f
                val scale=if(layer.animation==CreatorAnimation.POP) 0.75f+0.25f*phase else 1f
                val pose=CinemaCurves.motion(layer.motion,t-layer.startMs+layer.motionOffsetMs)
                return OverlaySettings.Builder().setAlphaScale(alpha*pose.opacity).setScale(scale*pose.scale*canvasScale,scale*pose.scale*canvasScale)
                    .setBackgroundFrameAnchor((layer.x+pose.x).coerceIn(-1f,1f),(layer.y+pose.y+if(layer.animation==CreatorAnimation.SLIDE) (1-phase)*0.15f else 0f).coerceIn(-1f,1f))
                    .setOverlayFrameAnchor(0f,0f).setRotationDegrees(layer.rotation+pose.rotation).build()
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
