package com.vidgravity.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/** All analysis times are absolute source milliseconds, never composition milliseconds. */
data class SmartFrameKey(val timeMs:Long,val x:Float,val y:Float)
data class SmartSuggestion(val startMs:Long,val endMs:Long,val score:Float,val reason:String)
data class SmartClipNotes(
    val sourceKey:String,val startMs:Long,val endMs:Long,val threshold:Float,
    val scenes:List<Long>,val silence:List<CreativeRange>,val highlights:List<SmartSuggestion>,
    val thumbnails:List<SmartSuggestion>,val faces:List<SmartFrameKey>,val aspect:Float,
    val audio:List<AudioObservation>,val rms:Float,val peak:Float,val audioStatus:String,val faceStatus:String
) {
    fun validate(duration:Long) {
        require(sourceKey.matches(Regex("[a-f0-9]{64}")) && startMs>=0 && endMs in (startMs+1)..duration)
        require(threshold.isFinite() && threshold in 0.001f..0.05f && aspect.isFinite() && aspect in 0.05f..20f)
        require(scenes.size<=30 && scenes==scenes.distinct().sorted() && scenes.all {it in startMs until endMs})
        require(silence.size<=100 && silence.all {it.startMs>=startMs && it.endMs<=endMs && it.durationMs>=100})
        require(highlights.size<=5 && thumbnails.size<=5 && faces.size<=30 && audio.size<=60)
        (highlights+thumbnails).forEach {require(it.startMs>=startMs && it.endMs<=endMs && it.endMs>it.startMs && it.score.isFinite() && it.score in 0f..1f && it.reason.length<=160)}
        faces.forEach {require(it.timeMs in startMs until endMs && it.x.isFinite() && it.x in 0f..1f && it.y.isFinite() && it.y in 0f..1f)}
        require(faces.map {it.timeMs}==faces.map {it.timeMs}.distinct().sorted())
        audio.forEach {require(it.startMs>=startMs && it.endMs<=endMs && it.endMs>it.startMs && it.rms.isFinite() && it.rms in 0f..1f && it.peak.isFinite() && it.peak in 0f..1f)}
        require(rms.isFinite() && rms in 0f..1f && peak.isFinite() && peak in 0f..1f && audioStatus.length<=300 && faceStatus.length<=300)
    }
    fun json()=JSONObject().put("key",sourceKey).put("start",startMs).put("end",endMs).put("threshold",threshold)
        .put("scenes",JSONArray(scenes)).put("silence",JSONArray().apply {silence.forEach {put(JSONArray(listOf(it.startMs,it.endMs)))}})
        .put("highlights",suggestionsJson(highlights)).put("thumbnails",suggestionsJson(thumbnails)).put("faces",keysJson(faces))
        .put("aspect",aspect).put("rms",rms).put("peak",peak).put("audioStatus",audioStatus).put("faceStatus",faceStatus)
        .put("audio",JSONArray().apply {audio.forEach {put(JSONArray(listOf(it.startMs,it.endMs,it.rms,it.peak)))}})
    companion object {
        private fun suggestionsJson(rows:List<SmartSuggestion>)=JSONArray().apply {rows.forEach {put(JSONArray(listOf(it.startMs,it.endMs,it.score,it.reason)))}}
        private fun suggestions(j:JSONArray)=List(j.length()) {i->j.getJSONArray(i).let {SmartSuggestion(it.getLong(0),it.getLong(1),it.getDouble(2).toFloat(),it.getString(3))}}
        fun decode(j:JSONObject)=SmartClipNotes(j.getString("key"),j.getLong("start"),j.getLong("end"),j.getDouble("threshold").toFloat(),
            j.getJSONArray("scenes").let {a->List(a.length()) {a.getLong(it)}},
            j.getJSONArray("silence").let {a->List(a.length()) {i->a.getJSONArray(i).let {CreativeRange(it.getLong(0),it.getLong(1))}}},
            suggestions(j.getJSONArray("highlights")),suggestions(j.getJSONArray("thumbnails")),decodeKeys(j.getJSONArray("faces")),j.getDouble("aspect").toFloat(),
            j.getJSONArray("audio").let {a->List(a.length()) {i->a.getJSONArray(i).let {AudioObservation(it.getLong(0),it.getLong(1),it.getDouble(2).toFloat(),it.getDouble(3).toFloat())}}},
            j.getDouble("rms").toFloat(),j.getDouble("peak").toFloat(),j.getString("audioStatus"),j.getString("faceStatus"))
    }
}
internal fun keysJson(keys:List<SmartFrameKey>)=JSONArray().apply {keys.forEach {put(JSONArray(listOf(it.timeMs,it.x,it.y)))}}
internal fun decodeKeys(rows:JSONArray)=List(rows.length()) {i->rows.getJSONArray(i).let {SmartFrameKey(it.getLong(0),it.getDouble(1).toFloat(),it.getDouble(2).toFloat())}}

fun interface SmartSceneDetector {fun detect(frames:List<FrameObservation>):List<Long>}
fun interface SmartHighlightRanker {fun rank(frames:List<FrameObservation>,audio:List<AudioObservation>,duration:Long):List<SmartSuggestion>}
fun interface SmartThumbnailRanker {fun rank(frames:List<FrameObservation>,duration:Long):List<SmartSuggestion>}
object SmartRanking {
    val scenes=SmartSceneDetector {frames->frames.filter {it.difference>0.18f}.map {it.timeMs}.distinct().sorted()}
    val highlights=SmartHighlightRanker {frames,audio,duration->
        val candidates=frames.map {f->
            val energy=audio.filter {abs(it.startMs-f.timeMs)<2000}.map {it.rms}.average().takeIf {it.isFinite()}?.toFloat() ?: 0f
            val length=minOf(6000L,duration); val start=(f.timeMs-length/2).coerceIn(0,duration-length)
            SmartSuggestion(start,start+length,(f.difference*1.5f+f.contrast+energy*2f+(1-abs(f.luma-0.5f)*2)*0.2f).coerceIn(0f,1f),"Visual change, contrast and local audio activity; no semantic understanding")
        }.sortedByDescending {it.score}
        val selected=mutableListOf<SmartSuggestion>()
        for(c in candidates) if(selected.none {abs(it.startMs-c.startMs)<minOf(3000L,c.endMs-c.startMs)}) {selected+=c;if(selected.size==3) break}
        selected
    }
    val thumbnails=SmartThumbnailRanker {frames,duration->
        val selected=mutableListOf<SmartSuggestion>()
        for(f in frames.sortedByDescending {it.contrast+(1-abs(it.luma-0.5f)*2)*0.3f-it.difference*0.2f+(if(it.faceX!=null) 0.1f else 0f)}) {
            if(selected.none {abs(it.startMs-f.timeMs)<1500}) selected+=SmartSuggestion(f.timeMs,minOf(duration,f.timeMs+100),
                (f.contrast+(1-abs(f.luma-0.5f)*2)*0.5f).coerceIn(0f,1f),"Exposure, contrast and temporal separation; review the actual frame")
            if(selected.size==3) break
        }; selected
    }
    fun normalization(rms:Float,peak:Float):Float {
        require(rms.isFinite() && peak.isFinite() && rms>=0 && peak>=0)
        return if(rms<0.0001f || peak<0.0001f) 1f else minOf(0.15f/rms,0.95f/peak,4f).coerceIn(0.25f,4f)
    }
}

object SmartFraming {
    fun frame(sourceAspect:Float,aspect:StudioAspect,x:Float=0.5f,y:Float=0.5f):StudioFrame {
        require(sourceAspect.isFinite() && sourceAspect>0 && aspect!=StudioAspect.SOURCE)
        val w=minOf(1f,aspect.ratio/sourceAspect).coerceAtLeast(0.05f); val h=minOf(1f,sourceAspect/aspect.ratio).coerceAtLeast(0.05f)
        val left=(x-w/2).coerceIn(0f,1-w);val top=(y-h/2).coerceIn(0f,1-h)
        return StudioFrame(left,top,left+w,top+h)
    }
    fun center(clip:StudioClip,time:Long):Pair<Float,Float> {
        val keys=clip.framingKeys
        val fallback=(clip.frame.left+clip.frame.right)/2 to (clip.frame.top+clip.frame.bottom)/2
        if(keys.isEmpty()) return fallback
        val left=keys.lastOrNull {it.timeMs<=time} ?: keys.first();val right=keys.firstOrNull {it.timeMs>=time} ?: keys.last()
        val f=if(right.timeMs==left.timeMs) 0f else ((time-left.timeMs).toFloat()/(right.timeMs-left.timeMs)).coerceIn(0f,1f)
        return (left.x+(right.x-left.x)*f) to (left.y+(right.y-left.y)*f)
    }
    fun bounded(frame:StudioFrame,key:SmartFrameKey)=key.copy(x=key.x.coerceIn((frame.right-frame.left)/2,1-(frame.right-frame.left)/2),
        y=key.y.coerceIn((frame.bottom-frame.top)/2,1-(frame.bottom-frame.top)/2))
}

/** Apply approved source-time cuts in-place, remapping all supported output-time layers. */
object SmartCuts {
    private data class Window(val oldStart:Long,val oldEnd:Long,val newStart:Long,val length:Long)
    fun remove(p:StudioProject,index:Int,approved:List<CreativeRange>):StudioProject {
        require(index in p.clips.indices && approved.isNotEmpty()) {"Select at least one range to remove."}
        require(p.musicUri==null && p.voiceUri==null) {"Attached looping music/voice cannot be retimed safely. Use independent audio lanes or trim manually."}
        val selected=p.clips[index];val removals=approved.sortedBy {it.startMs}; var cursor=selected.startMs
        val retained=mutableListOf<CreativeRange>()
        for(r in removals) {
            require(r.startMs>=selected.startMs && r.endMs<=selected.endMs && r.durationMs>0 && r.startMs>=cursor) {"Cut ranges must be ordered, non-overlapping and inside the selected clip."}
            if(r.startMs>cursor) retained+=CreativeRange(cursor,r.startMs)
            cursor=r.endMs
        }
        if(cursor<selected.endMs) retained+=CreativeRange(cursor,selected.endMs)
        require(retained.all {it.durationMs>=100}) {"A remaining fragment is shorter than 100 ms. Adjust the proposed cut boundaries."}
        val clips=mutableListOf<StudioClip>();val windows=mutableListOf<Window>();var oldOffset=0L;var newOffset=0L
        p.clips.forEachIndexed {i,c->
            val ranges=if(i==index) retained else listOf(CreativeRange(c.startMs,c.endMs))
            ranges.forEachIndexed {part,r->
                val next=c.copy(id=if(part==0) c.id else UUID.randomUUID().toString(),startMs=r.startMs,endMs=r.endMs,cinema=c.cinema.copy(exit=if(part==ranges.lastIndex) c.cinema.exit else CinemaTransition()))
                windows+=Window(oldOffset+((r.startMs-c.startMs)/c.speed).toLong(),oldOffset+((r.endMs-c.startMs)/c.speed).toLong(),newOffset,next.playbackDurationMs)
                clips+=next;newOffset+=next.playbackDurationMs
            };oldOffset+=c.playbackDurationMs
        }
        require(clips.size in 1..12) {"Reviewed cuts must leave 1–12 clips. Select fewer cuts or keep some content."}
        // Video clip boundaries are not cuts in composition time. Keep timed layers
        // continuous across them, otherwise fades/animations restart and a valid
        // edit can exceed lane limits merely because the video has many clips.
        val continuous=mutableListOf<Window>()
        for(window in windows) {
            val previous=continuous.lastOrNull()
            if(previous!=null && previous.oldEnd==window.oldStart &&
                previous.newStart+previous.length==window.newStart &&
                previous.oldEnd-previous.oldStart==previous.length &&
                window.oldEnd-window.oldStart==window.length) {
                continuous[continuous.lastIndex]=previous.copy(oldEnd=window.oldEnd,length=previous.length+window.length)
            } else continuous+=window
        }
        fun fragments(start:Long,end:Long)=continuous.mapNotNull {w->
            val a=maxOf(start,w.oldStart);val b=minOf(end,w.oldEnd)
            if(b<=a) null else Triple(a,b,CreativeRange(w.newStart+(a-w.oldStart).coerceAtMost(w.length),w.newStart+(b-w.oldStart).coerceAtMost(w.length)))
        }.filter {it.third.durationMs>0}
        val captions=p.tools.captions.flatMap {c->fragments(c.startMs,c.endMs).map {c.copy(startMs=it.third.startMs,endMs=it.third.endMs)}}
        val text=p.tools.textLayers.flatMap {c->fragments(c.startMs,c.endMs).mapIndexed {i,f->c.copy(id=if(i==0) c.id else UUID.randomUUID().toString(),startMs=f.third.startMs,endMs=f.third.endMs,motionOffsetMs=c.motionOffsetMs+f.first-c.startMs)}}
        val graphics=p.tools.graphics.flatMap {c->fragments(c.startMs,c.endMs).mapIndexed {i,f->c.copy(id=if(i==0) c.id else UUID.randomUUID().toString(),startMs=f.third.startMs,endMs=f.third.endMs,motionOffsetMs=c.motionOffsetMs+f.first-c.startMs)}}
        val audio=p.tools.audio.flatMap {a->fragments(a.offsetMs,a.offsetMs+a.durationMs).mapIndexedNotNull {i,f->
            require(f.third.durationMs>=100) {"A retimed audio fragment would be shorter than 100 ms. Apply fewer cuts."}
            a.copy(id=if(i==0) a.id else UUID.randomUUID().toString(),startMs=a.startMs+f.first-a.offsetMs,
                endMs=a.startMs+f.first-a.offsetMs+f.third.durationMs,offsetMs=f.third.startMs,
                fadeInMs=(a.fadeInMs-(f.first-a.offsetMs)).coerceIn(0,f.third.durationMs),fadeOutMs=(a.fadeOutMs-(a.offsetMs+a.durationMs-f.second)).coerceIn(0,f.third.durationMs),
                envelope=if(f.first!=a.offsetMs || f.second!=a.offsetMs+a.durationMs) CinemaMix.envelope(a) else a.envelope)
        }}
        require(audio.size<=4 && text.size<=16 && captions.size<=100 && graphics.size<=8) {"These cuts exceed the audio/text/caption lane limits. Apply fewer cuts; existing layers were preserved."}
        return p.copy(clips=clips,tools=p.tools.copy(captions=captions,textLayers=text,audio=audio,graphics=graphics)).also {it.validate()}
    }
    fun split(p:StudioProject,index:Int,sourceMs:Long):StudioProject {
        val c=p.clips[index];val parts=c.split(sourceMs-c.startMs)
        return p.copy(clips=p.clips.toMutableList().apply {removeAt(index);addAll(index,parts)}).also {it.validate()}
    }
}
