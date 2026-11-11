package com.honorablemobile

import java.io.OutputStream
import java.security.MessageDigest

/** A temporal strip is copied at its original spatial coordinates, without averaging frames. */
internal object VideoTimeSlice {
    const val MAX_EDGE = 512
    const val MAX_BYTES = 2L * 1024 * 1024
    enum class Axis { COLUMNS, ROWS }
    data class Plan(val fromMs: Long, val toMs: Long, val count: Int, val axis: Axis) {
        init { require(fromMs >= 0 && toMs > fromMs && toMs <= Int.MAX_VALUE && count in setOf(4, 8, 16) && toMs - fromMs >= count) { "Choose a valid source range with at least one millisecond per sample" } }
        val times: List<Long> get() = (0 until count).map { fromMs + (2L * it + 1) * (toMs - fromMs) / (2 * count) }
    }
    fun size(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
    fun render(plan: Plan, width: Int, height: Int, frame: (Int, Long) -> IntArray, current: () -> Unit = {}): IntArray {
        require(width in 1..MAX_EDGE && height in 1..MAX_EDGE)
        val dimension = if (plan.axis == Axis.COLUMNS) width else height
        require(dimension >= plan.count) { "This image is too narrow for that direction/sample count" }
        current(); val output = IntArray(width * height)
        plan.times.forEachIndexed { index, time ->
            current(); val pixels = frame(index, time)
            require(pixels.size == output.size) { "A decoded frame changed dimensions" }
            val from = index * dimension / plan.count; val to = (index + 1) * dimension / plan.count
            if (plan.axis == Axis.ROWS) {
                for (row in from until to) { current(); pixels.copyInto(output, row * width, row * width, (row + 1) * width) }
            } else {
                for (row in 0 until height) { current(); pixels.copyInto(output, row * width + from, row * width + from, row * width + to) }
            }
        }
        current(); return output
    }
    class OutputBudget(private val output: OutputStream, private val current: () -> Unit = {}, private val limit: Long = MAX_BYTES) : OutputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")
        private var count = 0L; private var failed = false; private var finished = false
        init { require(limit in 1..MAX_BYTES) }
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            check(!failed && !finished) { "Time-slice output already failed or finished" }
            try { current(); require(offset >= 0 && length >= 0 && offset <= bytes.size - length); check(length <= limit - count) { "Time-slice PNG exceeds its size limit" }; output.write(bytes, offset, length); digest.update(bytes, offset, length); count += length }
            catch (error: Throwable) { failed = true; throw error }
        }
        fun finish(): String {
            check(!failed && !finished); try { current(); check(count > 0); finished = true; return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } }
            catch (error: Throwable) { failed = true; throw error }
        }
    }
}
