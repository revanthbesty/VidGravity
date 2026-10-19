package com.vidgravity.app

/** Media3 Transformer already adds each item's output offset before running video effects.
 * ExoPlayer previews a single clipped source, whose effect timestamps begin at the clipped item's zero position. */
internal data class StudioEffectClock(val offsetMs: Long, val speed: Float, val exporting: Boolean) {
    fun outputMs(presentationTimeUs: Long): Long = if(exporting) presentationTimeUs/1000
        else offsetMs+((presentationTimeUs/1000)/speed).toLong()
    fun clipUs(presentationTimeUs: Long): Long = (outputMs(presentationTimeUs)-offsetMs)*1000
}
