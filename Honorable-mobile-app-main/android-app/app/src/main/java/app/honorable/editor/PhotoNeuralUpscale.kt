package app.honorable.editor

import java.security.MessageDigest
import java.util.Collections
import kotlin.math.roundToInt

/** Whole working-image x2 reconstruction. No resize, crop, interpolation fallback or original-file write. */
object PhotoNeuralUpscale {
    const val MAX_EDGE = 1024
    const val SCALE = 2
    const val WINDOW = 8
    const val CORE = 128
    const val HALO = 128
    const val MAX_TILE_EDGE = CORE + HALO * 2

    fun interface Network {
        /** RGB NCHW /255 input; complete RGB NCHW x2 output. The caller owns both arrays. */
        fun infer(width: Int, height: Int, rgb: FloatArray, current: () -> Unit): FloatArray
    }

    class Source private constructor(val width: Int, val height: Int, private val rgb: IntArray, val fingerprint: String) {
        internal fun pixel(x: Int, y: Int) = rgb[y * width + x]
        fun pixels() = rgb.copyOf()
        companion object {
            /** Explicit white matte for transparency; hidden RGB and original metadata are excluded. */
            fun capture(width: Int, height: Int, pixels: IntArray, current: () -> Unit = {}): Source {
                current()
                require(width in 1..MAX_EDGE && height in 1..MAX_EDGE && pixels.size.toLong() == width.toLong() * height) {
                    "Use a working image up to 1024 pixels per edge"
                }
                val owned = pixels.copyOf()
                for (y in 0 until height) {
                    current()
                    for (x in 0 until width) {
                        val index = y * width + x; val pixel = owned[index]; val alpha = pixel ushr 24
                        fun matte(shift: Int) = (((pixel ushr shift) and 255) * alpha + 255 * (255 - alpha) + 127) / 255
                        owned[index] = -0x1000000 or (matte(16) shl 16) or (matte(8) shl 8) or matte(0)
                    }
                }
                return Source(width, height, owned, fingerprint(width, height, owned, current))
            }
        }
    }

    class Result internal constructor(val width: Int, val height: Int, val sourceFingerprint: String, private val rgb: IntArray) {
        fun pixels() = rgb.copyOf()
    }

    data class Tile(val coreLeft: Int, val coreTop: Int, val coreRight: Int, val coreBottom: Int,
                    val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** Official processor adds a symmetric band even when already divisible by eight. */
    internal fun padded(size: Int): Int { require(size in 1..MAX_EDGE); return (size / WINDOW + 1) * WINDOW }
    internal fun symmetric(coordinate: Int, size: Int): Int {
        require(size > 0 && coordinate >= 0)
        val value = coordinate % (size * 2)
        return if (value < size) value else size * 2 - 1 - value
    }

    fun tiles(width: Int, height: Int): List<Tile> {
        val pw = padded(width); val ph = padded(height)
        if (pw <= MAX_TILE_EDGE && ph <= MAX_TILE_EDGE) return listOf(Tile(0, 0, width, height, 0, 0, pw, ph))
        val result = ArrayList<Tile>()
        for (y in 0 until height step CORE) for (x in 0 until width step CORE) {
            val right = minOf(width, x + CORE); val bottom = minOf(height, y + CORE)
            // Round the last core outward onto the global window grid before adding context.
            val gridRight = ((right + WINDOW - 1) / WINDOW) * WINDOW
            val gridBottom = ((bottom + WINDOW - 1) / WINDOW) * WINDOW
            result.add(Tile(x, y, right, bottom, maxOf(0, x - HALO), maxOf(0, y - HALO),
                minOf(pw, gridRight + HALO), minOf(ph, gridBottom + HALO)))
        }
        return Collections.unmodifiableList(result)
    }

    fun reconstruct(source: Source, network: Network, current: () -> Unit = {}, phase: (Int, Int) -> Unit = { _, _ -> }): Result {
        current()
        val jobs = tiles(source.width, source.height)
        val width = source.width * SCALE; val height = source.height * SCALE
        val pixels = IntArray(width * height)
        for ((index, tile) in jobs.withIndex()) {
            current(); phase(index + 1, jobs.size); current()
            val plane = tile.width * tile.height
            val input = FloatArray(plane * 3)
            for (y in 0 until tile.height) {
                current()
                val sy = symmetric(tile.top + y, source.height)
                for (x in 0 until tile.width) {
                    val color = source.pixel(symmetric(tile.left + x, source.width), sy)
                    val offset = y * tile.width + x
                    input[offset] = ((color ushr 16) and 255) / 255f
                    input[plane + offset] = ((color ushr 8) and 255) / 255f
                    input[plane * 2 + offset] = (color and 255) / 255f
                }
            }
            current()
            val output = network.infer(tile.width, tile.height, input, current)
            current()
            val outputPlane = plane * SCALE * SCALE
            require(output.size == outputPlane * 3) { "AI output has unexpected dimensions; your image was kept" }
            for (row in 0 until tile.height * SCALE) {
                current()
                for (x in 0 until tile.width * SCALE) {
                    val offset = row * tile.width * SCALE + x
                    require(output[offset].isFinite() && output[outputPlane + offset].isFinite() && output[outputPlane * 2 + offset].isFinite()) {
                        "AI produced invalid pixels; your image was kept"
                    }
                }
            }
            for (y in tile.coreTop * SCALE until tile.coreBottom * SCALE) {
                current()
                for (x in tile.coreLeft * SCALE until tile.coreRight * SCALE) {
                    val offset = (y - tile.top * SCALE) * tile.width * SCALE + x - tile.left * SCALE
                    fun channel(planeIndex: Int) = (output[planeIndex * outputPlane + offset].coerceIn(0f, 1f) * 255f).roundToInt()
                    pixels[y * width + x] = -0x1000000 or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
                }
            }
        }
        current()
        return Result(width, height, source.fingerprint, pixels)
    }

    private fun fingerprint(width: Int, height: Int, pixels: IntArray, current: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("NOBLE-NEURAL-UPSCALE-RGB-1".toByteArray(Charsets.US_ASCII))
        fun number(value: Int) { digest.update(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())) }
        number(width); number(height)
        for (y in 0 until height) {
            current()
            for (x in 0 until width) number(pixels[y * width + x])
        }
        current()
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
