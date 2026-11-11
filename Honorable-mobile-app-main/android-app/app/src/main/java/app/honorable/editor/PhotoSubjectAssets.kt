package app.honorable.editor

import java.security.MessageDigest
import java.util.Collections
import kotlin.math.roundToInt

/** Bounded conversion of real per-subject model masks; this class does not detect or invent subjects. */
object PhotoSubjectAssets {
    const val MAX_EDGE = 1024
    const val MAX_SUBJECTS = 12
    const val MAX_MASK_PIXELS = 4_194_304L

    class Source private constructor(val width: Int, val height: Int, private val argb: IntArray, val sha256: String) {
        internal fun pixel(index: Int) = argb[index]
        fun pixels() = argb.copyOf()
        companion object {
            fun capture(width: Int, height: Int, pixels: IntArray): Source {
                require(width in 1..MAX_EDGE && height in 1..MAX_EDGE && pixels.size.toLong() == width.toLong() * height) { "Use a preview up to 1024 pixels per edge" }
                val owned = pixels.copyOf()
                require(owned.all { it ushr 24 == 255 }) { "Use an opaque edited photo preview; existing transparent artwork was kept" }
                val digest = MessageDigest.getInstance("SHA-256")
                // Defined big-endian width,height,then row-major ARGB words. No URI, name or EXIF.
                fun word(value: Int) { digest.update((value ushr 24).toByte()); digest.update((value ushr 16).toByte()); digest.update((value ushr 8).toByte()); digest.update(value.toByte()) }
                word(width); word(height); owned.forEach(::word)
                return Source(width, height, owned, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
            }
        }
    }
    class Mask(val x: Int, val y: Int, val width: Int, val height: Int, confidence: FloatArray) {
        private val values: FloatArray
        init {
            require(x >= 0 && y >= 0 && width in 1..MAX_EDGE && height in 1..MAX_EDGE && confidence.size.toLong() == width.toLong() * height) { "Invalid AI subject bounds" }
            values = confidence.copyOf()
            require(values.all { it.isFinite() && it in 0f..1f }) { "Invalid AI confidence mask" }
        }
        internal fun confidence(index: Int) = values[index]
        internal val area get() = width.toLong() * height
    }
    class Cutout internal constructor(val subject: Int, val sourceSha256: String, val x: Int, val y: Int, val width: Int, val height: Int,
                                      val visiblePixels: Int, val softPixels: Int, private val argb: IntArray) {
        /** Callers receive an independent array; review cannot mutate another candidate or the input. */
        fun pixels() = argb.copyOf()
    }

    fun prepare(source: Source, masks: List<Mask>, current: () -> Unit = {}): List<Cutout> {
        current()
        require(masks.size in 1..MAX_SUBJECTS) { "No separate subjects, or too many subjects; use a clearer photo with up to 12 subjects" }
        var area = 0L
        // Validate every candidate before allocating any output. Refuse rather than silently truncate.
        for (mask in masks) {
            current()
            require(mask.x.toLong() + mask.width <= source.width && mask.y.toLong() + mask.height <= source.height) { "AI subject lies outside this preview" }
            require(mask.area <= MAX_MASK_PIXELS - area) { "AI subject masks exceed the safe pixel budget" }; area += mask.area
        }
        val result = masks.mapIndexed { index, mask ->
            val pixels = IntArray(mask.area.toInt()); var visible = 0; var soft = 0
            for (y in 0 until mask.height) {
                current()
                for (x in 0 until mask.width) {
                    val local = y * mask.width + x
                    val alpha = (mask.confidence(local) * 255f).roundToInt().coerceIn(0, 255)
                    if (alpha > 0) {
                        pixels[local] = (alpha shl 24) or (source.pixel((mask.y + y) * source.width + mask.x + x) and 0x00ffffff)
                        visible++; if (alpha < 255) soft++
                    }
                    // Fully transparent output is canonical zero, with no hidden source RGB.
                }
            }
            require(visible > 0) { "An AI candidate has no visible pixels; no asset pack was created" }
            Cutout(index + 1, source.sha256, mask.x, mask.y, mask.width, mask.height, visible, soft, pixels)
        }
        current()
        return Collections.unmodifiableList(result)
    }
}
