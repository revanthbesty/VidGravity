package com.vidgravity.app

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.ui.PlayerView
import org.junit.Rule
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.effect.RgbMatrix
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/** Decode real clipped preview frames through ExoPlayer's effect pipeline, including a seek. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class StudioPreviewClockTest {
    @get:Rule val compose=createComposeRule()
    @Test fun clippedPreviewFrameClockMatchesOutputTimeBeforeAndAfterSeeking():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=studioFixture("native-source.mp4")
        val times=ConcurrentLinkedQueue<Long>()
        val error=AtomicReference<PlaybackException?>()
        val clock=StudioEffectClock(500,1000,0.5f,false)
        var player:ExoPlayer?=null
        try {
            withContext(Dispatchers.Main) {
                player=ExoPlayer.Builder(context).build().apply {
                    addListener(object:Player.Listener {override fun onPlayerError(failure:PlaybackException) {error.set(failure)}})
                    trackSelectionParameters=trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).build()
                    setVideoEffects(listOf(RgbMatrix {time,_ ->times.add(time);floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)}))
                    setMediaItem(MediaItem.Builder().setUri(MediaLibraryIndexer.localUri(context,file)).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(1000).setEndPositionMs(2000).build()).build())
                    setPlaybackSpeed(0.5f);prepare();play()
                }
            }
            compose.setContent {AndroidView(factory={PlayerView(it).apply {this.player=player;useController=false}},modifier=Modifier.size(320.dp,180.dp))}
            withTimeout(30_000) {while(times.size<3) {check(error.get()==null) {"Preview failed: ${error.get()?.errorCode}"};delay(20)}}
            val first=times.minOrNull()!!
            android.util.Log.i("VGSmoke","sprint10.preview-clock.firstUs=$first outputMs=${clock.outputMs(first)}")
            assertTrue("Clipped preview must retain the source in-point: $first",first in 950_000..1_100_000)
            assertTrue(clock.outputMs(first) in 400..700)
            withContext(Dispatchers.Main) {player!!.pause();player!!.seekTo(500);times.clear();player!!.play()}
            withTimeout(30_000) {while(times.none {it>=1_450_000}) {check(error.get()==null);delay(20)}}
            val after=times.first {it>=1_450_000}
            assertTrue("Preview seek clock diverged: $after",clock.outputMs(after) in 1400..1700)
            android.util.Log.i("VGSmoke","sprint10.preview-clock.seekUs=$after outputMs=${clock.outputMs(after)}")
        } finally {withContext(NonCancellable+Dispatchers.Main) {
            player?.release()
        }}
    }
}
