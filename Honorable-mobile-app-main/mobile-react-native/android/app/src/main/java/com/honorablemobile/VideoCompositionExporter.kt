package com.honorablemobile

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import android.system.Os
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class VideoExportFailure(message: String, cause: Throwable, val partialDocumentMayRemain: Boolean) :
    Exception(message, cause)

/** Renders the project's clips in order to a private temporary MP4 before writing a new document. */
@UnstableApi
internal class VideoCompositionExporter(private val context: Context) {
    companion object {
        fun composition(source: Uri, clips: List<VideoSegment>): Composition {
            require(clips.isNotEmpty())
            val items = clips.map { clip ->
                val media = MediaItem.Builder().setUri(source).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                ).build()
                EditedMediaItem.Builder(media).apply {
                    setDurationUs((clip.endMs - clip.startMs) * 1000)
                    if (clip.speed != 1f) setSpeed(object : SpeedProvider {
                        override fun getSpeed(timeUs: Long) = clip.speed
                        override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
                    })
                    if (clip.volume != 1f) setEffects(Effects(
                        listOf(GainProcessor(DefaultGainProvider.Builder(clip.volume).build())),
                        emptyList(),
                    ))
                }.build()
            }
            return Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(items)).build()
        }

        /** Only call after rendering to a private file; never open the source for writing. */
        internal fun copyRenderedToDocument(
            context: Context,
            source: Uri,
            destination: Uri,
            rendered: File,
            clips: List<VideoSegment>,
            cancelled: AtomicBoolean,
            onChunkCopied: () -> Unit = {},
        ) {
            val resolver = context.contentResolver
            var safeToDelete = false
            try {
                if (cancelled.get()) throw OperationCanceledException()
                require(rendered.length() > 0) { "Rendered video is empty" }
                resolver.openFileDescriptor(source, "r").use { input ->
                    resolver.openFileDescriptor(destination, "rw").use { output ->
                        requireNotNull(input) { "Cannot open source video" }
                        requireNotNull(output) { "Cannot open destination" }
                        val from = Os.fstat(input.fileDescriptor)
                        val to = Os.fstat(output.fileDescriptor)
                        require(from.st_ino != 0L && to.st_ino != 0L) { "Cannot verify separate source and destination files" }
                        require(from.st_dev != to.st_dev || from.st_ino != to.st_ino) {
                            "The destination is the source video"
                        }
                        safeToDelete = true
                        Os.ftruncate(output.fileDescriptor, 0)
                        rendered.inputStream().use { stream ->
                            android.os.ParcelFileDescriptor.AutoCloseOutputStream(output.dup()).use { sink ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    if (cancelled.get()) throw OperationCanceledException()
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    sink.write(buffer, 0, count)
                                    onChunkCopied()
                                }
                                sink.flush()
                            }
                        }
                    }
                }
                if (cancelled.get()) throw OperationCanceledException()
                verifyOutput(context, source, destination, clips)
                if (cancelled.get()) throw OperationCanceledException()
            } catch (error: Throwable) {
                val removed = if (safeToDelete) runCatching {
                    DocumentsContract.deleteDocument(resolver, destination)
                }.getOrDefault(false) else false
                val message = if (error is OperationCanceledException) "Export cancelled" else "Export failed: ${error.message ?: "Unknown error"}"
                throw VideoExportFailure(message, error, safeToDelete && !removed)
            }
        }

        private fun verifyOutput(context: Context, source: Uri, destination: Uri, clips: List<VideoSegment>) {
            val expectedDurationMs = clips.sumOf { it.durationMs }
            val sourceExtractor = MediaExtractor()
            val outputExtractor = MediaExtractor()
            val sourceMetadata = MediaMetadataRetriever()
            val outputMetadata = MediaMetadataRetriever()
            try {
                sourceExtractor.setDataSource(context, source, null)
                outputExtractor.setDataSource(context, destination, null)
                fun track(extractor: MediaExtractor, prefix: String) = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(prefix)
                }
                fun requireSample(track: Int, kind: String) {
                    outputExtractor.selectTrack(track)
                    outputExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    require(outputExtractor.sampleTrackIndex == track && outputExtractor.sampleTime >= 0) {
                        "Saved copy has no $kind samples"
                    }
                    outputExtractor.unselectTrack(track)
                }
                val videoTrack = track(outputExtractor, "video/") ?: error("Saved copy has no video")
                requireSample(videoTrack, "video")
                if (track(sourceExtractor, "audio/") != null) {
                    val audioTrack = track(outputExtractor, "audio/") ?: error("Saved copy has no audio")
                    requireSample(audioTrack, "audio")
                }
                sourceMetadata.setDataSource(context, source)
                outputMetadata.setDataSource(context, destination)
                fun metadata(retriever: MediaMetadataRetriever, key: Int) =
                    retriever.extractMetadata(key)?.toLongOrNull() ?: 0L
                val actualDurationMs = metadata(outputMetadata, MediaMetadataRetriever.METADATA_KEY_DURATION)
                val toleranceMs = maxOf(750L, expectedDurationMs / 10)
                require(actualDurationMs > 0 && kotlin.math.abs(actualDurationMs - expectedDurationMs) <= toleranceMs) {
                    "Saved copy has unexpected duration"
                }
                fun portrait(retriever: MediaMetadataRetriever): Boolean {
                    val width = metadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val height = metadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val rotation = metadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
                    require(width > 0 && height > 0 && rotation in setOf(0, 90, 180, 270)) {
                        "Saved copy has invalid video dimensions or rotation"
                    }
                    return if (rotation == 90 || rotation == 270) width > height else height > width
                }
                require(portrait(sourceMetadata) == portrait(outputMetadata)) { "Saved copy has incorrect orientation" }
            } finally {
                outputMetadata.release()
                sourceMetadata.release()
                outputExtractor.release()
                sourceExtractor.release()
            }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var temporary: File? = null
    private var finished = false
    private var copying = false
    private val cancelled = AtomicBoolean(false)

    fun export(source: Uri, destination: Uri, clips: List<VideoSegment>, done: (Result<Unit>) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(transformer == null)
        fun complete(result: Result<Unit>) {
            if (finished) return
            finished = true
            transformer = null
            temporary?.delete()
            temporary = null
            done(result)
        }
        try {
            require(source != destination && clips.isNotEmpty())
            val renderedFile = File.createTempFile("honorable-edit-", ".mp4", context.cacheDir)
            temporary = renderedFile
            require(renderedFile.delete()) { "Cannot prepare temporary video" } // Transformer requires a new path.
            val composition = composition(source, clips)
            transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    if (cancelled.get()) { complete(Result.failure(OperationCanceledException())); return }
                    copying = true
                    Thread {
                        val copied = runCatching { copyRenderedToDocument(context, source, destination, renderedFile, clips, cancelled) }
                        main.post {
                            if (finished) { renderedFile.delete(); temporary = null }
                            else complete(copied)
                        }
                    }.start()
                }

                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    complete(Result.failure(exception))
                }
            }).build()
            transformer!!.start(composition, renderedFile.absolutePath)
        } catch (error: Exception) { complete(Result.failure(error)) }
    }

    fun cancel() {
        check(Looper.myLooper() == Looper.getMainLooper())
        cancelled.set(true)
        transformer?.cancel()
        transformer = null
        if (!copying) {
            temporary?.delete()
            temporary = null
        }
        finished = true
    }
}
