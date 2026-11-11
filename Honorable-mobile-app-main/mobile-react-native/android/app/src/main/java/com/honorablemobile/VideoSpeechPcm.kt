package com.honorablemobile

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/** Selected original-source PCM only. Missing audio is refused, never silently filled with zeros. */
internal class VideoSpeechPcm(private val startMs: Long, private val endMs: Long, private val rate: Int) {
    private val count: Int
    private val mono: FloatArray
    private val present: BooleanArray
    private var lastPacketUs: Long? = null
    init {
        require(startMs >= 0 && endMs <= Int.MAX_VALUE && endMs - startMs in 500..VideoSpeechCaptions.MAX_SECONDS * 1000L)
        require(rate in 8000..48000) { "Speech captions support decoded audio at 8–48 kHz" }
        count = ((endMs - startMs) * rate / 1000).toInt()
        mono = FloatArray(count); present = BooleanArray(count)
    }

    fun append(buffer: ByteBuffer, offset: Int, bytes: Int, sourceTimeUs: Long, channels: Int, floatPcm: Boolean, current: () -> Unit = {}) {
        require(channels in 1..8 && bytes in 0..1_048_576 && offset >= 0 && offset.toLong() + bytes <= buffer.limit())
        val width = if (floatPcm) 4 else 2
        require(bytes % (channels * width) == 0) { "Incomplete speech PCM frame" }
        require(sourceTimeUs >= 0 && sourceTimeUs <= (Int.MAX_VALUE.toLong() + 5000) * 1000)
        require(lastPacketUs == null || sourceTimeUs >= requireNotNull(lastPacketUs)) { "Speech audio timestamps moved backwards" }
        lastPacketUs = sourceTimeUs
        val first = ((sourceTimeUs - startMs * 1000) * rate / 1_000_000.0).roundToLong()
        val input = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply { position(offset); limit(offset + bytes) }
        val frames = bytes / (channels * width)
        for (frame in 0 until frames) {
            if (frame % 2048 == 0) current()
            var sum = 0.0
            repeat(channels) {
                val value = if (floatPcm) input.float else input.short / 32768f
                require(value.isFinite() && value in -1f..1f) { "Invalid speech PCM amplitude" }
                sum += value
            }
            val at = first + frame
            if (at in 0 until count.toLong()) {
                require(!present[at.toInt()]) { "Overlapping speech audio packets" }
                mono[at.toInt()] = (sum / channels).toFloat()
                present[at.toInt()] = true
            }
        }
    }

    fun finish(current: () -> Unit = {}): FloatArray {
        current()
        require(present.all { it }) { "The complete selected speech range could not be decoded; try a shorter range" }
        val outputCount = ((endMs - startMs) * VideoSpeechCaptions.RATE / 1000).toInt()
        // Source-time sampling, never concatenation of packets or playback-speed audio.
        return FloatArray(outputCount) { i ->
            if (i % 2048 == 0) current()
            val position = i.toDouble() * rate / VideoSpeechCaptions.RATE
            val from = position.toInt().coerceAtMost(mono.lastIndex)
            val fraction = position - from
            (mono[from] * (1 - fraction) + mono[minOf(from + 1, mono.lastIndex)] * fraction).toFloat()
        }
    }
}
