package com.vidgravity.app

import android.content.Context
import android.view.Surface
import androidx.media3.common.Format
import androidx.media3.transformer.Codec
import androidx.media3.transformer.DefaultDecoderFactory

/** API29 export surfaces can discard queued frames despite KEY_ALLOW_FRAME_DROP=0. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class StudioExportDecoders private constructor(private val delegate:Codec.DecoderFactory):Codec.DecoderFactory by delegate {
    constructor(context:Context):this(DefaultDecoderFactory.Builder(context).build())
    override fun createForVideoDecoding(format:Format,outputSurface:Surface,requestSdrToneMapping:Boolean):Codec {
        val decoder=delegate.createForVideoDecoding(format,outputSurface,requestSdrToneMapping)
        if(android.os.Build.VERSION.SDK_INT!=29) return decoder
        // Match Media3's pre-29 workaround: consume each surface frame before releasing another.
        // Otherwise the API29 trace shows14 decoder outputs but only10 surface inputs, then a20s EOS wait.
        android.util.Log.i("VGStudio","export.decoder.backpressure codec=${decoder.name} maxPending=1")
        return object:Codec by decoder {override fun getMaxPendingFrameCount()=1}
    }
}
