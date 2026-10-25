package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

enum class PhotoCreativeTool(val title: String) {
    VIBRANCE("Vibrance"), HUE("Hue shift"), FADE("Matte fade"), GRAIN("Film grain"),
    POSTERIZE("Posterize"), THRESHOLD("Threshold"), SPLIT_TONE("Split toning"), CHANNELS("RGB channel balance")
}

/** Deterministic local color tools. No model, source URI or random seed is stored. */
data class PhotoCreative(
    val vibrance: Float = 0f,
    val hue: Float = 0f,
    val fade: Float = 0f,
    val grain: Float = 0f,
    val posterize: Int = 0,
    val threshold: Float = -1f,
    val splitTone: Float = 0f,
    val red: Float = 0f,
    val green: Float = 0f,
    val blue: Float = 0f,
) {
    init {
        for (v in listOf(vibrance, hue, red, green, blue)) require(v.isFinite() && v in -1f..1f)
        for (v in listOf(fade, grain, splitTone)) require(v.isFinite() && v in 0f..1f)
        require(posterize == 0 || posterize in 2..16)
        require(threshold.isFinite() && (threshold == -1f || threshold in 0f..1f))
    }
    val isIdentity: Boolean get() = this == PhotoCreative()

    internal fun write(out: DataOutputStream) {
        for (v in listOf(vibrance, hue, fade, grain)) out.writeFloat(v)
        out.writeInt(posterize)
        for (v in listOf(threshold, splitTone, red, green, blue)) out.writeFloat(v)
    }
    companion object {
        internal fun read(input: DataInputStream) = PhotoCreative(input.readFloat(), input.readFloat(),
            input.readFloat(), input.readFloat(), input.readInt(), input.readFloat(),
            input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())
    }
}

/** One bounded row pass; transparent pixels and alpha are preserved. */
class PhotoCreativeKernel(private val edit: PhotoCreative, private val width: Int, private val height: Int) {
    init { require(width > 0 && height > 0) }
    private val identity = edit.isIdentity
    private val redGain = 2.0.pow(edit.red.toDouble())
    private val greenGain = 2.0.pow(edit.green.toDouble())
    private val blueGain = 2.0.pow(edit.blue.toDouble())
    private val angle = edit.hue * PI
    private val cosine = cos(angle)
    private val sine = sin(angle)
    fun apply(pixel: Int, x: Int, y: Int): Int {
        if (identity || pixel ushr 24 == 0) return pixel
        var r = ((pixel ushr 16) and 255).toDouble() / 255
        var g = ((pixel ushr 8) and 255).toDouble() / 255
        var b = (pixel and 255).toDouble() / 255
        fun luma() = .2126 * r + .7152 * g + .0722 * b
        if (edit.vibrance != 0f) {
            val gray = luma()
            val saturation = maxOf(r, g, b) - minOf(r, g, b)
            val gain = 1 + edit.vibrance * (1 - saturation)
            r = gray + (r - gray) * gain; g = gray + (g - gray) * gain; b = gray + (b - gray) * gain
        }
        if (edit.hue != 0f) {
            // Rotate chroma in YIQ, leaving luminance unchanged before gamut clipping.
            val yy = .299 * r + .587 * g + .114 * b
            val ii = .596 * r - .274 * g - .322 * b
            val qq = .211 * r - .523 * g + .312 * b
            val i = ii * cosine - qq * sine; val q = ii * sine + qq * cosine
            r = yy + .956 * i + .621 * q; g = yy - .272 * i - .647 * q; b = yy - 1.106 * i + 1.703 * q
        }
        r *= redGain; g *= greenGain; b *= blueGain
        if (edit.splitTone > 0f) {
            val light = luma().coerceIn(0.0, 1.0)
            val amount = edit.splitTone * .20
            r += amount * (2 * light - 1); b += amount * (1 - 2 * light)
            g += amount * .15 * light
        }
        if (edit.fade > 0f) {
            val floor = edit.fade * .22
            r = floor + r * (1 - floor); g = floor + g * (1 - floor); b = floor + b * (1 - floor)
        }
        if (edit.grain > 0f) {
            // Fixed normalized coordinates keep grain stable between preview and export sizes.
            val xx = (x.toLong() * 512 / width).toInt(); val yy = (y.toLong() * 512 / height).toInt()
            var hash = xx * 374761393 + yy * 668265263 + 1442695041
            hash = (hash xor (hash ushr 13)) * 1274126177
            val noise = (((hash xor (hash ushr 16)) and 65535) / 65535.0 - .5) * edit.grain * .22
            r += noise; g += noise; b += noise
        }
        if (edit.posterize != 0) {
            val steps = edit.posterize - 1.0
            r = round(r.coerceIn(0.0, 1.0) * steps) / steps
            g = round(g.coerceIn(0.0, 1.0) * steps) / steps
            b = round(b.coerceIn(0.0, 1.0) * steps) / steps
        }
        if (edit.threshold >= 0f) {
            val value = if (luma() >= edit.threshold) 1.0 else 0.0
            r = value; g = value; b = value
        }
        fun channel(value: Double) = (value.coerceIn(0.0, 1.0) * 255).roundToInt()
        return (pixel and -0x1000000) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}
