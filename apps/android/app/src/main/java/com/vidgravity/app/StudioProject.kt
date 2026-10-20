package com.vidgravity.app

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class StudioClip(
    val id: String = UUID.randomUUID().toString(), val uri: String, val name: String,
    val sourceDurationMs: Long, val startMs: Long = 0, val endMs: Long = sourceDurationMs, val speed: Float = 1f, val frame: StudioFrame = StudioFrame(), val opacity: Float = 1f,
    val audioGain:Float=1f,val framingKeys:List<SmartFrameKey> = emptyList(),val smart:SmartClipNotes?=null,val cinema:CinemaStyle=CinemaStyle()
) {
    val durationMs: Long get() = endMs - startMs
    val playbackDurationMs: Long get() = (durationMs / speed).toLong()
    fun split(offsetMs: Long): List<StudioClip> {
        require(offsetMs >= 100 && offsetMs <= durationMs - 100) { "Each split clip needs at least 0.1 seconds." }
        return listOf(copy(endMs = startMs + offsetMs,cinema=cinema.copy(exit=CinemaTransition())),
            copy(id = UUID.randomUUID().toString(), startMs = startMs + offsetMs))
    }
}

enum class StudioFilter { ORIGINAL, MONO, INVERT }
enum class StudioResolution(val height: Int, val label: String) {
    HD(720, "720p"), FULL_HD(1080, "1080p"), UHD(2160, "2160p / 4K landscape")
}

data class StudioProject(
    val id: String = UUID.randomUUID().toString(), val title: String,
    val clips: List<StudioClip>, val overlay: String = "",
    val filter: StudioFilter = StudioFilter.ORIGINAL,
    val resolution: StudioResolution = StudioResolution.HD,
    val fadeEdges: Boolean = false, val originalVolume: Float = 1f,
    val musicUri: String? = null, val musicVolume: Float = 0.3f,
    val voiceUri: String? = null, val voiceVolume: Float = 1f,
    val updatedAt: Long = System.currentTimeMillis(), val tools: StudioTools = StudioTools()
) {
    val durationMs: Long get() = clips.sumOf { it.playbackDurationMs }
    fun validate() {
        require(id.matches(Regex("[a-fA-F0-9-]{36}"))) { "Invalid project identifier." }
        require(title.isNotBlank() && title.length <= 100 && overlay.length <= 160) { "Project title or overlay is too long." }
        require(clips.size in 1..12 && clips.map { it.id }.distinct().size == clips.size) { "Use 1–12 distinct clips." }
        require(durationMs in 100..1_800_000) { "Projects must be between 0.1 seconds and 30 minutes." }
        for (clip in clips) {
            clip.frame.validate(); clip.cinema.validate(clip.sourceDurationMs)
            require(clip.opacity.isFinite() && clip.opacity in 0f..1f)
            require(clip.audioGain.isFinite() && clip.audioGain in 0.25f..4f)
            require(clip.framingKeys.size<=32 && clip.framingKeys.map {it.timeMs}==clip.framingKeys.map {it.timeMs}.distinct().sorted())
            clip.framingKeys.forEach {key->
                require(key.timeMs in 0..clip.sourceDurationMs && key.x.isFinite() && key.y.isFinite())
                require(SmartFraming.bounded(clip.frame,key)==key) {"Pan keyframes must stay inside the source. Clear keyframes before changing crop shape."}
            }
            clip.smart?.validate(clip.sourceDurationMs)
            require(LocalMediaPolicy.validUri(clip.uri)) { "Choose readable local media." }
            require(clip.speed.isFinite() && clip.speed in listOf(0.5f,1f,1.5f,2f))
            require(clip.startMs >= 0 && clip.endMs <= clip.sourceDurationMs && clip.durationMs >= 100) { "Invalid clip trim range." }
        }
        tools.validate(durationMs)
        for (uri in listOfNotNull(musicUri, voiceUri)) require(LocalMediaPolicy.validUri(uri))
        for (volume in listOf(originalVolume, musicVolume, voiceVolume)) require(volume.isFinite() && volume in 0f..1f)
    }
    fun fingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest(StudioProjectCodec.encode(copy(updatedAt = 0,clips=clips.map {it.copy(smart=null)})).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Versioned, explicit project format. Unknown versions are rejected rather than rewritten. */
object StudioProjectCodec {
    private const val MAX_CHARACTERS = 256_000
    fun encode(project: StudioProject): String {
        project.validate()
        return JSONObject().put("schema", 7).put("tools", StudioToolsCodec.encode(project.tools)).put("id", project.id).put("title", project.title)
            .put("updatedAt", project.updatedAt).put("overlay", project.overlay)
            .put("filter", project.filter.name).put("resolution", project.resolution.name)
            .put("fadeEdges", project.fadeEdges).put("originalVolume", project.originalVolume)
            .put("musicUri", project.musicUri ?: JSONObject.NULL).put("musicVolume", project.musicVolume)
            .put("voiceUri", project.voiceUri ?: JSONObject.NULL).put("voiceVolume", project.voiceVolume)
            .put("clips", JSONArray().apply { project.clips.forEach { clip ->
                put(JSONObject().put("id", clip.id).put("uri", clip.uri).put("name", clip.name)
                    .put("sourceDurationMs", clip.sourceDurationMs).put("startMs", clip.startMs).put("endMs", clip.endMs).put("speed", clip.speed).put("opacity",clip.opacity).put("frame", JSONObject().put("left",clip.frame.left).put("top",clip.frame.top).put("right",clip.frame.right).put("bottom",clip.frame.bottom))
                    .put("cinema",clip.cinema.json()).put("audioGain",clip.audioGain).put("framingKeys",keysJson(clip.framingKeys)).put("smart",clip.smart?.json() ?: JSONObject.NULL))
            } }).toString().also {
                require(it.length <= MAX_CHARACTERS) { "Project metadata is too large. Shorten media names or captions before saving." }
            }
    }

    fun decode(raw: String): StudioProject {
        require(raw.length <= MAX_CHARACTERS) { "Project file is too large." }
        val json = JSONObject(raw)
        require(json.getInt("schema") in 1..7) { "This project needs a newer VidGravity version." }
        val rows = json.getJSONArray("clips")
        require(rows.length() in 1..12)
        return StudioProject(id = json.getString("id"), title = json.getString("title"),
            clips = (0 until rows.length()).map { index -> rows.getJSONObject(index).let {
                StudioClip(it.getString("id"), it.getString("uri"), it.getString("name"),
                    it.getLong("sourceDurationMs"), it.getLong("startMs"), it.getLong("endMs"), it.optDouble("speed", 1.0).toFloat(), it.optJSONObject("frame")?.let { f -> StudioFrame(f.getDouble("left").toFloat(),f.getDouble("top").toFloat(),f.getDouble("right").toFloat(),f.getDouble("bottom").toFloat()) } ?: StudioFrame(), it.optDouble("opacity",1.0).toFloat(),
                    cinema=CinemaStyle.decode(it.optJSONObject("cinema")),audioGain=it.optDouble("audioGain",1.0).toFloat(),framingKeys=it.optJSONArray("framingKeys")?.let(::decodeKeys) ?: emptyList(),smart=it.optJSONObject("smart")?.let {j->SmartClipNotes.decode(j)})
            } }, overlay = json.getString("overlay"), filter = StudioFilter.valueOf(json.getString("filter")),
            resolution = StudioResolution.valueOf(json.getString("resolution")), fadeEdges = json.getBoolean("fadeEdges"),
            originalVolume = json.getDouble("originalVolume").toFloat(),
            musicUri = if (json.isNull("musicUri")) null else json.getString("musicUri"),
            musicVolume = json.getDouble("musicVolume").toFloat(),
            voiceUri = if (json.isNull("voiceUri")) null else json.getString("voiceUri"),
            voiceVolume = json.getDouble("voiceVolume").toFloat(), updatedAt = json.getLong("updatedAt"),
            tools = if (json.getInt("schema") >= 2) StudioToolsCodec.decode(json.getJSONObject("tools")) else StudioTools()
        ).also { it.validate() }
    }
}

internal object StudioFade {
    fun gain(timeUs: Long, durationUs: Long, edgeUs: Long = 250_000): Float {
        val edge = minOf(edgeUs, durationUs / 2).coerceAtLeast(1L)
        return minOf(1f, timeUs.toFloat() / edge, (durationUs - timeUs).toFloat() / edge).coerceIn(0f, 1f)
    }
}
