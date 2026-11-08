package com.honorablemobile

/** An explicit shot list, in original-source seconds; never sorts, merges or analyzes footage. */
internal object VideoRangeAssembly {
    fun propose(clips: List<VideoSegment>, index: Int, text: String): List<VideoSegment> {
        VideoPlaybackTime.durationUs(clips)
        require(index in clips.indices) { "Select a clip first" }
        require(text.length <= 16000) { "Use up to 16000 characters" }
        val rows = text.trim().lines().filter { it.isNotBlank() }
        require(rows.size in 1..100) { "Paste 1–100 ranges, one start,end pair per line" }
        val clip = clips[index]
        val excerpts = rows.mapIndexed { row, value ->
            val columns = value.split(',')
            require(columns.size == 2) { "Line ${row + 1}: use start,end in source seconds" }
            val (from, to) = try { VideoCaptions.seconds(columns[0]) to VideoCaptions.seconds(columns[1]) }
                catch (_: IllegalArgumentException) { throw IllegalArgumentException("Line ${row + 1}: use nonnegative seconds with up to three decimals") }
            require(from >= clip.startMs && to <= clip.endMs && from < to) {
                "Line ${row + 1}: choose an increasing range inside the selected clip"
            }
            require((to - from) * 1000.0 / clip.speed >= 100000.0) {
                "Line ${row + 1}: keep at least 0.1 playback second"
            }
            clip.copy(startMs = from, endMs = to,
                captions = clip.captions.filter { it.startMs < to && it.endMs > from })
        }
        require(clips.size - 1 + excerpts.size <= 1000) { "Shot list exceeds the 1000-clip project limit" }
        val result = clips.take(index) + excerpts + clips.drop(index + 1)
        require(result.sumOf { it.captions.size } <= VideoCaptions.MAX_PROJECT_CUES) { "Shot list exceeds the project caption limit" }
        require(result.sumOf { c -> c.captions.sumOf { it.text.toByteArray(Charsets.UTF_8).size } } <= VideoCaptions.MAX_PROJECT_TEXT_BYTES) { "Shot list exceeds the project caption text limit" }
        require(result.sumOf { it.title.toByteArray(Charsets.UTF_8).size } <= 128000) { "Shot list exceeds the project title limit" }
        VideoPlaybackTime.durationUs(result)
        return result
    }
}
