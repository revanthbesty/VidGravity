package com.vidgravity.app

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CinematicIntegrationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun clip(name:String)=StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture(name)))
    private suspend fun render(p:StudioProject,check:(MediaMetadataRetriever,File)->Unit) {
        val output=File(context.cacheDir,"cinema-${UUID.randomUUID()}.mp4");val reader=MediaMetadataRetriever()
        try {
            withTimeout(120_000) {NativeStudioRenderer.render(context,p,output) {}}
            reader.setDataSource(output.path)
            assertEquals("yes",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
            val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Wrong output duration $duration vs ${p.durationMs}",kotlin.math.abs(duration-p.durationMs)<350)
            check(reader,output)
        } catch(failure:Throwable) {
            runCatching {
                val dir=File(context.getExternalFilesDir(null),"smoke-evidence/sprint12/${p.title.replace(Regex("[^a-zA-Z0-9-]"),"_")}-${UUID.randomUUID()}").apply {mkdirs()}
                if(output.exists()) output.copyTo(File(dir,"failed-output.mp4"))
                for(us in listOf(200_000L,600_000L,1_200_000L,2_200_000L)) {
                    val b=reader.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    try {File(dir,"frame-$us.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {b.recycle()}
                }
            }
            throw failure
        } finally {reader.release();output.delete()}
    }
    private fun color(reader:MediaMetadataRetriever,timeUs:Long=500_000,x:Float=0.5f,y:Float=0.5f):Int {
        val b=reader.getFrameAtTime(timeUs,MediaMetadataRetriever.OPTION_CLOSEST)!!
        return try {b.getPixel((x*b.width).toInt().coerceIn(0,b.width-1),(y*b.height).toInt().coerceIn(0,b.height-1))} finally {b.recycle()}
    }
    @Test fun warmColorGradeAndZeroLookIntensityRenderRealPixelsAfterSaveReopen():Unit=runBlocking {
        val file=studioFixture("smart-quiet.mp4");val hash=DownloadApi.sha256(file)
        val c=clip("smart-quiet.mp4").copy(endMs=1000,cinema=CinemaStyle(CinemaLook.WARM,grade=CinemaGrade(exposure=0.15f,temperature=0.5f)))
        val p=StudioProject(title="Cinematic warm grade",clips=listOf(c),originalVolume=0f);val repository=StudioRepository(context)
        try {
            repository.save(p);assertEquals(p,repository.load(p.id))
            render(repository.load(p.id)) {r,_->val pixel=color(r);assertTrue("Warm grade was missing: $pixel",Color.red(pixel)>Color.blue(pixel)+25)}
            val zero=p.copy(clips=listOf(c.copy(cinema=CinemaStyle(CinemaLook.WARM,intensity=0f))))
            render(zero) {r,_->val pixel=color(r);assertTrue("Zero intensity did not restore neutral source",kotlin.math.abs(Color.red(pixel)-Color.blue(pixel))<10)}
        } finally {repository.delete(p.id)}
        assertEquals(hash,DownloadApi.sha256(file))
    }

    private fun luma(pixel:Int)=0.2126f*Color.red(pixel)+0.7152f*Color.green(pixel)+0.0722f*Color.blue(pixel)
    private fun rgb(pixel:Int)=listOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))
    @Test fun allThirteenLooksChangeDecodedPixelsAndGradeControlsRender():Unit=runBlocking {
        val source=studioFixture("cinema-chart.mp4");val hash=DownloadApi.sha256(source)
        val c=clip("cinema-chart.mp4").copy(endMs=1000)
        val base=StudioProject(title="Look reference",clips=listOf(c),originalVolume=0f)
        var reference:IntArray?=null
        fun samples(r:MediaMetadataRetriever)=intArrayOf(color(r),color(r,x=0.2f),color(r,x=0.8f),color(r,x=0.15f,y=0.85f),color(r,x=0.9f,y=0.9f),color(r,x=0.02f,y=0.02f),color(r,x=0.31f))
        render(base) {r,_->reference=samples(r)}
        render(base.copy(title="Identity color pipeline",clips=listOf(c.copy(cinema=CinemaStyle(CinemaLook.WARM,intensity=0f))))) {r,_->
            val neutral=samples(r);val before=reference!!
            neutral.indices.forEach {i->assertTrue("Zero intensity changed source patch $i: ${rgb(before[i])} to ${rgb(neutral[i])}",rgb(neutral[i]).zip(rgb(before[i])).all {(a,b)->kotlin.math.abs(a-b)<10})}
        }
        for(look in CinemaLook.entries.filter {it!=CinemaLook.ORIGINAL}) {
            render(base.copy(title="Look "+look.name,clips=listOf(c.copy(cinema=CinemaStyle(look))))) {r,_->
                val actual=samples(r);val before=reference!!
                android.util.Log.i("VGSmoke","cinema.look=$look reference=${before.map {rgb(it)}} actual=${actual.map {rgb(it)}}")
                val delta=actual.indices.sumOf {i->rgb(actual[i]).zip(rgb(before[i])).sumOf {(a,b)->kotlin.math.abs(a-b)}}
                assertTrue("${look.name} did not affect decoded pixels: delta=$delta",delta>12)
                when(look) {
                    CinemaLook.WARM,CinemaLook.SEPIA->assertTrue(Color.red(actual[0])>Color.blue(actual[0])+12)
                    CinemaLook.COOL->assertTrue(Color.blue(actual[0])>Color.red(actual[0])+12)
                    CinemaLook.MONO->assertTrue(rgb(actual[1]).max()-rgb(actual[1]).min()<12)
                    CinemaLook.VIGNETTE->assertTrue(Color.red(actual[5])<Color.red(actual[0])-20)
                    CinemaLook.GLOW,CinemaLook.DREAMY->assertTrue("$look luminance did not rise: ${rgb(before[0])} to ${rgb(actual[0])}",luma(actual[0])>luma(before[0])+10)
                    CinemaLook.CONTRAST,CinemaLook.SHADOWS->assertTrue("$look dark patch before=${rgb(before[3])} after=${rgb(actual[3])}",Color.red(actual[3])<Color.red(before[3])-8)
                    CinemaLook.FADE->assertTrue(Color.red(actual[3])>Color.red(before[3])+8)
                    else->Unit
                }
            }
        }
        render(base.copy(title="Manual grade",clips=listOf(c.copy(cinema=CinemaStyle(grade=CinemaGrade(brightness=0.1f,contrast=0.1f,saturation=-1f,exposure=0.2f,temperature=0.2f,tint=0.1f,highlights=0.1f,shadows=0.1f,gamma=1.2f,hue=10f,sharpness=0.5f)))))) {r,_->
            assertTrue("Manual grade did not brighten source",Color.red(color(r))>Color.red(reference!![0])+20)
        }
        assertEquals(hash,DownloadApi.sha256(source))
    }
    @Test fun blackAndWhiteBoundaryTransitionsRenderWithoutChangingDuration():Unit=runBlocking {
        val c=clip("cinema-steps.mp4");val repository=StudioRepository(context)
        for(kind in listOf(CinemaTransitionKind.BLACK,CinemaTransitionKind.WHITE)) {
            val first=c.copy(endMs=1000,cinema=CinemaStyle(exit=CinemaTransition(kind,800)))
            val last=c.copy(id=UUID.randomUUID().toString(),startMs=2000,endMs=3000)
            val p=StudioProject(title="Boundary "+kind.name,clips=listOf(first,last),originalVolume=0f)
            try {
                repository.save(p);val reopened=repository.load(p.id);assertEquals(p,reopened);assertEquals(2000L,reopened.durationMs)
                render(reopened) {r,_->
                    assertTrue("Red outgoing source missing",Color.red(color(r,200_000))>180)
                    val boundary=rgb(color(r,1_000_000))
                    if(kind==CinemaTransitionKind.WHITE) assertTrue("White boundary missing: $boundary",boundary.all {it>210})
                    else assertTrue("Black boundary missing: $boundary",boundary.all {it<35})
                    assertTrue("Blue incoming source missing",Color.blue(color(r,1_800_000))>180)
                }
            } finally {repository.delete(p.id)}
        }
    }
    @Test fun steppedRetimingPreservesColorOrderAudioAndSavedSourceRanges():Unit=runBlocking {
        val source=studioFixture("cinema-steps.mp4");val hash=DownloadApi.sha256(source)
        val p=StudioProject(title="Stepped speed render",clips=listOf(clip("cinema-steps.mp4").copy(endMs=3000)))
        val changed=CinemaSpeed.apply(p,0,listOf(CinemaSpeedKey(0,0.5f),CinemaSpeedKey(1000,1f),CinemaSpeedKey(2000,2f)))
        val repository=StudioRepository(context)
        try {
            repository.save(changed);val reopened=repository.load(p.id);assertEquals(changed,reopened);assertEquals(3500L,reopened.durationMs)
            render(reopened) {r,_->
                assertEquals("yes",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                assertTrue(Color.red(color(r,500_000))>180)
                assertTrue(Color.green(color(r,2_300_000))>90)
                assertTrue(Color.blue(color(r,3_200_000))>180)
            }
        } finally {repository.delete(p.id)}
        assertEquals(hash,DownloadApi.sha256(source))
    }
    private suspend fun levels(p:StudioProject):List<AudioObservation> {
        val copy=File(context.cacheDir,"cinema-audio-${UUID.randomUUID()}.mp4")
        try {
            var duration=0L
            render(p) {reader,file->duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong();file.copyTo(copy)}
            return CreativeAnalyzer.waveform(context,StudioAudioLane(uri=MediaLibraryIndexer.localUri(context,copy).toString(),name="Decoded cinema export",sourceDurationMs=duration))
        } finally {copy.delete()}
    }
    private fun mean(bins:List<AudioObservation>,start:Long,end:Long)=bins.filter {it.startMs>=start && it.endMs<=end}.map {it.rms}.average().toFloat()
    @Test fun mixerAutomationNormalizationDuckSoloMuteAndSplitAffectDecodedAudio():Unit=runBlocking {
        val c=clip("smart-quiet.mp4").copy(endMs=2000)
        val a=StudioAudioLane(uri=c.uri,name="Measured music",sourceDurationMs=c.sourceDurationMs,endMs=2000,volume=0.4f)
        val p=StudioProject(title="Mixer reference",clips=listOf(c),originalVolume=0f,tools=StudioTools(audio=listOf(a)))
        val reference=levels(p);val base=mean(reference,400,1600);assertTrue(base>0.001f)
        val keys=listOf(CinemaScalarKey(0,0f),CinemaScalarKey(1800,1f))
        val automated=p.copy(title="Volume automation",tools=p.tools.copy(audio=listOf(a.copy(volumeKeys=keys,normalization=2f))))
        val actual=levels(automated)
        assertTrue("Volume curve did not rise",mean(actual,1200,1600)>mean(actual,200,600)*2)
        assertTrue("Normalization not rendered",mean(actual,1400,1800)>base*1.3f)
        val split=levels(CinemaMix.split(automated,a.id,1000))
        assertTrue("Automation restarted after split",mean(split,1200,1600)/mean(actual,1200,1600) in 0.8f..1.2f)
        // A silent timed Voice lane deliberately proves timing-based, rather than speech-based, ducking.
        val voice=a.copy(id=UUID.randomUUID().toString(),name="Timed Voice",endMs=800,offsetMs=600,role=CinemaAudioRole.VOICE,volume=0f)
        val ducked=levels(p.copy(title="Timed duck",tools=p.tools.copy(audio=listOf(a.copy(duck=true,duckGain=0.25f),voice))))
        assertTrue("Timed duck not rendered",mean(ducked,800,1200)/base in 0.15f..0.4f)
        val solo=levels(p.copy(title="Silent solo",originalVolume=1f,tools=p.tools.copy(audio=listOf(a.copy(solo=true,muted=true)))))
        assertTrue("Muted solo leaked original audio",mean(solo,400,1600)<0.0005f)
    }
    @Test fun animatedTitleShapeAndImportedGraphicMoveAndFadeInRenderedVideo():Unit=runBlocking {
        val c=clip("creative-source.mp4").copy(startMs=2000,endMs=4000)
        val image=File(context.cacheDir,"cinema-image-${UUID.randomUUID()}.png")
        val bitmap=Bitmap.createBitmap(128,64,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.YELLOW)}
        try {image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
        val keys=listOf(CinemaMotionKey(0,CinemaPose(x=-0.45f)),CinemaMotionKey(1400,CinemaPose(x=0.45f)),CinemaMotionKey(1900,CinemaPose(x=0.45f,opacity=0f)))
        val title=StudioTextLayer(text="MOTION",endMs=2000,size=48,color=Color.RED,y=-0.6f,motion=keys)
        val shape=CinemaGraphic(endMs=2000,color=Color.CYAN,y=0f,motion=keys)
        val imported=CinemaGraphic(kind=CinemaGraphicKind.IMAGE,uri=MediaLibraryIndexer.localUri(context,image).toString(),endMs=2000,y=0.6f,motion=keys)
        val p=StudioProject(title="Animated graphics",clips=listOf(c),originalVolume=0f,tools=StudioTools(textLayers=listOf(title),graphics=listOf(shape,imported)))
        val repository=StudioRepository(context)
        fun centroid(r:MediaMetadataRetriever,us:Long,channel:Int):Pair<Float,Int> {
            val b=r.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST)!!
            try {
                var sum=0L;var count=0
                for(y in 0 until b.height step 2) for(x in 0 until b.width step 2) {
                    val pixel=b.getPixel(x,y);val red=Color.red(pixel);val green=Color.green(pixel);val blue=Color.blue(pixel)
                    val match=when(channel) {0->red>120 && green<70 && blue<70;1->green>120 && blue>120 && red<70;else->red>120 && green>120 && blue<70}
                    if(match) {sum+=x;count++}
                }
                return (if(count==0) -1f else sum.toFloat()/count/b.width) to count
            } finally {b.recycle()}
        }
        try {
            repository.save(p);assertEquals(p,repository.load(p.id))
            render(repository.load(p.id)) {r,_->for(channel in 0..2) {
                val early=centroid(r,200_000,channel);val late=centroid(r,1_300_000,channel);val faded=centroid(r,1_950_000,channel)
                assertTrue("Animated layer $channel missing: $early $late",early.second>10 && late.second>10)
                assertTrue("Layer $channel did not move: $early $late",late.first>early.first+0.2f)
                assertTrue("Layer $channel did not fade out: $faded",faded.second<early.second/4)
            }}
        } finally {repository.delete(p.id);image.delete()}
    }
    @Test fun sourceTimeLookAndClipMotionKeysRespectTrimSpeedAndCompositionOffset():Unit=runBlocking {
        val prefix=clip("cinema-steps.mp4").copy(endMs=500)
        val keys=listOf(CinemaMotionKey(500,CinemaPose(scale=0.8f,rotation=10f,opacity=1f)),CinemaMotionKey(1500,CinemaPose(scale=0.8f,rotation=10f,opacity=0f)))
        val c=clip("smart-quiet.mp4").copy(startMs=500,endMs=1500,speed=0.5f,
            cinema=CinemaStyle(CinemaLook.WARM,motion=keys,lookKeys=listOf(CinemaScalarKey(500,0f),CinemaScalarKey(1000,1f))))
        val p=StudioProject(title="Source key clock",clips=listOf(prefix,c),originalVolume=0f)
        render(p) {r,_->
            val early=color(r,600_000);val middle=color(r,1_500_000);val end=color(r,2_400_000)
            assertTrue("Trim/speed/offset lost warm intensity keys",Color.red(middle)>Color.blue(middle)+12)
            assertTrue("Opacity source clock was wrong",Color.red(early)>Color.red(end)+35)
            val corner=rgb(color(r,1_500_000,x=0.01f,y=0.01f))
            assertTrue("Clip scale/rotation did not expose black canvas",corner.all {it<20})
        }
    }

}
