package com.vidgravity.app

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CreativeStudioTest {
    private fun project()=StudioProject(title="My trip",clips=listOf(StudioClip(uri="content://video/1",name="one.mp4",sourceDurationMs=20_000)))
    private fun rejected(block:()->Unit) {try {block(); fail("Expected rejection")} catch(expected:IllegalArgumentException) {}}
    @Test fun schemaTwoMigratesWithNeutralFramingAndSoftFocus() {
        val p=project(); val old=JSONObject(StudioProjectCodec.encode(p)).put("schema",2)
        old.getJSONObject("tools").remove("soften"); old.getJSONArray("clips").getJSONObject(0).remove("frame")
        assertEquals(p,StudioProjectCodec.decode(old.toString()))
        val changed=p.copy(clips=p.clips.map {it.copy(frame=StudioFrame(0.2f,0f,0.7f,1f))},tools=p.tools.copy(soften=1f))
        assertEquals(changed,StudioProjectCodec.decode(StudioProjectCodec.encode(changed)))
        assertNotEquals(p.fingerprint(),changed.fingerprint())
    }
    @Test fun faceGuidedFramingStaysInsideSourceAtEdges() {
        for(x in listOf(0f,0.2f,0.5f,0.9f,1f)) {
            val f=CreativeRules.portraitFrame(16f/9f,x,0.5f); f.validate()
            assertTrue(x>=f.left && x<=f.right)
            assertEquals(9f/16f,(f.right-f.left)*16f/9f,0.0001f)
        }
        rejected {StudioFrame(0.8f,0f,0.2f,1f).validate()}
    }
    @Test fun silenceGroupingAndCutComplementDoNotDiscardUnmarkedAudio() {
        val windows=(0..19).map {i -> AudioObservation(i*200L,(i+1)*200L,if(i in 5..10) 0f else 0.2f)}
        val silence=CreativeRules.silence(windows)
        assertEquals(listOf(CreativeRange(1000,2200)),silence)
        val audible=CreativeRules.keepAudible(4000,silence)
        assertEquals(listOf(CreativeRange(0,1000),CreativeRange(2200,4000)),audible)
        assertEquals(4000L,audible.sumOf {it.durationMs}+silence.sumOf {it.durationMs})
        assertTrue(CreativeRules.keepAudible(4000,listOf(CreativeRange(0,4000))).isEmpty())
    }
    @Test fun derivedCutPreservesOriginalAndUsesSourceRelativeRanges() {
        val source=project().copy(clips=project().clips.map {it.copy(startMs=1000,endMs=9000,speed=2f)},
            musicUri="content://music/1",tools=StudioTools(captions=listOf(StudioCaption(0,1000,"Keep me"))))
        val cut=CreativeRules.derivedCut(source,source.clips[0],listOf(CreativeRange(0,1000),CreativeRange(2000,4000)),"Cut")
        assertNotEquals(source.id,cut.id); assertEquals(1000L,cut.clips[0].startMs); assertEquals(3000L,cut.clips[1].startMs)
        assertEquals(1500L,cut.durationMs); assertNull(cut.musicUri); assertTrue(cut.tools.captions.isEmpty())
        assertEquals("Keep me",source.tools.captions[0].text); assertEquals("content://music/1",source.musicUri)
        rejected {CreativeRules.derivedCut(source,source.clips[0],emptyList(),"Cut")}
    }
    @Test fun everyTemplateCreatesASeparateEditableProjectWithinSourceDuration() {
        val p=project()
        assertEquals(8,CreativeTemplates.all.size)
        CreativeTemplates.all.forEach {t ->
            val next=CreativeTemplates.create(p,t,"My opening",5)
            next.validate(); assertNotEquals(p.id,next.id); assertTrue(next.durationMs<=5000)
            assertEquals(t.aspect,next.tools.aspect); assertEquals("My opening",next.tools.captions.single().text)
            assertEquals(p.clips[0].uri,next.clips[0].uri)
            assertEquals(next,StudioProjectCodec.decode(StudioProjectCodec.encode(next)))
        }
        assertEquals(20_000L,p.durationMs)
    }
    @Test fun highlightsAreBoundedAndFavorUsefulFrameChanges() {
        val frames=listOf(FrameObservation(0,0f,0f,0f),FrameObservation(15_000,0.5f,0.4f,0.8f))
        val r=CreativeRules.highlight(frames,20_000)
        assertEquals(CreativeRange(10_000,20_000),r)
        assertEquals(CreativeRange(0,500),CreativeRules.highlight(emptyList(),500))
    }
    @Test fun writingProviderIsOfflineRulesAndCapabilityFallbackIsExplicit():Unit=runBlocking {
        val provider=OfflineCreativeRules(); assertFalse(provider.genuineInference)
        val copy=provider.generate(CreativeBrief("Trail camera tips","beginners"))
        assertTrue(copy.description.contains("Trail camera tips")); assertTrue(copy.hashtags.contains("#Trail")); assertEquals(3,copy.script.size)
        assertFalse(CreativeCapabilities.offlineDictation(29,true)); assertFalse(CreativeCapabilities.offlineDictation(35,false))
        assertTrue(CreativeCapabilities.offlineDictation(35,true)); assertFalse(CreativeCapabilities.videoTranscriptionAvailable)
        assertEquals(0L,CreativeCapabilities.bundledLanguageModelBytes)
    }
    @Test fun thumbnailSettingsRoundTripAndRejectRemoteInputs() {
        val p=project(); val t=StudioThumbnail(projectId=p.id,source=p.clips[0].uri,durationMs=20_000,timeMs=1500,title="Title",subtitle="Sub",
            badge="TIP",zoom=0.2f,brightness=0.1f,contrast=0.1f,color=0xFFFFFF00.toInt(),font="serif",rotation=90)
        assertEquals(t,ThumbnailCodec.decode(ThumbnailCodec.encode(t)))
        rejected {t.copy(source="https://example.com/file.mp4").validate()}
        rejected {t.copy(timeMs=20_000).validate()}
    }
}
