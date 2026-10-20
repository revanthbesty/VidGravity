package com.vidgravity.app

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.*

/** The same SDR shader and clocks serve ExoPlayer/Transformer using Media3 1.5.1’s default SDR working space. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class CinemaVideoEffect(private val project:StudioProject,private val clip:StudioClip,private val clock:StudioEffectClock):GlEffect {
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):BaseGlShaderProgram {
        if(useHdr) throw VideoFrameProcessingException("Cinema Studio currently requires SDR BT.709 input. Use an SDR source or reset cinematic effects.")
        return try {Program(context,project,clip,clock)} catch(failure:Exception) {throw VideoFrameProcessingException(failure)}
    }
    private class Program(context:Context,val project:StudioProject,val clip:StudioClip,val clock:StudioEffectClock):BaseGlShaderProgram(false,1) {
        val gl=GlProgram(context,"shaders/cinema_vertex.glsl","shaders/cinema_fragment.glsl")
        val index=project.clips.indexOfFirst {it.id==clip.id}
        val grade=clip.cinema.grade
        val gradeC=floatArrayOf(grade.gamma,Math.toRadians(grade.hue.toDouble()).toFloat(),grade.sharpness,clip.cinema.intensity)
        val transition=floatArrayOf(0f,0f)
        init {
            gl.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            gl.setFloatsUniform("uGradeA",floatArrayOf(grade.brightness,grade.contrast,grade.saturation,grade.exposure))
            gl.setFloatsUniform("uGradeB",floatArrayOf(grade.temperature,grade.tint,grade.highlights,grade.shadows))
            gl.setIntUniform("uLook",clip.cinema.look.ordinal)
        }
        override fun configure(inputWidth:Int,inputHeight:Int):Size {
            gl.setFloatsUniform("uTexel",floatArrayOf(1f/inputWidth,1f/inputHeight));return Size(inputWidth,inputHeight)
        }
        override fun drawFrame(inputTexId:Int,presentationTimeUs:Long) {
            try {
                val local=clock.clipUs(presentationTimeUs)/1000
                val source=(clip.startMs+local*clip.speed).toLong()
                gradeC[3]=CinemaCurves.scalar(clip.cinema.lookKeys,source,clip.cinema.intensity)
                val fade=CinemaTransitions.amount(project,index,local);transition[0]=fade.first;transition[1]=fade.second
                gl.use();gl.setSamplerTexIdUniform("uTexSampler",inputTexId,0)
                gl.setFloatsUniform("uGradeC",gradeC);gl.setFloatsUniform("uTransition",transition)
                gl.setFloatUniform("uTime",(clock.outputMs(presentationTimeUs)%4096000)/1000f)
                gl.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()
            } catch(failure:GlUtil.GlException) {throw VideoFrameProcessingException(failure,presentationTimeUs)}
        }
        override fun release() {try {super.release()} finally {try {gl.delete()} catch(failure:GlUtil.GlException) {throw VideoFrameProcessingException(failure)}}}
    }
}

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class CinemaVideoMotion(private val clip:StudioClip,private val clock:StudioEffectClock):MatrixTransformation {
    override fun getMatrix(presentationTimeUs:Long):Matrix {
        val source=(clip.startMs+clock.clipUs(presentationTimeUs)/1000.0*clip.speed).toLong()
        val pose=CinemaCurves.motion(clip.cinema.motion,source)
        return Matrix().apply {setScale(pose.scale,pose.scale);postRotate(pose.rotation);postTranslate(pose.x,pose.y)}
    }
}

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal object CinemaGraphics {
    private fun image(context:Context,uri:String):Bitmap {
        val parsed=Uri.parse(uri);val opts=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        context.contentResolver.openInputStream(parsed)!!.use {BitmapFactory.decodeStream(it,null,opts)}
        require(opts.outWidth>0 && opts.outHeight>0 && opts.outWidth.toLong()*opts.outHeight<=100_000_000) {"Choose an image below 100 megapixels."}
        opts.inJustDecodeBounds=false;opts.inSampleSize=1
        while(maxOf(opts.outWidth,opts.outHeight)/opts.inSampleSize>512) opts.inSampleSize*=2
        return context.contentResolver.openInputStream(parsed)!!.use {BitmapFactory.decodeStream(it,null,opts)} ?: error("Image overlay could not be decoded. Relink or remove it.")
    }
    fun overlays(context:Context?,p:StudioProject,clock:StudioEffectClock):List<TextureOverlay> = p.tools.graphics.map {g->
        require(context!=null) {"Graphic overlays require a local media context."}
        val bitmap=if(g.kind==CinemaGraphicKind.IMAGE) image(context,g.uri!!) else {
            Bitmap.createBitmap(if(g.kind==CinemaGraphicKind.CIRCLE) 192 else 512,if(g.kind==CinemaGraphicKind.CIRCLE) 192 else 128,Bitmap.Config.ARGB_8888).apply {
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=g.color};val canvas=Canvas(this)
                if(g.kind==CinemaGraphicKind.CIRCLE) canvas.drawOval(0f,0f,width.toFloat(),height.toFloat(),paint)
                else canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),18f,18f,paint)
            }
        }
        object:BitmapOverlay() {
            var scale=1f
            override fun configure(videoSize:Size) {super.configure(videoSize);scale=videoSize.width/1280f}
            override fun getBitmap(presentationTimeUs:Long)=bitmap
            override fun getOverlaySettings(presentationTimeUs:Long):OverlaySettings {
                val t=clock.outputMs(presentationTimeUs);val pose=CinemaCurves.motion(g.motion,t-g.startMs+g.motionOffsetMs)
                return OverlaySettings.Builder().setAlphaScale(if(t in g.startMs until g.endMs) pose.opacity else 0f)
                    .setScale(scale*pose.scale,scale*pose.scale).setRotationDegrees(pose.rotation)
                    .setBackgroundFrameAnchor((g.x+pose.x).coerceIn(-1f,1f),(g.y+pose.y).coerceIn(-1f,1f)).setOverlayFrameAnchor(0f,0f).build()
            }
            // BitmapOverlay owns texture cleanup; the source bitmap stays valid for GL upload.
        }
    }
}
