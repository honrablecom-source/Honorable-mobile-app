package com.honorablemobile

internal data class VideoTimelinePosition(val clipIndex: Int, val sourceId: String, val sourceTimeMs: Long)

/** Output time in microseconds, after clip speed changes. */
internal object VideoTimeline {
    fun soundtrackRepeatCount(clips: List<VideoSegment>, track: VideoSoundtrack): Long {
        require(clips.isNotEmpty())
        val duration=clips.sumOf { it.durationUs }
        val count=(duration-1)/(track.durationMs*1000)+1
        require(count <= 1000) { "Choose a longer soundtrack; up to 1000 repeats are supported" }
        return count
    }
    fun locate(clips: List<VideoSegment>, positionUs: Long): VideoTimelinePosition {
        require(clips.isNotEmpty())
        var offset = 0L
        val time = positionUs.coerceAtLeast(0)
        clips.forEachIndexed { index, clip ->
            if(time < offset + clip.durationUs || index == clips.lastIndex) {
                val local = (time-offset).coerceIn(0L, clip.durationUs-1)
                val source = (clip.startMs + local * clip.speed.toDouble()/1000).toLong().coerceIn(clip.startMs, clip.endMs-1)
                return VideoTimelinePosition(index, clip.sourceId, source)
            }
            offset += clip.durationUs
        }
        error("No clip")
    }
    fun fadeBrightness(clips: List<VideoSegment>, positionUs: Long): Float {
        var offset = 0L
        val time = positionUs.coerceAtLeast(0)
        clips.forEachIndexed { index, clip ->
            if(time < offset + clip.durationUs || index == clips.lastIndex) {
                if(!clip.fadeEdges) return 1f
                val local = (time-offset).coerceIn(0L, clip.durationUs)
                val edge = minOf(500_000L, clip.durationUs/2).coerceAtLeast(1)
                return minOf(local.toDouble()/edge, (clip.durationUs-local).toDouble()/edge, 1.0).toFloat().coerceIn(0f,1f)
            }
            offset += clip.durationUs
        }
        return 1f
    }
}
