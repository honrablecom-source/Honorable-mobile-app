package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Additional imported source metadata; bytes remain in the original document provider. */
internal data class VideoSource(val id: String, val uri: String, val durationMs: Long) {
    init {
        require(id != "primary" && id.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(uri.startsWith("content://") && uri.toByteArray(Charsets.UTF_8).size <= 2048 && uri.none { it.isISOControl() })
        require(durationMs in 1..Int.MAX_VALUE.toLong())
    }
}

/** A local draft. No source bytes, account credentials or export destinations. */
internal data class VideoDraft(
    val sourceDurationMs: Long,
    val segments: List<VideoSegment>,
    val selectedClip: Int,
    val previewPositionMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val sources: List<VideoSource> = emptyList(),
) {
    init {
        require(sourceDurationMs > 0 && segments.size in 1..1000)
        require(sources.size <= 31 && sources.map { it.id }.distinct().size == sources.size)
        require(sources.map { it.uri }.distinct().size == sources.size)
        val durations = mapOf("primary" to sourceDurationMs) + sources.associate { it.id to it.durationMs }
        require(segments.all { it.endMs <= (durations[it.sourceId] ?: 0) })
        require(selectedClip in segments.indices)
        require(previewPositionMs in 0 until durations.getValue(segments[selectedClip].sourceId))
        val selected = segments[selectedClip]
        require(trimStartMs >= selected.startMs && trimEndMs <= selected.endMs && trimEndMs > trimStartMs)
    }
}

internal object VideoDraftCodec {
    private const val MAGIC = 0x484e5644 // HNVD
    private const val VERSION = 2
    private const val MAX_BYTES = 128_000

    fun encode(draft: VideoDraft): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            val version = if(draft.sources.isEmpty()) 1 else VERSION
            output.writeInt(version)
            output.writeLong(draft.sourceDurationMs)
            output.writeInt(draft.selectedClip)
            output.writeLong(draft.previewPositionMs)
            output.writeLong(draft.trimStartMs)
            output.writeLong(draft.trimEndMs)
            output.writeInt(draft.segments.size)
            draft.segments.forEach { clip ->
                output.writeLong(clip.startMs); output.writeLong(clip.endMs)
                output.writeFloat(clip.speed); output.writeFloat(clip.volume)
                if(version == 2) output.writeInt((listOf("primary") + draft.sources.map { it.id }).indexOf(clip.sourceId))
            }
            if(version == 2) {
                output.writeInt(draft.sources.size)
                draft.sources.forEach { source -> output.writeUTF(source.id); output.writeUTF(source.uri); output.writeLong(source.durationMs) }
            }
        }
        bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    /** Reject corrupt, incompatible or changed-duration sources before replacing current edits. */
    fun decode(bytes: ByteArray, sourceDurationMs: Long): VideoDraft? = runCatching {
        require(bytes.size in 1..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC)
            val version = input.readInt(); require(version in 1..VERSION)
            require(input.readLong() == sourceDurationMs)
            val selected = input.readInt()
            val position = input.readLong()
            val start = input.readLong()
            val end = input.readLong()
            val count = input.readInt()
            require(count in 1..1000)
            val indices = mutableListOf<Int>()
            val raw = List(count) {
                VideoSegment(input.readLong(), input.readLong(), input.readFloat(), input.readFloat()).also {
                    indices.add(if(version == 2) input.readInt() else 0)
                }
            }
            val sources = if(version == 2) {
                val size = input.readInt(); require(size in 1..31)
                List(size) { VideoSource(input.readUTF(), input.readUTF(), input.readLong()) }
            } else emptyList()
            val ids = listOf("primary") + sources.map { it.id }
            val segments = raw.mapIndexed { index, clip -> clip.copy(sourceId = ids.getOrNull(indices[index]) ?: error("Unknown clip source")) }
            require(input.available() == 0)
            VideoDraft(sourceDurationMs, segments, selected, position, start, end, sources)
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
