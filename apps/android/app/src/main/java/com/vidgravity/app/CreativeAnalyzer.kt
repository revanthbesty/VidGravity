package com.vidgravity.app

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.media.*
import android.net.Uri
import kotlinx.coroutines.*
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/** Bounded, cancellable on-device analysis. Nothing is uploaded or written to source media. */
internal object CreativeAnalyzer {
    suspend fun analyze(context:Context,clip:StudioClip,onProgress:(String,Float)->Unit):CreativeAnalysis = withContext(Dispatchers.IO) {
        if(android.os.Build.VERSION.SDK_INT<27) error("Frame analysis needs Android 8.1/API27 or newer. Manual editing remains available.")
        val lowRam=(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
        val limit=minOf(clip.durationMs,if(lowRam) 15_000L else 60_000L)
        val count=((limit+1999)/2000).toInt().coerceAtLeast(1)
        val frames=mutableListOf<FrameObservation>(); var previous:FloatArray?=null
        var faceStatus=if(lowRam) "Face detection disabled on this low-RAM device; center framing remains available." else "Android framework face detector · frontal faces, static framing only."
        val reader=MediaMetadataRetriever()
        try {
            reader.setDataSource(context,Uri.parse(clip.uri))
            for(i in 0 until count) {
                ensureActive()
                val time=minOf(limit-1,i*2000L)
                onProgress("Sampling frame ${i+1}/$count",0.65f*i/count)
                val bitmap=reader.getScaledFrameAtTime((clip.startMs+time)*1000,MediaMetadataRetriever.OPTION_CLOSEST,320,320)
                    ?: continue
                try {
                    val values=FloatArray(16*16)
                    for(y in 0..15) for(x in 0..15) {
                        val pixel=bitmap.getPixel(x*(bitmap.width-1)/15,y*(bitmap.height-1)/15)
                        values[y*16+x]=(0.2126f*android.graphics.Color.red(pixel)+0.7152f*android.graphics.Color.green(pixel)+0.0722f*android.graphics.Color.blue(pixel))/255
                    }
                    val mean=values.average().toFloat()
                    val contrast=sqrt(values.map {(it-mean)*(it-mean)}.average()).toFloat()
                    val before=previous
                    val diff=if(before==null) 0f else values.indices.map {abs(values[it]-before[it])}.average().toFloat()
                    var face:PointF?=null
                    if(!lowRam && i%3==0) {
                        try {face=detectFace(bitmap)}
                        catch(failure:RuntimeException) {faceStatus="Framework face detection unavailable; center framing remains available."}
                        catch(failure:LinkageError) {faceStatus="Framework face detection unavailable; center framing remains available."}
                    }
                    frames+=FrameObservation(time,mean,contrast,diff,face?.x,face?.y,bitmap.width.toFloat()/bitmap.height)
                    previous=values
                } finally {bitmap.recycle()}
            }
        } finally {reader.release()}
        check(frames.isNotEmpty()) {"No video frames could be decoded. Relink the source or choose another clip."}
        var audioStatus="Audio energy analyzed locally. Silence is not speech recognition."
        val audio=try {decodeAudio(context,clip,limit) {p -> onProgress("Analyzing audio energy",0.65f+p*0.35f)}}
        catch(cancelled:CancellationException) {throw cancelled}
        catch(failure:Exception) {audioStatus="Audio could not be analyzed; visual suggestions remain available."; emptyList()}
        if(audio.isEmpty()) audioStatus="No readable audio samples; silence removal is unavailable."
        onProgress("Analysis complete",1f)
        CreativeAnalysis(clip.id,limit,frames,audio,audioStatus,faceStatus)
    }

    /** Platform model inference, not subject segmentation or face identity recognition. */
    fun detectFace(bitmap:Bitmap):PointF? {
        val width=bitmap.width-bitmap.width%2
        if(width<2) return null
        val rgb=Bitmap.createBitmap(width,bitmap.height,Bitmap.Config.RGB_565)
        return try {
            android.graphics.Canvas(rgb).drawBitmap(bitmap,0f,0f,null)
            val faces=arrayOfNulls<FaceDetector.Face>(3)
            FaceDetector(width,rgb.height,3).findFaces(rgb,faces)
            val best=faces.filterNotNull().filter {it.confidence()>=0.4f}.maxByOrNull {it.eyesDistance()*it.confidence()}
            best?.let {val point=PointF(); it.getMidPoint(point); PointF(point.x/width,point.y/rgb.height)}
        } finally {rgb.recycle()}
    }

    private suspend fun decodeAudio(context:Context,clip:StudioClip,limit:Long,onProgress:(Float)->Unit):List<AudioObservation> {
        val extractor=MediaExtractor(); var decoder:MediaCodec?=null
        try {
            extractor.setDataSource(context,Uri.parse(clip.uri),null)
            val track=(0 until extractor.trackCount).firstOrNull {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true} ?: return emptyList()
            extractor.selectTrack(track); extractor.seekTo(clip.startMs*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val format=extractor.getTrackFormat(track)
            decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(format,null,null,0); decoder.start()
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE); var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floating=false; var inputDone=false; var outputDone=false
            val bins=((limit+199)/200).toInt(); val squares=DoubleArray(bins); val counts=LongArray(bins)
            val info=MediaCodec.BufferInfo(); val deadline=System.nanoTime()+60_000_000_000L
            while(!outputDone) {
                currentCoroutineContext().ensureActive()
                check(System.nanoTime()<deadline) {"Audio analysis time limit reached."}
                if(!inputDone) {
                    val index=decoder.dequeueInputBuffer(10_000)
                    if(index>=0) {
                        val time=extractor.sampleTime
                        val buffer=decoder.getInputBuffer(index)!!
                        val size=if(time<0 || time>(clip.startMs+limit)*1000) -1 else extractor.readSampleData(buffer,0)
                        if(size<0) {decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true}
                        else {decoder.queueInputBuffer(index,0,size,time,0); extractor.advance()}
                    }
                }
                val index=decoder.dequeueOutputBuffer(info,10_000)
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output=decoder.outputFormat; rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floating=output.containsKey(MediaFormat.KEY_PCM_ENCODING) && output.getInteger(MediaFormat.KEY_PCM_ENCODING)==android.media.AudioFormat.ENCODING_PCM_FLOAT
                }
                if(index>=0) {
                    val buffer=decoder.getOutputBuffer(index)!!.duplicate().order(ByteOrder.nativeOrder())
                    buffer.position(info.offset); buffer.limit(info.offset+info.size)
                    var sample=0L
                    while(buffer.remaining()>=if(floating) 4 else 2) {
                        val value=if(floating) buffer.float.toDouble() else buffer.short.toDouble()/32768
                        val relative=info.presentationTimeUs+(sample++/channels)*1_000_000/rate-clip.startMs*1000
                        if(relative>=0 && relative<limit*1000) {val bin=(relative/200_000).toInt(); squares[bin]+=value*value; counts[bin]++}
                    }
                    onProgress(((info.presentationTimeUs/1000-clip.startMs).toFloat()/limit).coerceIn(0f,1f))
                    outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index,false)
                }
            }
            return (0 until bins).filter {counts[it]>0}.map {AudioObservation(it*200L,minOf(limit,(it+1)*200L),sqrt(squares[it]/counts[it]).toFloat())}
        } finally {runCatching {decoder?.stop()}; decoder?.release(); extractor.release()}
    }
}
