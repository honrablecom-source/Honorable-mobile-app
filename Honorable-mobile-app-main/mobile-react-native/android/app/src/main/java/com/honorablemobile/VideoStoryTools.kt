package com.honorablemobile

import kotlin.math.ceil

/** Local editing recipes. No model, upload, speech recognition or source-media modification. */
internal object VideoStoryTools {
    /** Balanced centre excerpts, in timeline order. Never stretches or repeats source footage. */
    fun montage(clips: List<VideoSegment>, targetMs: Long): List<VideoSegment> {
        require(clips.size in 1..1000) { "Choose a project with 1–1000 clips" }
        require(targetMs in 5_000..120_000) { "Choose 5–120 seconds" }
        val count = minOf(clips.size, 60, (targetMs / 500).toInt())
        val chosen = (0 until count).map { i -> clips[if (count == 1) 0 else i * (clips.size - 1) / (count - 1)] }
        val capacity = chosen.map { it.durationMs.coerceAtLeast(1) }
        var remaining = minOf(targetMs, capacity.sum())
        val lengths = LongArray(count)
        var active = chosen.indices.toList()
        // Water filling gives short clips their full length and redistributes the unused time.
        while (active.isNotEmpty()) {
            val share = remaining / active.size
            val short = active.filter { capacity[it] <= share }
            if (short.isEmpty()) {
                active.forEachIndexed { order, index -> lengths[index] = share + if (order < remaining % active.size) 1 else 0 }
                break
            }
            short.forEach { lengths[it] = capacity[it]; remaining -= capacity[it] }
            active = active.filterNot { it in short }
        }
        return chosen.mapIndexed { i, clip ->
            val length = (lengths[i] * clip.speed.toDouble()).toLong().coerceIn(1, clip.endMs - clip.startMs)
            val from = clip.startMs + (clip.endMs - clip.startMs - length) / 2
            val to = from + length
            clip.copy(startMs = from, endMs = to, captions = clip.captions.filter { it.endMs > from && it.startMs < to })
        }
    }

    /** One pasted line per cue, weighted by text length, across the selected source range. */
    fun scriptCaptions(clip: VideoSegment, script: String): List<VideoCaption> {
        require(script.length <= 16_000) { "Use a script up to 16000 characters" }
        val lines = script.replace("\r\n", "\n").replace('\r', '\n').lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.size in 1..100) { "Write 1–100 lines, one caption per line" }
        require(clip.endMs <= Int.MAX_VALUE.toLong()) { "This clip exceeds supported caption timestamps" }
        // Validate before allocating, including malformed Unicode and unsupported controls.
        lines.forEach { VideoCaption(0, 1, it) }
        val minimum = ceil(500 * clip.speed.toDouble()).toLong()
        val duration = clip.endMs - clip.startMs
        require(duration >= minimum * lines.size) { "Allow at least half a playback second per line; shorten the script or use a longer clip" }
        val weights = lines.map { it.codePointCount(0, it.length).toLong() }
        val total = weights.sum()
        val spare = duration - minimum * lines.size
        var previous = clip.startMs
        var usedWeight = 0L
        return lines.mapIndexed { index, text ->
            usedWeight += weights[index]
            val next = clip.startMs + minimum * (index + 1) + spare * usedWeight / total
            VideoCaption(previous, next, text).also { previous = next }
        }.also { VideoCaptions.validate(it) }
    }
}
