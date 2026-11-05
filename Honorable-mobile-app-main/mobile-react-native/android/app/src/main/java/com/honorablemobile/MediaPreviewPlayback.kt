package com.honorablemobile

/** Canonical playback intent; the Activity supplies observations only while prepared/foreground. */
internal class MediaPreviewPlayback(position: Long, playing: Boolean) {
    data class Saved(val position: Long, val playing: Boolean)
    data class Prepared(val position: Int, val start: Boolean)
    private var position = position.coerceIn(0, Int.MAX_VALUE.toLong())
    private var playing = playing
    private var ready = false
    private var foreground = false

    fun resume(): Boolean { foreground = true; return ready && playing }
    fun pause(observedPosition: Int?, observedPlaying: Boolean?) {
        observe(observedPosition, observedPlaying)
        foreground = false
    }
    fun prepared(duration: Int): Prepared {
        if (duration > 0) position = position.coerceAtMost(duration.toLong())
        ready = true
        return Prepared(position.toInt(), foreground && playing)
    }
    fun snapshot(observedPosition: Int?, observedPlaying: Boolean?): Saved {
        observe(observedPosition, observedPlaying)
        return Saved(position, playing)
    }
    fun failed() { ready = false; playing = false }
    fun completed() { playing = false }
    private fun observe(observedPosition: Int?, observedPlaying: Boolean?) {
        if (!ready || !foreground) return
        if (observedPosition != null && observedPosition >= 0) position = observedPosition.toLong()
        if (observedPlaying != null) playing = observedPlaying
    }
}
