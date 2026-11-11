package com.honorablemobile

/** Hard source-audio muting on the original edited-playback clock; every video source tick stays. */
internal object VideoPlaybackMute {
    data class Range(val from: Long, val to: Long)
    data class Piece(val originalIndex: Int, val clip: VideoSegment, val newlyMuted: Boolean)
    data class Result(val clips: List<VideoSegment>, val pieces: List<Piece>, val intervals: List<Range>,
                      val beforeUs: Long, val afterUs: Long)
    private fun union(values: List<Range>): List<Range> {
        val result = mutableListOf<Range>()
        values.sortedBy { it.from }.forEach { range ->
            val last = result.lastOrNull()
            if (last != null && range.from <= last.to) result[result.lastIndex] = Range(last.from, maxOf(last.to, range.to))
            else result += range
        }
        return result
    }
    private fun rate(speed: Float) = when (speed) { .5f -> 1L to 2L; 1f -> 1L to 1L; 1.5f -> 3L to 2L; 2f -> 2L to 1L; else -> error("Unsupported clip speed") }
    fun prepare(clips: List<VideoSegment>, text: String, music: VideoSoundtrack? = null): Result {
        val before = VideoPlaybackTime.durationUs(clips)
        require(before <= Int.MAX_VALUE.toLong() * 1000 && clips.all { it.endMs <= Int.MAX_VALUE }) { "Timeline exceeds supported millisecond timing bounds" }
        require(text.length <= 16000) { "Use at most 16000 characters" }
        val rows = text.trim().lines().filter { it.isNotBlank() }
        require(rows.size in 1..100) { "Paste 1–100 start,end ranges in edited playback seconds" }
        val intervals = union(rows.mapIndexed { index, row ->
            val columns = row.split(',')
            require(columns.size == 2) { "Line ${index + 1}: use start,end playback seconds" }
            val from = VideoCaptions.seconds(columns[0]) * 1000; val requestedTo = VideoCaptions.seconds(columns[1]) * 1000
            require(from < before && requestedTo > from && requestedTo <= ((before + 999) / 1000) * 1000) { "Line ${index + 1}: choose an increasing interval inside the edited timeline" }
            Range(from, minOf(requestedTo, before))
        })
        var offset = 0L
        val pieces = mutableListOf<Piece>()
        clips.forEachIndexed { index, clip ->
            val next = offset + clip.durationUs
            val (numerator, denominator) = rate(clip.speed)
            val divisor = 1000 * denominator
            val muted = if (clip.volume == 0f) emptyList() else union(intervals.mapNotNull { interval ->
                val from = maxOf(offset, interval.from); val to = minOf(next, interval.to)
                if (from >= to) null else {
                    val sourceFrom = if (from == offset) clip.startMs else clip.startMs + (from - offset) * numerator / divisor
                    val sourceTo = if (to == next) clip.endMs else clip.startMs + ((to - offset) * numerator + divisor - 1) / divisor
                    Range(sourceFrom, sourceTo)
                }
            })
            if (muted.isEmpty()) {
                require(pieces.size < 1000) { "Silencing these intervals would exceed 1000 clips" }
                pieces += Piece(index, clip, false)
            } else {
                val boundaries = (listOf(clip.startMs, clip.endMs) + muted.flatMap { listOf(it.from, it.to) }).distinct().sorted()
                boundaries.zipWithNext().forEach { (from, to) ->
                    require(pieces.size < 1000) { "Silencing these intervals would exceed 1000 clips; simplify the intervals first" }
                    check(from >= clip.startMs && to <= clip.endMs && to > from)
                    val silent = muted.any { from >= it.from && to <= it.to }
                    val selected = clip.copy(startMs = from, endMs = to, volume = if (silent) 0f else clip.volume,
                        captions = if (from == clip.startMs && to == clip.endMs) clip.captions else clip.captions.filter { it.startMs < to && it.endMs > from })
                    pieces += Piece(index, selected, silent)
                }
            }
            offset = next
        }
        val result = pieces.map { it.clip }
        require(result != clips) { "All requested original clip audio is already muted" }
        require(result.sumOf { it.captions.size } <= VideoCaptions.MAX_PROJECT_CUES && result.sumOf { clip -> clip.captions.sumOf { it.text.toByteArray(Charsets.UTF_8).size } } <= VideoCaptions.MAX_PROJECT_TEXT_BYTES) { "Audio splits exceed project caption limits" }
        require(result.sumOf { it.title.toByteArray(Charsets.UTF_8).size } <= 128000) { "Audio splits exceed project title limits" }
        VideoDraftCodec.requireRecoverableCaptions(result, music?.loop == false, music?.hasExcerpt == true)
        val after = VideoPlaybackTime.durationUs(result)
        check(after <= before && before - after <= pieces.size - clips.size)
        return Result(result, pieces.toList(), intervals, before, after)
    }
}
