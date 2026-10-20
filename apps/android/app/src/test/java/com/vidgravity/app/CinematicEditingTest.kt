package com.vidgravity.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CinematicEditingTest {
    private fun clip()=StudioClip(uri="content://media/external/video/media/12",name="Cinematic source",sourceDurationMs=6000)
    @Test fun schemaSevenRoundTripAndEveryOlderSchemaHasSafeDefaults() {
        val c=clip();val motion=listOf(CinemaMotionKey(0),CinemaMotionKey(3000,CinemaPose(0.3f,-0.2f,1.2f,45f,0.5f),CinemaEase.IN_OUT))
        val style=CinemaStyle(CinemaLook.WARM,0.6f,CinemaGrade(exposure=0.5f,gamma=1.1f),motion,listOf(CinemaScalarKey(0,0.2f),CinemaScalarKey(3000,1f)),CinemaTransition(CinemaTransitionKind.WHITE,800))
        val p=StudioProject(title="Cinema",clips=listOf(c.copy(cinema=style)),tools=StudioTools(
            audio=listOf(StudioAudioLane(uri=c.uri,name="Voice",sourceDurationMs=6000,muted=true,solo=true,role=CinemaAudioRole.VOICE,normalization=2f,volumeKeys=listOf(CinemaScalarKey(1000,0.5f)))),
            textLayers=listOf(StudioTextLayer(text="Motion",endMs=5000,motion=motion)),graphics=listOf(CinemaGraphic(endMs=4000,motion=motion))))
        assertEquals(7,JSONObject(StudioProjectCodec.encode(p)).getInt("schema"))
        assertEquals(p,StudioProjectCodec.decode(StudioProjectCodec.encode(p)))
        for(schema in 1..6) {
            val old=JSONObject(StudioProjectCodec.encode(StudioProject(title="Old",clips=listOf(c))))
            old.put("schema",schema);old.getJSONArray("clips").getJSONObject(0).remove("cinema")
            old.getJSONObject("tools").remove("graphics")
            val loaded=StudioProjectCodec.decode(old.toString());assertEquals(CinemaStyle(),loaded.clips.single().cinema);assertTrue(loaded.tools.graphics.isEmpty())
        }
    }
    @Test fun easingEndpointsMidpointsAndSparseCurveHoldsAreStable() {
        for(e in CinemaEase.entries) {assertEquals(0f,e.fraction(0f),0f);assertEquals(1f,e.fraction(1f),0f)}
        assertEquals(0.25f,CinemaEase.IN.fraction(0.5f),0.00001f);assertEquals(0.75f,CinemaEase.OUT.fraction(0.5f),0.00001f)
        assertEquals(0.5f,CinemaEase.IN_OUT.fraction(0.5f),0.00001f)
        val keys=listOf(CinemaScalarKey(100,0.2f,CinemaEase.IN),CinemaScalarKey(1100,1f))
        assertEquals(0.2f,CinemaCurves.scalar(keys,0),0f);assertEquals(1f,CinemaCurves.scalar(keys,9999),0f);assertEquals(0.4f,CinemaCurves.scalar(keys,600),0.00001f)
        val pose=CinemaCurves.motion(listOf(CinemaMotionKey(0),CinemaMotionKey(1000,CinemaPose(1f,-1f,3f,180f,0f))),500)
        assertEquals(CinemaPose(0.5f,-0.5f,2f,90f,0.5f),pose)
    }
    @Test fun malformedGradeCurvesAndGraphicsCannotOverwriteAValidProject() {
        val p=StudioProject(title="Keep",clips=listOf(clip()))
        fun rejects(next:StudioProject) {try {StudioProjectCodec.encode(next);fail("Expected safe rejection")} catch(expected:IllegalArgumentException) {}}
        rejects(p.copy(clips=listOf(clip().copy(cinema=CinemaStyle(grade=CinemaGrade(gamma=0f))))))
        rejects(p.copy(clips=listOf(clip().copy(cinema=CinemaStyle(motion=listOf(CinemaMotionKey(1000),CinemaMotionKey(0)))))))
        rejects(p.copy(clips=listOf(clip().copy(cinema=CinemaStyle(lookKeys=listOf(CinemaScalarKey(0,Float.NaN)))))))
        rejects(p.copy(tools=p.tools.copy(graphics=listOf(CinemaGraphic(kind=CinemaGraphicKind.IMAGE,uri="https://invalid/image",endMs=1000)))))
        assertEquals(6000L,p.durationMs)
    }
    @Test fun renderedChangesInvalidateExportsButColorClipboardOmitsTiming() {
        val c=clip();val p=StudioProject(title="P",clips=listOf(c));val style=CinemaStyle(CinemaLook.COOL,0.5f,motion=listOf(CinemaMotionKey(0)),exit=CinemaTransition(CinemaTransitionKind.WHITE))
        assertNotEquals(p.fingerprint(),p.copy(clips=listOf(c.copy(cinema=style))).fingerprint())
        assertTrue(style.colorOnly().motion.isEmpty());assertEquals(CinemaTransitionKind.NONE,style.colorOnly().exit.kind)
        assertEquals(style.grade,style.colorOnly().grade);assertEquals(style.look,style.colorOnly().look)
    }
    @Test fun transitionsAreClampedAtBothNeighborsAndDoNotChangeDuration() {
        val a=clip().copy(endMs=1000,cinema=CinemaStyle(exit=CinemaTransition(CinemaTransitionKind.WHITE,2000)))
        val b=clip().copy(endMs=400);val p=StudioProject(title="Boundary",clips=listOf(a,b))
        assertEquals(200L,CinemaTransitions.half(p,0));assertEquals(1400L,p.durationMs)
        assertEquals(0f,CinemaTransitions.amount(p,0,800).first,0f)
        assertEquals(0.5f,CinemaTransitions.amount(p,0,900).first,0.0001f)
        assertEquals(1f,CinemaTransitions.amount(p,1,0).first,0f);assertEquals(1f,CinemaTransitions.amount(p,1,0).second,0f)
        assertEquals(0f,CinemaTransitions.amount(p,1,200).first,0f)
    }
    @Test fun muteSoloVolumeKeysNormalizationAndTimedVoiceDuckingUseOneGainRule() {
        val c=clip();val music=StudioAudioLane(uri=c.uri,name="Music",sourceDurationMs=6000,volume=0.5f,duck=true,
            volumeKeys=listOf(CinemaScalarKey(0,0f),CinemaScalarKey(1000,1f)),normalization=2f)
        val voice=StudioAudioLane(uri=c.uri,name="Voice",sourceDurationMs=6000,endMs=2000,offsetMs=1000,role=CinemaAudioRole.VOICE)
        val p=StudioProject(title="Mix",clips=listOf(c),tools=StudioTools(audio=listOf(music,voice)))
        assertEquals(0.5f,CinemaMix.gain(p,music,500),0.0001f)
        assertEquals(0.25f,CinemaMix.gain(p,music,1500),0.0001f)
        assertEquals(1f,CinemaMix.gain(p,music,4000),0f)
        val solo=p.copy(tools=p.tools.copy(audio=listOf(music,voice.copy(solo=true))))
        assertEquals(0f,CinemaMix.sourceVolume(solo),0f);assertEquals(0f,CinemaMix.gain(solo,music,1500),0f)
        assertEquals(0f,CinemaMix.gain(p,voice.copy(muted=true),1500),0f)
    }
    @Test fun splitAudioPreservesSourceFadeAndVolumeAutomationAtEverySampleWindow() {
        val c=clip();val a=StudioAudioLane(uri=c.uri,name="Fade",sourceDurationMs=6000,fadeInMs=4000,fadeOutMs=1000,volumeKeys=listOf(CinemaScalarKey(0,0.5f),CinemaScalarKey(6000,1.5f)))
        val p=StudioProject(title="Split",clips=listOf(c),tools=StudioTools(audio=listOf(a)))
        val next=CinemaMix.split(p,a.id,1500);assertEquals(2,next.tools.audio.size);assertEquals(1500L,next.tools.audio.last().offsetMs)
        for(time in 0L until 6000L step 37) {val lane=next.tools.audio.first {time in it.startMs until it.endMs};assertEquals(CinemaMix.gain(p,a,time),CinemaMix.gain(next,lane,time),0.000001f)}
        assertEquals(next,StudioProjectCodec.decode(StudioProjectCodec.encode(next)))
    }
    @Test fun smartCutsPreserveMotionPhaseGraphicsAndAudioEnvelopes() {
        val c=clip();val keys=listOf(CinemaMotionKey(0),CinemaMotionKey(6000,CinemaPose(x=1f)))
        val a=StudioAudioLane(uri=c.uri,name="Music",sourceDurationMs=6000,fadeInMs=4000)
        val p=StudioProject(title="Cuts",clips=listOf(c),tools=StudioTools(audio=listOf(a),textLayers=listOf(StudioTextLayer(text="Title",endMs=6000,motion=keys)),graphics=listOf(CinemaGraphic(endMs=6000,motion=keys))))
        val next=SmartCuts.remove(p,0,listOf(CreativeRange(1000,2000)))
        val title=next.tools.textLayers.last();assertEquals(2000L,title.motionOffsetMs);assertEquals(2000L,next.tools.graphics.last().motionOffsetMs)
        assertEquals(CinemaCurves.motion(keys,2500),CinemaCurves.motion(title.motion,1500-title.startMs+title.motionOffsetMs))
        assertEquals(CinemaMix.gain(p,a,2500),CinemaMix.gain(next,next.tools.audio.last(),2500),0.00001f)
    }
    @Test fun steppedSpeedSequenceHasExactDurationSourceMotionAndOneUndo() {
        val c=clip().copy(cinema=CinemaStyle(motion=listOf(CinemaMotionKey(0),CinemaMotionKey(6000,CinemaPose(x=0.5f))),exit=CinemaTransition(CinemaTransitionKind.BLACK)))
        val p=StudioProject(title="Speed",clips=listOf(c));val next=CinemaSpeed.apply(p,0,listOf(CinemaSpeedKey(0,0.5f),CinemaSpeedKey(2000,1f),CinemaSpeedKey(4000,2f)))
        assertEquals(7000L,next.durationMs);assertEquals(listOf(0L,2000L,4000L),next.clips.map {it.startMs});assertTrue(next.clips.all {it.cinema.motion==c.cinema.motion})
        assertEquals(CinemaTransitionKind.NONE,next.clips.first().cinema.exit.kind);assertEquals(CinemaTransitionKind.BLACK,next.clips.last().cinema.exit.kind)
        val h=StudioHistory();h.record(p);assertEquals(p,h.undo(next));assertEquals(next,h.redo(p))
        val timed=p.copy(tools=p.tools.copy(captions=listOf(StudioCaption(0,1000,"Keep"))))
        try {CinemaSpeed.apply(timed,0,listOf(CinemaSpeedKey(0,1f),CinemaSpeedKey(3000,2f)));fail()} catch(expected:IllegalArgumentException) {}
    }
    @Test fun splitAndMergeNeverDropACinematicBoundaryOrMotion() {
        val c=clip().copy(cinema=CinemaStyle(exit=CinemaTransition(CinemaTransitionKind.WHITE)))
        val parts=c.split(3000);assertEquals(CinemaTransitionKind.NONE,parts.first().cinema.exit.kind);assertEquals(CinemaTransitionKind.WHITE,parts.last().cinema.exit.kind)
        val p=StudioProject(title="Merge",clips=listOf(c.copy(endMs=3000),c.copy(id=java.util.UUID.randomUUID().toString(),startMs=3000)))
        try {StudioEdits.mergeNext(p,0);fail()} catch(expected:IllegalArgumentException) {}
    }
    @Test fun timelineMarkersRespectTrimSpeedOffsetAndOverlayAnimationPhase() {
        val prefix=clip().copy(endMs=1000)
        val source=clip().copy(startMs=2000,endMs=4000,speed=2f,cinema=CinemaStyle(motion=listOf(CinemaMotionKey(0),CinemaMotionKey(3000)),lookKeys=listOf(CinemaScalarKey(2500,0.5f))))
        val overlay=StudioTextLayer(text="Retimed",startMs=500,endMs=1200,motionOffsetMs=1000,
            motion=listOf(CinemaMotionKey(0),CinemaMotionKey(1100),CinemaMotionKey(3000)))
        val p=StudioProject(title="Markers",clips=listOf(prefix,source),tools=StudioTools(textLayers=listOf(overlay)))
        assertEquals(listOf(600L,1250L,1500L),CinemaMarkers.times(p))
    }

}
