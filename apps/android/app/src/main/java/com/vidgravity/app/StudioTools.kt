package com.vidgravity.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Output-time cues, independent audio lanes and non-destructive adjustments. */
data class StudioCaption(val startMs: Long, val endMs: Long, val text: String)
data class StudioAudioLane(
    val id: String = UUID.randomUUID().toString(), val uri: String, val name: String,
    val sourceDurationMs: Long, val startMs: Long = 0, val endMs: Long = sourceDurationMs,
    val offsetMs: Long = 0, val volume: Float = 0.5f, val fadeInMs: Long = 0, val fadeOutMs: Long = 0
) { val durationMs: Long get() = endMs - startMs }
enum class StudioAspect(val ratio: Float, val label: String) { SOURCE(0f,"Source"), WIDE(16f/9f,"16:9"), PORTRAIT(9f/16f,"9:16"), SQUARE(1f,"1:1"), FEED(4f/5f,"4:5"), CLASSIC(4f/3f,"4:3") }
data class StudioTools(
    val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f,
    val rotation: Int = 0, val crop: Float = 0f, val aspect: StudioAspect = StudioAspect.SOURCE,
    val captionColor: Int = -1, val captionSize: Int = 32, val captionY: Float = -0.7f,
    val captionFont: String = "sans-serif", val captions: List<StudioCaption> = emptyList(),
    val audio: List<StudioAudioLane> = emptyList(), val frameRate: Int = 0, val bitrate: Int = 0, val soften: Float = 0f, val social: CreatorStyle = CreatorStyle()
) {
    fun validate(duration: Long) {
        social.validate()
        require(brightness.isFinite() && brightness in -1f..1f && contrast.isFinite() && contrast in -0.9f..0.9f)
        require(saturation.isFinite() && saturation in -100f..100f && crop.isFinite() && crop in 0f..0.4f)
        require(soften.isFinite() && soften in 0f..3f)
        require(rotation in listOf(0,90,180,270) && captionSize in 16..64)
        require(captionY.isFinite() && captionY in -0.8f..0.8f && captionFont in listOf("sans-serif","serif","monospace"))
        require(frameRate in listOf(0,15,24,30) && bitrate in listOf(0,2_000_000,5_000_000,10_000_000,20_000_000))
        require(captions.size <= 100 && captions.all { it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= duration && it.text.isNotBlank() && it.text.length <= 500 }) { "Caption cues must fit the video timeline (100 cues maximum)." }
        require(audio.size <= 4 && audio.map { it.id }.distinct().size == audio.size) { "Use up to four independent audio lanes." }
        audio.forEach {
            require(it.uri.startsWith("content://") && it.uri.length <= 8192)
            require(it.startMs >= 0 && it.endMs <= it.sourceDurationMs && it.durationMs >= 100 && it.offsetMs in 0 until duration)
            require(it.volume.isFinite() && it.volume in 0f..1f && it.fadeInMs in 0..it.durationMs && it.fadeOutMs in 0..it.durationMs)
        }
    }
}

internal object StudioToolsCodec {
    fun encode(t: StudioTools) = JSONObject().apply {
        put("brightness",t.brightness); put("contrast",t.contrast); put("saturation",t.saturation)
        put("rotation",t.rotation); put("crop",t.crop); put("aspect",t.aspect.name)
        put("captionColor",t.captionColor); put("captionSize",t.captionSize); put("captionY",t.captionY); put("captionFont",t.captionFont)
        put("social",t.social.json()); put("soften",t.soften); put("frameRate",t.frameRate); put("bitrate",t.bitrate)
        put("captions",JSONArray().apply { t.captions.forEach { put(JSONObject().put("start",it.startMs).put("end",it.endMs).put("text",it.text)) } })
        put("audio",JSONArray().apply { t.audio.forEach { a -> put(JSONObject().put("id",a.id).put("uri",a.uri).put("name",a.name)
            .put("duration",a.sourceDurationMs).put("start",a.startMs).put("end",a.endMs).put("offset",a.offsetMs)
            .put("volume",a.volume).put("fadeIn",a.fadeInMs).put("fadeOut",a.fadeOutMs)) } })
    }
    fun decode(j: JSONObject): StudioTools {
        val c = j.getJSONArray("captions"); val a = j.getJSONArray("audio")
        return StudioTools(j.getDouble("brightness").toFloat(),j.getDouble("contrast").toFloat(),j.getDouble("saturation").toFloat(),
            j.getInt("rotation"),j.getDouble("crop").toFloat(),StudioAspect.valueOf(j.getString("aspect")),
            j.getInt("captionColor"),j.getInt("captionSize"),j.getDouble("captionY").toFloat(),j.getString("captionFont"),
            (0 until c.length()).map { c.getJSONObject(it).let { r -> StudioCaption(r.getLong("start"),r.getLong("end"),r.getString("text")) } },
            (0 until a.length()).map { a.getJSONObject(it).let { r -> StudioAudioLane(r.getString("id"),r.getString("uri"),r.getString("name"),
                r.getLong("duration"),r.getLong("start"),r.getLong("end"),r.getLong("offset"),r.getDouble("volume").toFloat(),r.getLong("fadeIn"),r.getLong("fadeOut")) } },
            j.getInt("frameRate"),j.getInt("bitrate"),j.optDouble("soften",0.0).toFloat(),CreatorStyle.decode(j.optJSONObject("social")))
    }
}

object StudioSrt {
    private val stamp = Regex("(\\d{2,}):(\\d{2}):(\\d{2})[,.](\\d{3})")
    private fun time(value: String): Long {
        val m = stamp.matchEntire(value.trim()) ?: error("Use SRT timestamps HH:MM:SS,mmm.")
        val (h,mn,s,ms) = m.destructured
        require(mn.toInt() < 60 && s.toInt() < 60)
        return h.toLong()*3_600_000 + mn.toLong()*60_000 + s.toLong()*1000 + ms.toLong()
    }
    fun parse(raw: String, duration: Long): List<StudioCaption> {
        require(raw.length <= 64_000) { "SRT must be smaller than 64 KB." }
        val clean = raw.removePrefix("\uFEFF").replace("\r\n","\n").trim()
        if (clean.isEmpty()) return emptyList()
        val cues = clean.split(Regex("\n[ \\t]*\n")).map { block ->
            val lines = block.lines(); val index = if (lines.first().trim().toIntOrNull() != null) 1 else 0
            require(lines.size > index + 1) { "SRT cue is missing text." }
            val range = lines[index].split("-->"); require(range.size == 2)
            StudioCaption(time(range[0]),time(range[1]),lines.drop(index+1).joinToString("\n").trim())
        }.sortedBy { it.startMs }
        StudioTools(captions = cues).validate(duration)
        return cues
    }
    private fun format(ms: Long) = java.lang.String.format(java.util.Locale.ROOT,"%02d:%02d:%02d,%03d",ms/3_600_000,ms/60_000%60,ms/1000%60,ms%1000)
    fun encode(cues: List<StudioCaption>) = cues.sortedBy { it.startMs }.mapIndexed { i,c -> "${i+1}\n${format(c.startMs)} --> ${format(c.endMs)}\n${c.text}" }.joinToString("\n\n",postfix=if(cues.isEmpty()) "" else "\n")
}

/** Bounded editing history. Continuous edits coalesce; undo/redo snapshots never touch original media. */
class StudioHistory {
    private val past = ArrayDeque<StudioProject>(); private val future = ArrayDeque<StudioProject>()
    private var lastGroup: String? = null; private var lastAt = 0L
    val canUndo get() = past.isNotEmpty(); val canRedo get() = future.isNotEmpty()
    fun record(before: StudioProject, group: String? = null, now: Long = System.currentTimeMillis()) {
        if (group == null || group != lastGroup || now - lastAt > 750) { past.addLast(before); if (past.size > 50) past.removeFirst() }
        future.clear(); lastGroup = group; lastAt = now
    }
    fun undo(current: StudioProject): StudioProject { future.addLast(current); lastGroup=null; return past.removeLast() }
    fun redo(current: StudioProject): StudioProject { past.addLast(current); lastGroup=null; return future.removeLast() }
}

object StudioEdits {
    fun move(p: StudioProject, from: Int, to: Int): StudioProject {
        require(from in p.clips.indices && to in p.clips.indices)
        val rows=p.clips.toMutableList(); rows.add(to,rows.removeAt(from)); return p.copy(clips=rows)
    }
    fun duplicate(p: StudioProject, index: Int): StudioProject = p.copy(clips=p.clips.toMutableList().apply {
        add(index+1,p.clips[index].copy(id=UUID.randomUUID().toString())) }).also { it.validate() }
    fun mergeNext(p: StudioProject, index: Int): StudioProject {
        require(index < p.clips.lastIndex)
        val a=p.clips[index]; val b=p.clips[index+1]
        require(a.uri == b.uri && a.endMs == b.startMs && a.speed == b.speed) { "Merge requires adjacent ranges from the same source and speed." }
        return p.copy(clips=p.clips.toMutableList().apply { this[index]=a.copy(endMs=b.endMs); removeAt(index+1) })
    }
    fun relink(p: StudioProject, index: Int, replacement: StudioClip): StudioProject {
        val old=p.clips[index]
        require(replacement.sourceDurationMs >= old.endMs) { "Replacement is shorter than this clip's saved trim range." }
        return p.copy(clips=p.clips.mapIndexed { i,c -> if(i==index) c.copy(uri=replacement.uri,name=replacement.name,sourceDurationMs=replacement.sourceDurationMs) else c })
    }
}
