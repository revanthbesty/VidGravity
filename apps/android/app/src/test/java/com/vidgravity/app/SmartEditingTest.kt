package com.vidgravity.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SmartEditingTest {
    private fun clip(duration:Long=5000)=StudioClip(uri="content://media/external/video/media/100",name="Source",sourceDurationMs=duration)
    private fun notes()=SmartClipNotes("a".repeat(64),0,4000,0.008f,listOf(2000),listOf(CreativeRange(1000,2000)),
        listOf(SmartSuggestion(0,3000,0.7f,"signals")),listOf(SmartSuggestion(500,600,0.8f,"contrast")),listOf(SmartFrameKey(0,0.4f,0.5f),SmartFrameKey(3000,0.6f,0.5f)),
        16f/9f,listOf(AudioObservation(0,1000,0.07f,0.1f)),0.07f,0.1f,"PCM","frontal faces")
    @Test fun schemaSixRoundTripsNotesGainAndKeysAndReadsSchemaFive() {
        val c=clip().copy(frame=StudioFrame(0.2f,0f,0.8f,1f),audioGain=2f,framingKeys=listOf(SmartFrameKey(0,0.4f,0.5f),SmartFrameKey(4000,0.6f,0.5f)),smart=notes())
        val p=StudioProject(title="Smart",clips=listOf(c))
        assertEquals(p,StudioProjectCodec.decode(StudioProjectCodec.encode(p)))
        val old=JSONObject(StudioProjectCodec.encode(p)).put("schema",5)
        old.getJSONArray("clips").getJSONObject(0).apply {remove("smart");remove("audioGain");remove("framingKeys")}
        val loaded=StudioProjectCodec.decode(old.toString()).clips.single()
        assertNull(loaded.smart);assertEquals(1f,loaded.audioGain,0f);assertTrue(loaded.framingKeys.isEmpty())
    }
    @Test fun analysisDoesNotInvalidateExportButGainAndPanDo() {
        val p=StudioProject(title="P",clips=listOf(clip()))
        assertEquals(p.fingerprint(),p.copy(clips=listOf(p.clips[0].copy(smart=notes()))).fingerprint())
        assertNotEquals(p.fingerprint(),p.copy(clips=listOf(p.clips[0].copy(audioGain=2f))).fingerprint())
        val framed=p.copy(clips=listOf(p.clips[0].copy(frame=StudioFrame(0.2f,0f,0.8f,1f))))
        assertNotEquals(framed.fingerprint(),framed.copy(clips=listOf(framed.clips[0].copy(framingKeys=listOf(SmartFrameKey(0,0.4f,0.5f))))).fingerprint())
    }
    @Test fun approvedCutsRetimeEveryLayerPreserveSourcesAndSupportHistory() {
        val c=clip();val p=StudioProject(title="Layers",clips=listOf(c),tools=StudioTools(captions=listOf(StudioCaption(500,2500,"Keep words")),
            textLayers=listOf(StudioTextLayer(text="Keep title",startMs=500,endMs=2500)),audio=listOf(StudioAudioLane(uri=c.uri,name="Track",sourceDurationMs=5000,volume=0.4f))))
        val next=SmartCuts.remove(p,0,listOf(CreativeRange(1000,2000)))
        assertEquals(4000L,next.durationMs);assertEquals(listOf(0L,2000L),next.clips.map {it.startMs});assertEquals(2,next.tools.textLayers.size)
        assertEquals(listOf(500L to 1000L,1000L to 1500L),next.tools.captions.map {it.startMs to it.endMs})
        assertEquals(listOf(0L,2000L),next.tools.audio.map {it.startMs});assertEquals(listOf(0L,1000L),next.tools.audio.map {it.offsetMs})
        assertEquals(5000L,p.durationMs);assertTrue(next.clips.all {it.uri==c.uri})
        val history=StudioHistory();history.record(p);assertEquals(p,history.undo(next));assertEquals(next,history.redo(p))
    }
    @Test fun cutsPreserveUnanalyzedTailAndHonorSpeed() {
        val p=StudioProject(title="Long",clips=listOf(clip(70000)))
        val next=SmartCuts.remove(p,0,listOf(CreativeRange(1000,2000)))
        assertEquals(69000L,next.durationMs);assertEquals(70000L,next.clips.last().endMs)
        val slow=StudioProject(title="Slow",clips=listOf(clip(4000).copy(speed=0.5f)))
        assertEquals(6000L,SmartCuts.remove(slow,0,listOf(CreativeRange(1000,2000))).durationMs)
    }
    @Test fun cutsRejectDataLossLimitsAndUnsupportedAttachedAudio() {
        val p=StudioProject(title="Safe",clips=listOf(clip()))
        fun rejects(project:StudioProject,ranges:List<CreativeRange>) {try {SmartCuts.remove(project,0,ranges);fail("Expected safe rejection")} catch(expected:IllegalArgumentException) {}}
        rejects(p,listOf(CreativeRange(0,5000)));rejects(p,listOf(CreativeRange(50,1000)))
        rejects(p,listOf(CreativeRange(500,1500),CreativeRange(1000,2000)))
        rejects(p.copy(musicUri=p.clips[0].uri),listOf(CreativeRange(1000,2000)))
        val lanes=List(4) {StudioAudioLane(uri=p.clips[0].uri,name="Track",sourceDurationMs=5000)}
        rejects(p.copy(tools=StudioTools(audio=lanes)),listOf(CreativeRange(1000,2000)))
        assertEquals(5000L,p.durationMs)
    }
    @Test fun sceneSplitsPreserveMetadataAndKeyframeInterpolationIsBounded() {
        val c=clip().copy(smart=notes(),frame=StudioFrame(0.2f,0f,0.8f,1f),framingKeys=listOf(SmartFrameKey(0,0.3f,0.5f),SmartFrameKey(4000,0.7f,0.5f)))
        val p=StudioProject(title="Scene",clips=listOf(c));val next=SmartCuts.split(p,0,2000)
        assertEquals(p.durationMs,next.durationMs);assertEquals(listOf(2000L,5000L),next.clips.map {it.endMs});assertEquals(notes(),next.clips.last().smart)
        assertEquals(0.5f,SmartFraming.center(c,2000).first,0.00001f);assertEquals(0.7f,SmartFraming.center(c,5000).first,0.00001f)
        for(a in listOf(StudioAspect.WIDE,StudioAspect.PORTRAIT,StudioAspect.SQUARE,StudioAspect.FEED)) {
            val frame=SmartFraming.frame(16f/9f,a,1f,0f);frame.validate()
            assertEquals(a.ratio,(frame.right-frame.left)*16f/9f/(frame.bottom-frame.top),0.0001f)
        }
    }
    @Test fun normalizationUsesMeasuredPeakAndNeverInventsSpeechOrMeaning() {
        assertEquals(3f,SmartRanking.normalization(0.05f,0.1f),0.001f)
        assertTrue(SmartRanking.normalization(0.05f,0.9f)<=0.95f/0.9f+0.001f)
        assertEquals(1f,SmartRanking.normalization(0f,0f),0f)
        val frames=List(20) {FrameObservation(it*2000L,0.5f,0.2f,if(it==5) 0.5f else 0f)}
        assertEquals(listOf(10000L),SmartRanking.scenes.detect(frames))
        val ranked=SmartRanking.highlights.rank(frames,listOf(AudioObservation(10000,10200,0.4f,0.6f)),40000)
        assertTrue(ranked.first().startMs<=10000 && ranked.first().endMs>10000)
        assertTrue(SmartRanking.thumbnails.rank(frames,40000).size in 1..3)
        assertFalse(CreativeCapabilities.videoTranscriptionAvailable)
    }
    @Test fun mergeRefusesToDropPanOrGainAndMalformedNotesAreRejected() {
        val c=clip();val a=c.copy(endMs=2000);val b=c.copy(id=java.util.UUID.randomUUID().toString(),startMs=2000,audioGain=2f)
        try {StudioEdits.mergeNext(StudioProject(title="P",clips=listOf(a,b)),0);fail()} catch(expected:IllegalArgumentException) {}
        try {notes().copy(scenes=listOf(50000)).validate(5000);fail()} catch(expected:IllegalArgumentException) {}
    }
}
