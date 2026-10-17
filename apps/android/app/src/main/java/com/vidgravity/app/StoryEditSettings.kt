package com.vidgravity.app

/** Sharing is valid only for the exact source and settings submitted to the renderer. */
internal data class StoryEditSettings(
    val source: String,
    val startSeconds: Float,
    val endSeconds: Float,
    val title: String,
    val artist: String,
    val layout: String
)
