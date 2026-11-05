package com.honorablemobile

/** Accumulate authoritative clip microseconds before rounding to a display/file-check unit. */
internal object VideoPlaybackTime {
    fun durationUs(clips: List<VideoSegment>): Long {
        require(clips.size in 1..1000) { "Use a project with 1–1000 clips" }
        return try { clips.fold(0L) { total, clip -> Math.addExact(total, clip.durationUs) } }
        catch (failure: ArithmeticException) { throw IllegalArgumentException("Project duration is too large", failure) }
    }
    fun durationMs(clips: List<VideoSegment>): Long = durationUs(clips) / 1000
}
