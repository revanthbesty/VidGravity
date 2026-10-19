package com.vidgravity.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudioSprint10Test {
    private fun base()=StudioProject(title="Layers",clips=listOf(StudioClip(uri="content://video/1",name="one",sourceDurationMs=5000)))
    private fun rejects(action:()->Unit) {try {action();fail("Invalid project was accepted")} catch(expected:IllegalArgumentException) {}}
    @Test fun independentLayersOpacityAndFadeSurviveSaveFormatAndInvalidateExport() {
        val p=base();val layer=StudioTextLayer(text="Title\n字幕",startMs=200,endMs=2000,font="serif",color=0xFFFFFF00.toInt(),background=0xCC000000.toInt(),size=48,x=0.2f,y=-0.3f,rotation=25f,animation=CreatorAnimation.SLIDE)
        val next=p.copy(clips=p.clips.map {it.copy(opacity=0.6f)},fadeEdges=true,tools=p.tools.copy(textLayers=listOf(layer,layer.copy(id=java.util.UUID.randomUUID().toString(),text="Front")),fadeDurationMs=700))
        assertEquals(next,StudioProjectCodec.decode(StudioProjectCodec.encode(next)))
        assertNotEquals(next.fingerprint(),next.copy(tools=next.tools.copy(textLayers=next.tools.textLayers.reversed())).fingerprint())
        val old=JSONObject(StudioProjectCodec.encode(p)).put("schema",4)
        old.getJSONObject("tools").remove("textLayers");old.getJSONObject("tools").remove("fadeDuration")
        old.getJSONArray("clips").getJSONObject(0).remove("opacity")
        assertEquals(p,StudioProjectCodec.decode(old.toString()))
    }
    @Test fun invalidLayerBoundsDuplicatesAndNonFiniteValuesAreRejected() {
        val p=base();val layer=StudioTextLayer(endMs=1000)
        rejects {p.copy(tools=p.tools.copy(textLayers=listOf(layer,layer))).validate()}
        rejects {p.copy(tools=p.tools.copy(textLayers=listOf(layer.copy(endMs=6000)))).validate()}
        rejects {p.copy(tools=p.tools.copy(textLayers=listOf(layer.copy(rotation=Float.NaN)))).validate()}
        rejects {p.copy(clips=p.clips.map {it.copy(opacity=Float.POSITIVE_INFINITY)}).validate()}
        rejects {p.copy(tools=p.tools.copy(fadeDurationMs=0)).validate()}
    }
    @Test fun mergeCannotSilentlyDiscardAnEditedCropOrOpacity() {
        val split=base().copy(clips=base().clips.single().split(2000))
        rejects {StudioEdits.mergeNext(split.copy(clips=split.clips.mapIndexed {i,c ->if(i==1) c.copy(opacity=0.4f) else c}),0)}
        rejects {StudioEdits.mergeNext(split.copy(clips=split.clips.mapIndexed {i,c ->if(i==1) c.copy(frame=StudioFrame(0.1f,0f,1f,1f)) else c}),0)}
        assertEquals(5000,StudioEdits.mergeNext(split,0).durationMs.toInt())
    }
    @Test fun snappingAndAbsoluteFrameStepsRemainBoundedWithoutRoundingDrift() {
        assertEquals(1000L,StudioTiming.snap(1040,listOf(0,1000,2000),2000))
        assertEquals(1200L,StudioTiming.snap(1200,listOf(0,1000,2000),2000))
        assertEquals(500L,StudioTiming.snap(1000,listOf(1000),500))
        assertEquals(1000L,StudioTiming.frame(1001,30,5000))
        assertEquals(958L,StudioTiming.frame(960,24,5000))
        assertEquals(0L,StudioTiming.frame(-100,30,5000))
    }
    @Test fun monitoringUsesOutputTimeTrimmingLoopingAndClippedFadeLength() {
        val lane=StudioPreviewAudio("content://audio/1",100,1100,500,0.8f,200,200)
        assertNull(lane.position(499,1000,2000));assertEquals(250L,lane.position(750,1000,2000))
        assertNull(lane.position(1500,1000,2000))
        assertEquals(0.4f,lane.volume(100,1000),0.001f);assertEquals(0.4f,lane.volume(900,1000),0.001f)
        assertEquals(250L,lane.copy(loop=true).position(1750,1000,3000))
        assertNull(lane.position(2000,1000,2000))
    }
    @Test fun effectClocksUseCompositionTimeForExportAndClippedItemTimeForPreview() {
        val export=StudioEffectClock(500,0.5f,true)
        val preview=export.copy(exporting=false)
        assertEquals(1250L,export.outputMs(1_250_000))
        assertEquals(1250L,preview.outputMs(375_000))
        assertEquals(750_000L,export.clipUs(1_250_000))
        assertEquals(750_000L,preview.clipUs(375_000))
    }
    @Test fun adjustableFadeMatchesSpeedAdjustedOutputClock() {
        assertEquals(0.5f,StudioFade.gain(200_000,2_000_000,400_000),0.001f)
        assertEquals(0.5f,StudioFade.gain(1_800_000,2_000_000,400_000),0.001f)
        assertEquals(1f,StudioFade.gain(1_000_000,2_000_000,400_000),0.001f)
    }
}
