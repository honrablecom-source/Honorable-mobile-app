package com.honorablemobile

/** Explicitly relinked video-only cut lists; no source URIs, automatic media lookup or effects. */
internal object VideoEdlImport {
    const val MAX_BYTES = 128000
    data class Event(val number: Int, val reel: String, val sourceInFrame: Long, val sourceOutFrame: Long,
                     val recordInFrame: Long, val recordOutFrame: Long, val clip: VideoSegment)
    data class Result(val clips: List<VideoSegment>, val events: List<Event>, val fps: Int,
                      val recordOriginFrame: Long, val recordFrames: Long, val frameDurationUs: Long,
                      val nativeDurationUs: Long, val widenedEvents: Int)
    private val reelPattern = Regex("[A-Za-z0-9_]{1,8}")
    private val eventPattern = Regex("([0-9]{3})\\s+([A-Za-z0-9_]{1,8})\\s+V\\s+C\\s+([0-9]{2}:[0-9]{2}:[0-9]{2}:[0-9]{2})\\s+([0-9]{2}:[0-9]{2}:[0-9]{2}:[0-9]{2})\\s+([0-9]{2}:[0-9]{2}:[0-9]{2}:[0-9]{2})\\s+([0-9]{2}:[0-9]{2}:[0-9]{2}:[0-9]{2})")
    private fun frame(text: String, fps: Int): Long {
        val parts = text.split(':').map { it.toInt() }
        require(parts[0] in 0..23 && parts[1] in 0..59 && parts[2] in 0..59 && parts[3] in 0 until fps) { "Invalid non-drop timecode" }
        return ((parts[0] * 3600L + parts[1] * 60 + parts[2]) * fps) + parts[3]
    }
    fun prepare(text: String, mappingText: String, fps: Int, durations: Map<String, Long>, music: VideoSoundtrack? = null): Result {
        require(fps in setOf(24, 25, 30)) { "Choose 24, 25 or 30 non-drop frames per second" }
        require(durations.size in 1..32 && durations.all { (id, duration) -> id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && !id.startsWith("__") && duration in 1..Int.MAX_VALUE.toLong() }) { "Original source catalog is invalid" }
        require(text.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES && text.none { it.isISOControl() && it !in "\r\n\t" }) { "Use a readable cut list up to 128000 UTF-8 bytes" }
        require(mappingText.toByteArray(Charsets.UTF_8).size <= 8192) { "Use a reel mapping up to 8192 bytes" }
        val links = linkedMapOf<String, String>()
        val rows = mappingText.trim().lines().filter { it.isNotBlank() }
        require(rows.size in 1..32) { "Link 1–32 reels to existing source IDs" }
        rows.forEachIndexed { index, row ->
            val columns = row.split(',').map { it.trim() }
            require(columns.size == 2 && reelPattern.matches(columns[0]) && columns[1] in durations) { "Mapping line ${index + 1}: use reel,existingSourceID" }
            require(links.put(columns[0], columns[1]) == null) { "A reel was linked more than once" }
        }
        var titleSeen = false
        var fcmSeen = false
        var rateSeen = false
        val events = mutableListOf<Event>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("TITLE:") -> { require(!titleSeen && events.isEmpty()) { "Line ${index + 1}: duplicate or misplaced title" }; titleSeen = true }
                line.startsWith("FCM:") -> {
                    require(!fcmSeen && events.isEmpty() && line == "FCM: NON-DROP FRAME") { "Only one NON-DROP FRAME header before events is supported" }; fcmSeen = true
                }
                line.startsWith("* NOBLE FRAME RATE:") -> {
                    require(!rateSeen && events.isEmpty() && line.substringAfter(':').trim().toIntOrNull() == fps) { "Declared Noble frame rate does not match the chosen rate" }; rateSeen = true
                }
                line.startsWith('*') -> Unit // Metadata comments are not relinking authority or imported effects.
                else -> {
                    require(fcmSeen) { "Add FCM: NON-DROP FRAME before the first event" }
                    require(events.size < 999) { "Use at most 999 video cuts" }
                    val match = eventPattern.matchEntire(line) ?: throw IllegalArgumentException("Line ${index + 1}: only video-only C cuts with four non-drop timecodes are supported")
                    val number = match.groupValues[1].toInt(); val reel = match.groupValues[2]
                    require(number == events.size + 1) { "Events must be numbered consecutively from 001" }
                    val source = links[reel] ?: throw IllegalArgumentException("Event $number: explicitly link this reel to an existing original")
                    val (sourceIn, sourceOut, recordIn, recordOut) = match.groupValues.drop(3).map { frame(it, fps) }
                    require(sourceOut > sourceIn && recordOut > recordIn && sourceOut - sourceIn == recordOut - recordIn) { "Event $number: source and record frame spans must be equal and increasing" }
                    require(events.lastOrNull()?.recordOutFrame?.let { it == recordIn } != false) { "Event $number: record gaps, overlaps and reordering are unsupported" }
                    // The whole-millisecond envelope preserves listed frame codes on inward re-export.
                    // It can widen each source edge by <1ms; native duration is disclosed separately.
                    val from = sourceIn * 1000 / fps
                    val to = (sourceOut * 1000 + fps - 1) / fps
                    require(to <= durations.getValue(source)) { "Event $number: requested source window exceeds that original" }
                    events += Event(number, reel, sourceIn, sourceOut, recordIn, recordOut, VideoSegment(from, to, sourceId = source))
                }
            }
        }
        require(fcmSeen && events.isNotEmpty()) { "Paste at least one video cut and its non-drop header" }
        val clips = events.map { it.clip }
        VideoDraftCodec.requireRecoverableCaptions(clips, music?.loop == false, music?.hasExcerpt == true)
        val origin = events.first().recordInFrame; val frames = events.last().recordOutFrame - origin
        val ideal = frames * 1_000_000 / fps; val actual = VideoPlaybackTime.durationUs(clips)
        check(actual >= ideal && actual - ideal < events.size * 2000L)
        return Result(clips, events.toList(), fps, origin, frames, ideal, actual,
            events.count { it.sourceInFrame * 1000 % fps != 0L || it.sourceOutFrame * 1000 % fps != 0L })
    }
}
