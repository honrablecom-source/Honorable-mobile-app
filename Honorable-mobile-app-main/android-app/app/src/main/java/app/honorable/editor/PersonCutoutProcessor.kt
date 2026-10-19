package app.honorable.editor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.atomic.AtomicBoolean

/** Owns an independent input copy until ML Kit completes, including after the editor is closed. */
object PersonCutoutProcessor {
    fun start(input: Bitmap, completed: (Result<Bitmap>) -> Unit): AutoCloseable {
        val active = AtomicBoolean(true)
        val client = Segmentation.getClient(SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).enableRawSizeMask().build())
        try {
            client.process(InputImage.fromBitmap(input, 0))
                .addOnSuccessListener { mask ->
                    if (!active.get()) return@addOnSuccessListener
                    val result = runCatching {
                        val count = mask.width.toLong() * mask.height
                        require(count in 1..PersonMaskAlpha.MAX_PIXELS.toLong())
                        val buffer = mask.buffer.duplicate().order(mask.buffer.order())
                        buffer.rewind()
                        require(buffer.remaining().toLong() >= count * 4)
                        val values = FloatArray(count.toInt()) { buffer.float }
                        Bitmap.createBitmap(PersonMaskAlpha.pixels(mask.width, mask.height, values),
                            mask.width, mask.height, Bitmap.Config.ARGB_8888)
                    }
                    completed(result)
                }
                .addOnFailureListener { error -> if (active.get()) completed(Result.failure(error)) }
                .addOnCompleteListener { active.set(false); client.close(); input.recycle() }
        } catch (error: Exception) {
            active.set(false); client.close(); input.recycle(); completed(Result.failure(error))
        }
        return AutoCloseable { active.set(false); client.close() }
    }
}
