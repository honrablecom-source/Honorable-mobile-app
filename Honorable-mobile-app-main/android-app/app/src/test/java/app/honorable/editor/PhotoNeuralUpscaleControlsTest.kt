package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoNeuralUpscaleControlsTest {
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun next() { tasks.removeFirst().run(); shadowOf(Looper.getMainLooper()).idle() }
    }
    private lateinit var controller: org.robolectric.android.controller.ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var controls: PhotoNeuralUpscaleControls
    private lateinit var queue: Queue
    private lateinit var original: Bitmap
    private var current = true
    private var released = 0
    private var prepared = 0
    private var calls = 0
    private var afterInput: () -> Unit = {}
    private var afterInference: () -> Unit = {}
    private var shareAction: (File) -> Unit = { shared += it }
    private val inputs = mutableListOf<Bitmap>()
    private val shared = mutableListOf<File>()
    private val messages = mutableListOf<String>()
    @Before fun start() {
        controller = Robolectric.buildActivity(Activity::class.java).setup(); activity = controller.get(); queue = Queue()
        original = Bitmap.createBitmap(7, 5, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) setPixel(x, y, -0x1000000 or ((x * 31) shl 16) or ((y * 47) shl 8) or (x * 11 + y))
        }
        controls = make(); controls.open(); shadowOf(Looper.getMainLooper()).idle()
    }
    private fun make(executor: Executor = queue) = PhotoNeuralUpscaleControls(activity, executor,
        prepareInput = { check -> check(); prepared++; original.copy(Bitmap.Config.ARGB_8888, false).also { inputs += it; afterInput() } },
        isCurrent = { current }, requireWorkerCurrent = { check(current) }, share = { shareAction(it) }, message = { messages += it }, released = { released++ },
        reconstruct = { source, cancellation, check, phase ->
            cancellation.check(); calls++
            PhotoNeuralUpscale.reconstruct(source, PhotoNeuralUpscale.Network { w, h, input, active ->
                active()
                FloatArray(w * h * 12) { i -> val plane = w * h * 4; val c = i / plane; val offset = i % plane; input[c * w * h + offset / (w * 2) / 2 * w + offset % (w * 2) / 2] }
            }, check, phase).also { afterInference() }
        })
    @After fun stop() { controls.close(); while (queue.tasks.isNotEmpty()) queue.next(); original.recycle(); controller.pause().stop().destroy() }
    private fun dialog() = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun confirm() = views(dialog().window!!.decorView).filterIsInstance<CheckBox>().single()
    private fun files() = File(activity.cacheDir, "created-images").listFiles()?.filter { it.name.startsWith("noble-upscale-") }.orEmpty()
    private fun begin() { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle() }
    private fun review(): AlertDialog { begin(); queue.next(); assertTrue(dialog().isShowing); return dialog() }
    private fun queueShare(): Button { confirm().isChecked = true; return dialog().getButton(AlertDialog.BUTTON_POSITIVE).also { it.performClick(); shadowOf(Looper.getMainLooper()).idle() } }

    @Test fun setupDoesNoModelOrImageWorkAndStatesWorkingCopyLimit() {
        controls.open(); assertEquals(0, prepared); assertEquals(0, calls); assertTrue(queue.tasks.isEmpty()); assertTrue(files().isEmpty())
        val copy = views(dialog().window!!.decorView).filterIsInstance<android.widget.TextView>().joinToString { it.text.toString() }
        assertTrue(copy.contains("fitted within 1024")); assertTrue(copy.contains("not a 2× enlargement of the full-resolution original")); assertTrue(copy.contains("Transparency is flattened onto white"))
    }

    @Test fun exactWholePngPreviewNeedsUncheckedConfirmationAndSharesOnce() {
        val review = review(); val button = review.getButton(AlertDialog.BUTTON_POSITIVE)
        assertFalse(confirm().isChecked); assertFalse(button.isEnabled); button.performClick(); assertTrue(queue.tasks.isEmpty())
        val image = views(review.window!!.decorView).filterIsInstance<ImageView>().single { it.contentDescription?.startsWith("Complete AI upscale") == true }
        val bitmap = (image.drawable as BitmapDrawable).bitmap
        assertEquals(14, bitmap.width); assertEquals(10, bitmap.height)
        val expected = IntArray(140); bitmap.getPixels(expected, 0, 14, 0, 0, 14, 10)
        for (y in 0 until 10) for (x in 0 until 14) assertEquals(original.getPixel(x / 2, y / 2), expected[y * 14 + x])
        queueShare(); button.performClick(); assertEquals(1, queue.tasks.size); queue.next(); button.performClick()
        assertEquals(1, shared.size); assertEquals(1, released); assertTrue(shared.single().isFile)
        val png = ImageIO.read(shared.single()); assertArrayEquals(expected, png.getRGB(0, 0, 14, 10, null, 0, 14))
        assertFalse(original.isRecycled); assertTrue(inputs.single().isRecycled); assertTrue(bitmap.isRecycled)
        val bytes = shared.single().readBytes(); assertArrayEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10), bytes.copyOfRange(0, 8))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Exif"))
    }

    @Test fun queuedStopKeepsParentLeaseUntilWorkerSettlesAndCannotRestart() {
        val retained = dialog().getButton(AlertDialog.BUTTON_POSITIVE); begin()
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, released); retained.performClick(); assertEquals(1, queue.tasks.size)
        queue.next(); assertEquals(1, released); assertEquals(0, prepared); assertEquals(0, calls); assertTrue(files().isEmpty())
    }

    @Test fun stopDuringOwnedInputCreationRecyclesOnlyCopyAfterWorkerSettles() {
        afterInput = { controls.close(); assertEquals(0, released); assertFalse(inputs.single().isRecycled) }
        begin(); queue.next(); assertEquals(1, released); assertEquals(0, calls); assertTrue(inputs.single().isRecycled); assertFalse(original.isRecycled); assertTrue(files().isEmpty())
    }

    @Test fun stopDuringInferenceDiscardsLateResultAndNeverCreatesPng() {
        afterInference = { controls.close(); assertEquals(0, released) }
        begin(); queue.next(); assertEquals(1, calls); assertEquals(1, released); assertTrue(shared.isEmpty()); assertTrue(files().isEmpty()); assertFalse(dialog().isShowing)
    }

    @Test fun parentInvalidatedBeforeWorkNeverCapturesOrRunsModel() {
        begin(); current = false; queue.next(); assertEquals(0, prepared); assertEquals(0, calls); assertEquals(1, released); assertTrue(files().isEmpty()); assertTrue(shared.isEmpty())
    }

    @Test fun parentInvalidatedDuringModelCannotPublishReview() {
        afterInference = { current = false }
        begin(); queue.next(); assertEquals(1, calls); assertFalse(dialog().isShowing); assertEquals(1, released); assertTrue(files().isEmpty())
    }

    @Test fun activityPauseDiscardsReviewAndRetainedShareCannotRun() {
        val review = review(); confirm().isChecked = true; val retained = review.getButton(AlertDialog.BUTTON_POSITIVE)
        controller.pause(); shadowOf(Looper.getMainLooper()).idle(); retained.performClick()
        assertTrue(shared.isEmpty()); assertTrue(queue.tasks.isEmpty()); assertTrue(files().isEmpty()); assertEquals(1, released); assertFalse(review.isShowing)
    }

    @Test fun ownerChangeAtShareButtonCannotQueueExport() {
        val review = review(); confirm().isChecked = true; current = false; review.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(queue.tasks.isEmpty()); assertTrue(shared.isEmpty())
    }

    @Test fun queuedShareRechecksOwnerAndDeletesUnsharedCopy() {
        review(); queueShare(); current = false; queue.next(); assertTrue(shared.isEmpty()); assertTrue(files().isEmpty()); assertEquals(1, released)
    }

    @Test fun sameLengthReviewedFileMutationCannotBeShared() {
        review(); val file = files().single(); val bytes = file.readBytes(); bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte(); file.writeBytes(bytes)
        queueShare(); queue.next(); assertTrue(shared.isEmpty()); assertTrue(files().isEmpty()); assertTrue(messages.single().contains("photo and edits were kept"))
    }

    @Test fun shareFailureDeletesCopyAndReleasesOnce() {
        shareAction = { throw IllegalStateException("Synthetic receiver failure") }
        review(); queueShare(); queue.next(); controls.close()
        assertTrue(shared.isEmpty()); assertTrue(files().isEmpty()); assertEquals(1, released); assertTrue(messages.single().contains("could not be shared"))
    }

    @Test fun rejectedWorkerNeverReadsOriginalAndReleasesParent() {
        controls.close(); released = 0; controls = make(Executor { throw RejectedExecutionException() }); controls.open(); begin()
        assertEquals(0, prepared); assertEquals(0, calls); assertEquals(1, released); assertFalse(original.isRecycled); assertTrue(files().isEmpty())
    }

    @Test fun zoomKeepsFullImageDimensionsAndRetainedZoomIsInertAfterDiscard() {
        val review = review(); val image = views(review.window!!.decorView).filterIsInstance<ImageView>().single { it.contentDescription?.startsWith("Complete AI upscale") == true }
        val zoom = views(review.window!!.decorView).filterIsInstance<Button>().single { it.text == "4×" }; zoom.performClick()
        assertEquals(56, image.layoutParams.width); assertEquals(40, image.layoutParams.height)
        review.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle(); zoom.performClick()
        assertEquals(56, image.layoutParams.width); assertTrue(files().isEmpty()); assertTrue(shared.isEmpty())
    }

    @Test fun cacheCleanupTouchesOnlyExpiredOwnedPrefixAndRefusesFreshBudgetOverflow() {
        val dir = File(activity.cacheDir, "created-images").apply { mkdirs() }
        val peer = File(dir, "noble-peer.png").apply { writeBytes(byteArrayOf(3, 4, 5)); setLastModified(1) }
        val old = File(dir, "noble-upscale-expired.png").apply { writeBytes(byteArrayOf(1)); setLastModified(1) }
        for (i in 0..3) File(dir, "noble-upscale-fresh-$i.png").writeBytes(byteArrayOf(2))
        begin(); queue.next()
        assertFalse(old.exists()); assertArrayEquals(byteArrayOf(3, 4, 5), peer.readBytes()); assertEquals(4, files().size); assertTrue(shared.isEmpty()); assertEquals(1, released)
    }

    @Test fun actualPhotoEditorRejectsInvalidAdmissionBeforeWidgetsAndModel() {
        for (flag in listOf("background", "busy", "export", "reading", "enhancing", "cutting", "missing", "mode", "destroyed", "finishing", "existing")) {
            val root = Robolectric.buildActivity(PhotoEditorActivity::class.java).get()
            fun set(name: String, value: Any?) { PhotoEditorActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(root, value) }
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            try {
                set("bitmap", bitmap); set("accessMode", EditorAccessMode.GUEST_BASIC); set("copyForeground", true)
                when (flag) {
                    "background" -> set("copyForeground", false)
                    "busy" -> set("photoProjectBusy", true)
                    "export" -> set("exportInProgress", true)
                    "reading" -> set("readingText", true)
                    "enhancing" -> set("enhancing", true)
                    "cutting" -> set("cuttingOut", true)
                    "missing" -> set("bitmap", null)
                    "mode" -> set("accessMode", null)
                    "existing" -> set("neuralUpscaleControls", controls)
                    else -> Activity::class.java.getDeclaredField(if (flag == "destroyed") "mDestroyed" else "mFinished").apply { isAccessible = true }.setBoolean(root, true)
                }
                PhotoEditorActivity::class.java.getDeclaredMethod("openNeuralUpscaleCopy").apply { isAccessible = true }.invoke(root)
                assertNull("$flag must not access widgets", PhotoEditorActivity::class.java.getDeclaredField("note").apply { isAccessible = true }.get(root))
                assertTrue(queue.tasks.isEmpty()); assertEquals(0, calls)
            } finally {
                bitmap.recycle()
                (PhotoEditorActivity::class.java.getDeclaredField("worker").apply { isAccessible = true }.get(root) as java.util.concurrent.ExecutorService).shutdownNow()
            }
        }
    }
}
