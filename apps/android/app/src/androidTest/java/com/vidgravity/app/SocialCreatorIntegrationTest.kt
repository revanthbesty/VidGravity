package com.vidgravity.app

import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SocialCreatorIntegrationTest {
    @Test fun brandLogoPersistenceAndSocialProjectsReopen():Unit=runBlocking(Dispatchers.IO) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val repository=CreatorBrandRepository(context)
        val previous=repository.load();val fixture=studioFixture("native-source.mp4")
        val p=StudioProject(title="Social persistence",clips=listOf(StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,fixture))))
        val logoFile=File(context.cacheDir,"social-test-logo.png")
        val image=android.graphics.Bitmap.createBitmap(1024,256,android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.CYAN)
        try {logoFile.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {image.recycle()}
        val logo=try {repository.importLogo(MediaLibraryIndexer.localUri(context,logoFile))} finally {logoFile.delete()}
        val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
        context.contentResolver.openInputStream(android.net.Uri.parse(logo))!!.use {android.graphics.BitmapFactory.decodeStream(it,null,bounds)}
        assertTrue(bounds.outWidth<=512 && bounds.outHeight<=512)
        val kit=CreatorBrand(name="Local creator",logo=logo,intro="Welcome",outro="Follow",color=0xFFFFFF00.toInt(),font="serif")
        val projects=StudioRepository(context);var saved:StudioProject?=null
        try {
            repository.save(kit);assertEquals(kit,repository.load())
            val next=kit.apply(SocialCreator.create(p,SocialCreator.templates.first(),"Your hook",2));saved=next
            projects.save(next);assertEquals(next,projects.load(next.id));assertEquals("Local creator",projects.load(next.id).tools.social.watermark)
        } finally {repository.save(previous);saved?.let {projects.delete(it.id)};context.contentResolver.delete(android.net.Uri.parse(logo),null,null)}
    }
    @Test fun realFeedAndClassicExportsContainGraphicsAndPreserveSource():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val fixture=studioFixture("native-source.mp4");val hash=DownloadApi.sha256(fixture)
        val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,fixture)).copy(endMs=1500)
        for((aspect,width,height) in listOf(Triple(StudioAspect.FEED,720,900),Triple(StudioAspect.CLASSIC,960,720))) {
            val p=StudioProject(title="Social render",clips=listOf(clip),tools=StudioTools(aspect=aspect,captions=listOf(StudioCaption(0,1500,"SOCIAL")),social=CreatorStyle(fit=CreatorFit.FILL,animation=CreatorAnimation.SLIDE,watermark="CREATOR",progress=true,lowerThird="Native render")))
            val out=File(context.cacheDir,"social-${UUID.randomUUID()}.mp4")
            try {withTimeout(120_000) {NativeStudioRenderer.render(context,p,out) {}}
                val reader=MediaMetadataRetriever();try {
                    reader.setDataSource(out.path);assertEquals(width.toString(),reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));assertEquals(height.toString(),reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                    val bitmap=reader.getFrameAtTime(500_000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                    var lit=0;for(y in 0 until bitmap.height step 16) for(x in 0 until bitmap.width step 16) {val c=bitmap.getPixel(x,y);if(android.graphics.Color.red(c)+android.graphics.Color.green(c)+android.graphics.Color.blue(c)>120) lit++}
                    bitmap.recycle();assertTrue("Rendered output must not be black",lit>100)
                } finally {reader.release()}
            } finally {out.delete()}
        }
        assertEquals(hash,DownloadApi.sha256(fixture))
    }
    @Test fun serializedExportsPublishRecoverAndReuseUnchangedOutput():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val repository=StudioRepository(context);val manager=WorkManager.getInstance(context)
        val file=studioFixture("native-source.mp4");val clip=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,file)).copy(endMs=1000)
        val variants=SocialCreator.variants(StudioProject(title="Queue regression",clips=listOf(clip)),listOf(StudioAspect.SQUARE,StudioAspect.FEED),CreatorFit.FIT)
        val ids=withContext(Dispatchers.IO) {variants.map {NativeStudioExportWorker.enqueue(context,it)}}
        try {
            withTimeout(180_000) {while(true) {
                val works=withContext(Dispatchers.IO) {ids.map {manager.getWorkInfoById(it).get()}}
                assertTrue("Native encoders must run serially",works.count {it.state==WorkInfo.State.RUNNING}<=1)
                if(works.all {it.state.isFinished}) {works.forEach {assertEquals(it.outputData.getString("error"),WorkInfo.State.SUCCEEDED,it.state)};break};delay(500)
            }}
            val again=withContext(Dispatchers.IO) {NativeStudioExportWorker.enqueue(context,variants[0])};assertEquals(ids[0],again)
            withContext(Dispatchers.IO) {variants.forEach {p ->val record=repository.record(p.id)!!;assertNotNull(record.uri);assertEquals(p.fingerprint(),record.fingerprint)
                context.contentResolver.openInputStream(android.net.Uri.parse(record.uri))!!.use {input ->val copy=File(context.cacheDir,"queue-hash.mp4");try {copy.outputStream().use {input.copyTo(it)};assertEquals(manager.getWorkInfoById(UUID.fromString(record.workId)).get().outputData.getString("sha256"),DownloadApi.sha256(copy))} finally {copy.delete()}}}}
        } finally {withContext(Dispatchers.IO) {ids.forEach {manager.cancelWorkById(it).result.get()};variants.forEach {p ->repository.record(p.id)?.uri?.let {context.contentResolver.delete(android.net.Uri.parse(it),null,null)};repository.delete(p.id)}}}
    }
    @Test fun missingMediaFailsWithoutPublishingAndPreservesProject():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val repository=StudioRepository(context);val manager=WorkManager.getInstance(context)
        val p=StudioProject(title="Missing media",clips=listOf(StudioClip(uri="content://com.vidgravity.app.files/legacy_media/missing-${UUID.randomUUID()}.mp4",name="Missing",sourceDurationMs=1000)))
        val id=withContext(Dispatchers.IO) {NativeStudioExportWorker.enqueue(context,p)}
        try {withTimeout(30_000) {while(true) {val w=withContext(Dispatchers.IO) {manager.getWorkInfoById(id).get()};if(w.state.isFinished) {assertEquals(WorkInfo.State.FAILED,w.state);assertNull(w.outputData.getString("content_uri"));break};delay(250)}}
            assertEquals(p,withContext(Dispatchers.IO) {repository.load(p.id)})
        } finally {withContext(Dispatchers.IO) {manager.cancelWorkById(id).result.get();repository.delete(p.id)}}
    }
}
