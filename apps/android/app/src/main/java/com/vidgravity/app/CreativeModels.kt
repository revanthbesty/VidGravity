package com.vidgravity.app

import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/** Normalized source coordinates; stored per clip and rendered non-destructively. */
data class StudioFrame(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    fun validate() {
        require(listOf(left,top,right,bottom).all { it.isFinite() && it in 0f..1f })
        require(right-left >= 0.05f && bottom-top >= 0.05f) { "Framing must retain at least 5% of each source dimension." }
    }
}
data class CreativeRange(val startMs: Long, val endMs: Long) { val durationMs get() = endMs-startMs }
data class FrameObservation(val timeMs: Long, val luma: Float, val contrast: Float, val difference: Float,
    val faceX: Float? = null, val faceY: Float? = null, val aspect: Float = 16f/9f)
data class AudioObservation(val startMs: Long, val endMs: Long, val rms: Float)
data class CreativeAnalysis(val sourceClipId: String, val analyzedMs: Long, val frames: List<FrameObservation>,
    val audio: List<AudioObservation>, val audioStatus: String, val faceStatus: String) {
    val scenes get() = frames.filter {it.difference>0.18f}.map {it.timeMs}
    val silence get() = CreativeRules.silence(audio)
    val highlight get() = CreativeRules.highlight(frames,analyzedMs)
    val exposure get() = ((0.45f-(frames.map {it.luma}.average().takeIf {it.isFinite()} ?: 0.45).toFloat())*0.5f).coerceIn(-0.2f,0.2f)
    val dominantFace get() = frames.firstOrNull {it.faceX!=null}
}

object CreativeRules {
    fun silence(windows: List<AudioObservation>, threshold: Float = 0.008f): List<CreativeRange> {
        val out=mutableListOf<CreativeRange>(); var start:Long?=null; var end=0L
        for(w in windows.sortedBy {it.startMs}) {
            if(w.rms < threshold) {
                if(start!=null && w.startMs>end+20) {if(end-start>=600) out+=CreativeRange(start,end); start=null}
                if(start==null) start=w.startMs
                end=w.endMs
            } else if(start!=null) {if(end-start>=600) out+=CreativeRange(start,end); start=null}
        }
        if(start!=null && end-start>=600) out+=CreativeRange(start,end)
        return out
    }
    fun highlight(frames: List<FrameObservation>, duration: Long): CreativeRange {
        require(duration>=100)
        val best=frames.maxByOrNull {it.difference*2+it.contrast-abs(it.luma-0.5f)}?.timeMs ?: 0
        val length=minOf(10_000L,duration); val start=(best-length/2).coerceIn(0,duration-length)
        return CreativeRange(start,start+length)
    }
    fun keepAudible(duration:Long,silence:List<CreativeRange>): List<CreativeRange> {
        val out=mutableListOf<CreativeRange>(); var cursor=0L
        for(s in silence.sortedBy {it.startMs}) {
            val start=s.startMs.coerceIn(cursor,duration); val end=s.endMs.coerceIn(start,duration)
            if(start-cursor>=100) out+=CreativeRange(cursor,start)
            cursor=end
        }
        if(duration-cursor>=100) out+=CreativeRange(cursor,duration)
        return out
    }
    fun portraitFrame(sourceAspect:Float,centerX:Float=0.5f,centerY:Float=0.5f): StudioFrame {
        require(sourceAspect.isFinite() && sourceAspect>0)
        val desired=9f/16f
        val width=minOf(1f,desired/sourceAspect).coerceAtLeast(0.05f)
        val height=minOf(1f,sourceAspect/desired).coerceAtLeast(0.05f)
        val left=(centerX-width/2).coerceIn(0f,1f-width); val top=(centerY-height/2).coerceIn(0f,1f-height)
        return StudioFrame(left,top,left+width,top+height).also {it.validate()}
    }
    /** A derived cut keeps source video/audio. Timed overlays/music remain in the source project. */
    fun derivedCut(project:StudioProject,clip:StudioClip,ranges:List<CreativeRange>,suffix:String): StudioProject {
        require(ranges.isNotEmpty() && ranges.size<=12) {"This cut needs 1–12 audible segments. Use manual trimming for finer edits."}
        val clips=ranges.map {r ->
            require(r.startMs>=0 && r.endMs<=clip.durationMs && r.durationMs>=100)
            clip.copy(id=UUID.randomUUID().toString(),startMs=clip.startMs+r.startMs,endMs=clip.startMs+r.endMs)
        }
        return project.copy(id=UUID.randomUUID().toString(),title=(project.title.take(75)+" · "+suffix).take(100),clips=clips,
            musicUri=null,voiceUri=null,tools=project.tools.copy(captions=emptyList(),audio=emptyList()),updatedAt=System.currentTimeMillis()).also {it.validate()}
    }
}

data class CreativeTemplate(val id:String,val label:String,val aspect:StudioAspect,val seconds:Int,val opening:String,val brightness:Float,val saturation:Float)
object CreativeTemplates {
    val all=listOf(
        CreativeTemplate("reels","Instagram Reels",StudioAspect.PORTRAIT,15,"Watch this",0.04f,10f),
        CreativeTemplate("shorts","YouTube Shorts",StudioAspect.PORTRAIT,30,"Here's the idea",0.02f,5f),
        CreativeTemplate("status","WhatsApp Status",StudioAspect.PORTRAIT,30,"A moment to share",0.04f,0f),
        CreativeTemplate("stories","Stories",StudioAspect.PORTRAIT,15,"Today’s story",0.05f,5f),
        CreativeTemplate("product","Product promotion",StudioAspect.SQUARE,15,"Meet your next favorite",0.06f,10f),
        CreativeTemplate("travel","Travel",StudioAspect.WIDE,30,"Go somewhere new",0.02f,15f),
        CreativeTemplate("birthday","Birthday & celebration",StudioAspect.PORTRAIT,15,"Celebrate this moment",0.08f,12f),
        CreativeTemplate("education","Education",StudioAspect.WIDE,30,"Learn it step by step",0.03f,0f))
    fun create(source:StudioProject,template:CreativeTemplate,text:String,seconds:Int=template.seconds,music:String?=null):StudioProject {
        require(seconds in 1..60 && text.isNotBlank() && text.length<=160)
        var remaining=minOf(seconds*1000L,source.durationMs)
        val clips=source.clips.mapNotNull {clip ->
            if(remaining<=0) null else {
                val length=minOf(clip.playbackDurationMs,remaining)
                val end=minOf(clip.endMs,clip.startMs+(length*clip.speed).toLong())
                if(end-clip.startMs<100) null else clip.copy(id=UUID.randomUUID().toString(),endMs=end).also {remaining-=it.playbackDurationMs}
            }
        }
        val duration=clips.sumOf {it.playbackDurationMs}
        return source.copy(id=UUID.randomUUID().toString(),title=(template.label+" · "+source.title).take(100),clips=clips,
            overlay="",fadeEdges=true,musicUri=music,voiceUri=null,
            tools=source.tools.copy(aspect=template.aspect,brightness=template.brightness,saturation=template.saturation,
                captions=listOf(StudioCaption(0,minOf(3000L,duration),text)),audio=emptyList()),updatedAt=System.currentTimeMillis()).also {it.validate()}
    }
}

data class CreativeBrief(val topic:String,val audience:String="viewers",val style:String="Helpful")
data class CreativeCopy(val titles:List<String>,val description:String,val hashtags:List<String>,val hook:String,val script:List<String>,val thumbnail:String,val workflow:String,val cta:String="",val repurpose:String="")
interface CreativeTextProvider {
    val label:String
    val genuineInference:Boolean
    suspend fun generate(brief:CreativeBrief):CreativeCopy
}
class OfflineCreativeRules:CreativeTextProvider {
    override val label="Offline writing templates · rule-based, no language model"
    override val genuineInference=false
    override suspend fun generate(brief:CreativeBrief):CreativeCopy {
        val topic=brief.topic.trim().take(100); require(topic.isNotEmpty()) {"Enter a topic."}
        val audience=brief.audience.trim().take(60).ifEmpty {"viewers"}
        val tags=topic.split(Regex("[^\\p{L}\\p{N}]+")).filter {it.length>2}.distinct().take(5).map {"#"+it.replaceFirstChar(Char::uppercase)}
        return CreativeCopy(listOf("$topic: start here","A fresh look at $topic","$topic in three steps"),
            "Explore $topic with a ${brief.style.lowercase()} guide for $audience. Save this video and share what you would try next.",
            (tags+listOf("#VideoCreator","#LearnAndCreate")).distinct(),"What if $topic could be simpler?",
            listOf("0–3s: Show the result and ask one clear question.","3–12s: Demonstrate one practical idea about $topic for $audience.","12–20s: Show the outcome, add captions, and invite a specific next step."),
            topic.take(35),"Choose a clear opening frame, remove pauses after reviewing silence suggestions, keep captions readable, preview the result and export a short test first.","Save this guide to $topic and share one thing you will try.","Turn one clear point about $topic into a short clip; use Social Creator to review fit/framing for vertical, square and landscape versions.")
    }
}
object CreativeCapabilities {
    fun offlineDictation(api:Int,providerAvailable:Boolean)=api>=31 && providerAvailable
    const val videoTranscriptionAvailable=false
    const val bundledLanguageModelBytes=0L
}
