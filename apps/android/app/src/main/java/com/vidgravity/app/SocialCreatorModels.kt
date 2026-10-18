package com.vidgravity.app

import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToLong

enum class CreatorFit { FIT, FILL }
enum class CreatorAnimation { NONE, FADE, POP, SLIDE }
data class CreatorStyle(
    val fit:CreatorFit=CreatorFit.FIT, val animation:CreatorAnimation=CreatorAnimation.NONE,
    val watermark:String="", val logo:String?=null, val x:Float=0.65f,val y:Float=-0.75f,
    val progress:Boolean=false,val lowerThird:String=""
) {
    fun validate() {
        require(watermark.length<=80 && lowerThird.length<=100)
        require(logo==null || logo.startsWith("content://") && logo.length<=8192)
        require(x.isFinite() && y.isFinite() && x in -0.8f..0.8f && y in -0.8f..0.8f)
    }
    fun json()=JSONObject().put("fit",fit.name).put("animation",animation.name).put("watermark",watermark)
        .put("logo",logo ?: JSONObject.NULL).put("x",x).put("y",y).put("progress",progress).put("lowerThird",lowerThird)
    companion object {fun decode(j:JSONObject?)=if(j==null) CreatorStyle() else CreatorStyle(CreatorFit.valueOf(j.getString("fit")),CreatorAnimation.valueOf(j.getString("animation")),j.getString("watermark"),
        if(j.isNull("logo")) null else j.getString("logo"),j.getDouble("x").toFloat(),j.getDouble("y").toFloat(),j.getBoolean("progress"),j.getString("lowerThird")).also {it.validate()}}
}
data class CreatorBrand(val name:String="",val logo:String?=null,val color:Int=-1,val font:String="sans-serif",
    val x:Float=0.65f,val y:Float=-0.75f,val intro:String="",val outro:String="",val resolution:StudioResolution=StudioResolution.HD,
    val fps:Int=30,val bitrate:Int=5_000_000) {
    fun validate() {require(name.length<=80 && intro.length<=100 && outro.length<=100)
        require(font in listOf("sans-serif","serif","monospace")); CreatorStyle(watermark=name,logo=logo,x=x,y=y).validate()
        StudioTools(frameRate=fps,bitrate=bitrate).validate(1000)}
    fun apply(p:StudioProject):StudioProject {
        validate(); val cues=p.tools.captions.toMutableList()
        if(intro.isNotBlank()) cues+=StudioCaption(0,minOf(2000,p.durationMs),intro)
        if(outro.isNotBlank()) cues+=StudioCaption(maxOf(0,p.durationMs-2000),p.durationMs,outro)
        return p.copy(resolution=resolution,tools=p.tools.copy(captionColor=color,captionFont=font,frameRate=fps,bitrate=bitrate,
            captions=cues,social=p.tools.social.copy(watermark=name,logo=logo,x=x,y=y))).also {it.validate()}
    }
    fun json():String {validate(); return JSONObject().put("schema",1).put("name",name).put("logo",logo ?: JSONObject.NULL).put("color",color).put("font",font)
        .put("x",x).put("y",y).put("intro",intro).put("outro",outro).put("resolution",resolution.name).put("fps",fps).put("bitrate",bitrate).toString()}
    companion object {fun decode(raw:String):CreatorBrand {require(raw.length<16_000); val j=JSONObject(raw); require(j.getInt("schema")==1)
        return CreatorBrand(j.getString("name"),if(j.isNull("logo")) null else j.getString("logo"),j.getInt("color"),j.getString("font"),j.getDouble("x").toFloat(),j.getDouble("y").toFloat(),j.getString("intro"),j.getString("outro"),StudioResolution.valueOf(j.getString("resolution")),j.getInt("fps"),j.getInt("bitrate")).also {it.validate()}}}
}
data class SocialRecipe(val id:String,val label:String,val aspect:StudioAspect,val seconds:Int,val opening:String,val closing:String,val animation:CreatorAnimation=CreatorAnimation.FADE)
object SocialCreator {
    val workflows=listOf(
        SocialRecipe("reels","Instagram Reels",StudioAspect.PORTRAIT,30,"Watch this","Save for later"),
        SocialRecipe("shorts","YouTube Shorts",StudioAspect.PORTRAIT,30,"Start here","What would you try?"),
        SocialRecipe("status","WhatsApp Status",StudioAspect.PORTRAIT,30,"A moment worth sharing","Your story"),
        SocialRecipe("stories","Instagram / Facebook Stories",StudioAspect.PORTRAIT,15,"Today’s story","Reply with your thoughts"),
        SocialRecipe("youtube","YouTube landscape",StudioAspect.WIDE,60,"Welcome","Thanks for watching"),
        SocialRecipe("ad","Promotional advertisement",StudioAspect.FEED,20,"Meet your next favorite","Learn more"),
        SocialRecipe("education","Educational video",StudioAspect.WIDE,45,"One idea, explained","Try it yourself"),
        SocialRecipe("product","Product showcase",StudioAspect.SQUARE,20,"Take a closer look","Explore the details"),
        SocialRecipe("travel","Travel & lifestyle",StudioAspect.PORTRAIT,30,"Come along","Where next?"))
    val templates=listOf(
        SocialRecipe("short","Trending-style short",StudioAspect.PORTRAIT,15,"Here’s the result","Save this idea",CreatorAnimation.POP),
        SocialRecipe("promo","Product promotion",StudioAspect.FEED,20,"Made for your day","Discover more"),
        SocialRecipe("before","Before & after",StudioAspect.SQUARE,12,"BEFORE","AFTER"),
        SocialRecipe("explain","Educational explainer",StudioAspect.WIDE,30,"The question","The takeaway",CreatorAnimation.SLIDE),
        SocialRecipe("motivation","Motivational story",StudioAspect.PORTRAIT,15,"Start small","Keep going"),
        SocialRecipe("travel","Travel highlights",StudioAspect.PORTRAIT,20,"The journey","The memory"),
        SocialRecipe("celebrate","Celebration",StudioAspect.SQUARE,20,"Celebrate together","More moments ahead",CreatorAnimation.POP),
        SocialRecipe("business","Business advertisement",StudioAspect.FEED,20,"Your next solution","Get in touch"),
        SocialRecipe("personal","Personal branding",StudioAspect.WIDE,20,"Meet the creator","Follow the journey"),
        SocialRecipe("intro","Intro & outro",StudioAspect.WIDE,15,"Welcome to my channel","See you next time"))
    fun fitDuration(p:StudioProject,ms:Long):StudioProject {
        require(ms>=100); var remaining=minOf(ms,p.durationMs)
        val clips=p.clips.mapNotNull {c ->
            if(remaining<100) null else {
                val output=minOf(remaining,c.playbackDurationMs)
                val end=minOf(c.endMs,c.startMs+(output*c.speed).toLong())
                if(end-c.startMs<100) null else c.copy(endMs=end).also {remaining-=it.playbackDurationMs}
            }}
        val duration=clips.sumOf {it.playbackDurationMs}
        return p.copy(clips=clips,tools=p.tools.copy(captions=p.tools.captions.mapNotNull {if(it.startMs>=duration) null else it.copy(endMs=minOf(it.endMs,duration))},
            audio=p.tools.audio.filter {it.offsetMs<duration})).also {it.validate()}
    }
    fun create(p:StudioProject,r:SocialRecipe,title:String,seconds:Int=r.seconds):StudioProject {
        require(seconds in 1..1800 && title.isNotBlank())
        val fitted=fitDuration(p.copy(id=UUID.randomUUID().toString(),title=(r.label+" · "+title).take(100),tools=p.tools.copy(captions=emptyList(),audio=emptyList()),musicUri=null,voiceUri=null),seconds*1000L)
        val middle=(fitted.durationMs/2).coerceAtLeast(1)
        val cues=if(r.id=="before") listOf(StudioCaption(0,middle,r.opening),StudioCaption(middle,fitted.durationMs,r.closing))
            else listOf(StudioCaption(0,minOf(2500,fitted.durationMs),title.take(160)),StudioCaption(maxOf(0,fitted.durationMs-2000),fitted.durationMs,r.closing))
        return fitted.copy(fadeEdges=true,updatedAt=System.currentTimeMillis(),tools=fitted.tools.copy(aspect=r.aspect,captions=cues,social=fitted.tools.social.copy(animation=r.animation))).also {it.validate()}
    }
    fun variants(p:StudioProject,aspects:List<StudioAspect>,fit:CreatorFit):List<StudioProject> {
        require(aspects.isNotEmpty() && aspects.size<=5 && aspects.distinct().size==aspects.size && StudioAspect.SOURCE !in aspects)
        return aspects.map {a -> p.copy(id=UUID.randomUUID().toString(),title=(p.title.take(85)+" · "+a.label),updatedAt=System.currentTimeMillis(),
            tools=p.tools.copy(aspect=a,social=p.tools.social.copy(fit=fit))).also {it.validate()} }
    }
    fun estimatedBytes(p:StudioProject):Long = ((if(p.tools.bitrate>0) p.tools.bitrate else 8_000_000).toLong()+192_000)*p.durationMs/8000
    fun requireSpace(available:Long,estimate:Long) {require(available>=estimate*2+32L*1024*1024) {"Insufficient free storage. Free space or use a smaller preset; the source project is preserved."}}
    fun cutGrid(p:StudioProject,index:Int,bpm:Int,jump:Boolean=false):StudioProject {
        require(bpm in 40..240); val c=p.clips[index]; val step=(60_000.0/bpm*c.speed).roundToLong().coerceAtLeast(100)
        val ranges=mutableListOf<StudioClip>(); var start=c.startMs; var n=0
        while(start<c.endMs) {val end=minOf(c.endMs,start+step); if(end-start>=100 && (!jump || n%2==0)) ranges+=c.copy(id=UUID.randomUUID().toString(),startMs=start,endMs=end);start=end;n++}
        require(ranges.size+p.clips.size-1<=12) {"This beat grid needs more than 12 clips. Trim the source or lower BPM."}
        val next=p.copy(id=UUID.randomUUID().toString(),title=(p.title.take(75)+if(jump) " jump cut" else " beat grid"),clips=p.clips.flatMapIndexed {i,clip -> if(i==index) ranges else listOf(clip)},tools=p.tools.copy(captions=emptyList(),audio=emptyList()),musicUri=null,voiceUri=null)
        return next.also {it.validate()}
    }
    fun speedRamp(p:StudioProject,index:Int):StudioProject {
        val c=p.clips[index];require(c.durationMs>=600 && p.clips.size<=10)
        val thirds=(0..2).map {i -> c.copy(id=UUID.randomUUID().toString(),startMs=c.startMs+c.durationMs*i/3,endMs=c.startMs+c.durationMs*(i+1)/3,speed=listOf(0.5f,1f,2f)[i])}
        return p.copy(id=UUID.randomUUID().toString(),title=p.title.take(80)+" stepped speed",clips=p.clips.flatMapIndexed {i,clip -> if(i==index) thirds else listOf(clip)},tools=p.tools.copy(captions=emptyList(),audio=emptyList()),musicUri=null,voiceUri=null).also {it.validate()}
    }
}
