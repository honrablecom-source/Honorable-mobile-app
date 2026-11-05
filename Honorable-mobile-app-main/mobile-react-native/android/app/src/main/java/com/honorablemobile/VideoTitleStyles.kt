package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.text.Normalizer

internal data class NamedVideoTitleStyle(val name: String, val style: VideoTitleStyle) {
    init {
        require(name == name.trim() && name.isNotEmpty() && name.codePointCount(0, name.length) <= 40 && name.none { it.isISOControl() }) { "Use a style name of 1–40 characters" }
        val bytes = name.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 160 && bytes.toString(Charsets.UTF_8) == name) { "Use a valid style name" }
    }
    fun applyTo(clip: VideoSegment): VideoSegment = clip.copy(titleStyle = style)
}

internal class VideoTitleStyleCatalog(entries: List<NamedVideoTitleStyle> = emptyList()) {
    val styles: List<NamedVideoTitleStyle> = Collections.unmodifiableList(entries.toList())
    init {
        require(styles.size <= 20) { "Use up to 20 saved video styles" }
        require(styles.map { Normalizer.normalize(it.name, Normalizer.Form.NFKC).lowercase(Locale.ROOT) }.distinct().size == styles.size) { "Choose an unused style name" }
    }
    fun add(value: NamedVideoTitleStyle): VideoTitleStyleCatalog = VideoTitleStyleCatalog(styles + value)
    fun remove(value: NamedVideoTitleStyle): VideoTitleStyleCatalog {
        require(styles.find { it.name == value.name } == value) { "Saved styles changed. Open the shelf again." }
        return VideoTitleStyleCatalog(styles.filter { it.name != value.name })
    }
    override fun equals(other: Any?) = other is VideoTitleStyleCatalog && styles == other.styles
    override fun hashCode() = styles.hashCode()
}

/** Versioned title-appearance-only envelope. No media, source URI, account/token or timing data. */
internal object VideoTitleStyleCodec {
    const val MAX_BYTES = 8192
    private const val MAGIC = 0x4e565453
    fun encode(catalog: VideoTitleStyleCatalog): ByteArray {
        val payload = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeByte(1); out.writeByte(catalog.styles.size)
            for (style in catalog.styles) {
                val name = style.name.toByteArray(Charsets.UTF_8)
                out.writeShort(name.size); out.write(name)
                with(style.style) { out.writeByte(position); out.writeFloat(size); out.writeBoolean(light); out.writeBoolean(plate) }
            }
        } }.toByteArray()
        return ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(payload.size); out.write(MessageDigest.getInstance("SHA-256").digest(payload)); out.write(payload)
        } }.toByteArray().also { require(it.size <= MAX_BYTES) { "Video style shelf is too large" } }
    }
    private fun boolean(input: DataInputStream): Boolean {
        val value = input.readUnsignedByte()
        require(value in 0..1) { "Invalid title style flag" }
        return value == 1
    }
    fun decode(bytes: ByteArray): VideoTitleStyleCatalog {
        require(bytes.size in 42..MAX_BYTES) { "Invalid video style shelf size" }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC) { "Unsupported video style shelf" }
        val size = input.readInt()
        require(size in 2..MAX_BYTES && input.available() == size + 32) { "Invalid video style shelf length" }
        val digest = ByteArray(32).also { input.readFully(it) }
        val payload = ByteArray(size).also { input.readFully(it) }
        require(MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(payload))) { "Video style shelf is damaged" }
        val data = DataInputStream(ByteArrayInputStream(payload))
        require(data.readUnsignedByte() == 1) { "Unsupported video style shelf version" }
        val count = data.readUnsignedByte(); require(count <= 20)
        val styles = List(count) {
            val length = data.readUnsignedShort(); require(length in 1..160)
            val encoded = ByteArray(length).also { data.readFully(it) }
            val name = encoded.toString(Charsets.UTF_8)
            require(name.toByteArray(Charsets.UTF_8).contentEquals(encoded)) { "Invalid video style name encoding" }
            NamedVideoTitleStyle(name, VideoTitleStyle(data.readUnsignedByte(), data.readFloat(), boolean(data), boolean(data)))
        }
        require(data.available() == 0) { "Unexpected video style shelf data" }
        return VideoTitleStyleCatalog(styles)
    }
}
