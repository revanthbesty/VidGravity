package com.vidgravity.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SocialCreatorTest {
    private fun project()=StudioProject(title="Source",clips=listOf(StudioClip(uri="content://video/one",name="source.mp4",sourceDurationMs=12_000)))
    private fun rejected(action:()->Unit) {try {action();fail("Expected rejection")} catch(expected:IllegalArgumentException) {}}
    @Test fun priorSchemaMigratesNeutralAndSocialSettingsInvalidateExports() {
        val p=project();val old=JSONObject(StudioProjectCodec.encode(p)).put("schema",3);old.getJSONObject("tools").remove("social")
        assertEquals(p,StudioProjectCodec.decode(old.toString()))
        val next=p.copy(tools=p.tools.copy(aspect=StudioAspect.FEED,social=CreatorStyle(fit=CreatorFit.FILL,animation=CreatorAnimation.POP,watermark="Brand",progress=true)))
        assertEquals(next,StudioProjectCodec.decode(StudioProjectCodec.encode(next)));assertNotEquals(p.fingerprint(),next.fingerprint())
    }
    @Test fun allWorkflowsAndOriginalTemplatesCreateEditableBoundedProjects() {
        val p=project()
        assertEquals(9,SocialCreator.workflows.size);assertEquals(10,SocialCreator.templates.size)
        (SocialCreator.workflows+SocialCreator.templates).forEach {r ->
            val n=SocialCreator.create(p,r,"Opening",3);n.validate();assertNotEquals(p.id,n.id)
            assertTrue(n.durationMs in 100..3000);assertEquals(r.aspect,n.tools.aspect);assertTrue(n.tools.captions.isNotEmpty())
            assertEquals(p.clips.first().uri,n.clips.first().uri);assertEquals(n,StudioProjectCodec.decode(StudioProjectCodec.encode(n)))
        }
        assertEquals(12_000,p.durationMs)
    }
    @Test fun beforeAfterUsesTimedLabelsAndDurationFitDoesNotExtend() {
        val p=project();val n=SocialCreator.create(p,SocialCreator.templates.first {it.id=="before"},"Before",30)
        assertEquals(12_000,n.durationMs);assertEquals(listOf("BEFORE","AFTER"),n.tools.captions.map {it.text})
        assertEquals(n.tools.captions[0].endMs,n.tools.captions[1].startMs)
    }
    @Test fun variantsKeepSourceIndependentAndRejectDuplicateFormats() {
        val p=project();val variants=SocialCreator.variants(p,listOf(StudioAspect.PORTRAIT,StudioAspect.FEED,StudioAspect.CLASSIC),CreatorFit.FIT)
        assertEquals(3,variants.map {it.id}.distinct().size);assertTrue(variants.all {it.clips==p.clips && it.tools.social.fit==CreatorFit.FIT})
        rejected {SocialCreator.variants(p,listOf(StudioAspect.FEED,StudioAspect.FEED),CreatorFit.FILL)}
    }
    @Test fun brandSnapshotRoundTripsAndCanRemoveWatermark() {
        val b=CreatorBrand(name="Test Brand",color=0xFFFFFF00.toInt(),font="serif",intro="Hello",outro="Thanks")
        assertEquals(b,CreatorBrand.decode(b.json()));val p=b.apply(project());assertEquals("Test Brand",p.tools.social.watermark)
        assertEquals(2,p.tools.captions.size);assertEquals("serif",p.tools.captionFont)
        assertEquals("",p.copy(tools=p.tools.copy(social=CreatorStyle())).tools.social.watermark)
        rejected {CreatorBrand(font="unknown").validate()}
    }
    @Test fun beatGridJumpCutsAndSteppedSpeedPreserveSources() {
        val p=project().copy(clips=project().clips.map {it.copy(endMs=3000)})
        val beat=SocialCreator.cutGrid(p,0,120);assertEquals(6,beat.clips.size);assertEquals(p.durationMs,beat.durationMs)
        val jump=SocialCreator.cutGrid(p,0,120,true);assertEquals(1500,jump.durationMs)
        val ramp=SocialCreator.speedRamp(p,0);assertEquals(listOf(0.5f,1f,2f),ramp.clips.map {it.speed});assertEquals(3500,ramp.durationMs)
        assertTrue((beat.clips+jump.clips+ramp.clips).all {it.uri==p.clips[0].uri});rejected {SocialCreator.cutGrid(project(),0,240)}
    }
    @Test fun conservativeStorageGuardAndSizeEstimate() {
        val p=project().copy(tools=StudioTools(bitrate=5_000_000))
        assertEquals(7_788_000,SocialCreator.estimatedBytes(p));rejected {SocialCreator.requireSpace(1024,SocialCreator.estimatedBytes(p))}
        SocialCreator.requireSpace(200_000_000,SocialCreator.estimatedBytes(p))
    }
    @Test fun oldThumbnailMigratesAndCoverStylePersists() {
        val p=project();val t=StudioThumbnail(projectId=p.id,source=p.clips[0].uri,durationMs=12000,timeMs=0,background=0xCC1E1B4B.toInt(),aspect=StudioAspect.FEED)
        assertEquals(t,ThumbnailCodec.decode(ThumbnailCodec.encode(t)))
        val j=JSONObject(ThumbnailCodec.encode(t)).put("schema",1);j.remove("background")
        assertEquals(0xB3000000.toInt(),ThumbnailCodec.decode(j.toString()).background)
    }
}
