package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Guest-only envelope: no account IDs, credentials, export destination or original media bytes. */
internal object GuestVideoProjectCodec {
    private const val MAGIC = 0x48475650 // HGVP
    private const val VERSION = 1
    const val MAX_BYTES = 256_076
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun sourceHash(source: String): ByteArray {
        require(source.startsWith("content://") && source.none { it.isISOControl() })
        val bytes = source.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 2048 && bytes.toString(Charsets.UTF_8) == source)
        return hash(bytes)
    }
    fun storageName(source: String) = "guest-video-project-" + sourceHash(source).joinToString("") { "%02x".format(it) } + ".bin"
    fun encode(source: String, draft: VideoDraft): ByteArray {
        val identity = sourceHash(source)
        val payload = VideoDraftCodec.encode(draft)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC); out.writeInt(VERSION); out.write(identity)
                out.writeInt(payload.size); out.write(hash(payload)); out.write(payload)
            }
            bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
        }
    }
    fun decode(bytes: ByteArray, source: String, durationMs: Long): VideoDraft {
        require(bytes.size in 77..MAX_BYTES) { "Saved guest project is damaged or unsupported" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            val identity = ByteArray(32); input.readFully(identity)
            require(MessageDigest.isEqual(identity, sourceHash(source))) { "Open the original video used for this guest project" }
            val count = input.readInt(); require(count in 1..256_000 && count == input.available() - 32)
            val digest = ByteArray(32); input.readFully(digest)
            val payload = ByteArray(count); input.readFully(payload)
            require(input.available() == 0 && MessageDigest.isEqual(digest, hash(payload))) { "Saved guest project is damaged" }
            return requireNotNull(VideoDraftCodec.decode(payload, durationMs)) { "Saved guest project is incompatible or the original video changed" }
        }
    }
}

internal object GuestVideoProjectMedia {
    private val permissionTransactionLock = Any()
    fun originals(primary: String, draft: VideoDraft): Set<String> =
        linkedSetOf(primary).apply {
            addAll(draft.sources.map { it.uri }); draft.soundtrack?.let { add(it.uri) }
            addAll(draft.segments.mapNotNull { it.captionSourceUri })
        }

    /** Call on save and restore; persistable permission alone does not prove the file still exists. */
    fun validate(primary: String, draft: VideoDraft, hasRead: (String) -> Boolean,
        videoDuration: (String) -> Long, audioDuration: (String) -> Long,
        subtitleReadable: (String, Long) -> Unit) {
        require(draft.sources.none { it.uri == primary }) { "Conflicting original video references" }
        require(originals(primary, draft).all(hasRead)) { "A project original no longer has durable document access" }
        require(videoDuration(primary) == draft.sourceDurationMs) { "Original video changed or unavailable" }
        draft.sources.forEach { require(videoDuration(it.uri) == it.durationMs) { "Imported video changed or unavailable" } }
        draft.soundtrack?.let { require(audioDuration(it.uri) == it.durationMs) { "Soundtrack changed or unavailable" } }
        val durations = mapOf("primary" to draft.sourceDurationMs) + draft.sources.associate { it.id to it.durationMs }
        draft.segments.mapNotNull { clip -> clip.captionSourceUri?.let { it to durations.getValue(clip.sourceId) } }
            .distinct().forEach { (uri, duration) -> subtitleReadable(uri, duration) }
    }

    /** Keep prior grants; roll back only grants acquired by a failed save operation. */
    fun <T> withDurableAccess(originals: Set<String>, hasRead: (String) -> Boolean,
        takeRead: (String) -> Unit, releaseRead: (String) -> Unit,
        requireCurrent: () -> Unit, action: () -> T): T = synchronized(permissionTransactionLock) {
        val acquired = mutableListOf<String>()
        try {
            for (uri in originals) {
                requireCurrent()
                if (!hasRead(uri)) { takeRead(uri); acquired.add(uri) }
                require(hasRead(uri)) { "This provider cannot keep project access. Choose the original in Files again." }
            }
            requireCurrent()
            return action()
        } catch (error: Throwable) {
            acquired.asReversed().forEach { uri -> runCatching { releaseRead(uri) }.exceptionOrNull()?.let(error::addSuppressed) }
            throw error
        }
    }
}
