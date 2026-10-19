package com.honorablemobile

/** Immutable effective selection; late inference cannot replace a newer project state. */
internal data class VideoSceneSelection private constructor(
    val clips: List<VideoSegment>, val selectedIndex: Int, val startMs: Long, val endMs: Long,
) {
    fun sampleTimes(): List<Long> {
        val span = endMs - startMs
        val count = (span / 4_000).coerceIn(1L, 12L).toInt()
        return List(count) { startMs + ((it + .5) * span / count).toLong() }
    }
    fun suggestedRange(centerMs: Long): Pair<Long, Long> {
        val center = centerMs.coerceIn(startMs, endMs - 1)
        return (center - minOf(4_000, center - startMs)) to (center + minOf(4_000, endMs - center))
    }
    fun stillMatches(current: List<VideoSegment>, index: Int, from: Long, to: Long): Boolean =
        runCatching { capture(current, index, from, to) == this }.getOrDefault(false)

    companion object {
        fun capture(clips: List<VideoSegment>, index: Int, from: Long, to: Long): VideoSceneSelection {
            val clip = clips.getOrNull(index) ?: throw IllegalArgumentException("Choose a clip first")
            val start = maxOf(clip.startMs, from)
            val end = minOf(clip.endMs, to)
            require(start < end) { "Select a valid trim range" }
            return VideoSceneSelection(clips.toList(), index, start, end)
        }
    }
}
