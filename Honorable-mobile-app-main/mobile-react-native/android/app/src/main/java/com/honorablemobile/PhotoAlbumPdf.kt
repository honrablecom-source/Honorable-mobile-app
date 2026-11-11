package com.honorablemobile

import java.io.OutputStream
import java.security.MessageDigest

/** One orientation-corrected photo per A4 page; never crops or changes source bytes. */
internal object PhotoAlbumPdf {
    const val MAX_PHOTOS = 8
    const val MAX_OUTPUT_BYTES = 16L * 1024 * 1024
    const val MARGIN = 24

    data class Page(
        val width: Int,
        val height: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val imageWidth get() = right - left
        val imageHeight get() = bottom - top
    }

    fun requireCount(count: Int) {
        require(count in 2..MAX_PHOTOS) { "Select two to eight different photo originals" }
    }

    /** A failed native write cannot later be accepted as a complete PDF. */
    class WriteBudget(private val output: OutputStream, private val limit: Long = MAX_OUTPUT_BYTES, private val current: () -> Unit = {}) : OutputStream() {
        init { require(limit in 1..MAX_OUTPUT_BYTES) }
        var bytes = 0L; private set
        private var failed = false
        private var completed: String? = null
        private val digest = MessageDigest.getInstance("SHA-256")
        private fun writeChecked(length: Int, action: () -> Unit) {
            try {
                check(!failed && completed == null) { "PDF output is no longer writable" }; current()
                check(length >= 0 && bytes + length <= limit) { "PDF exceeds its output limit. Try fewer photos" }
                action(); bytes += length; current()
            } catch (error: Throwable) { failed = true; throw error }
        }
        override fun write(value: Int) = writeChecked(1) { output.write(value); digest.update(value.toByte()) }
        override fun write(data: ByteArray, offset: Int, length: Int) {
            if (offset < 0 || length < 0 || offset > data.size - length) { failed = true; throw IndexOutOfBoundsException("Invalid PDF write range") }
            writeChecked(length) { output.write(data, offset, length); digest.update(data, offset, length) }
        }
        fun finish(): String {
            try {
                check(!failed && bytes > 0) { "PDF output did not complete" }; current()
                return completed ?: digest.digest().joinToString("") { "%02x".format(it) }.also { completed = it }
            } catch (error: Throwable) { failed = true; throw error }
        }
    }

    /** Dimensions are corrected for EXIF first; a square uses a portrait page. */
    fun page(width: Int, height: Int): Page {
        require(width in 1..32768 && height in 1..32768 && width.toLong() * height <= 100_000_000) {
            "A selected photo exceeds the PDF dimension limit"
        }
        val landscape = width > height
        val pageWidth = if (landscape) 842 else 595
        val pageHeight = if (landscape) 595 else 842
        val scale = minOf((pageWidth - 2 * MARGIN).toDouble() / width, (pageHeight - 2 * MARGIN).toDouble() / height)
        val imageWidth = width * scale
        val imageHeight = height * scale
        val left = (pageWidth - imageWidth) / 2
        val top = (pageHeight - imageHeight) / 2
        return Page(pageWidth, pageHeight, left.toFloat(), top.toFloat(), (left + imageWidth).toFloat(), (top + imageHeight).toFloat())
    }
}
