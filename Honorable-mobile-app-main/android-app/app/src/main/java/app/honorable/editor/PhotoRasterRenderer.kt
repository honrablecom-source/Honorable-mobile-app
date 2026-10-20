package app.honorable.editor

import android.graphics.*
import kotlin.math.pow

/** Preview and PNG export share this path, including crop/rotation of the source-aligned matte. */
object PhotoRasterRenderer {
    fun render(input: Bitmap, edit: PhotoEdit, personMask: Bitmap?): Bitmap {
        val mask = if (edit.personCutout) requireNotNull(personMask) { "Person cutout is unavailable" } else null
        val width = (input.width * edit.crop).toInt().coerceAtLeast(1)
        val height = (input.height * edit.crop).toInt().coerceAtLeast(1)
        val rotated = edit.rotation % 180 != 0
        val output = Bitmap.createBitmap(if (rotated) height else width, if (rotated) width else height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.translate(output.width / 2f, output.height / 2f)
            canvas.rotate(edit.rotation.toFloat())
            val gain = 2f.pow(edit.exposure) * edit.contrast
            val offset = edit.brightness + 128 * (1 - edit.contrast)
            val color = ColorMatrix().apply {
                setSaturation(edit.saturation)
                postConcat(ColorMatrix(floatArrayOf(gain,0f,0f,0f,offset,0f,gain,0f,0f,offset,0f,0f,gain,0f,offset,0f,0f,0f,1f,0f)))
            }
            val source = Rect((input.width-width)/2, (input.height-height)/2, (input.width+width)/2, (input.height+height)/2)
            val destination = RectF(-width/2f, -height/2f, width/2f, height/2f)
            canvas.drawBitmap(input, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(color)
            })
            if (mask != null) {
                // Scale from the mask's native resolution before applying exactly the source crop.
                canvas.save()
                canvas.clipRect(destination)
                canvas.translate(-width/2f - source.left, -height/2f - source.top)
                canvas.drawBitmap(mask, null, RectF(0f, 0f, input.width.toFloat(), input.height.toFloat()),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    })
                canvas.restore()
            }
            return output
        } catch (error: Throwable) { output.recycle(); throw error }
    }
}
