package com.honorablemobile

import java.util.Collections

/** Caller must load the saved draft through the current primary-original/account version scope. */
internal object VideoCaptionRecovery {
    private data class Key(val source: String, val from: Long, val to: Long)
    data class Group(val number: Int, val sourceId: String, val from: Long, val to: Long, val savedIndices: List<Int>)
    data class Change(val currentIndex: Int, val group: Int, val before: VideoSegment, val after: VideoSegment)
    data class Result(val draft: VideoDraft, val changes: List<Change>, val groups: List<Group>,
                      val unmatched: List<Int>, val sameCaptions: Int, val reviewText: String)
    private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())
    private fun freeze(draft: VideoDraft) = draft.copy(
        segments = frozen(draft.segments.map { it.copy(captions = frozen(it.captions)) }), sources = frozen(draft.sources))
    fun prepare(current: VideoDraft, saved: VideoDraft): Result {
        val base = freeze(current); val alternative = freeze(saved)
        require(base.sourceDurationMs == alternative.sourceDurationMs) { "The primary original duration changed" }
        val currentSources = base.sources.associateBy { it.id }; val savedSources = alternative.sources.associateBy { it.id }
        val candidates = alternative.segments.withIndex().groupBy { Key(it.value.sourceId, it.value.startMs, it.value.endMs) }
        val groups = linkedMapOf<Key, Pair<Group, VideoSegment>>()
        val changes = mutableListOf<Change>(); val unmatched = mutableListOf<Int>(); var same = 0
        val clips = base.segments.mapIndexed { index, clip ->
            val key = Key(clip.sourceId, clip.startMs, clip.endMs)
            val matches = candidates[key]
            if (matches == null) { unmatched += index; clip }
            else {
                if (clip.sourceId != "primary") require(currentSources[clip.sourceId] == savedSources[clip.sourceId]) { "An added original's URI or duration differs; captions cannot be relinked by source ID alone" }
                val (group, representative) = groups.getOrPut(key) {
                    val first = matches.first().value
                    require(matches.all { it.value.captions == first.captions && it.value.captionSourceUri == first.captionSourceUri }) { "Saved positions have conflicting captions for the same source window; choose an unambiguous version" }
                    Group(groups.size + 1, clip.sourceId, clip.startMs, clip.endMs, frozen(matches.map { it.index })) to first
                }
                if (clip.captions == representative.captions && clip.captionSourceUri == representative.captionSourceUri) { same++; clip }
                else clip.copy(captions = representative.captions, captionSourceUri = representative.captionSourceUri).also {
                    changes += Change(index, group.number, clip, it)
                }
            }
        }
        require(changes.isNotEmpty()) { "No different saved captions match exact current source windows" }
        val proposed = base.copy(segments = frozen(clips))
        VideoDraftCodec.encode(proposed) // Enforce actual editable recovery size before review.
        val used = changes.map { it.group }.toSet()
        val usedGroups = frozen(groups.values.map { it.first }.filter { it.number in used })
        val report = buildString {
            append("Recover captions on ${changes.size} current clips; $same matching clips already have these captions.\n")
            append("Unmatched current positions kept: ${if (unmatched.isEmpty()) "none" else unmatched.joinToString { (it + 1).toString() }}\n\n")
            append("Saved source-window groups (identical duplicate caption payloads are equivalent):\n")
            usedGroups.forEach { group ->
                append("Group ${group.number}: source ${group.sourceId}, ${VideoCaptions.formatTime(group.from)}–${VideoCaptions.formatTime(group.to)}; saved positions ${group.savedIndices.joinToString { (it + 1).toString() }}\n")
            }
            fun cues(label: String, values: List<VideoCaption>) {
                append("$label (${values.size} cues):\n")
                if (values.isEmpty()) append("(none)\n")
                values.forEach { append("${VideoCaptions.formatTime(it.startMs)} → ${VideoCaptions.formatTime(it.endMs)}\n${it.text}\n") }
            }
            changes.forEach { change ->
                append("\nCurrent position ${change.currentIndex + 1} ← saved group ${change.group}\n")
                cues("Current captions", change.before.captions); cues("Saved captions to recover", change.after.captions)
                val sameReference = change.before.captionSourceUri == change.after.captionSourceUri
                append("Subtitle document reference: ${if (sameReference) "unchanged" else "changes"}; current ${if (change.before.captionSourceUri == null) "none" else "present"} → saved ${if (change.after.captionSourceUri == null) "none" else "present"}. URI metadata is not displayed.\n")
            }
            append("\nOnly captions and their subtitle-document references change. Exact source windows match regardless of timeline position, speed or appearance. Current order, source identities, trim/playhead selection metadata, speeds, volume, titles, grades/layout/motion/fades and soundtrack stay. Empty saved captions clear current cues as shown. Unmatched current clips stay. All saved cue source times are copied, including cues outside the visible window; playback follows the current speed. Review every text/reference change. Original documents are checked again before Apply. One Undo restores prior captions; original media and saved versions stay unchanged.")
        }
        require(report.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Caption review is too large; simplify the versions before recovery" }
        return Result(proposed, frozen(changes), usedGroups, frozen(unmatched), same, report)
    }
}
