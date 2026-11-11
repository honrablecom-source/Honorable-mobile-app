package com.honorablemobile

/** Exact source-ID/time de-duplication, not visual matching or source-file deletion. */
internal object VideoUniqueFootage {
    data class Piece(val originalIndex: Int, val clip: VideoSegment)
    data class Result(val clips: List<VideoSegment>, val pieces: List<Piece>, val removedClips: List<Int>,
                      val changedClips: List<Int>, val beforeUs: Long, val afterUs: Long)
    private data class Range(val from: Long, val to: Long)
    fun prepare(clips: List<VideoSegment>, music: VideoSoundtrack? = null, current: () -> Unit = {}): Result {
        val before = VideoPlaybackTime.durationUs(clips)
        require(before <= Int.MAX_VALUE.toLong() * 1000 && clips.all { it.endMs <= Int.MAX_VALUE }) { "Timeline exceeds supported millisecond timing bounds" }
        val seen = mutableMapOf<String, List<Range>>()
        val pieces = mutableListOf<Piece>()
        val removed = mutableListOf<Int>()
        val changed = mutableListOf<Int>()
        clips.forEachIndexed { index, clip ->
            current()
            val previous = seen[clip.sourceId].orEmpty()
            var cursor = clip.startMs
            val kept = mutableListOf<Range>()
            for (range in previous) {
                current()
                if (range.to <= cursor) continue
                if (range.from >= clip.endMs) break
                if (cursor < range.from) kept += Range(cursor, minOf(range.from, clip.endMs))
                cursor = maxOf(cursor, range.to)
                if (cursor >= clip.endMs) break
            }
            if (cursor < clip.endMs) kept += Range(cursor, clip.endMs)
            val intact = kept.size == 1 && kept[0].from == clip.startMs && kept[0].to == clip.endMs
            if (!intact) changed += index
            if (kept.isEmpty()) removed += index
            kept.forEach { range ->
                current()
                require(pieces.size < 1000) { "Removing repeats would exceed 1000 clips; simplify the project first" }
                val selected = if (intact) clip else {
                    require((range.to - range.from) * 1000.0 / clip.speed >= 100_000) { "A new remaining piece is shorter than 0.1 playback second; adjust the cuts before removing repeats" }
                    clip.copy(startMs = range.from, endMs = range.to,
                        captions = clip.captions.filter { it.startMs < range.to && it.endMs > range.from })
                }
                pieces += Piece(index, selected)
            }
            // Merge all earlier coverage, including intentionally removed later duplicates.
            val merged = mutableListOf<Range>()
            (previous + Range(clip.startMs, clip.endMs)).sortedBy { it.from }.forEach { range ->
                val last = merged.lastOrNull()
                if (last != null && range.from <= last.to) merged[merged.lastIndex] = Range(last.from, maxOf(last.to, range.to))
                else merged += range
            }
            seen[clip.sourceId] = merged
        }
        current()
        require(changed.isNotEmpty()) { "No repeated source-time footage to remove" }
        val result = pieces.map { it.clip }
        check(result.isNotEmpty())
        VideoDraftCodec.requireRecoverableCaptions(result, music?.loop == false, music?.hasExcerpt == true)
        val after = VideoPlaybackTime.durationUs(result)
        check(after < before)
        current()
        return Result(result, pieces.toList(), removed.toList(), changed.toList(), before, after)
    }
}
