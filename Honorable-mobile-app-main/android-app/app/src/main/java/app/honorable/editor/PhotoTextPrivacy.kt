package app.honorable.editor

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Geometry comes from real OCR. No text inference, sensitive-data classifier or safety guarantee here. */
object PhotoTextPrivacy {
    const val MAX_EDGE = 1024
    const val MAX_REGIONS = 100
    const val MAX_PADDING = 24
    const val MAX_PNG_BYTES = 4_000_000
    private const val BLACK = -0x1000000

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        init { require(left < right && top < bottom) { "Text region must have positive area" } }
    }
    data class Region(val id: Int, val box: Box)

    class Source private constructor(val width: Int, val height: Int, private val samples: IntArray, val fingerprint: String) {
        internal fun copyPixels() = samples.copyOf()
        companion object {
            /** Flatten alpha onto white. Neither hidden RGB nor source metadata enters the output. */
            fun capture(width: Int, height: Int, pixels: IntArray, current: () -> Unit = {}): Source {
                current()
                require(width in 1..MAX_EDGE && height in 1..MAX_EDGE && pixels.size.toLong() == width.toLong() * height) { "Use an image up to 1024 pixels per edge" }
                val owned = pixels.copyOf()
                for (y in 0 until height) {
                    current()
                    for (x in 0 until width) {
                        val index = y * width + x; val pixel = owned[index]; val alpha = pixel ushr 24
                        fun matte(shift: Int): Int = ((((pixel ushr shift) and 255) * alpha + 255 * (255 - alpha) + 127) / 255)
                        owned[index] = BLACK or (matte(16) shl 16) or (matte(8) shl 8) or matte(0)
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update("NOBLE-TEXT-PRIVACY-1".toByteArray(Charsets.US_ASCII))
                fun number(value: Int) { digest.update(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())) }
                number(width); number(height)
                val row = ByteArray(width * 4)
                for (y in 0 until height) {
                    current()
                    for (x in 0 until width) {
                        val value = owned[y * width + x]; val offset = x * 4
                        row[offset] = (value ushr 24).toByte(); row[offset + 1] = (value ushr 16).toByte()
                        row[offset + 2] = (value ushr 8).toByte(); row[offset + 3] = value.toByte()
                    }
                    digest.update(row)
                }
                current()
                return Source(width, height, owned, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
            }
        }
    }

    class Review private constructor(val source: Source, regions: List<Region>) {
        val regions: List<Region> = Collections.unmodifiableList(regions.toList())
        companion object {
            /** Floor lower edges/ceil upper edges so scaling never shrinks a detected box. */
            fun create(source: Source, originalWidth: Int, originalHeight: Int, detections: List<Box>, current: () -> Unit = {}): Review {
                current()
                require(originalWidth > 0 && originalHeight > 0) { "Invalid OCR input dimensions" }
                require(detections.size in 1..MAX_REGIONS) { "Review 1–100 detected text regions; use a smaller crop for dense text" }
                fun lower(value: Int, bound: Int, target: Int) = (value.coerceIn(0, bound).toLong() * target / bound).toInt()
                fun upper(value: Int, bound: Int, target: Int) = ((value.coerceIn(0, bound).toLong() * target + bound.toLong() - 1) / bound).toInt()
                val clipped = LinkedHashSet<Box>()
                for (box in detections) {
                    current()
                    val left = lower(box.left, originalWidth, source.width); val top = lower(box.top, originalHeight, source.height)
                    val right = upper(box.right, originalWidth, source.width); val bottom = upper(box.bottom, originalHeight, source.height)
                    if (left < right && top < bottom) clipped.add(Box(left, top, right, bottom))
                }
                require(clipped.isNotEmpty()) { "No detected text regions intersect this image" }
                current()
                return Review(source, clipped.mapIndexed { index, box -> Region(index + 1, box) })
            }
        }
        fun render(selected: Set<Int>, padding: Int, current: () -> Unit = {}): Rendered {
            current()
            require(padding in 0..MAX_PADDING) { "Use a border from 0 to 24 preview pixels" }
            require(selected.isNotEmpty() && selected.all { id -> regions.any { it.id == id } }) { "Choose detected text regions from this review" }
            val chosen = regions.filter { it.id in selected }
            val boxes = chosen.map { region -> region.box.let { Box((it.left - padding).coerceAtLeast(0), (it.top - padding).coerceAtLeast(0), (it.right + padding).coerceAtMost(source.width), (it.bottom + padding).coerceAtMost(source.height)) } }
            val output = source.copyPixels()
            for (y in 0 until source.height) {
                current()
                val intervals = boxes.filter { y >= it.top && y < it.bottom }.sortedBy { it.left }
                var left = -1; var right = -1
                fun flush() { if (left >= 0) java.util.Arrays.fill(output, y * source.width + left, y * source.width + right, BLACK) }
                for (box in intervals) {
                    if (left < 0) { left = box.left; right = box.right }
                    else if (box.left <= right) right = maxOf(right, box.right)
                    else { flush(); left = box.left; right = box.right }
                }
                flush()
            }
            current()
            return Rendered(source.width, source.height, source.fingerprint, chosen.map { it.id }, padding, output)
        }
    }

    class Rendered internal constructor(val width: Int, val height: Int, val sourceFingerprint: String, ids: List<Int>, val padding: Int, private val samples: IntArray) {
        val selectedIds: List<Int> = Collections.unmodifiableList(ids.toList())
        fun pixels() = samples.copyOf()
        /** Only IHDR/RGB IDAT/IEND. No EXIF, text chunks, layers, undo, source bytes or OCR strings. */
        fun png(current: () -> Unit = {}): ByteArray {
            current()
            val compressed = BoundedBytes(MAX_PNG_BYTES - 57, current)
            val deflater = Deflater(6)
            try {
                DeflaterOutputStream(compressed, deflater, 8192).use { zipped ->
                    val row = ByteArray(1 + width * 3)
                    for (y in 0 until height) {
                        current(); row[0] = 0
                        for (x in 0 until width) {
                            val color = samples[y * width + x]; val offset = 1 + x * 3
                            row[offset] = (color ushr 16).toByte(); row[offset + 1] = (color ushr 8).toByte(); row[offset + 2] = color.toByte()
                        }
                        zipped.write(row)
                    }
                }
            } finally { deflater.end() }
            current()
            val bytes = BoundedBytes(MAX_PNG_BYTES, current)
            DataOutputStream(bytes).use { out ->
                out.write(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))
                fun chunk(name: String, payload: ByteArray) {
                    current(); val type = name.toByteArray(Charsets.US_ASCII); val crc = CRC32(); crc.update(type); crc.update(payload)
                    out.writeInt(payload.size); out.write(type); out.write(payload); out.writeInt(crc.value.toInt()); current()
                }
                val header = ByteArrayOutputStream()
                DataOutputStream(header).use { h -> h.writeInt(width); h.writeInt(height); h.write(byteArrayOf(8, 2, 0, 0, 0)) }
                chunk("IHDR", header.toByteArray()); chunk("IDAT", compressed.toByteArray()); chunk("IEND", byteArrayOf())
            }
            current()
            return bytes.toByteArray()
        }
    }
    private class BoundedBytes(private val limit: Int, private val current: () -> Unit) : OutputStream() {
        private val bytes = ByteArrayOutputStream()
        override fun write(value: Int) { current(); require(bytes.size() < limit) { "PNG is too large" }; bytes.write(value) }
        override fun write(value: ByteArray, offset: Int, length: Int) { current(); require(length >= 0 && bytes.size().toLong() + length <= limit) { "PNG is too large" }; bytes.write(value, offset, length) }
        fun toByteArray() = bytes.toByteArray()
    }
}
