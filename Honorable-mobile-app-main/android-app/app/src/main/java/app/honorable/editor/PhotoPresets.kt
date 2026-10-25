package app.honorable.editor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Only global deterministic adjustments: never a source, mask, framing, local anchor or text. */
data class PhotoAdjustmentPreset(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val exposure: Float = 0f,
    val curve: PhotoToneCurve = PhotoToneCurve(),
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val vignette: Float = 0f,
    val sharpen: Float = 0f,
    val creative: PhotoCreative = PhotoCreative(),
    val effects: PhotoEffects = PhotoEffects(),
) {
    init {
        // Use precisely the same bounds as the existing editor/render model.
        PhotoEdit(brightness = brightness, contrast = contrast, saturation = saturation, exposure = exposure,
            curve = curve, finish = PhotoFinish(warmth = warmth, tint = tint, shadows = shadows,
                highlights = highlights, vignette = vignette, sharpen = sharpen))
    }

    /** Replace adjustment values, retaining this photo's framing, local light, people and text. */
    fun applyTo(target: PhotoEdit): PhotoEdit = target.copy(
        brightness = brightness, contrast = contrast, saturation = saturation, exposure = exposure,
        curve = curve, creative = creative, effects = effects, finish = target.finish.copy(warmth = warmth, tint = tint, shadows = shadows,
            highlights = highlights, vignette = vignette, sharpen = sharpen),
    )

    companion object {
        fun capture(edit: PhotoEdit) = PhotoAdjustmentPreset(edit.brightness, edit.contrast,
            edit.saturation, edit.exposure, edit.curve, edit.finish.warmth, edit.finish.tint,
            edit.finish.shadows, edit.finish.highlights, edit.finish.vignette, edit.finish.sharpen, edit.creative, edit.effects)
    }
}

data class NamedPhotoPreset(val name: String, val adjustments: PhotoAdjustmentPreset) {
    init {
        require(name == name.trim() && name.isNotEmpty() && name.length <= 40)
        require(name.none { it.isISOControl() })
        val bytes = name.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 120 && bytes.toString(Charsets.UTF_8) == name)
    }
    internal val key: String get() = name.lowercase(Locale.ROOT)
}

class PhotoPresetCatalog(entries: List<NamedPhotoPreset> = emptyList()) {
    val entries: List<NamedPhotoPreset> = java.util.Collections.unmodifiableList(entries.toList())
    init {
        require(this.entries.size <= MAX_ENTRIES)
        require(this.entries.map { it.key }.distinct().size == this.entries.size)
    }
    fun find(name: String): NamedPhotoPreset? = entries.find { it.key == name.lowercase(Locale.ROOT) }
    fun add(preset: NamedPhotoPreset): PhotoPresetCatalog {
        require(find(preset.name) == null) { "A look with this name already exists" }
        return PhotoPresetCatalog(entries + preset)
    }
    fun remove(name: String): PhotoPresetCatalog {
        val existing = requireNotNull(find(name)) { "Saved look is unavailable" }
        return PhotoPresetCatalog(entries.filter { it !== existing })
    }
    override fun equals(other: Any?): Boolean = other is PhotoPresetCatalog && entries == other.entries
    override fun hashCode(): Int = entries.hashCode()
    companion object { const val MAX_ENTRIES = 20 }
}

/** Bounded format; reads legacy v1, creative v2 and effects v3 without partial acceptance. */
object PhotoPresetCodec {
    const val MAX_BYTES = 8192
    private const val MAGIC = 0x48505031
    private const val VERSION = 3

    fun encode(catalog: PhotoPresetCatalog): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            val version=if(catalog.entries.any{it.adjustments.effects!=PhotoEffects()})VERSION else if(catalog.entries.any{!it.adjustments.creative.isIdentity})2 else 1
            out.writeInt(MAGIC); out.writeInt(version); out.writeInt(catalog.entries.size)
            catalog.entries.forEach { preset ->
                val name = preset.name.toByteArray(Charsets.UTF_8)
                out.writeInt(name.size); out.write(name)
                with(preset.adjustments) {
                    listOf(brightness, contrast, saturation, exposure, curve.shadows, curve.midtones,
                        curve.highlights, warmth, tint, shadows, highlights, vignette, sharpen)
                        .forEach(out::writeFloat)
                    if(version>=2)creative.write(out)
                    if(version>=3)effects.write(out)
                }
            }
        }
        return bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): PhotoPresetCatalog {
        require(bytes.size in 12..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC)
            val version=input.readInt();require(version in 1..VERSION)
            val count = input.readInt(); require(count in 0..PhotoPresetCatalog.MAX_ENTRIES)
            val entries = List(count) {
                val length = input.readInt(); require(length in 1..120)
                val nameBytes = ByteArray(length); input.readFully(nameBytes)
                val name = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(nameBytes)).toString()
                val brightness = input.readFloat(); val contrast = input.readFloat()
                val saturation = input.readFloat(); val exposure = input.readFloat()
                val curve = PhotoToneCurve(input.readFloat(), input.readFloat(), input.readFloat())
                val adjustments=PhotoAdjustmentPreset(brightness, contrast, saturation, exposure, curve,
                    input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())
                val creative=if(version>=2)PhotoCreative.read(input)else PhotoCreative()
                val effects=if(version>=3)PhotoEffects.read(input)else PhotoEffects()
                NamedPhotoPreset(name,adjustments.copy(creative=creative,effects=effects))
            }
            require(input.available() == 0)
            return PhotoPresetCatalog(entries)
        }
    }
}

object PhotoPresetNamespace {
    const val GUEST = "guest-basic"
    fun account(account: String, api: String): String =
        "account-" + PhotoSessionCodec.scope(account, api, "photo-adjustment-presets-v1")
    fun valid(value: String): Boolean = value == GUEST || value.matches(Regex("account-[0-9a-f]{64}"))
}
