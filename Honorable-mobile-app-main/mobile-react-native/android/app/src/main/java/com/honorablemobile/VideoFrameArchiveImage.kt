package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

internal object VideoFrameArchiveImage {
    fun prepare(context: Context, source: Uri, durationMs: Long, plan: VideoFrameArchive.Plan, current: () -> Unit, progress: (Int, Int) -> Unit): VideoFrameArchive.Prepared {
        require(source.scheme == "content" && plan.toMs <= durationMs)
        val began = System.nanoTime()
        fun active() { current(); check(System.nanoTime() - began < 60_000_000_000L) { "Frame archive preparation timed out" } }
        active(); val runtime = Runtime.getRuntime()
        check(runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) >= 96L * 1024 * 1024) { "Not enough memory for a frame archive" }
        val retriever = MediaMetadataRetriever(); var file: File? = null
        try {
            retriever.setDataSource(context, source); active()
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() == durationMs) { "Original video changed" }
            if (Build.VERSION.SDK_INT < 27) require(VideoFramePhotoPlan.legacyDecodeAllowed(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)) { "Legacy decoding requires at most 8.3 megapixels" }
            val directory = File(context.cacheDir, "created-images")
            check(directory.canonicalFile == File(context.cacheDir.canonicalFile, "created-images")); check(directory.isDirectory || directory.mkdirs())
            directory.listFiles()?.filter { it.name.startsWith("noble-frame-archive-") && it.extension == "zip" && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
            file = File.createTempFile("noble-frame-archive-", ".zip", directory)
            var rawSize: Pair<Int, Int>? = null
            return VideoFrameArchive.create(file, plan, ::active) { index, time ->
                active(); progress(index + 1, plan.count)
                val decoded = if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 512, 512) else retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                requireNotNull(decoded) { "A source frame could not be decoded" }; var scaled: Bitmap? = null
                try {
                    active(); val dimensions = decoded.width to decoded.height
                    if (rawSize == null) rawSize = dimensions else check(rawSize == dimensions) { "Source frame dimensions changed" }
                    val target = VideoTimeSlice.size(decoded.width, decoded.height)
                    val bitmap = if (dimensions == target) decoded else Bitmap.createScaledBitmap(decoded, target.first, target.second, true).also { scaled = it }
                    val output = ByteArrayOutputStream(); val budget = VideoTimeSlice.OutputBudget(output, ::active)
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, budget)); budget.finish(); active()
                    VideoFrameArchive.Frame(output.toByteArray(), target.first, target.second)
                } finally { if (scaled !== decoded) scaled?.recycle(); decoded.recycle() }
            }
        } catch (error: Throwable) { file?.delete(); throw error }
        finally { runCatching { retriever.release() } }
    }
    fun previews(value: VideoFrameArchive.Prepared, current: () -> Unit): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        try {
            VideoFrameArchive.verify(value, current)
            ZipFile(value.file).use { zip -> value.entries.forEach { entry ->
                current(); val bytes = VideoFrameArchive.read(zip, entry, current)
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)); result += bitmap
                check(bitmap.width == entry.width && bitmap.height == entry.height); current()
            } }
            return result
        } catch (error: Throwable) { result.forEach { it.recycle() }; throw error }
    }
}

internal class VideoFrameArchiveReview(val archive: VideoFrameArchive.Prepared, val previews: List<Bitmap>) {
    val token get() = archive.token
    fun clearPreview() { previews.forEach { if (!it.isRecycled) it.recycle() } }
    fun dispose() { clearPreview(); archive.dispose() }
}
