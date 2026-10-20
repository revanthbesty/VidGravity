package com.vidgravity.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.*

/** One curve format for source-time video/audio and local-time overlay animation. */
enum class CinemaEase(val label:String) {
    LINEAR("Linear"), IN("Ease in"), OUT("Ease out"), IN_OUT("Ease in-out");
    fun fraction(value:Float):Float {
        val t=value.coerceIn(0f,1f)
        return when(this) {LINEAR->t;IN->t*t;OUT->1-(1-t)*(1-t);IN_OUT->t*t*(3-2*t)}
    }
}
data class CinemaScalarKey(val timeMs:Long,val value:Float,val ease:CinemaEase=CinemaEase.LINEAR)
data class CinemaPose(val x:Float=0f,val y:Float=0f,val scale:Float=1f,val rotation:Float=0f,val opacity:Float=1f) {
    fun validate() {require(x.isFinite() && x in -1f..1f && y.isFinite() && y in -1f..1f && scale.isFinite() && scale in 0.25f..3f && rotation.isFinite() && rotation in -180f..180f && opacity.isFinite() && opacity in 0f..1f) {"Motion values are outside the supported canvas bounds."}}
}
data class CinemaMotionKey(val timeMs:Long,val pose:CinemaPose=CinemaPose(),val ease:CinemaEase=CinemaEase.LINEAR)
object CinemaCurves {
    private fun validateTimes(times:List<Long>,limit:Long) {require(times.size<=32 && times==times.distinct().sorted() && times.all {it in 0..limit}) {"Use up to 32 distinct, ordered keyframe times inside the source."}}
    fun validate(keys:List<CinemaScalarKey>,limit:Long,range:ClosedFloatingPointRange<Float>) {validateTimes(keys.map {it.timeMs},limit);require(keys.all {it.value.isFinite() && it.value in range})}
    fun validateMotion(keys:List<CinemaMotionKey>,limit:Long) {validateTimes(keys.map {it.timeMs},limit);keys.forEach {it.pose.validate()}}
    fun scalar(keys:List<CinemaScalarKey>,time:Long,fallback:Float=1f):Float {
        if(keys.isEmpty()) return fallback
        if(time<=keys.first().timeMs) return keys.first().value
        if(time>=keys.last().timeMs) return keys.last().value
        var index=0;while(index<keys.lastIndex-1 && keys[index+1].timeMs<=time) index++
        val a=keys[index];val b=keys[index+1]
        val f=a.ease.fraction((time-a.timeMs).toFloat()/(b.timeMs-a.timeMs))
        return a.value+(b.value-a.value)*f
    }
    fun motion(keys:List<CinemaMotionKey>,time:Long):CinemaPose {
        if(keys.isEmpty()) return CinemaPose()
        val a=keys.lastOrNull {it.timeMs<=time} ?: keys.first();val b=keys.firstOrNull {it.timeMs>=time} ?: keys.last()
        val f=if(a.timeMs==b.timeMs) 0f else a.ease.fraction((time-a.timeMs).toFloat()/(b.timeMs-a.timeMs))
        fun lerp(x:Float,y:Float)=x+(y-x)*f
        return CinemaPose(lerp(a.pose.x,b.pose.x),lerp(a.pose.y,b.pose.y),lerp(a.pose.scale,b.pose.scale),lerp(a.pose.rotation,b.pose.rotation),lerp(a.pose.opacity,b.pose.opacity))
    }
    fun scalarJson(keys:List<CinemaScalarKey>)=JSONArray().apply {keys.forEach {put(JSONObject().put("time",it.timeMs).put("value",it.value).put("ease",it.ease.name))}}
    fun scalarDecode(rows:JSONArray?)=if(rows==null) emptyList() else List(rows.length()) {i->rows.getJSONObject(i).let {CinemaScalarKey(it.getLong("time"),it.getDouble("value").toFloat(),CinemaEase.valueOf(it.getString("ease")))}}
    fun motionJson(keys:List<CinemaMotionKey>)=JSONArray().apply {keys.forEach {k->put(JSONObject().put("time",k.timeMs).put("x",k.pose.x).put("y",k.pose.y).put("scale",k.pose.scale).put("rotation",k.pose.rotation).put("opacity",k.pose.opacity).put("ease",k.ease.name))}}
    fun motionDecode(rows:JSONArray?)=if(rows==null) emptyList() else List(rows.length()) {i->rows.getJSONObject(i).let {CinemaMotionKey(it.getLong("time"),CinemaPose(it.getDouble("x").toFloat(),it.getDouble("y").toFloat(),it.getDouble("scale").toFloat(),it.getDouble("rotation").toFloat(),it.getDouble("opacity").toFloat()),CinemaEase.valueOf(it.getString("ease")))}}
}

enum class CinemaLook(val label:String) {
    ORIGINAL("Original"),WARM("Cinematic Warm"),COOL("Cinematic Cool"),MONO("Black and White"),VINTAGE("Vintage Film"),SEPIA("Sepia"),GRAIN("Film Grain"),VIGNETTE("Vignette"),GLOW("Soft Glow"),CONTRAST("High Contrast"),DREAMY("Dreamy Look"),SHADOWS("Dramatic Shadows"),FADE("Fade · matte"),BLUR("Blur")
}
data class CinemaGrade(val brightness:Float=0f,val contrast:Float=0f,val saturation:Float=0f,val exposure:Float=0f,
    val temperature:Float=0f,val tint:Float=0f,val highlights:Float=0f,val shadows:Float=0f,val gamma:Float=1f,val hue:Float=0f,val sharpness:Float=0f) {
    fun validate() {
        require(listOf(brightness,contrast,saturation,temperature,tint,highlights,shadows).all {it.isFinite() && it in -1f..1f})
        require(exposure.isFinite() && exposure in -2f..2f && gamma.isFinite() && gamma in 0.3f..3f && hue.isFinite() && hue in -180f..180f && sharpness.isFinite() && sharpness in 0f..1f)
    }
    fun json()=JSONObject().put("brightness",brightness).put("contrast",contrast).put("saturation",saturation).put("exposure",exposure).put("temperature",temperature).put("tint",tint).put("highlights",highlights).put("shadows",shadows).put("gamma",gamma).put("hue",hue).put("sharpness",sharpness)
    companion object {fun decode(j:JSONObject?)=if(j==null) CinemaGrade() else CinemaGrade(j.getDouble("brightness").toFloat(),j.getDouble("contrast").toFloat(),j.getDouble("saturation").toFloat(),j.getDouble("exposure").toFloat(),j.getDouble("temperature").toFloat(),j.getDouble("tint").toFloat(),j.getDouble("highlights").toFloat(),j.getDouble("shadows").toFloat(),j.getDouble("gamma").toFloat(),j.getDouble("hue").toFloat(),j.getDouble("sharpness").toFloat())}
}
enum class CinemaTransitionKind(val label:String) {NONE("Cut"),BLACK("Fade to Black"),WHITE("Fade to White")}
data class CinemaTransition(val kind:CinemaTransitionKind=CinemaTransitionKind.NONE,val durationMs:Long=500) {
    fun validate() {require(durationMs in 100..2000)}
    fun json()=JSONObject().put("kind",kind.name).put("duration",durationMs)
    companion object {fun decode(j:JSONObject?)=if(j==null) CinemaTransition() else CinemaTransition(CinemaTransitionKind.valueOf(j.getString("kind")),j.getLong("duration"))}
}
data class CinemaStyle(val look:CinemaLook=CinemaLook.ORIGINAL,val intensity:Float=1f,val grade:CinemaGrade=CinemaGrade(),
    val motion:List<CinemaMotionKey> = emptyList(),val lookKeys:List<CinemaScalarKey> = emptyList(),val exit:CinemaTransition=CinemaTransition()) {
    fun validate(sourceDuration:Long) {grade.validate();exit.validate();require(intensity.isFinite() && intensity in 0f..1f);CinemaCurves.validateMotion(motion,sourceDuration);CinemaCurves.validate(lookKeys,sourceDuration,0f..1f)}
    fun json()=JSONObject().put("look",look.name).put("intensity",intensity).put("grade",grade.json()).put("motion",CinemaCurves.motionJson(motion)).put("lookKeys",CinemaCurves.scalarJson(lookKeys)).put("exit",exit.json())
    fun colorOnly()=copy(motion=emptyList(),lookKeys=emptyList(),exit=CinemaTransition())
    companion object {fun decode(j:JSONObject?)=if(j==null) CinemaStyle() else CinemaStyle(CinemaLook.valueOf(j.getString("look")),j.getDouble("intensity").toFloat(),CinemaGrade.decode(j.optJSONObject("grade")),CinemaCurves.motionDecode(j.optJSONArray("motion")),CinemaCurves.scalarDecode(j.optJSONArray("lookKeys")),CinemaTransition.decode(j.optJSONObject("exit")))}
}

/** Envelope remains tied to original source time when a lane is split or cut. */
data class CinemaAudioEnvelope(val startMs:Long,val endMs:Long,val fadeInMs:Long,val fadeOutMs:Long) {
    fun validate(sourceDuration:Long) {require(startMs>=0 && endMs in (startMs+1)..sourceDuration && fadeInMs in 0..(endMs-startMs) && fadeOutMs in 0..(endMs-startMs))}
    fun gain(sourceMs:Long):Float=minOf(1f,if(fadeInMs>0) (sourceMs-startMs).toFloat()/fadeInMs else 1f,if(fadeOutMs>0) (endMs-sourceMs).toFloat()/fadeOutMs else 1f).coerceIn(0f,1f)
    fun json()=JSONObject().put("start",startMs).put("end",endMs).put("in",fadeInMs).put("out",fadeOutMs)
    companion object {fun decode(j:JSONObject?)=j?.let {CinemaAudioEnvelope(it.getLong("start"),it.getLong("end"),it.getLong("in"),it.getLong("out"))}}
}
enum class CinemaAudioRole {MUSIC,VOICE,FX}
object CinemaMix {
    fun hasSolo(p:StudioProject)=p.tools.audio.any {it.solo}
    fun sourceVolume(p:StudioProject)=if(hasSolo(p)) 0f else p.originalVolume
    fun envelope(a:StudioAudioLane)=a.envelope ?: CinemaAudioEnvelope(a.startMs,a.endMs,a.fadeInMs,a.fadeOutMs)
    fun gain(p:StudioProject,a:StudioAudioLane,sourceMs:Long)=compile(p,a)(sourceMs)
    /** Compile once per PCM processor; no per-sample collection construction. */
    fun compile(p:StudioProject,a:StudioAudioLane):(Long)->Float {
        val solo=hasSolo(p);val audible=!a.muted && (!solo || a.solo);val envelope=envelope(a)
        val voices=if(a.duck && a.role!=CinemaAudioRole.VOICE) p.tools.audio.filter {it.id!=a.id && it.role==CinemaAudioRole.VOICE && !it.muted && (!solo || it.solo)}
            .map {longArrayOf(it.offsetMs,minOf(p.durationMs,it.offsetMs+it.durationMs))} else emptyList()
        val base=a.volume*a.normalization
        return {sourceMs->
            var activity=0f;val global=a.offsetMs+sourceMs-a.startMs
            for(i in voices.indices) {val voice=voices[i]
                if(global>=voice[0] && global<voice[1]) activity=maxOf(activity,minOf(1f,(global-voice[0])/80f,(voice[1]-global)/160f).coerceIn(0f,1f))
            }
            if(!audible) 0f else base*CinemaCurves.scalar(a.volumeKeys,sourceMs)*envelope.gain(sourceMs)*(1-activity*(1-a.duckGain))
        }
    }
    fun split(p:StudioProject,id:String,sourceMs:Long):StudioProject {
        val a=p.tools.audio.first {it.id==id};val part=sourceMs-a.startMs
        require(p.tools.audio.size<4 && part>=100 && a.endMs-sourceMs>=100) {"Splitting needs two 100 ms fragments and a free audio lane."}
        val env=envelope(a)
        val left=a.copy(endMs=sourceMs,fadeInMs=a.fadeInMs.coerceAtMost(part),fadeOutMs=(a.fadeOutMs-(a.endMs-sourceMs)).coerceIn(0,part),envelope=env)
        val right=a.copy(id=UUID.randomUUID().toString(),startMs=sourceMs,offsetMs=a.offsetMs+part,fadeInMs=(a.fadeInMs-part).coerceIn(0,a.endMs-sourceMs),fadeOutMs=a.fadeOutMs.coerceAtMost(a.endMs-sourceMs),envelope=env)
        return p.copy(tools=p.tools.copy(audio=p.tools.audio.flatMap {if(it.id==id) listOf(left,right) else listOf(it)})).also {it.validate()}
    }
}

enum class CinemaGraphicKind {RECTANGLE,CIRCLE,IMAGE}
data class CinemaGraphic(val id:String=UUID.randomUUID().toString(),val kind:CinemaGraphicKind=CinemaGraphicKind.RECTANGLE,
    val uri:String?=null,val startMs:Long=0,val endMs:Long,val color:Int=0xFF12B8C4.toInt(),val x:Float=0f,val y:Float=-0.5f,
    val motion:List<CinemaMotionKey> = emptyList(),val motionOffsetMs:Long=0) {
    fun validate(duration:Long) {
        require(id.matches(Regex("[a-fA-F0-9-]{36}")) && startMs>=0 && endMs>startMs && endMs<=duration && motionOffsetMs in 0..1_800_000)
        require(x.isFinite() && x in -0.8f..0.8f && y.isFinite() && y in -0.8f..0.8f)
        require(if(kind==CinemaGraphicKind.IMAGE) uri!=null && LocalMediaPolicy.validUri(uri) else uri==null) {"Choose a readable local image."}
        CinemaCurves.validateMotion(motion,1_800_000)
    }
    fun json()=JSONObject().put("id",id).put("kind",kind.name).put("uri",uri ?: JSONObject.NULL).put("start",startMs).put("end",endMs).put("color",color).put("x",x).put("y",y).put("motion",CinemaCurves.motionJson(motion)).put("motionOffset",motionOffsetMs)
    companion object {fun decode(j:JSONObject)=CinemaGraphic(j.getString("id"),CinemaGraphicKind.valueOf(j.getString("kind")),if(j.isNull("uri")) null else j.getString("uri"),j.getLong("start"),j.getLong("end"),j.getInt("color"),j.getDouble("x").toFloat(),j.getDouble("y").toFloat(),CinemaCurves.motionDecode(j.optJSONArray("motion")),j.optLong("motionOffset",0))}
}

object CinemaTransitions {
    fun half(p:StudioProject,index:Int):Long {
        if(index !in 0 until p.clips.lastIndex) return 0
        return minOf(p.clips[index].cinema.exit.durationMs/2,p.clips[index].playbackDurationMs/2,p.clips[index+1].playbackDurationMs/2)
    }
    fun amount(p:StudioProject,index:Int,localMs:Long):Pair<Float,Float> {
        val current=p.clips[index];val previous=p.clips.getOrNull(index-1)
        val enter=if(previous!=null && previous.cinema.exit.kind!=CinemaTransitionKind.NONE) half(p,index-1) else 0
        if(enter>0 && localMs<enter) return (1-localMs.toFloat()/enter).coerceIn(0f,1f) to if(previous!!.cinema.exit.kind==CinemaTransitionKind.WHITE) 1f else 0f
        val exit=if(current.cinema.exit.kind!=CinemaTransitionKind.NONE) half(p,index) else 0
        if(exit>0 && localMs>current.playbackDurationMs-exit) return ((localMs-current.playbackDurationMs+exit).toFloat()/exit).coerceIn(0f,1f) to if(current.cinema.exit.kind==CinemaTransitionKind.WHITE) 1f else 0f
        return 0f to 0f
    }
}

data class CinemaSpeedKey(val sourceMs:Long,val speed:Float)
object CinemaSpeed {
    /** Explicit discrete segments, not optical flow or continuously interpolated time remapping. */
    fun apply(p:StudioProject,index:Int,keys:List<CinemaSpeedKey>):StudioProject {
        val c=p.clips[index]
        require(p.tools.audio.isEmpty() && p.tools.captions.isEmpty() && p.tools.textLayers.isEmpty() && p.tools.graphics.isEmpty() && p.musicUri==null && p.voiceUri==null) {"Apply a speed sequence before adding timed audio/titles. Existing layers were preserved; no partial speed edit was applied."}
        require(keys.size in 2..6 && keys.first().sourceMs==c.startMs && keys.map {it.sourceMs}==keys.map {it.sourceMs}.distinct().sorted())
        require(keys.all {it.speed in listOf(0.5f,1f,1.5f,2f) && it.sourceMs in c.startMs until c.endMs})
        val parts=keys.mapIndexed {i,k->val end=keys.getOrNull(i+1)?.sourceMs ?: c.endMs
            require(end-k.sourceMs>=100) {"Each speed segment needs at least 100 source ms."}
            c.copy(id=if(i==0) c.id else UUID.randomUUID().toString(),startMs=k.sourceMs,endMs=end,speed=k.speed,
                cinema=c.cinema.copy(exit=if(i==keys.lastIndex) c.cinema.exit else CinemaTransition()))
        }
        return p.copy(clips=p.clips.take(index)+parts+p.clips.drop(index+1)).also {it.validate()}
    }
}

object CinemaTitles {
    fun create(duration:Long,title:String,lowerThird:Boolean=false,zoom:Boolean=false):StudioTextLayer {
        val end=minOf(4000,duration);val edge=minOf(400,end/3)
        val from=if(zoom) CinemaPose(scale=0.5f,opacity=0f) else CinemaPose(x=if(lowerThird) -0.65f else 0f,opacity=0f)
        return StudioTextLayer(text=title,startMs=0,endMs=end,size=if(lowerThird) 30 else 48,y=if(lowerThird) -0.55f else 0f,
            background=if(lowerThird) 0xCC083D4B.toInt() else 0,
            motion=listOf(CinemaMotionKey(0,from,CinemaEase.OUT),CinemaMotionKey(edge,CinemaPose()),CinemaMotionKey(end-edge,CinemaPose(),CinemaEase.IN),CinemaMotionKey(end,from)))
    }
}
