package app.honorable.editor

import kotlin.math.roundToInt

/** Converts local model confidence to a soft alpha matte, without thresholding away edge detail. */
object PersonMaskAlpha {
    class NoClearPerson : IllegalArgumentException("No clear person boundary")
    const val MAX_PIXELS = 1_048_576

    fun pixels(width: Int, height: Int, confidence: FloatArray): IntArray {
        val count = width.toLong() * height
        require(width > 0 && height > 0 && count <= MAX_PIXELS && count == confidence.size.toLong())
        require(confidence.all { it.isFinite() })
        val minimum = maxOf(1, confidence.size / 100)
        if (confidence.count { it >= .65f } < minimum || confidence.count { it <= .35f } < minimum) {
            throw NoClearPerson()
        }
        return IntArray(confidence.size) { index ->
            ((confidence[index].coerceIn(0f, 1f) * 255).roundToInt() shl 24) or 0x00ffffff
        }
    }
}
