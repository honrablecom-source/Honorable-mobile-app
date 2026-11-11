package app.honorable.editor

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Real bundled Latin OCR; owns the image/client until the underlying SDK task settles. */
object PhotoTextLayoutReader {
    internal class Pending(val task: Task<*>, val close: () -> Unit, val lines: () -> List<PhotoTextLayout.RawLine>)
    private val direct = Executor { it.run() }

    private fun start(snapshot: Bitmap): Pending {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val task = client.process(InputImage.fromBitmap(snapshot, 0))
            return Pending(task, { client.close() }) {
                val result = task.result
                val lines = result.textBlocks.asSequence().flatMap { it.lines.asSequence() }.take(PhotoTextLayout.MAX_LINES + 1).toList()
                require(lines.size in 1..PhotoTextLayout.MAX_LINES) { "Use a sharper crop with 1–100 recognized lines" }
                lines.map { line ->
                    val box = requireNotNull(line.boundingBox) { "OCR line placement is unavailable" }
                    PhotoTextLayout.RawLine(line.text, PhotoTextLayout.Box(box.left, box.top, box.right, box.bottom))
                }
            }
        } catch (failure: Throwable) { client.close(); throw failure }
    }

    /** Worker-only. settled may run on an SDK completion thread, even after Stop/timeout. */
    internal fun read(snapshot: Bitmap, current: () -> Unit, settled: () -> Unit,
                      starter: (Bitmap) -> Pending = ::start, now: () -> Long = System::nanoTime): PhotoTextLayout.Batch {
        var pending: Pending? = null
        val cleaned = AtomicBoolean(false)
        val began = now()
        fun cleanup() {
            if (cleaned.compareAndSet(false, true)) {
                runCatching { pending?.close?.invoke() }
                try { if (!snapshot.isRecycled) snapshot.recycle() } finally { settled() }
            }
        }
        fun checked() {
            current(); check(!Thread.currentThread().isInterrupted) { "Text layout recognition stopped" }
            check(now() - began < 90_000_000_000L) { "Text layout recognition timed out" }
        }
        try {
            checked()
            require(!snapshot.isRecycled && snapshot.width in 1..8192 && snapshot.height in 1..8192 && snapshot.width.toLong() * snapshot.height <= 6_000_000) { "Use a bounded working preview" }
            val request = starter(snapshot); pending = request
            val width = snapshot.width; val height = snapshot.height
            while (true) {
                checked()
                try {
                    Tasks.await(request.task, 250, TimeUnit.MILLISECONDS)
                    checked()
                    return PhotoTextLayout.Batch.capture(width, height, request.lines())
                } catch (_: TimeoutException) { /* Cancellation does not recycle an in-flight ML Kit input. */ }
            }
        } finally {
            val request = pending
            if (request == null || request.task.isComplete) cleanup()
            else request.task.addOnCompleteListener(direct) { cleanup() }
        }
    }
}
