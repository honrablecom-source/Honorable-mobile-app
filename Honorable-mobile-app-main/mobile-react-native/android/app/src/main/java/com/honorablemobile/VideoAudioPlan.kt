package com.honorablemobile

/** Timeline and acceptance rules shared by the audio-only composition and file verifier. */
internal object VideoAudioPlan {
    fun requireAudio(clips: List<VideoSegment>, audioSources: Set<String>, soundtrack: VideoSoundtrack?) {
        require(clips.isNotEmpty()) { "Add a video clip before exporting audio" }
        soundtrack?.let { VideoTimeline.soundtrackRepeatCount(clips,it) }
        require(clips.any { it.sourceId in audioSources && it.volume > 0f } || soundtrack?.let { it.volume > 0f } == true) {
            "This project has no enabled audio. Unmute a clip with audio or add a soundtrack."
        }
    }

    fun durationUs(clips: List<VideoSegment>): Long {
        require(clips.isNotEmpty())
        return clips.sumOf { it.durationUs }
    }

    fun validateDuration(clips: List<VideoSegment>, actualDurationMs: Long) {
        val expectedMs=(durationUs(clips)+999)/1000
        val toleranceMs=maxOf(250L,expectedMs/1000).coerceAtMost(2000L)
        require(actualDurationMs > 0 && kotlin.math.abs(actualDurationMs-expectedMs) <= toleranceMs) {
            "Saved audio has unexpected duration"
        }
    }
}
