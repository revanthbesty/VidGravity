package com.vidgravity.app

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Editable image projects keep source references, never screenshots of the app UI. */
data class StudioThumbnail(
    val id:String=UUID.randomUUID().toString(),val projectId:String,val source:String,val durationMs:Long,val timeMs:Long,
    val title:String="Your story",val subtitle:String="",val badge:String="PLAY",val aspect:StudioAspect=StudioAspect.WIDE,
    val zoom:Float=0f,val brightness:Float=0f,val contrast:Float=0f,val color:Int=-1,val textY:Float=0.72f,val font:String="sans-serif",
    val frame:StudioFrame=StudioFrame(),val rotation:Int=0,val updatedAt:Long=System.currentTimeMillis()
) {
    fun validate() {
        require(id.matches(Regex("[a-fA-F0-9-]{36}")) && projectId.matches(Regex("[a-fA-F0-9-]{36}")))
        require(source.startsWith("content://") && source.length<=8192 && durationMs in 100..1_800_000 && timeMs in 0 until durationMs)
        require(title.isNotBlank() && title.length<=100 && subtitle.length<=120 && badge in listOf("PLAY","NEW","TIP","NONE"))
        require(aspect!=StudioAspect.SOURCE && zoom.isFinite() && zoom in 0f..0.4f && brightness.isFinite() && brightness in -1f..1f && contrast.isFinite() && contrast in -0.9f..0.9f)
        require(textY.isFinite() && textY in 0.2f..0.85f && font in listOf("sans-serif","serif","monospace") && rotation in listOf(0,90,180,270)); frame.validate()
    }
}
internal object ThumbnailCodec {
    fun encode(t:StudioThumbnail):String {
        t.validate()
        return JSONObject().put("schema",1).put("id",t.id).put("projectId",t.projectId).put("source",t.source).put("duration",t.durationMs).put("time",t.timeMs)
            .put("title",t.title).put("subtitle",t.subtitle).put("badge",t.badge).put("aspect",t.aspect.name).put("zoom",t.zoom).put("brightness",t.brightness)
            .put("contrast",t.contrast).put("color",t.color).put("textY",t.textY).put("font",t.font).put("rotation",t.rotation).put("updatedAt",t.updatedAt)
            .put("frame",JSONObject().put("left",t.frame.left).put("top",t.frame.top).put("right",t.frame.right).put("bottom",t.frame.bottom)).toString()
    }
    fun decode(raw:String):StudioThumbnail {
        require(raw.length<24_000)
        val j=JSONObject(raw); require(j.getInt("schema")==1)
        val f=j.getJSONObject("frame")
        return StudioThumbnail(j.getString("id"),j.getString("projectId"),j.getString("source"),j.getLong("duration"),j.getLong("time"),
            j.getString("title"),j.getString("subtitle"),j.getString("badge"),StudioAspect.valueOf(j.getString("aspect")),j.getDouble("zoom").toFloat(),
            j.getDouble("brightness").toFloat(),j.getDouble("contrast").toFloat(),j.getInt("color"),j.getDouble("textY").toFloat(),j.getString("font"),
            StudioFrame(f.getDouble("left").toFloat(),f.getDouble("top").toFloat(),f.getDouble("right").toFloat(),f.getDouble("bottom").toFloat()),j.getInt("rotation"),j.getLong("updatedAt")).also {it.validate()}
    }
}
internal class ThumbnailRepository(context:Context) {
    companion object {private val diskLock=Any()}
    private val root=File(context.filesDir,"studio-thumbnails")
    private fun file(id:String):File {require(id.matches(Regex("[a-fA-F0-9-]{36}"))); return File(root,"$id.json")}
    fun save(t:StudioThumbnail):Unit = synchronized(diskLock) {
        val target=file(t.id)
        if(target.exists() || File(target.path+".bak").exists()) {
            val previous=load(t.id) // Unknown/corrupt versions are preserved rather than overwritten.
            if(previous.updatedAt>t.updatedAt) return@synchronized
        }
        val text=ThumbnailCodec.encode(t); check(root.exists() || root.mkdirs())
        val atomic=AtomicFile(file(t.id)); val stream=atomic.startWrite()
        try {stream.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream)} catch(failure:Exception) {atomic.failWrite(stream); throw failure}
    }
    fun load(id:String)=synchronized(diskLock) {ThumbnailCodec.decode(AtomicFile(file(id)).openRead().bufferedReader().use {it.readText()})}
    fun list(projectId:String)=root.listFiles().orEmpty().map {it.name.removeSuffix(".bak").removeSuffix(".json")}.distinct()
        .mapNotNull {runCatching {load(it)}.getOrNull()}.filter {it.projectId==projectId}.sortedByDescending {it.updatedAt}
    fun delete(id:String)=synchronized(diskLock) {AtomicFile(file(id)).delete()}
}

internal object ThumbnailRenderer {
    /** All callers use IO; decode bounded frames even when the source is 4K. */
    fun frame(context:Context,source:String,timeMs:Long,maxSize:Int=1280):Bitmap {
        if(android.os.Build.VERSION.SDK_INT<27) error("Thumbnail frame extraction needs Android 8.1/API27 or newer.")
        val reader=MediaMetadataRetriever()
        try {
            reader.setDataSource(context,Uri.parse(source))
            return reader.getScaledFrameAtTime(timeMs*1000,MediaMetadataRetriever.OPTION_CLOSEST,maxSize,maxSize)
                ?: error("This frame could not be decoded. Choose another time or relink the video.")
        } finally {reader.release()}
    }
    fun render(context:Context,t:StudioThumbnail,preview:Boolean=false):Bitmap {
        t.validate(); val source=frame(context,t.source,t.timeMs,if(preview) 640 else 1280)
        try {return draw(source,t,if(preview) 360 else 720)} finally {source.recycle()}
    }
    fun draw(source:Bitmap,t:StudioThumbnail,height:Int):Bitmap {
        t.validate()
        val width=(height*t.aspect.ratio).toInt().coerceAtLeast(1)
        val crop=Rect((source.width*t.frame.left).toInt(),(source.height*t.frame.top).toInt(),
            (source.width*t.frame.right).toInt().coerceAtMost(source.width),(source.height*t.frame.bottom).toInt().coerceAtMost(source.height))
        var working=Bitmap.createBitmap(source,crop.left,crop.top,crop.width().coerceAtLeast(1),crop.height().coerceAtLeast(1))
        if(t.rotation!=0) {
            val turned=Bitmap.createBitmap(working,0,0,working.width,working.height,Matrix().apply {postRotate(t.rotation.toFloat())},true)
            if(working!==source && working!==turned) working.recycle()
            working=turned
        }
        try {
            val ratio=width.toFloat()/height
            val w=minOf(working.width.toFloat(),working.height*ratio)*(1-t.zoom)
            val h=w/ratio
            val left=((working.width-w)/2).toInt(); val top=((working.height-h)/2).toInt()
            val src=Rect(left,top,(left+w).toInt().coerceAtLeast(left+1),(top+h).toInt().coerceAtLeast(top+1))
            val output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); val canvas=Canvas(output)
            val gain=1+t.contrast; val offset=t.brightness*255+128*(1-gain)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {colorFilter=ColorMatrixColorFilter(floatArrayOf(gain,0f,0f,0f,offset,0f,gain,0f,0f,offset,0f,0f,gain,0f,offset,0f,0f,0f,1f,0f))}
            canvas.drawBitmap(working,src,Rect(0,0,width,height),paint)
            val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=t.color; typeface=Typeface.create(t.font,Typeface.BOLD); textSize=minOf(width*0.08f,height*0.1f); setShadowLayer(3f,1f,2f,Color.BLACK)}
            val maxWidth=width*0.84f; val lines=mutableListOf<String>(); var line=""
            for(word in t.title.split(Regex("\\s+"))) {
                val next=if(line.isEmpty()) word else "$line $word"
                if(text.measureText(next)>maxWidth && line.isNotEmpty()) {lines+=line; line=word} else line=next
            }
            if(line.isNotEmpty()) lines+=line
            val shown=lines.take(3); val lineHeight=text.textSize*1.2f
            val start=(height*t.textY).coerceAtMost(height-lineHeight*(shown.size+1))
            paint.colorFilter=null; paint.color=0xB3000000.toInt()
            canvas.drawRoundRect(width*0.04f,start-text.textSize,width*0.96f,minOf(height.toFloat(),start+lineHeight*(shown.size+1)),height*0.02f,height*0.02f,paint)
            shown.forEachIndexed {i,value -> canvas.drawText(value,width*0.08f,start+i*lineHeight,text)}
            if(t.subtitle.isNotBlank()) {text.textSize*=0.48f; text.typeface=Typeface.create(t.font,Typeface.NORMAL); canvas.drawText(t.subtitle.take(60),width*0.08f,start+shown.size*lineHeight,text)}
            if(t.badge!="NONE") {
                paint.color=0xFF6D28D9.toInt(); canvas.drawRoundRect(width*0.05f,height*0.06f,width*0.31f,height*0.18f,height*0.02f,height*0.02f,paint)
                text.textSize=height*0.055f; text.color=Color.WHITE; text.typeface=Typeface.DEFAULT_BOLD
                canvas.drawText(t.badge,width*0.08f,height*0.14f,text)
            }
            return output
        } finally {if(working!==source) working.recycle()}
    }
    suspend fun publish(context:Context,t:StudioThumbnail):PublishedMedia {
        val file=File(context.cacheDir,"thumbnail-${UUID.randomUUID()}.png")
        try {
            val bitmap=render(context,t)
            try {currentCoroutineContext().ensureActive(); file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}} finally {bitmap.recycle()}
            currentCoroutineContext().ensureActive()
            return MediaLibraryIndexer.publish(context,file,"${t.title.take(50)}_thumbnail.png",DownloadApi.sha256(file))
        } finally {file.delete()}
    }
}
