package com.honorablemobile

/** Millisecond input precision is not a promise of frame-exact codec output. */
internal data class VideoTrimRange(val startMs: Long, val endMs: Long) {
    companion object {
        private fun milliseconds(text: String): Long {
            val value = text.trim()
            require(value.matches(Regex("(?:0|[1-9][0-9]{0,6})(?:\\.[0-9]{1,3})?"))) { "Use source seconds with up to 3 decimal places" }
            val parts = value.split('.')
            return parts[0].toLong() * 1000 + parts.getOrElse(1) { "" }.padEnd(3, '0').toLong()
        }
        fun parse(start: String, end: String, clip: VideoSegment, sourceDurationMs: Long): VideoTrimRange {
            val from = milliseconds(start); val to = milliseconds(end)
            require(sourceDurationMs > 0 && clip.endMs <= sourceDurationMs && from >= clip.startMs && to <= clip.endMs && to > from) { "Choose an increasing range inside the current clip and original source" }
            return VideoTrimRange(from, to)
        }
        fun format(value: Long): String { require(value >= 0); return "${value / 1000}.${(value % 1000).toString().padStart(3, '0')}" }
    }
}
