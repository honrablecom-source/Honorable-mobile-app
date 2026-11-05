package com.honorablemobile

/** Timeline and acceptance rules shared by the audio-only composition and file verifier. */
internal object VideoAudioPlan {
    fun requireAudio(clips: List<VideoSegment>, audioSources: Set<String>, soundtrack: VideoSoundtrack?) {
        durationUs(clips) // Validate canonical timeline bounds even when no soundtrack is attached.
        soundtrack?.let { VideoTimeline.soundtrackRepeatCount(clips,it) }
        require(clips.any { it.sourceId in audioSources && it.volume > 0f } || soundtrack?.let { it.volume > 0f } == true) {
            "This project has no enabled audio. Unmute a clip with audio or add a soundtrack."
        }
    }

    fun durationUs(clips: List<VideoSegment>): Long = VideoPlaybackTime.durationUs(clips)

    fun validateDuration(clips: List<VideoSegment>, actualDurationMs: Long) {
        val microseconds=durationUs(clips)
        val expectedMs=microseconds/1000 + if(microseconds%1000==0L)0 else 1
        val toleranceMs=maxOf(250L,expectedMs/1000).coerceAtMost(2000L)
        require(actualDurationMs > 0 && kotlin.math.abs(actualDurationMs-expectedMs) <= toleranceMs) {
            "Saved audio has unexpected duration"
        }
    }
}
