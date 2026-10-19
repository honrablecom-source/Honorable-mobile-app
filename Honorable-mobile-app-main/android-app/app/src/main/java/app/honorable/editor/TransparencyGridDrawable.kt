package app.honorable.editor

import android.graphics.*
import android.graphics.drawable.Drawable

class TransparencyGridDrawable(private val cell: Int) : Drawable() {
    private val paint = Paint()
    override fun draw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(36, 36, 36))
        paint.color = Color.rgb(64, 64, 64)
        val step = cell.coerceAtLeast(1)
        for (y in bounds.top until bounds.bottom step step) {
            for (x in bounds.left until bounds.right step step) {
                if (((x-bounds.left)/step + (y-bounds.top)/step) % 2 == 0)
                    canvas.drawRect(x.toFloat(), y.toFloat(), minOf(x+step,bounds.right).toFloat(), minOf(y+step,bounds.bottom).toFloat(), paint)
            }
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.OPAQUE
}
