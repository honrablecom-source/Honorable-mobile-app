package com.honorablemobile

import java.util.Collections
import kotlin.math.ceil

/** Suggestions based only on bounded downsampled SDR source images, never a content guarantee. */
internal object VideoDarkHolds {
    enum class State { DARK, BRIGHT, UNKNOWN }
    data class Candidate(val first: Int, val last: Int, val fromMs: Long, val toMs: Long)
    data class Analysis(val clip: VideoSegment, val times: List<Long>, val states: List<State>, val candidates: List<Candidate>)
    data class Result(val clips: List<VideoSegment>, val retained: List<VideoSegment>, val removed: List<Candidate>, val beforeUs: Long, val afterUs: Long, val reviewText: String)
    private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())
    private fun freeze(clip: VideoSegment) = clip.copy(captions = frozen(clip.captions))
    fun times(clip: VideoSegment): List<Long> {
        val span = clip.endMs - clip.startMs
        require(span in 2500L..120000L && clip.endMs <= Int.MAX_VALUE) { "Choose a whole clip of 2.5–120 source seconds" }
        val count = minOf(64, ((span + 499) / 500).toInt())
        return frozen((0 until count).map { clip.startMs + (2L * it + 1) * span / (2L * count) })
    }
    fun size(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, 256.0 / maxOf(width, height))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
    fun classify(width: Int, height: Int, pixels: IntArray, current: () -> Unit = {}): State {
        require(width in 1..256 && height in 1..256 && pixels.size == width * height)
        var bright = false; var unknown = false
        for (row in 0 until height) {
            current()
            for (column in 0 until width) {
                val pixel = pixels[row * width + column]
                if ((pixel ushr 24) != 255) unknown = true
                if (((pixel ushr 16) and 255) > 12 || ((pixel ushr 8) and 255) > 12 || (pixel and 255) > 12) bright = true
            }
        }
        current(); return if (unknown) State.UNKNOWN else if (bright) State.BRIGHT else State.DARK
    }
    fun analyze(clip: VideoSegment, sourceStates: List<State>): Analysis {
        val captured = freeze(clip); val times = times(captured); require(sourceStates.size == times.size) { "Incomplete frame samples" }
        val states = frozen(sourceStates); val candidates = mutableListOf<Candidate>(); var index = 0
        while (index < states.size) {
            if (states[index] != State.DARK) { index++; continue }
            val first = index; while (index < states.size && states[index] == State.DARK) index++
            val last = index - 1
            if (last - first + 1 >= 3 && first > 0 && index < states.size && states[first - 1] == State.BRIGHT && states[index] == State.BRIGHT)
                candidates += Candidate(first, last, times[first], times[last])
        }
        require(candidates.isNotEmpty()) { "No interior runs of at least three dark samples between bright samples; your edit stays unchanged" }
        return Analysis(captured, times, states, frozen(candidates))
    }
    fun prepare(clips: List<VideoSegment>, index: Int, analysis: Analysis, chosenIndices: List<Int>, music: VideoSoundtrack? = null): Result {
        require(clips.size in 1..1000 && index in clips.indices)
        val snapshot = clips.map(::freeze); val original = snapshot[index]
        require(original == analysis.clip) { "Selected clip changed; scan again" }
        require(chosenIndices.isNotEmpty() && chosenIndices.distinct().size == chosenIndices.size && chosenIndices.all { it in analysis.candidates.indices }) { "Choose at least one distinct reviewed dark hold" }
        // Re-derive every candidate from the captured immutable sample states, not arbitrary supplied bounds.
        val checked = analyze(original, analysis.states); require(checked == analysis) { "Frame analysis changed; scan again" }
        val removed = chosenIndices.sorted().map { analysis.candidates[it] }
        val ranges = mutableListOf<Pair<Long, Long>>(); var from = original.startMs
        removed.forEach { item -> require(item.fromMs > from && item.toMs > item.fromMs && item.toMs < original.endMs); ranges += from to item.fromMs; from = item.toMs }
        ranges += from to original.endMs
        val minimum = ceil(160 * original.speed.toDouble()).toLong()
        require(ranges.all { it.second - it.first >= minimum }) { "Keep at least 160 playback milliseconds per retained piece" }
        require(snapshot.size + ranges.size - 1 <= 1000) { "Project clip limit reached; choose fewer holds" }
        val retained = ranges.map { (a, b) -> original.copy(startMs = a, endMs = b, captions = frozen(original.captions.filter { it.startMs < b && it.endMs > a })) }
        val result = frozen(snapshot.take(index) + retained + snapshot.drop(index + 1))
        require(result.sumOf { it.captions.size } <= VideoCaptions.MAX_PROJECT_CUES && result.sumOf { clip -> clip.captions.sumOf { it.text.toByteArray(Charsets.UTF_8).size } } <= VideoCaptions.MAX_PROJECT_TEXT_BYTES) { "Retained pieces exceed project caption limits" }
        require(result.sumOf { it.title.toByteArray(Charsets.UTF_8).size } <= 128000) { "Retained pieces exceed project title limit" }
        VideoDraftCodec.requireRecoverableCaptions(result, music?.loop == false, music?.hasExcerpt == true)
        val before = VideoPlaybackTime.durationUs(snapshot); val after = VideoPlaybackTime.durationUs(result)
        val review = buildString {
            append("Clip ${index + 1}: remove ${removed.size} explicitly selected sampled dark holds; keep ${retained.size} pieces.\n")
            append("Timeline ${before} → ${after} microseconds; ${before - after}us shorter.\n\nChosen original-source intervals (first to last requested sample centers, not decoded timestamps):\n")
            removed.forEach { append("${VideoCaptions.formatTime(it.fromMs)} → ${VideoCaptions.formatTime(it.toMs)}; dark samples ${it.first + 1}–${it.last + 1}\n") }
            append("\nAll sample classifications (${analysis.times.size}; unknowns never join a dark run):\n")
            analysis.times.forEachIndexed { position, time -> append("${position + 1}: requested ${VideoCaptions.formatTime(time)} · ${analysis.states[position]}\n") }
            append("\nComplete resulting timeline source windows:\n")
            result.forEachIndexed { position, clip -> append("${position + 1}: source ${clip.sourceId}, ${VideoCaptions.formatTime(clip.startMs)}–${VideoCaptions.formatTime(clip.endMs)}, ${clip.speed}x, ${clip.captions.size} cues\n") }
            append("\nEvery selected-clip caption and its retained output positions (none means removed/outside the visible window):\n")
            if (original.captions.isEmpty()) append("(none)\n")
            original.captions.forEach { cue ->
                val positions = retained.withIndex().filter { (_, piece) -> cue.startMs < piece.endMs && cue.endMs > piece.startMs }.map { index + it.index + 1 }
                append("${VideoCaptions.formatTime(cue.startMs)} → ${VideoCaptions.formatTime(cue.endMs)}; output ${if (positions.isEmpty()) "none" else positions.joinToString()}\n${cue.text}\n")
            }
            append("\nSampling can miss bright flashes or intentional dark content between frames; repeated nearest frames and downsampling can mislead. Every channel ≤12 on opaque ≤256px SDR samples is not proof of empty/black original footage. No actual decoded timestamps or frame-exact removal guarantee. Original frames exclude titles/captions/grade/crop/motion/fades: meaningful edited overlays may be removed. Both picture and original audio in selected intervals disappear, later clips ripple earlier. Kept pieces retain their original settings, source-motion anchors and intersecting cue text/times/reference. Video/audio edge fades repeat on new pieces. Other clips and soundtrack settings stay; picture/music synchronization changes. Listen/watch before export. One Undo restores the previous edit; original files stay unchanged.")
        }
        require(review.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Review is too large; simplify the edit first" }
        return Result(result, frozen(retained), frozen(removed), before, after, review)
    }
}
