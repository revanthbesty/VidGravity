package com.vidgravity.app

import android.graphics.Matrix
import androidx.media3.common.util.Size
import androidx.media3.effect.MatrixTransformation

/** H.264 YUV420 needs even dimensions, including source-aspect portrait output.
 * Keep the requested height and image proportions; pad at most one pixel per edge dimension.
 * This is geometry alignment, not an encoder resolution/bitrate fallback.
 */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class StudioEncoderCanvas:MatrixTransformation {
    private val matrix=Matrix()
    override fun configure(inputWidth:Int,inputHeight:Int):Size {
        require(inputWidth>0 && inputHeight>0)
        val width=inputWidth+(inputWidth%2)
        val height=inputHeight+(inputHeight%2)
        matrix.setScale(inputWidth.toFloat()/width,inputHeight.toFloat()/height)
        if(width!=inputWidth || height!=inputHeight) android.util.Log.i("VGStudio","export.canvas ${inputWidth}x${inputHeight} -> ${width}x${height}")
        return Size(width,height)
    }
    override fun getMatrix(presentationTimeUs:Long):Matrix=matrix
    override fun isNoOp(inputWidth:Int,inputHeight:Int):Boolean=inputWidth%2==0 && inputHeight%2==0
}
