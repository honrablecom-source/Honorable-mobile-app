package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

internal data class NamedVideoColorLook(val name: String, val grade: VideoGrade) {
    init {
        require(name == name.trim() && name.isNotEmpty() && name.length <= 40 && name.none { it.isISOControl() }) { "Use a look name of 1–40 characters" }
        val bytes = name.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 120 && bytes.toString(Charsets.UTF_8) == name) { "Use a valid look name" }
    }
    fun applyTo(clip: VideoSegment): VideoSegment = clip.copy(grade = grade)
}

internal class VideoColorLookCatalog(entries: List<NamedVideoColorLook> = emptyList()) {
    val looks: List<NamedVideoColorLook> = Collections.unmodifiableList(entries.toList())
    init {
        require(looks.size <= 20) { "Use up to 20 saved video looks" }
        require(looks.map { it.name.lowercase(Locale.ROOT) }.distinct().size == looks.size) { "Choose an unused look name" }
    }
    fun add(value: NamedVideoColorLook): VideoColorLookCatalog = VideoColorLookCatalog(looks + value)
    fun remove(value: NamedVideoColorLook): VideoColorLookCatalog {
        require(looks.find { it.name == value.name } == value) { "Saved looks changed. Open the shelf again." }
        return VideoColorLookCatalog(looks.filter { it.name != value.name })
    }
    override fun equals(other: Any?) = other is VideoColorLookCatalog && looks == other.looks
    override fun hashCode() = looks.hashCode()
}

/** Versioned color-only envelope. No media, source URI, account/token or timing data. */
internal object VideoColorLookCodec {
    const val MAX_BYTES = 8192
    private const val MAGIC = 0x4e564c4b
    fun encode(catalog: VideoColorLookCatalog): ByteArray {
        val payload = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeByte(1); out.writeByte(catalog.looks.size)
            for (look in catalog.looks) {
                val name = look.name.toByteArray(Charsets.UTF_8)
                out.writeByte(name.size); out.write(name)
                with(look.grade) { out.writeFloat(exposure); out.writeFloat(contrast); out.writeFloat(saturation); out.writeFloat(warmth); out.writeFloat(tint) }
            }
        } }.toByteArray()
        return ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(payload.size); out.write(MessageDigest.getInstance("SHA-256").digest(payload)); out.write(payload)
        } }.toByteArray().also { require(it.size <= MAX_BYTES) { "Video look shelf is too large" } }
    }
    fun decode(bytes: ByteArray): VideoColorLookCatalog {
        require(bytes.size in 42..MAX_BYTES) { "Invalid video look shelf size" }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC) { "Unsupported video look shelf" }
        val size = input.readInt()
        require(size in 2..MAX_BYTES && input.available() == size + 32) { "Invalid video look shelf length" }
        val digest = ByteArray(32).also { input.readFully(it) }
        val payload = ByteArray(size).also { input.readFully(it) }
        require(MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(payload))) { "Video look shelf is damaged" }
        val data = DataInputStream(ByteArrayInputStream(payload))
        require(data.readUnsignedByte() == 1) { "Unsupported video look shelf version" }
        val count = data.readUnsignedByte(); require(count <= 20)
        val looks = List(count) {
            val length = data.readUnsignedByte(); require(length in 1..120)
            val encoded = ByteArray(length).also { data.readFully(it) }
            val name = encoded.toString(Charsets.UTF_8)
            require(name.toByteArray(Charsets.UTF_8).contentEquals(encoded)) { "Invalid video look name encoding" }
            NamedVideoColorLook(name, VideoGrade(data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat()))
        }
        require(data.available() == 0) { "Unexpected video look shelf data" }
        return VideoColorLookCatalog(looks)
    }
}
