package com.honorablemobile

import android.graphics.Bitmap
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/** Composition-time captions use the same mapping in clip preview, project preview and export. */
@UnstableApi
internal class VideoCaptionOverlay(private val cues: List<TimelineCaption>) : BitmapOverlay() {
    private var frame = Size(720, 1280)
    private var activeText: String? = null
    private var title: VideoTitleOverlay? = null
    private var blank: Bitmap? = null
    private val hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).build()

    override fun configure(videoSize: Size) { frame = videoSize; title?.configure(videoSize) }

    private fun current(timeUs: Long): VideoTitleOverlay? {
        val text = VideoCaptions.at(cues, timeUs)?.text
        if (text != activeText) {
            title?.release()
            title = text?.let { VideoTitleOverlay(it, VideoTitleStyle(size = .04f), fitCaption = true).also { overlay -> overlay.configure(frame) } }
            activeText = text
        }
        return title
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = current(presentationTimeUs)?.getBitmap(presentationTimeUs)
        ?: blank ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { blank = it }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = current(presentationTimeUs)?.getOverlaySettings(presentationTimeUs) ?: hidden

    override fun release() {
        try { super.release() } finally { title?.release(); title = null; activeText = null; blank?.recycle(); blank = null }
    }
}
