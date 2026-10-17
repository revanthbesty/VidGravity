package com.vidgravity.app

/** A committed composition alone is not evidence of a non-empty drawn root. */
internal class StartupGate {
    private var committed = false
    private var laidOut = false
    private var finished = false

    fun committed() { committed = true }
    fun laidOut(width: Int, height: Int) { laidOut = width > 0 && height > 0 }
    fun fail() { finished = true }

    fun drawn(): Boolean {
        if (finished || !committed || !laidOut) return false
        finished = true
        return true
    }
}
