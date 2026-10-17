package com.vidgravity.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudioEditingTest {
    private fun project() = StudioProject(title="Editor",clips=listOf(StudioClip(uri="content://video/1",name="one.mp4",sourceDurationMs=10_000)))
    private fun rejected(action:()->Unit) {try {action(); fail("Expected invalid edit to fail")} catch(expected:IllegalArgumentException) {}}
    @Test fun legacySchemaLoadsAndAdvancedSettingsRoundTrip() {
        val base=project()
        val legacy=JSONObject(StudioProjectCodec.encode(base)).put("schema",1).apply {remove("tools"); getJSONArray("clips").getJSONObject(0).remove("speed")}
        assertEquals(base,StudioProjectCodec.decode(legacy.toString()))
        val advanced=base.copy(clips=base.clips.map {it.copy(speed=2f)},tools=StudioTools(brightness=0.2f,contrast=0.1f,saturation=20f,
            rotation=90,crop=0.1f,aspect=StudioAspect.SQUARE,captionColor=0xFFFFFF00.toInt(),captionFont="serif",captionSize=48,captionY=0.5f,
            captions=listOf(StudioCaption(100,1000,"Title\n字幕")),audio=listOf(StudioAudioLane(uri="content://audio/1",name="tone",sourceDurationMs=4000,
                startMs=100,endMs=3000,offsetMs=250,volume=0.7f,fadeInMs=200,fadeOutMs=400)),frameRate=24,bitrate=5_000_000))
        assertEquals(5000L,advanced.durationMs)
        assertEquals(advanced,StudioProjectCodec.decode(StudioProjectCodec.encode(advanced)))
        assertNotEquals(base.fingerprint(),advanced.fingerprint())
        assertNotEquals(advanced.fingerprint(),advanced.copy(tools=advanced.tools.copy(captionY=-0.5f)).fingerprint())
    }
    @Test fun splitMergeDuplicateAndReorderPreserveRanges() {
        val p=project(); val pieces=p.clips.single().copy(speed=2f).split(4000)
        val split=p.copy(clips=pieces)
        assertEquals(5000L,split.durationMs)
        val merged=StudioEdits.mergeNext(split,0)
        assertEquals(1,merged.clips.size); assertEquals(10_000L,merged.clips.single().endMs)
        val copy=StudioEdits.duplicate(split,0)
        assertEquals(3,copy.clips.size); assertNotEquals(copy.clips[0].id,copy.clips[1].id)
        assertEquals(copy.clips[0],StudioEdits.move(copy,0,2).clips.last())
        rejected {StudioEdits.mergeNext(StudioEdits.move(split,0,1),0)}
    }
    @Test fun undoRedoCoalescesContinuousChangesAndClearsRedoOnBranch() {
        val history=StudioHistory(); val p=project(); val b=p.copy(overlay="B"); val c=p.copy(overlay="C")
        history.record(p,"text",1000); history.record(b,"text",1100)
        assertEquals(p,history.undo(c)); assertTrue(history.canRedo); assertFalse(history.canUndo)
        assertEquals(c,history.redo(p)); assertTrue(history.canUndo)
        history.undo(c); history.record(p,"other",1200); assertFalse(history.canRedo)
    }
    @Test fun srtRoundTripsUnicodeMultilineAndRejectsBadCueBounds() {
        val cues=StudioSrt.parse("\uFEFF1\r\n00:00:00,100 --> 00:00:01,500\r\nHello\r\n字幕\r\n\r\n2\r\n00:00:02,000 --> 00:00:03,000\r\nNext",4000)
        assertEquals(2,cues.size); assertEquals("Hello\n字幕",cues[0].text)
        assertEquals(cues,StudioSrt.parse(StudioSrt.encode(cues),4000))
        rejected {StudioSrt.parse("1\n00:00:03,000 --> 00:00:02,000\nBad",4000)}
        rejected {StudioSrt.parse("1\n00:00:00,000 --> 00:00:05,000\nToo long",4000)}
    }
    @Test fun badAudioAndAdjustmentValuesCannotCorruptSavedProject() {
        val p=project()
        rejected {p.copy(tools=StudioTools(brightness=Float.NaN)).validate()}
        rejected {p.copy(tools=StudioTools(audio=listOf(StudioAudioLane(uri="content://audio/1",name="tone",sourceDurationMs=1000,offsetMs=10_000)))).validate()}
        rejected {p.copy(clips=p.clips.map {it.copy(speed=0f)}).validate()}
        rejected {p.copy(tools=StudioTools(frameRate=120)).validate()}
    }
    @Test fun relinkPreservesEditIdentityAndRejectsShortReplacement() {
        val p=project().copy(clips=project().clips.map {it.copy(startMs=1000,endMs=8000,speed=0.5f)})
        val replacement=StudioClip(uri="content://video/new",name="new.mp4",sourceDurationMs=9000)
        val linked=StudioEdits.relink(p,0,replacement)
        assertEquals(p.clips[0].id,linked.clips[0].id); assertEquals(1000L,linked.clips[0].startMs)
        assertEquals(0.5f,linked.clips[0].speed); assertNotEquals(p.fingerprint(),linked.fingerprint())
        rejected {StudioEdits.relink(p,0,replacement.copy(sourceDurationMs=7000))}
    }
}
