package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.StaticOverlaySettings

/** Composition-wide fading includes titles and uses final output timestamps. */
@UnstableApi
internal class VideoTimelineFade(private val clips: List<VideoSegment>) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val value = VideoTimeline.fadeBrightness(clips, presentationTimeUs)
        return floatArrayOf(value,0f,0f,0f, 0f,value,0f,0f, 0f,0f,value,0f, 0f,0f,0f,1f)
    }
}

/** Bounded, wrapped lower-third title. Texture allocation is lazy and owned by this overlay. */
@UnstableApi
internal class VideoTitleOverlay(private val title: String) : BitmapOverlay() {
    private var frame = Size(720,1280)
    private var owned: Bitmap? = null
    private val width: Int get() = (frame.width*.84f).toInt().coerceIn(64,1024)
    private val scale: Float get() = frame.width*.84f/width
    override fun configure(videoSize: Size) {
        if(frame != videoSize) { owned?.recycle(); owned = null; frame = videoSize }
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        owned?.let { return it }
        val padding = (width*.035f).toInt().coerceAtLeast(4)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (minOf(frame.width*.055f, frame.height*.09f)/scale).coerceIn(12f,72f)
        }
        val layout = StaticLayout.Builder.obtain(title,0,title.length,paint,width-padding*2)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
            .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build()
        return Bitmap.createBitmap(width,layout.height+padding*2,Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.drawRoundRect(RectF(0f,0f,it.width.toFloat(),it.height.toFloat()),8f,8f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(205,0,0,0) })
            canvas.translate(padding.toFloat(),padding.toFloat());layout.draw(canvas)
            owned = it
        }
    }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(0f,-.78f).setOverlayFrameAnchor(0f,-1f).setScale(scale,scale).build()
    override fun release() { try { super.release() } finally { owned?.recycle(); owned = null } }
}
