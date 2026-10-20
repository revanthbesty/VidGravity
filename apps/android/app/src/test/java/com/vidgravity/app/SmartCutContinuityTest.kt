package com.vidgravity.app

import org.junit.Assert.*
import org.junit.Test

class SmartCutContinuityTest {
    private fun clips(count:Int)=List(count) {
        StudioClip(uri="content://media/external/video/media/${it+1}",name="Clip $it",sourceDurationMs=5000,endMs=1000)
    }
    @Test fun cutsSplitLayersOnlyAtRemovedTimeAndNotAtVideoBoundaries() {
        val video=clips(5)
        val lane=StudioAudioLane(uri=video[0].uri,name="Continuous music",sourceDurationMs=5000,fadeInMs=100,fadeOutMs=100)
        val title=StudioTextLayer(text="Continuous title",endMs=5000,animation=CreatorAnimation.FADE)
        val p=StudioProject(title="Five clips",clips=video,tools=StudioTools(audio=listOf(lane),textLayers=listOf(title),captions=listOf(StudioCaption(0,5000,"Continuous caption"))))
        val next=SmartCuts.remove(p,0,listOf(CreativeRange(200,400)))
        assertEquals(4800L,next.durationMs);assertEquals(6,next.clips.size)
        assertEquals(2,next.tools.audio.size);assertEquals(2,next.tools.textLayers.size)
        assertEquals(listOf(0L to 200L,200L to 4800L),next.tools.captions.map {it.startMs to it.endMs})
        assertEquals(listOf(0L to 200L,400L to 5000L),next.tools.audio.map {it.startMs to it.endMs})
        assertEquals(listOf(0L,200L),next.tools.audio.map {it.offsetMs})
        assertEquals(listOf(100L,0L),next.tools.audio.map {it.fadeInMs})
        assertEquals(listOf(0L,100L),next.tools.audio.map {it.fadeOutMs})
        assertEquals(lane.id,next.tools.audio.first().id);assertEquals(title.id,next.tools.textLayers.first().id)
        assertEquals(2,next.tools.audio.map {it.id}.distinct().size)
        assertEquals(2,next.tools.textLayers.map {it.id}.distinct().size)
        assertEquals(next,StudioProjectCodec.decode(StudioProjectCodec.encode(next)))
        val history=StudioHistory();history.record(p);assertEquals(p,history.undo(next));assertEquals(next,history.redo(p))
        assertEquals(5000L,p.durationMs);assertEquals(listOf(lane),p.tools.audio)
    }
    @Test fun unaffectedLayersAtCapacityRetainIdentityFadesAndAnimationAcrossClips() {
        val video=clips(5)
        val audio=List(4) {StudioAudioLane(uri=video[0].uri,name="Track $it",sourceDurationMs=5000,startMs=1000,endMs=4000,offsetMs=1000,fadeInMs=1500,fadeOutMs=500)}
        val text=List(16) {StudioTextLayer(text="Title $it",startMs=1000,endMs=5000,animation=CreatorAnimation.FADE)}
        val captions=List(100) {StudioCaption(1000,5000,"Cue $it")}
        val p=StudioProject(title="At capacity",clips=video,tools=StudioTools(audio=audio,textLayers=text,captions=captions))
        p.validate()
        val next=SmartCuts.remove(p,0,listOf(CreativeRange(200,400)))
        assertEquals(audio.map {it.copy(offsetMs=800)},next.tools.audio)
        assertEquals(text.map {it.copy(startMs=800,endMs=4800)},next.tools.textLayers)
        assertEquals(captions.map {it.copy(startMs=800,endMs=4800)},next.tools.captions)
    }
    @Test fun wholeClipDeletionKeepsTheRealGapButJoinsOtherContinuousBoundaries() {
        val video=clips(5)
        val p=StudioProject(title="Delete middle",clips=video,tools=StudioTools(captions=listOf(StudioCaption(0,5000,"Across all clips"))))
        val next=SmartCuts.remove(p,2,listOf(CreativeRange(0,1000)))
        assertEquals(4000L,next.durationMs);assertEquals(listOf(video[0],video[1],video[3],video[4]),next.clips)
        assertEquals(listOf(0L to 2000L,2000L to 4000L),next.tools.captions.map {it.startMs to it.endMs})
    }
    @Test fun fractionalSpeedBoundariesPreserveLayerTimesAndTheUncutTail() {
        val video=clips(4).map {it.copy(endMs=999,speed=1.5f)}
        val p=StudioProject(title="Speed",clips=video,tools=StudioTools(captions=listOf(StudioCaption(0,2664,"Across fast clips"))))
        val next=SmartCuts.remove(p,0,listOf(CreativeRange(300,600)))
        assertEquals(2464L,next.durationMs)
        assertEquals(listOf(0L to 200L,200L to 2464L),next.tools.captions.map {it.startMs to it.endMs})
        assertEquals(999L,next.clips.last().endMs)
    }
}
