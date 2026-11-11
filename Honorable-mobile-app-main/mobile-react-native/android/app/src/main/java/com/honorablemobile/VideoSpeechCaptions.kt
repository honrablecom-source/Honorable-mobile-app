package com.honorablemobile

import java.util.concurrent.CancellationException
import kotlin.math.sqrt

/** The pinned English Wav2Vec2 CTC vocabulary. Scores are not confidence probabilities. */
internal object VideoSpeechCaptions {
    const val RATE = 16_000
    const val MAX_SECONDS = 20
    private const val HOP = 320
    private const val RECEPTIVE_FIELD = 400
    private val vocabulary = arrayOf("", "<s>", "</s>", "<unk>", " ", "E", "T", "A", "O", "N", "I", "H", "S", "R", "D", "L", "U", "M", "W", "C", "F", "G", "Y", "P", "B", "V", "K", "'", "X", "J", "Q", "Z")
    data class Word(val startMs: Long, val endMs: Long, val text: String)
    data class Draft(val words: List<Word>, val captions: List<VideoCaption>)

    fun normalize(pcm: FloatArray, cancelled: () -> Boolean = { false }): FloatArray {
        require(pcm.size in RATE / 2..RATE * MAX_SECONDS) { "Choose 0.5–20 source seconds" }
        var mean = 0.0
        for (i in pcm.indices) {
            if (i % 4096 == 0) current(cancelled)
            require(pcm[i].isFinite() && pcm[i] in -1f..1f) { "Invalid decoded speech samples" }
            mean += pcm[i]
        }
        mean /= pcm.size
        var variance = 0.0
        for (value in pcm) variance += (value - mean) * (value - mean)
        val scale = sqrt(variance / pcm.size + 1e-7)
        return FloatArray(pcm.size) { i ->
            if (i % 4096 == 0) current(cancelled)
            ((pcm[i] - mean) / scale).toFloat()
        }
    }

    /** 20 ms model frames are approximate source alignment, never forced/exact word alignment. */
    fun decode(logits: Array<FloatArray>, sampleCount: Int, sourceStartMs: Long, cancelled: () -> Boolean = { false }): Draft {
        require(sampleCount in RATE / 2..RATE * MAX_SECONDS)
        val durationMs = sampleCount * 1000L / RATE
        require(sourceStartMs >= 0 && sourceStartMs <= Int.MAX_VALUE - durationMs)
        require(logits.size == (sampleCount - RECEPTIVE_FIELD) / HOP + 1) { "Unexpected speech model frame count" }
        val words = mutableListOf<Word>()
        val text = StringBuilder()
        var firstFrame = -1
        var lastFrame = -1
        fun flush() {
            if (text.isEmpty()) return
            val from = sourceStartMs + firstFrame * HOP * 1000L / RATE
            val until = sourceStartMs + minOf(durationMs, (lastFrame * HOP + RECEPTIVE_FIELD) * 1000L / RATE)
            require(text.length <= 80 && words.size < 200) { "Speech output exceeds review limits" }
            words.add(Word(from, until, text.toString()))
            text.clear(); firstFrame = -1; lastFrame = -1
        }
        var previous = -1
        for ((frame, scores) in logits.withIndex()) {
            current(cancelled)
            require(scores.size == vocabulary.size && scores.all { it.isFinite() }) { "Invalid speech model scores" }
            var best = 0
            for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
            require(best !in 1..3) { "Speech model returned an unsupported token" }
            if (best != 0 && best != previous) {
                if (best == 4) flush()
                else {
                    if (firstFrame < 0) firstFrame = frame
                    text.append(vocabulary[best]); lastFrame = frame
                }
            } else if (best != 0 && best != 4) lastFrame = frame
            previous = best
        }
        flush()
        val captions = mutableListOf<VideoCaption>()
        var group = mutableListOf<Word>()
        fun flushGroup() {
            if (group.isEmpty()) return
            captions.add(VideoCaption(group.first().startMs, group.last().endMs, group.joinToString(" ") { it.text }))
            group = mutableListOf()
        }
        for (word in words) {
            if (group.isNotEmpty() && (word.endMs - group.first().startMs > 4000 ||
                    group.sumOf { it.text.length + 1 } + word.text.length > 72 ||
                    word.startMs - group.last().endMs > 700)) flushGroup()
            group.add(word)
        }
        flushGroup()
        VideoCaptions.validate(captions)
        current(cancelled)
        return Draft(words.toList(), captions.toList())
    }

    private fun current(cancelled: () -> Boolean) {
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Speech captions stopped")
    }
}
