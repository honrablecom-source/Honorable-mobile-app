package com.honorablemobile

/** Different footage, same source span/playback length. Existing source-anchored metadata stays anchored. */
internal object VideoClipWindow {
    fun latestStart(clip:VideoSegment,sourceDurationMs:Long):Long {
        require(sourceDurationMs>0&&clip.endMs<=sourceDurationMs){"Original source duration changed"}
        return sourceDurationMs-(clip.endMs-clip.startMs)
    }
    fun move(clip:VideoSegment,startMs:Long,sourceDurationMs:Long):VideoSegment {
        val latest=latestStart(clip,sourceDurationMs)
        require(startMs in 0..latest){"Keep the complete same-length window inside the original source"}
        require(startMs!=clip.startMs){"Choose a different source start"}
        // Subtraction-bound validation above makes addition safe even near Long.MAX_VALUE.
        return clip.copy(startMs=startMs,endMs=startMs+(clip.endMs-clip.startMs))
    }
}
