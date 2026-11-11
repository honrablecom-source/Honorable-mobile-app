package app.honorable.editor

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Actual on-device inference only. Never constructs substitute detections when the SDK is unavailable. */
object PhotoSubjectSegmenter {
    class ModelUnavailable : IllegalStateException("Compatible Google Play services are unavailable. Your photo and edits were kept.")
    data class Batch(val source: PhotoSubjectAssets.Source, val cutouts: List<PhotoSubjectAssets.Cutout>)
    internal fun releaseAfter(task: Task<*>?, cleanup: () -> Unit) {
        if (task == null || task.isComplete) cleanup()
        else task.addOnCompleteListener(Executor { it.run() }) { cleanup() }
    }

    /** Takes exclusive ownership of input, even on failure. Call on a worker after explicit UI consent. */
    fun analyze(context: Context, input: Bitmap, allowModelInstall: Boolean, current: () -> Unit,
                phase: (String) -> Unit): Batch {
        var client: SubjectSegmenter? = null
        var inference: Task<SubjectSegmentationResult>? = null
        val cleaned = AtomicBoolean(false)
        fun cleanup() {
            if (cleaned.compareAndSet(false, true)) {
                runCatching { client?.close() }
                if (!input.isRecycled) input.recycle()
            }
        }
        val began = System.nanoTime()
        fun checked() {
            current(); check(!Thread.currentThread().isInterrupted) { "AI subject work stopped" }
            check(System.nanoTime() - began < 180_000_000_000L) { "AI model or inference timed out; your photo was kept" }
        }
        fun <T> await(task: Task<T>): T {
            while (true) {
                checked()
                try { return Tasks.await(task, 250, TimeUnit.MILLISECONDS).also { checked() } }
                catch (_: TimeoutException) { /* Keep checking foreground/owner/cancellation. */ }
            }
        }
        try {
            checked()
            require(!input.isRecycled && input.width in 1..PhotoSubjectAssets.MAX_EDGE && input.height in 1..PhotoSubjectAssets.MAX_EDGE) { "Use a preview up to 1024 pixels per edge" }
            val colors = IntArray(input.width * input.height)
            input.getPixels(colors, 0, input.width, 0, 0, input.width, input.height)
            val source = PhotoSubjectAssets.Source.capture(input.width, input.height, colors)
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) throw ModelUnavailable()
            val perSubject = SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask().build()
            val detector = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableMultipleSubjects(perSubject).build())
            client = detector
            val installer = ModuleInstall.getClient(context.applicationContext)
            phase("Checking the on-device AI model")
            var available = await(installer.areModulesAvailable(detector)).areModulesAvailable()
            if (!available) {
                check(allowModelInstall) { "The subject AI model is not installed; choose model setup before trying again" }
                checked(); phase("Preparing the AI model; Google Play services may download it")
                // A successful install request is not installation completion. Recheck actual availability.
                await(installer.installModules(ModuleInstallRequest.newBuilder().addApi(detector).build()))
                while (!available) {
                    checked(); Thread.sleep(250); checked()
                    available = await(installer.areModulesAvailable(detector)).areModulesAvailable()
                }
            }
            checked(); phase("Separating subjects on this device")
            val task = detector.process(InputImage.fromBitmap(input, 0)); inference = task
            val subjects = await(task).subjects
            require(subjects.size in 1..PhotoSubjectAssets.MAX_SUBJECTS) { "No separate subjects, or too many subjects; use a clearer photo with up to 12 subjects" }
            var total = 0L
            // Verify all SDK geometry before copying buffers or allocating candidate output bitmaps.
            subjects.forEach { subject ->
                checked()
                val area = subject.width.toLong() * subject.height
                require(subject.startX >= 0 && subject.startY >= 0 && subject.width in 1..source.width && subject.height in 1..source.height &&
                    subject.startX.toLong() + subject.width <= source.width && subject.startY.toLong() + subject.height <= source.height &&
                    area <= PhotoSubjectAssets.MAX_MASK_PIXELS - total) { "AI subject geometry exceeds the preview or safe pixel budget" }
                total += area
            }
            val masks = subjects.map { subject ->
                checked()
                val area = subject.width * subject.height
                val buffer = requireNotNull(subject.confidenceMask) { "AI subject confidence mask is unavailable" }.duplicate().apply { rewind() }
                require(buffer.remaining() >= area) { "AI subject confidence mask is incomplete" }
                val values = FloatArray(area)
                for (y in 0 until subject.height) {
                    checked()
                    for (x in 0 until subject.width) values[y * subject.width + x] = buffer.get()
                }
                PhotoSubjectAssets.Mask(subject.startX, subject.startY, subject.width, subject.height, values)
            }
            checked(); phase("Preparing separate cutout candidates")
            return Batch(source, PhotoSubjectAssets.prepare(source, masks, ::checked))
        } finally {
            // Stopping the UI must not recycle an image still read by the SDK. No late result is used.
            releaseAfter(inference, ::cleanup)
        }
    }
}
