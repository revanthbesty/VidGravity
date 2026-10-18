package com.vidgravity.app

import android.graphics.*

internal object SocialPreview {
    /** Same normalized crop and fit/fill geometry as native effects, no text/animation simulation. */
    fun draw(source:Bitmap,frame:StudioFrame,aspect:StudioAspect,fit:CreatorFit,guides:Boolean):Bitmap {
        val height=320;val width=(height*aspect.ratio).toInt().coerceAtLeast(1)
        val out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(out);canvas.drawColor(Color.BLACK)
        val src=Rect((source.width*frame.left).toInt(),(source.height*frame.top).toInt(),(source.width*frame.right).toInt(),(source.height*frame.bottom).toInt())
        val scale=if(fit==CreatorFit.FILL) maxOf(width.toFloat()/src.width(),height.toFloat()/src.height()) else minOf(width.toFloat()/src.width(),height.toFloat()/src.height())
        val w=src.width()*scale;val h=src.height()*scale
        canvas.drawBitmap(source,src,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),Paint(Paint.FILTER_BITMAP_FLAG))
        if(guides) canvas.drawRect(width*0.1f,height*0.15f,width*0.9f,height*0.8f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.YELLOW;style=Paint.Style.STROKE;strokeWidth=2f})
        return out
    }
}
