package com.honorablemobile

import java.math.BigInteger

/** Shorten centered shot windows while preserving every clip, its order and its speed. */
internal object VideoFitDuration {
    data class Result(val clips: List<VideoSegment>, val requestedUs: Long, val beforeUs: Long, val afterUs: Long, val minimumUs: Long)
    private fun rate(speed: Float) = when (speed) { .5f -> 1L to 2L; 1f -> 1L to 1L; 1.5f -> 3L to 2L; 2f -> 2L to 1L; else -> error("Unsupported clip speed") }
    private fun duration(span: Long, speed: Float): Long { val (n, d) = rate(speed); return span * 1000 * d / n }
    fun prepare(clips: List<VideoSegment>, seconds: String, music: VideoSoundtrack? = null): Result {
        val before = VideoPlaybackTime.durationUs(clips)
        require(before <= Int.MAX_VALUE.toLong() * 1000 && clips.all { it.endMs <= Int.MAX_VALUE }) { "Timeline exceeds supported millisecond timing bounds" }
        val target = VideoCaptions.seconds(seconds) * 1000
        require(target in 1L until before) { "Choose a positive target shorter than current playback" }
        val maximum = clips.map { it.endMs - it.startMs }
        val minimum = clips.mapIndexed { index, clip ->
            val (n, d) = rate(clip.speed)
            minOf(maximum[index], (100_000 * n + 1000 * d - 1) / (1000 * d))
        }
        val lower = clips.indices.map { duration(minimum[it], clips[it].speed) }
        val minimumUs = lower.sum()
        require(target >= minimumUs) { String.format(java.util.Locale.ROOT, "Keeping every shot requires at least %.6f playback seconds; new trimmed pieces need 0.1 second", minimumUs / 1_000_000.0) }
        val capacities = clips.indices.map { clips[it].durationUs - lower[it] }
        val capacity = capacities.sum()
        check(capacity > 0)
        val spare = target - minimumUs
        val spans = LongArray(clips.size) { index ->
            val budget = lower[index] + BigInteger.valueOf(spare).multiply(BigInteger.valueOf(capacities[index])).divide(BigInteger.valueOf(capacity)).toLong()
            val (n, d) = rate(clips[index].speed)
            (budget * n / (1000 * d)).coerceIn(minimum[index], maximum[index])
        }
        var after = clips.indices.sumOf { duration(spans[it], clips[it].speed) }
        // Fill quantization remainder with source-millisecond ticks, never exceeding the target.
        // Initial remainder is <2001us per clip; each tick consumes >=500us.
        do {
            var advanced = false
            for (index in clips.indices) {
                if (spans[index] == maximum[index]) continue
                val delta = duration(spans[index] + 1, clips[index].speed) - duration(spans[index], clips[index].speed)
                if (delta <= target - after) { spans[index]++; after += delta; advanced = true }
            }
        } while (advanced)
        check(after <= target && target - after < 2000)
        val result = clips.mapIndexed { index, clip ->
            val span = spans[index]
            if (span == maximum[index]) clip else {
                val from = clip.startMs + (maximum[index] - span) / 2; val to = from + span
                check(duration(span, clip.speed) >= 100_000)
                clip.copy(startMs = from, endMs = to, captions = clip.captions.filter { it.startMs < to && it.endMs > from })
            }
        }
        check(VideoPlaybackTime.durationUs(result) == after && result != clips)
        VideoDraftCodec.requireRecoverableCaptions(result, music?.loop == false, music?.hasExcerpt == true)
        return Result(result, target, before, after, minimumUs)
    }
}
