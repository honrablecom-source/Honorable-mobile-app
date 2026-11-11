package app.honorable.editor

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/** Same sans/normal, left alignment, no tracking/pad, max-three-line layout as PhotoLayersRenderer. */
object PhotoTextLayoutTypography : PhotoTextLayout.Fitter {
    override fun fit(text: String, frame: PhotoTextLayout.Frame, unit: Float, idealSize: Float): Float? {
        require(unit.isFinite() && unit > 0 && frame.width.isFinite() && frame.height.isFinite() && frame.width > 0 && frame.height > 0)
        require(idealSize.isFinite() && idealSize in .02f.. .2f)
        fun fits(size: Float): Boolean = complete(text, frame, unit, size)
        if (fits(idealSize)) return idealSize
        if (!fits(.02f)) return null
        var lower = .02f; var upper = idealSize
        repeat(16) {
            val middle = (lower + upper) / 2
            if (fits(middle)) lower = middle else upper = middle
        }
        return lower
    }

    internal fun complete(text: String, frame: PhotoTextLayout.Frame, unit: Float, size: Float): Boolean {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = -0x1000000; textSize = (size * unit).coerceAtLeast(1f); letterSpacing = 0f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, frame.width.toInt().coerceAtLeast(1))
            .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        return layout.lineCount in 1..3 && layout.getLineEnd(layout.lineCount - 1) == text.length &&
            (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 } && layout.height <= frame.height
    }
}
