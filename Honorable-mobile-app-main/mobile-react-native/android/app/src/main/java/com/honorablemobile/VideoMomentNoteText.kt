package com.honorablemobile

/** Explicit source bookmarks from a pasted production log; no media or speech analysis. */
internal object VideoMomentNoteText {
    fun parse(text: String, durationMs: Long): List<VideoMomentNote> {
        require(text.length <= 16000) { "Paste up to 16000 characters" }
        val rows = text.trim().lines().filter { it.isNotBlank() }
        require(rows.size in 1..100) { "Paste 1–100 notes, one source-seconds | label per line" }
        val notes = rows.mapIndexed { index, row ->
            val columns = row.split('|', limit = 2)
            require(columns.size == 2) { "Line ${index + 1}: use source seconds | label" }
            try { VideoMomentNote(VideoCaptions.seconds(columns[0]), columns[1].trim()) }
            catch (failure: IllegalArgumentException) { throw IllegalArgumentException("Line ${index + 1}: ${failure.message}") }
        }
        return VideoMomentNotes(durationMs, notes).notes
    }

    fun merge(catalog: VideoMomentNotes, notes: List<VideoMomentNote>): VideoMomentNotes {
        require(notes.size in 1..100) { "Import 1–100 notes" }
        return VideoMomentNotes(catalog.durationMs, catalog.notes + notes)
    }
}
