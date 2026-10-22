package com.honorablemobile

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Times refer to the original source, so captions follow trimming, splitting and speed changes. */
internal data class VideoCaption(val startMs: Long, val endMs: Long, val text: String) {
    init {
        require(startMs >= 0 && endMs > startMs && endMs <= Int.MAX_VALUE.toLong()) { "Choose valid source timestamps" }
        require(text.isNotBlank() && text.length <= 240 && text.count { it == '\n' } <= 2) { "Use 1–240 characters and up to three lines" }
        require(text.none { it.isISOControl() && it != '\n' })
        require(text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == text)
    }
}

internal data class TimelineCaption(val startUs: Long, val endUs: Long, val text: String)

internal object VideoCaptions {
    const val MAX_PER_CLIP = 200
    const val MAX_PROJECT_CUES = 1000
    const val MAX_PROJECT_TEXT_BYTES = 64_000
    const val MAX_SRT_BYTES = 128_000

    fun validate(cues: List<VideoCaption>) {
        require(cues.size <= MAX_PER_CLIP) { "Use up to 200 captions per clip" }
        require(cues.zipWithNext().all { (a, b) -> a.endMs <= b.startMs }) { "Captions must be ordered and cannot overlap" }
    }

    fun parseSrt(bytes: ByteArray, sourceDurationMs: Long): List<VideoCaption> {
        require(bytes.size in 1..MAX_SRT_BYTES) { "Choose an SRT file up to 128 KB" }
        require(sourceDurationMs in 1..Int.MAX_VALUE.toLong())
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            .removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
        require(text.isNotEmpty()) { "The SRT file is empty" }
        val blocks = text.split(Regex("\n(?:[ \\t]*\n)+"))
        require(blocks.size in 1..MAX_PER_CLIP) { "Use up to 200 captions per clip" }
        val cues = blocks.map { block ->
            val lines = block.lines()
            require(lines.size >= 3 && lines[0].trim().matches(Regex("[0-9]{1,6}"))) { "Expected numbered SRT cues" }
            val timing = Regex("([0-9]{2,7}:[0-5][0-9]:[0-5][0-9][,.][0-9]{3})[ \\t]+-->[ \\t]+([0-9]{2,7}:[0-5][0-9]:[0-5][0-9][,.][0-9]{3})").matchEntire(lines[1].trim())
                ?: error("Invalid SRT timestamp")
            val start = parseTimestamp(timing.groupValues[1]); val end = parseTimestamp(timing.groupValues[2])
            require(end <= sourceDurationMs) { "Caption exceeds the original source duration" }
            VideoCaption(start, end, lines.drop(2).joinToString("\n").trim())
        }.sortedBy { it.startMs }
        validate(cues)
        require(cues.sumOf { it.text.toByteArray(Charsets.UTF_8).size } <= MAX_PROJECT_TEXT_BYTES) { "Caption text limit reached" }
        return cues
    }

    private fun parseTimestamp(value: String): Long {
        val parts = value.replace(',', ':').replace('.', ':').split(':').map { it.toLong() }
        return parts[0] * 3_600_000 + parts[1] * 60_000 + parts[2] * 1000 + parts[3]
    }

    fun seconds(value: String): Long {
        val clean = value.trim()
        require(clean.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,3})?"))) { "Use seconds with up to three decimals" }
        val parts = clean.split('.')
        return (parts[0].toLong() * 1000 + (parts.getOrNull(1)?.padEnd(3, '0')?.toLong() ?: 0)).also { require(it <= Int.MAX_VALUE) }
    }

    fun formatTime(ms: Long): String {
        require(ms >= 0)
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
    }

    fun resolve(clips: List<VideoSegment>): List<TimelineCaption> {
        val result = mutableListOf<TimelineCaption>()
        var offsetUs = 0L
        for (clip in clips) {
            for (cue in clip.captions) {
                val start = maxOf(cue.startMs, clip.startMs); val end = minOf(cue.endMs, clip.endMs)
                if (end <= start) continue
                val a = offsetUs + ((start - clip.startMs) * 1000.0 / clip.speed).toLong()
                val b = offsetUs + ((end - clip.startMs) * 1000.0 / clip.speed).toLong()
                val last = result.lastOrNull()
                if (last != null && last.endUs == a && last.text == cue.text) result[result.lastIndex] = last.copy(endUs = b)
                else result.add(TimelineCaption(a, b, cue.text))
            }
            offsetUs += clip.durationUs
        }
        return result
    }

    fun at(cues: List<TimelineCaption>, timeUs: Long): TimelineCaption? {
        var low = 0; var high = cues.size
        while (low < high) { val middle = (low + high) ushr 1; if (cues[middle].startUs <= timeUs) low = middle + 1 else high = middle }
        return cues.getOrNull(low - 1)?.takeIf { timeUs < it.endUs }
    }

    /** SRT uses milliseconds; omit intervals shorter than that representable precision. */
    fun exportSrt(clips: List<VideoSegment>): ByteArray {
        val cues = resolve(clips).filter { it.endUs / 1000 > it.startUs / 1000 }
        require(cues.isNotEmpty()) { "Add captions before exporting SRT" }
        return cues.mapIndexed { index, cue -> "${index + 1}\n${formatTime(cue.startUs / 1000)} --> ${formatTime(cue.endUs / 1000)}\n${cue.text}\n" }
            .joinToString("\n").toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_SRT_BYTES) { "Subtitle export exceeds 128 KB" } }
    }
}
