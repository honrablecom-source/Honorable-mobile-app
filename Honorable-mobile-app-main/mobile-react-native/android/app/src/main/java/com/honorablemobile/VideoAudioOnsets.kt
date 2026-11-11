package com.honorablemobile

import java.util.Collections
import kotlin.math.ceil
import kotlin.math.abs

/** Energy-rise suggestions only: no beat, speech, semantic or sample-exact detection. */
internal object VideoAudioOnsets {
    data class Event(val sourceMs: Long, val peakRms: Double, val baselineRms: Double)
    data class Analysis(val clip: VideoSegment, val events: List<Event>, val knownMs: Long, val totalMs: Long, val suppressed: Int)
    data class Result(val clips: List<VideoSegment>, val pieces: List<VideoSegment>, val chosen: List<Event>, val beforeUs: Long, val afterUs: Long, val reviewText: String)
    private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())
    private fun freeze(clip: VideoSegment) = clip.copy(captions = frozen(clip.captions))
    fun detect(clip: VideoSegment, windows: List<VideoQuietWindow>, current: () -> Unit = {}): Analysis {
        val captured = freeze(clip); val from = captured.startMs; val to = captured.endMs
        require(from >= 0 && to <= Int.MAX_VALUE && to - from <= 120_000) { "Choose at most two source minutes" }
        require(windows.size == ((to - from + 19) / 20).toInt() && windows.size in 1..6000) { "Incomplete energy grid" }
        var known = 0L
        windows.forEachIndexed { index, window ->
            current()
            require(window.startMs == from + index * 20L && window.endMs == minOf(to, window.startMs + 20)) { "Audio windows changed order, duration or source range" }
            window.rms?.let { require(it.isFinite() && it in 0.0..1.0); known += window.endMs - window.startMs }
        }
        require(known * 100 >= (to - from) * 95) { "At least 95% of source time needs known audio; choose a shorter range or another source" }
        val gap = ceil(160 * captured.speed.toDouble()).toLong()
        val candidates = mutableListOf<Event>()
        for (index in 10 until windows.lastIndex) {
            current(); val window = windows[index]; val peak = window.rms ?: continue
            val previous = windows[index - 1].rms ?: continue; val next = windows[index + 1].rms ?: continue
            if (peak < .02 || peak <= previous || peak < next || peak - previous < .01) continue
            val history = windows.subList(index - 10, index).map { it.rms ?: return@map Double.NaN }
            if (history.any { !it.isFinite() }) continue
            val ordered = history.sorted(); val baseline = maxOf(.001, (ordered[4] + ordered[5]) / 2)
            if (peak < baseline * 2 || window.startMs - from < gap || to - window.startMs < gap) continue
            candidates += Event(window.startMs, peak, baseline)
        }
        // Resolve nearby alternatives by strongest relative rise, then earliest source time.
        val selected = mutableListOf<Event>()
        candidates.sortedWith(compareByDescending<Event> { it.peakRms / it.baselineRms }.thenBy { it.sourceMs }).forEach { event ->
            current()
            if (selected.none { abs(it.sourceMs - event.sourceMs) < gap }) {
                require(selected.size < 200) { "More than 200 onset suggestions; shorten this clip before analysis" }; selected += event
            }
        }
        require(selected.isNotEmpty()) { "No clear energy rises matched; use manual cuts or another range" }
        current(); return Analysis(captured, frozen(selected.sortedBy { it.sourceMs }), known, to - from, candidates.size - selected.size)
    }
    fun prepare(clips: List<VideoSegment>, index: Int, analysis: Analysis, selectedTimes: List<Long>, music: VideoSoundtrack? = null): Result {
        require(clips.size in 1..1000 && index in clips.indices)
        val snapshot = clips.map(::freeze); val original = snapshot[index]
        require(original == analysis.clip) { "Selected clip changed; analyse again" }
        require(selectedTimes.size in 1..200 && selectedTimes.distinct().size == selectedTimes.size) { "Choose at least one distinct onset" }
        val lookup = analysis.events.associateBy { it.sourceMs }
        val chosen = selectedTimes.sorted().map { requireNotNull(lookup[it]) { "A chosen onset does not belong to this analysis" } }
        val boundaries = listOf(original.startMs) + chosen.map { it.sourceMs } + original.endMs
        val minimum = ceil(160 * original.speed.toDouble()).toLong()
        require(boundaries.zipWithNext().all { (a, b) -> b > a && b - a >= minimum }) { "Keep at least 160 playback milliseconds per new piece" }
        require(snapshot.size + chosen.size <= 1000) { "Project clip limit reached; choose fewer onsets" }
        val pieces = boundaries.zipWithNext().map { (from, to) -> original.copy(startMs = from, endMs = to, captions = frozen(original.captions.filter { it.startMs < to && it.endMs > from })) }
        val result = frozen(snapshot.take(index) + pieces + snapshot.drop(index + 1))
        require(result.sumOf { it.captions.size } <= VideoCaptions.MAX_PROJECT_CUES && result.sumOf { clip -> clip.captions.sumOf { it.text.toByteArray(Charsets.UTF_8).size } } <= VideoCaptions.MAX_PROJECT_TEXT_BYTES) { "Splits exceed project caption limits; choose fewer onsets" }
        require(result.sumOf { it.title.toByteArray(Charsets.UTF_8).size } <= 128000) { "Splits exceed project title limit; choose fewer onsets" }
        VideoDraftCodec.requireRecoverableCaptions(result, music?.loop == false, music?.hasExcerpt == true)
        val before = VideoPlaybackTime.durationUs(snapshot); val after = VideoPlaybackTime.durationUs(result)
        check(after <= before && before - after <= chosen.size)
        val review = buildString {
            append("Clip ${index + 1}: ${chosen.size} chosen energy rises → ${pieces.size} source-contiguous pieces.\n")
            append("Known original source audio ${analysis.knownMs}/${analysis.totalMs}ms; ${analysis.suppressed} nearby alternatives suppressed.\n")
            append("Playback ${before} → ${after} microseconds; ${before - after}us integer-rounding contraction.\n\nChosen source onsets (20ms energy window starts, not exact event/sample times):\n")
            chosen.forEach { append("${VideoCaptions.formatTime(it.sourceMs)} · peak RMS ${it.peakRms}; 200ms median/floor baseline ${it.baselineRms}\n") }
            append("\nComplete resulting timeline source windows:\n")
            result.forEachIndexed { position, clip -> append("${position + 1}: source ${clip.sourceId}, ${VideoCaptions.formatTime(clip.startMs)}–${VideoCaptions.formatTime(clip.endMs)}, ${clip.speed}x, ${clip.captions.size} source cues\n") }
            append("\nEvery selected-clip caption, with retained output positions (no positions means outside the original visible window):\n")
            if (original.captions.isEmpty()) append("(none)\n")
            original.captions.forEach { cue ->
                val positions = pieces.withIndex().filter { (_, piece) -> cue.startMs < piece.endMs && cue.endMs > piece.startMs }.map { index + it.index + 1 }
                append("${VideoCaptions.formatTime(cue.startMs)} → ${VideoCaptions.formatTime(cue.endMs)}; output ${if (positions.isEmpty()) "none" else positions.joinToString()}\n${cue.text}\n")
            }
            append("\nOnly source-contiguous cuts are added to the chosen clip: every source millisecond, order, speed, gain, title/style, grade, layout, source-anchored motion and subtitle reference stay. Intersecting cues keep their full original source times/text; outside cues disappear from this edit. Other clips and soundtrack settings stay. Existing VIDEO and audio edge fades repeat per new piece and can visibly/audibly change the result. Soundtrack timing can shift by the disclosed microsecond rounding contraction. Suggestions are energy rises, not beats, speech or semantic cuts; some onsets can be missed and false positives selected. Original audio was measured before mute/volume/speed/fades/soundtrack. Preview/listen before export; timing is not codec frame/sample exact. One Undo restores the previous edit; original files stay unchanged.")
        }
        require(review.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Review is too large; simplify the project before adding cuts" }
        return Result(result, frozen(pieces), frozen(chosen), before, after, review)
    }
}
