package com.honorablemobile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.util.Collections

internal class VideoDarkHoldPack(val analysis: VideoDarkHolds.Analysis, frames: List<Bitmap?>) {
    val frames: List<Bitmap?> = Collections.unmodifiableList(frames.toList())
    fun dispose() { frames.forEach { if (it != null && !it.isRecycled) it.recycle() } }
}
internal object VideoDarkHoldFrames {
    fun scan(context: Context, source: Uri, durationMs: Long, clip: VideoSegment, current: () -> Unit, progress: (Int, Int) -> Unit): VideoDarkHoldPack {
        val times = VideoDarkHolds.times(clip); require(source.scheme == "content" && clip.endMs <= durationMs)
        val began = System.nanoTime()
        fun active() { current(); check(System.nanoTime() - began < 60_000_000_000L) { "Frame scan timed out; choose a shorter clip" } }
        active(); val runtime = Runtime.getRuntime()
        check(runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) >= 96L * 1024 * 1024) { "Not enough memory for frame review" }
        val retriever = MediaMetadataRetriever(); val frames = mutableListOf<Bitmap?>(); val states = mutableListOf<VideoDarkHolds.State>()
        try {
            retriever.setDataSource(context, source); active()
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() == durationMs) { "Original video changed" }
            if (Build.VERSION.SDK_INT < 27) require(VideoFramePhotoPlan.legacyDecodeAllowed(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)) { "Legacy decoding requires at most 8.3 megapixels" }
            var rawSize: Pair<Int, Int>? = null
            times.forEachIndexed { index, time ->
                active(); progress(index + 1, times.size)
                val decoded = if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 256, 256) else retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (decoded == null) { states += VideoDarkHolds.State.UNKNOWN; frames += null; active() }
                else {
                    var used: Bitmap? = null; var kept = false
                    try {
                        active(); val dimensions = decoded.width to decoded.height
                        if (rawSize == null) rawSize = dimensions else check(rawSize == dimensions) { "Source frame dimensions changed" }
                        val target = VideoDarkHolds.size(decoded.width, decoded.height)
                        val image = if (dimensions == target) decoded else Bitmap.createScaledBitmap(decoded, target.first, target.second, true)
                        used = image
                        val pixels = IntArray(image.width * image.height); image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                        val state = VideoDarkHolds.classify(image.width, image.height, pixels, ::active)
                        states += state; frames += image; kept = true; active()
                    } finally {
                        if (!kept && used !== decoded) used?.recycle()
                        if (!kept || used !== decoded) decoded.recycle()
                    }
                }
            }
            active(); return VideoDarkHoldPack(VideoDarkHolds.analyze(clip, states), frames).also { active() }
        } catch (error: Throwable) { frames.forEach { if (it != null && !it.isRecycled) it.recycle() }; throw error }
        finally { runCatching { retriever.release() } }
    }
}
