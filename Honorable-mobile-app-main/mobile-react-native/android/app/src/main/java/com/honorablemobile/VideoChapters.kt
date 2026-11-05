package com.honorablemobile

import java.util.Locale

/** Read-only clip starts in edited playback time. Never reads source media or its URI. */
internal object VideoChapters {
    fun text(clips: List<VideoSegment>): String {
        require(clips.size in 1..1000) { "Use a project with 1–1000 clips" }
        var startUs = 0L
        val rows = clips.mapIndexed { index, clip ->
            val ms = startUs / 1000
            val title = clip.title.replace(Regex("[\\s\\p{Z}]+"), " ").trim().ifEmpty { "Clip ${index + 1}" }
            val row = String.format(Locale.ROOT, "%02d:%02d:%02d.%03d %s", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000, title)
            startUs = try { Math.addExact(startUs, clip.durationUs) } catch (_: ArithmeticException) {
                throw IllegalArgumentException("Project duration is too large for a chapter list")
            }
            row
        }
        return "Clip starts in edited playback time (HH:MM:SS.mmm)\n\n" + rows.joinToString("\n")
    }
}
