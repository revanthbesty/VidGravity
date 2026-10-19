package com.vidgravity.app

import android.content.Context
import android.graphics.Matrix
import androidx.media3.effect.MatrixTransformation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** Move source pixels into the saved crop; the crop dimensions remain fixed throughout a pan. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class SmartPan(private val clip:StudioClip,private val clock:StudioEffectClock):MatrixTransformation {
    override fun getMatrix(presentationTimeUs:Long):Matrix {
        val source=(clip.startMs+clock.clipUs(presentationTimeUs)/1000.0*clip.speed).toLong().coerceIn(clip.startMs,clip.endMs)
        val (x,y)=SmartFraming.center(clip,source)
        return Matrix().apply {setTranslate((clip.frame.left+clip.frame.right-2*x),2*y-clip.frame.top-clip.frame.bottom)}
    }
}

/** Same bounded PCM gain in preview, audition and export; no network audio processing. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class SmartAudioRenderers(context:Context,gain:Float):DefaultRenderersFactory(context) {
    val processor=StudioVolumeProcessor(gain)
    override fun buildAudioSink(context:Context,enableFloatOutput:Boolean,enableAudioTrackPlaybackParams:Boolean):AudioSink =
        DefaultAudioSink.Builder(context).setEnableFloatOutput(false).setAudioProcessors(arrayOf(processor)).build()
}
