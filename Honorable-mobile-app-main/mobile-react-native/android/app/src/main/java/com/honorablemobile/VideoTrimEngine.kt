package com.honorablemobile

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import java.nio.ByteBuffer

internal data class VideoTrimResult(
    val alignedStartMs: Long,
    val actualEndMs: Long,
    val rotationDegrees: Int,
    val audioIncluded: Boolean,
)

internal class VideoTrimFailure(message: String, cause: Throwable, val partialDocumentMayRemain: Boolean) :
    Exception(message, cause)

/** Copies encoded tracks locally. The start moves to the preceding video keyframe. */
internal object VideoTrimEngine {
    fun trim(
        context: Context,
        source: Uri,
        destination: Uri,
        startMs: Long,
        endMs: Long,
        cancellation: CancellationSignal,
    ): VideoTrimResult {
        require(startMs >= 0 && endMs > startMs) { "Choose a valid range" }
        require(source != destination) { "The source and destination must be different" }
        val resolver = context.contentResolver
        val extractor = MediaExtractor()
        var input: ParcelFileDescriptor? = null
        var output: ParcelFileDescriptor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var safeToDeleteDestination = false
        var failure: Throwable? = null
        var result: VideoTrimResult? = null
        try {
            cancellation.throwIfCanceled()
            input = resolver.openFileDescriptor(source, "r") ?: error("Cannot open source video")
            output = resolver.openFileDescriptor(destination, "rw") ?: error("Cannot open destination")
            // SAF and MediaStore can expose the same file through different content URIs.
            val sourceStat = Os.fstat(input.fileDescriptor)
            val destinationStat = Os.fstat(output.fileDescriptor)
            require(sourceStat.st_ino != 0L && destinationStat.st_ino != 0L) { "Cannot verify separate source and destination files" }
            require(sourceStat.st_dev != destinationStat.st_dev || sourceStat.st_ino != destinationStat.st_ino) {
                "The chosen destination is the source video"
            }
            safeToDeleteDestination = true
            Os.ftruncate(output.fileDescriptor, 0)
            extractor.setDataSource(input.fileDescriptor)
            val videoTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")
            } ?: error("No video track found")
            val retriever = MediaMetadataRetriever()
            val rotation = try {
                retriever.setDataSource(context, source)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } finally { retriever.release() }
            require(rotation in setOf(0, 90, 180, 270)) { "Unsupported video rotation" }

            extractor.selectTrack(videoTrack)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val alignedStartUs = extractor.sampleTime
            require(alignedStartUs >= 0 && alignedStartUs < endMs * 1000) { "No video keyframe in the selected range" }
            require(extractor.sampleTrackIndex == videoTrack && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                "Cannot start on a video keyframe"
            }
            extractor.unselectTrack(videoTrack)

            muxer = MediaMuxer(output.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(rotation)
            val tracks = mutableMapOf<Int, Int>()
            var audioTrackCount = 0
            var bufferSize = 1024 * 1024
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    tracks[index] = muxer.addTrack(format)
                    extractor.selectTrack(index)
                    if (mime.startsWith("audio/")) audioTrackCount++
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }
            require(bufferSize <= 32 * 1024 * 1024) { "Video samples are too large for this editor" }
            muxer.start()
            muxerStarted = true
            extractor.seekTo(alignedStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            var videoSamples = 0
            var audioSamples = 0
            var lastVideoUs = alignedStartUs
            while (true) {
                cancellation.throwIfCanceled()
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val timeUs = extractor.sampleTime
                if (timeUs >= endMs * 1000) break
                if (timeUs >= alignedStartUs && tracks.containsKey(track)) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(0, size, timeUs - alignedStartUs, extractor.sampleFlags)
                    muxer.writeSampleData(tracks.getValue(track), buffer, info)
                    if (track == videoTrack) { videoSamples++; lastVideoUs = timeUs }
                    else audioSamples++
                }
                extractor.advance()
            }
            require(videoSamples > 0) { "No video samples were written" }
            require(audioTrackCount == 0 || audioSamples > 0) { "Audio was lost from the selected range" }
            result = VideoTrimResult(alignedStartUs / 1000, lastVideoUs / 1000, rotation, audioSamples > 0)
        } catch (error: Throwable) {
            failure = error
        } finally {
            fun finish(action: () -> Unit) {
                try { action() } catch (error: Throwable) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            if (muxerStarted) finish { muxer?.stop() }
            finish { muxer?.release() }
            finish { extractor.release() }
            finish { output?.close() }
            finish { input?.close() }
        }
        if (failure == null) {
            try { verifyOutput(context, destination, requireNotNull(result)) }
            catch (error: Throwable) { failure = error }
        }
        if (failure != null) {
            val removed = if (safeToDeleteDestination) runCatching {
                DocumentsContract.deleteDocument(resolver, destination)
            }.getOrDefault(false) else false
            val message = when (failure) {
                is OperationCanceledException -> "Export cancelled"
                else -> "Export failed: ${failure!!.message ?: "Unknown error"}"
            }
            throw VideoTrimFailure(message, failure!!, safeToDeleteDestination && !removed)
        }
        return requireNotNull(result)
    }

    private fun verifyOutput(context: Context, destination: Uri, expected: VideoTrimResult) {
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(context, destination, null)
            val videoTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")
            } ?: error("Saved copy has no video")
            val audioTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            }
            require(!expected.audioIncluded || audioTrack != null) { "Saved copy has no audio" }
            fun sampleBounds(track: Int): Pair<Long, Long> {
                extractor.selectTrack(track)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                var first = -1L
                var last = -1L
                while (extractor.sampleTrackIndex >= 0) {
                    val time = extractor.sampleTime
                    if (first < 0) first = time
                    require(time >= last) { "Saved samples are out of order" }
                    last = time
                    if (!extractor.advance()) break
                }
                extractor.unselectTrack(track)
                require(first >= 0) { "Saved track has no samples" }
                return first to last
            }
            val videoBounds = sampleBounds(videoTrack)
            require(videoBounds.first <= 100_000) { "Saved video does not start at the aligned keyframe" }
            require(videoBounds.second <= (expected.actualEndMs - expected.alignedStartMs + 1000) * 1000) {
                "Saved video exceeds the selected end"
            }
            audioTrack?.let {
                val audioBounds = sampleBounds(it)
                require(kotlin.math.abs(audioBounds.first - videoBounds.first) <= 1_000_000) { "Saved audio starts out of sync" }
                require(audioBounds.second <= videoBounds.second + 1_000_000) { "Saved audio ends out of sync" }
            }
            retriever.setDataSource(context, destination)
            val savedRotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            require(savedRotation == expected.rotationDegrees) { "Saved copy has incorrect rotation" }
            val savedDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            require(savedDuration > 0 && savedDuration <= expected.actualEndMs - expected.alignedStartMs + 2000) { "Saved copy has unexpected duration" }
        } finally { retriever.release(); extractor.release() }
    }
}
