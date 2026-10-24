package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Additional imported source metadata; bytes remain in the original document provider. */
internal data class VideoSource(val id: String, val uri: String, val durationMs: Long) {
    init {
        require(id != "primary" && !id.startsWith("__") && id.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(uri.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == uri)
        require(uri.startsWith("content://") && uri.toByteArray(Charsets.UTF_8).size <= 2048 && uri.none { it.isISOControl() })
        require(durationMs in 1..Int.MAX_VALUE.toLong())
    }
}

/** Project-wide looping soundtrack; imported bytes stay with the document provider. */
internal data class VideoSoundtrack(val uri: String, val durationMs: Long, val volume: Float = .25f) {
    init {
        require(uri.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == uri)
        require(uri.startsWith("content://") && uri.toByteArray(Charsets.UTF_8).size <= 2048 && uri.none { it.isISOControl() })
        require(durationMs in 1..Int.MAX_VALUE.toLong() && volume.isFinite() && volume in 0f..1f)
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
    val soundtrack: VideoSoundtrack? = null,
) {
    init {
        VideoDraftCodec.requireRecoverableCaptions(segments)
        require(segments.mapNotNull { it.captionSourceUri }.distinct().size <= 32)
        require(segments.sumOf { it.captions.size } <= VideoCaptions.MAX_PROJECT_CUES)
        require(segments.sumOf { clip -> clip.captions.sumOf { it.text.toByteArray(Charsets.UTF_8).size } } <= VideoCaptions.MAX_PROJECT_TEXT_BYTES)
        require(segments.sumOf { it.title.toByteArray(Charsets.UTF_8).size } <= 128_000)
        require(sourceDurationMs > 0 && segments.size in 1..1000)
        require(sources.size <= 31 && sources.map { it.id }.distinct().size == sources.size)
        require(sources.map { it.uri }.distinct().size == sources.size)
        val durations = mapOf("primary" to sourceDurationMs) + sources.associate { it.id to it.durationMs }
        require(segments.all { clip -> clip.endMs <= (durations[clip.sourceId] ?: 0) && clip.captions.all { it.endMs <= (durations[clip.sourceId] ?: 0) } })
        require(selectedClip in segments.indices)
        require(previewPositionMs in 0 until durations.getValue(segments[selectedClip].sourceId))
        val selected = segments[selectedClip]
        require(trimStartMs >= selected.startMs && trimEndMs <= selected.endMs && trimEndMs > trimStartMs)
    }
}

internal object VideoDraftCodec {
    private const val MAGIC = 0x484e5644 // HNVD
    private const val VERSION = 6
    private const val MAX_BYTES = 256_000

    /** Reserve the largest permitted source catalog, soundtrack and fixed header before accepting edits. */
    fun requireRecoverableCaptions(clips: List<VideoSegment>) {
        if(clips.none { it.captions.isNotEmpty() || it.captionSourceUri != null }) return
        val references=clips.mapNotNull { it.captionSourceUri }.distinct()
        val payload=4L + references.sumOf { 4L + it.toByteArray(Charsets.UTF_8).size } + clips.sumOf { clip ->
            74L + clip.title.toByteArray(Charsets.UTF_8).size + clip.captions.sumOf { 20L + it.text.toByteArray(Charsets.UTF_8).size }
        }
        // 48-byte header, 31 maximum-length sources, soundtrack and source-count field.
        require(payload + 68_024 <= MAX_BYTES) { "Project caption recovery limit reached" }
    }

    fun encode(draft: VideoDraft): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            val version = if(draft.segments.any { it.captions.isNotEmpty() || it.captionSourceUri != null }) 6 else if(draft.segments.any{!it.layout.isIdentity||!it.titleStyle.isIdentity||it.audioFade||it.grade.warmth!=0f||it.grade.tint!=0f})5 else if(draft.segments.any { !it.grade.isIdentity }) 4 else if(draft.soundtrack != null || draft.segments.any { it.title.isNotEmpty() || it.fadeEdges }) 3 else if(draft.sources.isEmpty()) 1 else 2
            output.writeInt(version)
            output.writeLong(draft.sourceDurationMs)
            output.writeInt(draft.selectedClip)
            output.writeLong(draft.previewPositionMs)
            output.writeLong(draft.trimStartMs)
            output.writeLong(draft.trimEndMs)
            output.writeInt(draft.segments.size)
            val captionReferences=draft.segments.mapNotNull { it.captionSourceUri }.distinct()
            if(version >= 6) {
                output.writeInt(captionReferences.size)
                captionReferences.forEach { writeText(output,it,2048) }
            }
            draft.segments.forEach { clip ->
                output.writeLong(clip.startMs); output.writeLong(clip.endMs)
                output.writeFloat(clip.speed); output.writeFloat(clip.volume)
                if(version >= 2) output.writeInt((listOf("primary") + draft.sources.map { it.id }).indexOf(clip.sourceId))
                if(version >= 3) { writeText(output,clip.title,360); output.writeBoolean(clip.fadeEdges) }
                if(version >= 4) { output.writeFloat(clip.grade.exposure);output.writeFloat(clip.grade.contrast);output.writeFloat(clip.grade.saturation) }
                if(version>=5){
                    output.writeFloat(clip.grade.warmth);output.writeFloat(clip.grade.tint)
                    output.writeByte(clip.layout.ratio);output.writeBoolean(clip.layout.crop);output.writeByte(clip.layout.rotation/90);output.writeBoolean(clip.layout.mirrorX);output.writeBoolean(clip.layout.mirrorY)
                    output.writeByte(clip.titleStyle.position);output.writeFloat(clip.titleStyle.size);output.writeBoolean(clip.titleStyle.light);output.writeBoolean(clip.titleStyle.plate);output.writeBoolean(clip.audioFade)
                }
                if(version >= 6) {
                    output.writeInt(clip.captions.size)
                    clip.captions.forEach { cue -> output.writeLong(cue.startMs); output.writeLong(cue.endMs); writeText(output,cue.text,720) }
                    output.writeInt(clip.captionSourceUri?.let { captionReferences.indexOf(it) } ?: -1)
                }
            }
            if(version >= 2) {
                output.writeInt(draft.sources.size)
                draft.sources.forEach { source -> output.writeUTF(source.id); if(version >= 3) writeText(output,source.uri,2048) else output.writeUTF(source.uri); output.writeLong(source.durationMs) }
            }
            if(version >= 3) {
                output.writeBoolean(draft.soundtrack != null)
                draft.soundtrack?.let { writeText(output,it.uri,2048);output.writeLong(it.durationMs);output.writeFloat(it.volume) }
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
            val captionReferences=if(version >= 6) {
                val size=input.readInt(); require(size in 0..32)
                List(size) { readText(input,2048) }.also { require(it.distinct().size==it.size) }
            } else emptyList()
            val indices = mutableListOf<Int>()
            val raw = List(count) {
                val clip = VideoSegment(input.readLong(), input.readLong(), input.readFloat(), input.readFloat())
                indices.add(if(version >= 2) input.readInt() else 0)
                val titled=if(version >= 3) clip.copy(title = readText(input,360), fadeEdges = input.readBoolean()) else clip
                val graded=if(version >= 4)titled.copy(grade=VideoGrade(input.readFloat(),input.readFloat(),input.readFloat())) else titled
                val finished = if(version>=5)graded.copy(grade=graded.grade.copy(warmth=input.readFloat(),tint=input.readFloat()),layout=VideoLayout(input.readUnsignedByte(),input.readBoolean(),input.readUnsignedByte()*90,input.readBoolean(),input.readBoolean()),titleStyle=VideoTitleStyle(input.readUnsignedByte(),input.readFloat(),input.readBoolean(),input.readBoolean()),audioFade=input.readBoolean())else graded
                if(version >= 6) {
                    val cueCount = input.readInt(); require(cueCount in 0..VideoCaptions.MAX_PER_CLIP)
                    val cues = List(cueCount) { VideoCaption(input.readLong(),input.readLong(),readText(input,720)) }
                    val reference=input.readInt(); require(reference in -1 until captionReferences.size)
                    finished.copy(captions=cues,captionSourceUri=if(reference==-1)null else captionReferences[reference])
                } else finished
            }
            val sources = if(version >= 2) {
                val size = input.readInt(); require(size in (if(version >= 3) 0 else 1)..31)
                List(size) { VideoSource(input.readUTF(), if(version >= 3) readText(input,2048) else input.readUTF(), input.readLong()) }
            } else emptyList()
            val ids = listOf("primary") + sources.map { it.id }
            val segments = raw.mapIndexed { index, clip -> clip.copy(sourceId = ids.getOrNull(indices[index]) ?: error("Unknown clip source")) }
            val soundtrack = if(version >= 3 && input.readBoolean()) VideoSoundtrack(readText(input,2048),input.readLong(),input.readFloat()) else null
            require(input.available() == 0)
            VideoDraft(sourceDurationMs, segments, selected, position, start, end, sources, soundtrack)
        }
    }.getOrNull()

    private fun writeText(output: DataOutputStream, text: String, max: Int) {
        val bytes=text.toByteArray(Charsets.UTF_8); require(bytes.size <= max)
        output.writeInt(bytes.size);output.write(bytes)
    }
    private fun readText(input: DataInputStream, max: Int): String {
        val size=input.readInt();require(size in 0..max)
        val bytes=ByteArray(size);input.readFully(bytes)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

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
