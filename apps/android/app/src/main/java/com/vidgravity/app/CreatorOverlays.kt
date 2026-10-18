package com.vidgravity.app

import android.content.Context
import android.graphics.*
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import androidx.media3.effect.*

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal object CreatorOverlays {
    fun text(value:String,p:StudioProject,size:Int=p.tools.captionSize)=SpannableString(value.ifBlank {" "}).apply {
        setSpan(ForegroundColorSpan(p.tools.captionColor),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(AbsoluteSizeSpan(size,true),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(TypefaceSpan(p.tools.captionFont),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    fun captionSettings(p:StudioProject,timeMs:Long):OverlaySettings {
        val cue=p.tools.captions.firstOrNull {timeMs>=it.startMs && timeMs<it.endMs}
        val phase=if(cue==null) 1f else ((timeMs-cue.startMs)/250f).coerceIn(0f,1f)
        val s=p.tools.social
        return OverlaySettings.Builder().setBackgroundFrameAnchor(0f,p.tools.captionY+(if(s.animation==CreatorAnimation.SLIDE) (1-phase)*0.15f else 0f))
            .setOverlayFrameAnchor(0f,0f).apply {
                if(s.animation==CreatorAnimation.FADE) setAlphaScale(phase)
                if(s.animation==CreatorAnimation.POP) {val scale=0.75f+0.25f*phase;setScale(scale,scale)}
            }.build()
    }
    fun overlays(context:Context?,p:StudioProject,offset:Long,speed:Float):List<TextureOverlay> {
        val s=p.tools.social; val result=mutableListOf<TextureOverlay>()
        if(s.watermark.isNotBlank()) result+=object:TextOverlay() {
            override fun getText(presentationTimeUs:Long)=text(s.watermark,p,18)
            override fun getOverlaySettings(presentationTimeUs:Long)=OverlaySettings.Builder().setBackgroundFrameAnchor(s.x,s.y).setOverlayFrameAnchor(0f,0f).build()
        }
        if(s.logo!=null) {
            require(context!=null) {"Logo rendering needs a local media context."}
            val bitmap=context.contentResolver.openInputStream(android.net.Uri.parse(s.logo))!!.use {BitmapFactory.decodeStream(it)} ?: error("Brand logo is missing. Remove or replace branding.")
            result+=BitmapOverlay.createStaticBitmapOverlay(bitmap,OverlaySettings.Builder().setScale(0.18f,0.18f).setBackgroundFrameAnchor(s.x,-s.y).build())
        }
        if(s.lowerThird.isNotBlank()) result+=object:TextOverlay() {
            override fun getText(presentationTimeUs:Long)=text(if(offset+(presentationTimeUs/1000/speed).toLong()<5000) s.lowerThird else " ",p,24)
            override fun getOverlaySettings(presentationTimeUs:Long)=OverlaySettings.Builder().setBackgroundFrameAnchor(0f,-0.55f).build()
        }
        if(s.progress) result+=object:BitmapOverlay() {
            private val bitmap=Bitmap.createBitmap(512,8,Bitmap.Config.ARGB_8888)
            private val paint=Paint().apply {color=p.tools.captionColor}
            override fun getBitmap(presentationTimeUs:Long):Bitmap {
                val fraction=((offset+presentationTimeUs/1000/speed)/p.durationMs).coerceIn(0f,1f)
                bitmap.eraseColor(Color.TRANSPARENT);Canvas(bitmap).drawRect(0f,0f,512*fraction,8f,paint);return bitmap
            }
            override fun getOverlaySettings(presentationTimeUs:Long)=OverlaySettings.Builder().setBackgroundFrameAnchor(0f,-0.94f).build()
        }
        return result
    }
}
