package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal class VideoTimeSlicePrepared(val file: File, val digest: String, val width: Int, val height: Int, val plan: VideoTimeSlice.Plan, var preview: Bitmap?) {
    val token = UUID.randomUUID().toString()
    fun clearPreview() { preview?.let { if (!it.isRecycled) it.recycle() }; preview = null }
    fun dispose() { clearPreview(); file.delete() }
}
internal object VideoTimeSliceImage {
    fun bytes(value: VideoTimeSlicePrepared, current: () -> Unit): ByteArray {
        current(); check(value.file.isFile && value.file.length() in 1..VideoTimeSlice.MAX_BYTES) { "Prepared image expired" }
        val output = ByteArrayOutputStream(); val budget = VideoTimeSlice.OutputBudget(output, current)
        value.file.inputStream().use { input -> val buffer = ByteArray(32768); while (true) { current(); val count = input.read(buffer); if (count < 0) break; budget.write(buffer, 0, count) } }
        check(budget.finish() == value.digest) { "Prepared image changed" }; return output.toByteArray()
    }
    fun prepare(context: Context, source: Uri, durationMs: Long, plan: VideoTimeSlice.Plan, current: () -> Unit, progress: (Int, Int) -> Unit): VideoTimeSlicePrepared {
        require(source.scheme == "content" && plan.toMs <= durationMs)
        val began = System.nanoTime()
        fun active() { current(); check(System.nanoTime() - began < 60_000_000_000L) { "Time-slice preparation timed out" } }
        active(); val runtime = Runtime.getRuntime()
        check(runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) >= 96L * 1024 * 1024) { "Not enough memory for a time-slice image" }
        val retriever = MediaMetadataRetriever(); var file: File? = null; var outputBitmap: Bitmap? = null; var prepared: VideoTimeSlicePrepared? = null
        try {
            retriever.setDataSource(context, source); active()
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() == durationMs) { "Original video changed" }
            if (Build.VERSION.SDK_INT < 27) require(VideoFramePhotoPlan.legacyDecodeAllowed(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)) { "Legacy decoding requires at most 8.3 megapixels" }
            var rawSize: Pair<Int, Int>? = null; var size: Pair<Int, Int>? = null
            fun frame(index: Int, time: Long): IntArray {
                active(); progress(index + 1, plan.count)
                val decoded = if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST, VideoTimeSlice.MAX_EDGE, VideoTimeSlice.MAX_EDGE) else retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                requireNotNull(decoded) { "A source frame could not be decoded" }; var scaled: Bitmap? = null
                try {
                    active(); val dimensions = decoded.width to decoded.height
                    if (rawSize == null) { rawSize = dimensions; size = VideoTimeSlice.size(decoded.width, decoded.height) } else check(rawSize == dimensions) { "Source frame dimensions changed" }
                    val target = requireNotNull(size)
                    val bitmap = if (dimensions == target) decoded else Bitmap.createScaledBitmap(decoded, target.first, target.second, true).also { scaled = it }
                    return IntArray(target.first * target.second).also { bitmap.getPixels(it, 0, target.first, 0, 0, target.first, target.second); active() }
                } finally { if (scaled !== decoded) scaled?.recycle(); decoded.recycle() }
            }
            var first: IntArray? = frame(0, plan.times[0]); val target = requireNotNull(size)
            val pixels = VideoTimeSlice.render(plan, target.first, target.second, { index, time -> if (index == 0) requireNotNull(first).also { first = null } else frame(index, time) }, ::active)
            outputBitmap = Bitmap.createBitmap(pixels, target.first, target.second, Bitmap.Config.ARGB_8888)
            val directory = File(context.cacheDir, "created-images")
            check(directory.canonicalFile == File(context.cacheDir.canonicalFile, "created-images")); check(directory.isDirectory || directory.mkdirs())
            directory.listFiles()?.filter { it.name.startsWith("noble-time-slice-") && it.extension == "png" && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
            file = File.createTempFile("noble-time-slice-", ".png", directory)
            val digest = FileOutputStream(file).use { stream -> val budget = VideoTimeSlice.OutputBudget(stream, ::active); check(outputBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, budget)); val hash = budget.finish(); stream.fd.sync(); hash }
            outputBitmap?.recycle(); outputBitmap = null; active()
            prepared = VideoTimeSlicePrepared(file, digest, target.first, target.second, plan, null)
            val encoded = bytes(prepared, ::active)
            prepared.preview = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size))
            check(prepared.preview!!.width == target.first && prepared.preview!!.height == target.second); active()
            return prepared
        } catch (error: Throwable) { prepared?.dispose(); file?.delete(); throw error }
        finally { outputBitmap?.recycle(); runCatching { retriever.release() } }
    }
}
