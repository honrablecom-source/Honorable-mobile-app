package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** A single-source local draft. No source bytes, account credentials or export destinations. */
internal data class VideoDraft(
    val sourceDurationMs: Long,
    val segments: List<VideoSegment>,
    val selectedClip: Int,
    val previewPositionMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
) {
    init {
        require(sourceDurationMs > 0 && segments.size in 1..1000)
        require(segments.all { it.endMs <= sourceDurationMs })
        require(selectedClip in segments.indices && previewPositionMs in 0 until sourceDurationMs)
        val selected = segments[selectedClip]
        require(trimStartMs >= selected.startMs && trimEndMs <= selected.endMs && trimEndMs > trimStartMs)
    }
}

internal object VideoDraftCodec {
    private const val MAGIC = 0x484e5644 // HNVD
    private const val VERSION = 1
    private const val MAX_BYTES = 32_000

    fun encode(draft: VideoDraft): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeLong(draft.sourceDurationMs)
            output.writeInt(draft.selectedClip)
            output.writeLong(draft.previewPositionMs)
            output.writeLong(draft.trimStartMs)
            output.writeLong(draft.trimEndMs)
            output.writeInt(draft.segments.size)
            draft.segments.forEach { clip ->
                output.writeLong(clip.startMs); output.writeLong(clip.endMs)
                output.writeFloat(clip.speed); output.writeFloat(clip.volume)
            }
        }
        bytes.toByteArray()
    }

    /** Reject corrupt, incompatible or changed-duration sources before replacing current edits. */
    fun decode(bytes: ByteArray, sourceDurationMs: Long): VideoDraft? = runCatching {
        require(bytes.size in 1..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            require(input.readLong() == sourceDurationMs)
            val selected = input.readInt()
            val position = input.readLong()
            val start = input.readLong()
            val end = input.readLong()
            val count = input.readInt()
            require(count in 1..1000)
            val segments = List(count) {
                VideoSegment(input.readLong(), input.readLong(), input.readFloat(), input.readFloat())
            }
            require(input.available() == 0)
            VideoDraft(sourceDurationMs, segments, selected, position, start, end)
        }
    }.getOrNull()

    /** Account, API and selected source isolate drafts; private identifiers never become filenames. */
    fun storageName(accountId: String, apiUrl: String, sourceUri: String): String {
        require(accountId.isNotBlank() && apiUrl.isNotBlank() && sourceUri.isNotBlank())
        val identity = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                for (part in listOf(accountId, apiUrl, sourceUri)) {
                    val value = part.toByteArray(Charsets.UTF_8)
                    out.writeInt(value.size); out.write(value)
                }
            }
            bytes.toByteArray()
        }
        return "video-draft-" + MessageDigest.getInstance("SHA-256").digest(identity)
            .joinToString("") { "%02x".format(it) }
    }
}
