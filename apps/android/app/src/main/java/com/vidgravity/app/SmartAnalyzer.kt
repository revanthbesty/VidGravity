package com.vidgravity.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.sqrt

/** Reuses the bounded PCM/frame decoder. The saved project is the local analysis cache. */
internal object SmartAnalyzer {
    private val gate=Mutex()
    suspend fun analyze(context:Context,clip:StudioClip,threshold:Float=0.008f,force:Boolean=false,onProgress:(String,Float)->Unit):SmartClipNotes = gate.withLock {
        withContext(Dispatchers.IO) {
            require(threshold.isFinite() && threshold in 0.001f..0.05f)
            ensureActive()
            val key=sourceKey(context,clip)
            val cached=clip.smart
            if(!force && cached!=null && cached.sourceKey==key && cached.startMs==clip.startMs && cached.endMs<=clip.endMs && cached.threshold==threshold) {
                cached.validate(clip.sourceDurationMs);onProgress("Using saved local analysis; reanalyze if the source was replaced",1f);return@withContext cached
            }
            val r=CreativeAnalyzer.analyze(context,clip,onProgress)
            ensureActive()
            val offset=clip.startMs
            val duration=r.audio.sumOf {it.endMs-it.startMs}.coerceAtLeast(1)
            val rms=sqrt(r.audio.sumOf {it.rms.toDouble()*it.rms*(it.endMs-it.startMs)}/duration).toFloat().coerceIn(0f,1f)
            fun shift(rows:List<SmartSuggestion>)=rows.map {it.copy(startMs=it.startMs+offset,endMs=it.endMs+offset)}
            SmartClipNotes(key,offset,offset+r.analyzedMs,threshold,
                SmartRanking.scenes.detect(r.frames).map {it+offset},CreativeRules.silence(r.audio,threshold).map {CreativeRange(it.startMs+offset,it.endMs+offset)},
                shift(SmartRanking.highlights.rank(r.frames,r.audio,r.analyzedMs)),shift(SmartRanking.thumbnails.rank(r.frames,r.analyzedMs)),
                r.frames.filter {it.faceX!=null && it.faceY!=null}.map {SmartFrameKey(it.timeMs+offset,it.faceX!!,it.faceY!!)},
                r.frames.first().aspect,
                r.audio.chunked(5).map {bins->val length=bins.sumOf {it.endMs-it.startMs}.coerceAtLeast(1)
                    AudioObservation(offset+bins.first().startMs,offset+bins.last().endMs,
                        sqrt(bins.sumOf {it.rms.toDouble()*it.rms*(it.endMs-it.startMs)}/length).toFloat().coerceIn(0f,1f),bins.maxOf {it.peak}.coerceIn(0f,1f))},
                rms,(r.audio.maxOfOrNull {it.peak} ?: 0f).coerceIn(0f,1f),r.audioStatus,r.faceStatus
            ).also {it.validate(clip.sourceDurationMs)}
        }
    }
    private fun sourceKey(context:Context,clip:StudioClip):String {
        val uri=Uri.parse(clip.uri)
        val size=runCatching {context.contentResolver.openFileDescriptor(uri,"r")?.use {it.statSize}}.getOrNull()
        val modified=if(uri.scheme=="file") java.io.File(uri.path.orEmpty()).lastModified() else runCatching {
            context.contentResolver.query(uri,arrayOf("date_modified"),null,null,null)?.use {if(it.moveToFirst()) it.getLong(0) else 0L}
        }.getOrNull()
        return MessageDigest.getInstance("SHA-256").digest("${clip.uri}|${clip.sourceDurationMs}|$size|$modified|smart-v1".toByteArray()).joinToString("") {"%02x".format(it)}
    }
}
