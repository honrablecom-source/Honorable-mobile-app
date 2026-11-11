package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoTextPrivacyControlsTest {
    private class Queue : Executor { val tasks = ArrayDeque<Runnable>(); override fun execute(command: Runnable) { tasks.add(command) }; fun next() { tasks.removeFirst().run(); shadowOf(Looper.getMainLooper()).idle() } }
    private lateinit var activityController: org.robolectric.android.controller.ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var original: Bitmap
    private lateinit var input: PhotoTextPrivacyControls.Input
    private lateinit var queue: Queue
    private lateinit var controls: PhotoTextPrivacyControls
    private var current = true
    private val shared = mutableListOf<File>()
    private val notices = mutableListOf<String?>()
    @Before fun start() {
        activityController = Robolectric.buildActivity(Activity::class.java).setup(); activity = activityController.get()
        original = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(70, 100, 130)) }
        input = PhotoTextPrivacyControls.capture(original, listOf(PhotoTextPrivacy.Box(2, 3, 7, 8), PhotoTextPrivacy.Box(11, 1, 15, 4))) { check(current) }
        queue = Queue(); controls = PhotoTextPrivacyControls(activity, input, { current }, { notices.add(it) }, queue, { shared.add(it) })
        controls.open(); shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun stop() { controls.close(); while (queue.tasks.isNotEmpty()) queue.next(); original.recycle(); activityController.pause().stop().destroy() }
    private fun dialog() = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun check(label: String): CheckBox = views(dialog().window!!.decorView).filterIsInstance<CheckBox>().single { it.text.toString() == label }
    private fun prepared() { queue.next(); assertTrue(dialog().isShowing) }
    private fun proof(): AlertDialog {
        prepared(); check("Cover text region 1").isChecked = true
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle(); queue.next()
        return dialog()
    }
    @Test fun captureOwnsIndependentBitmapAndSelectionStartsEmptyWithoutFileIo() {
        assertNotSame(original, input.bitmap); controls.open(); assertEquals(1, queue.tasks.size)
        prepared(); assertTrue(input.bitmap.isRecycled); assertFalse(original.isRecycled)
        assertFalse(check("Cover text region 1").isChecked); assertFalse(check("Cover text region 2").isChecked)
        val preview = dialog().getButton(AlertDialog.BUTTON_POSITIVE); assertFalse(preview.isEnabled)
        preview.performClick(); assertEquals(0, queue.tasks.size); assertTrue(shared.isEmpty())
        assertFalse(File(activity.cacheDir, "created-images").exists())
    }
    @Test fun reviewedPngNeedsFreshConfirmationAndSharesExactPreviewOnlyOnce() {
        val proof = proof(); val confirm = check("I reviewed the whole copy for missed private content")
        val share = proof.getButton(AlertDialog.BUTTON_POSITIVE); assertFalse(confirm.isChecked); assertFalse(share.isEnabled)
        share.performClick(); assertEquals(0, queue.tasks.size)
        val view = views(proof.window!!.decorView).filterIsInstance<ImageView>().single { it.contentDescription?.toString()?.startsWith("Photo preview;") == true }
        val bitmap = (view.drawable as android.graphics.drawable.BitmapDrawable).bitmap
        val expected = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(expected, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        confirm.isChecked = true; share.performClick(); share.performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, queue.tasks.size); queue.next(); share.performClick()
        assertEquals(1, shared.size); val file = shared.single(); assertTrue(file.name.startsWith("noble-text-privacy-")); assertTrue(file.isFile)
        val decoded = ImageIO.read(file); assertEquals(16, decoded.width); assertEquals(12, decoded.height)
        assertArrayEquals(expected, decoded.getRGB(0, 0, 16, 12, null, 0, 16))
        assertTrue(notices.any { it?.contains("copy shared") == true }); assertFalse(original.isRecycled)
    }
    @Test fun queuedStopDoesNotRecycleInputUnderAReaderAndSuppressesLatePublication() {
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertFalse(input.bitmap.isRecycled); assertFalse(dialog().isShowing)
        queue.next(); assertTrue(input.bitmap.isRecycled); assertFalse(dialog().isShowing); assertTrue(shared.isEmpty())
        assertFalse(original.isRecycled)
    }
    @Test fun pauseClosesReviewAndRetainedPreviewCannotRestartIt() {
        prepared(); check("Cover text region 1").isChecked = true
        val retained = dialog().getButton(AlertDialog.BUTTON_POSITIVE)
        activityController.pause(); shadowOf(Looper.getMainLooper()).idle(); retained.performClick()
        assertFalse(dialog().isShowing); assertTrue(queue.tasks.isEmpty()); assertTrue(shared.isEmpty())
    }
    @Test fun ownerChangeBeforeWorkerCompletionCannotShowRegions() {
        current = false; queue.next()
        assertFalse(dialog().isShowing); assertTrue(input.bitmap.isRecycled); assertTrue(shared.isEmpty()); assertFalse(original.isRecycled)
    }
    @Test fun changingSelectionInvalidatesOldProofAndResetsConfirmation() {
        val old = proof(); val retained = old.getButton(AlertDialog.BUTTON_POSITIVE)
        check("I reviewed the whole copy for missed private content").isChecked = true
        old.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        retained.performClick(); assertTrue(queue.tasks.isEmpty())
        check("Cover text region 2").isChecked = true
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle(); queue.next()
        assertFalse(check("I reviewed the whole copy for missed private content").isChecked); assertTrue(shared.isEmpty())
    }
    @Test fun cancellationDuringQueuedExportDeletesUnsharedCopy() {
        proof(); check("I reviewed the whole copy for missed private content").isChecked = true
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle(); queue.next()
        assertTrue(shared.isEmpty()); assertTrue(File(activity.cacheDir, "created-images").listFiles().isNullOrEmpty())
    }
    @Test fun transparentCaptureIsWhiteMattedAndBoundedWithoutChangingOriginal() {
        val image = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val copy = PhotoTextPrivacyControls.capture(image, listOf(PhotoTextPrivacy.Box(10, 10, 20, 20))) {}
        assertEquals(1024, copy.bitmap.width); assertEquals(512, copy.bitmap.height); assertEquals(Color.WHITE, copy.bitmap.getPixel(500, 300)); assertEquals(Color.TRANSPARENT, image.getPixel(500, 300))
        copy.close(); assertTrue(copy.bitmap.isRecycled); assertFalse(image.isRecycled); image.recycle()
    }
    @Test fun readerCopyRetainsSensitiveClipboardBehaviorAndDoesNotOpenRedaction() {
        controls.close(); queue.next(); var calls = 0
        PhotoTextReader.showRecognized(activity, original, "Synthetic local text", listOf(PhotoTextPrivacy.Box(2, 3, 7, 8)), { current }, { notices.add(it) }, { _, _, _ -> calls++ })
        shadowOf(Looper.getMainLooper()).idle() // Deliver onShow before a synthetic button click.
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals("Synthetic local text", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertTrue(clipboard.primaryClipDescription!!.extras!!.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE))
        assertEquals(0, calls); assertFalse(original.isRecycled)
    }
    @Test fun readerTransfersIndependentCopyAfterSdkSnapshotIsRecycled() {
        controls.close(); queue.next(); var captured: PhotoTextPrivacyControls.Input? = null
        PhotoTextReader.showRecognized(activity, original, "Synthetic local text", listOf(PhotoTextPrivacy.Box(2, 3, 7, 8)), { current }, { notices.add(it) }, { owned, valid, _ -> assertTrue(valid()); captured = owned })
        shadowOf(Looper.getMainLooper()).idle() // Deliver onShow before a synthetic button click.
        val retained = dialog().getButton(AlertDialog.BUTTON_NEUTRAL)
        original.recycle(); retained.performClick(); shadowOf(Looper.getMainLooper()).idle()
        val transferred = requireNotNull(captured); assertFalse(transferred.bitmap.isRecycled)
        assertEquals(Color.rgb(70, 100, 130), transferred.bitmap.getPixel(1, 1))
        retained.performClick(); assertSame(transferred, captured); transferred.close()
    }
    @Test fun readerDoesNotTruncateDenseDetectionsOrDisableOriginalCopy() {
        controls.close(); queue.next(); var calls = 0
        PhotoTextReader.showRecognized(activity, original, "Synthetic dense text", List(101) { PhotoTextPrivacy.Box(1, 1, 2, 2) }, { current }, {}, { _, _, _ -> calls++ })
        shadowOf(Looper.getMainLooper()).idle() // Deliver onShow before a synthetic button click.
        assertTrue(dialog().getButton(AlertDialog.BUTTON_NEUTRAL)?.visibility != View.VISIBLE)
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals("Synthetic dense text", clipboard.primaryClip!!.getItemAt(0).text.toString()); assertEquals(0, calls)
    }
    @Test fun readerPauseDisposesCopyAndRetainedActionCannotPublishAfterResume() {
        controls.close(); queue.next(); var calls = 0
        PhotoTextReader.showRecognized(activity, original, "Synthetic local text", listOf(PhotoTextPrivacy.Box(2, 3, 7, 8)), { current }, {}, { _, _, _ -> calls++ })
        shadowOf(Looper.getMainLooper()).idle() // Deliver onShow before a synthetic button click.
        val retained = dialog().getButton(AlertDialog.BUTTON_NEUTRAL)
        activityController.pause(); shadowOf(Looper.getMainLooper()).idle(); activityController.resume()
        retained.performClick(); assertEquals(0, calls); assertFalse(dialog().isShowing)
    }

    private fun finishExportOffUi(): File {
        proof(); check("I reviewed the whole copy for missed private content").isChecked = true
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        val task = queue.tasks.removeFirst(); val thread = Thread(task); thread.start(); thread.join(10_000); assertFalse(thread.isAlive)
        return File(activity.cacheDir, "created-images").listFiles()!!.single { it.name.startsWith("noble-text-privacy-") }
    }
    @Test fun ownerChangeAfterPrivateWriteDeletesFileBeforeAnyShare() {
        val file = finishExportOffUi(); assertTrue(file.isFile); current = false
        shadowOf(Looper.getMainLooper()).idle(); assertTrue(shared.isEmpty()); assertFalse(file.exists()); assertFalse(original.isRecycled)
    }
    @Test fun privateFileTamperBetweenEncodingAndUiHandoffRefusesSharing() {
        val file = finishExportOffUi(); val bytes = file.readBytes(); bytes[20] = (bytes[20].toInt() xor 1).toByte(); file.writeBytes(bytes)
        shadowOf(Looper.getMainLooper()).idle(); assertTrue(shared.isEmpty()); assertFalse(file.exists()); assertFalse(original.isRecycled)
    }
    @Test fun temporaryQuotaRefusesNinthCopyAndNeverDeletesAnotherFeatureFile() {
        val dir = File(activity.cacheDir, "created-images").apply { mkdirs() }
        val peer = File(dir, "noble-subject-synthetic.png").apply { writeBytes(byteArrayOf(3, 4, 5)) }
        repeat(8) { File(dir, "noble-text-privacy-synthetic-$it.png").writeBytes(byteArrayOf(1)) }
        proof(); check("I reviewed the whole copy for missed private content").isChecked = true
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle(); queue.next()
        assertTrue(shared.isEmpty()); assertArrayEquals(byteArrayOf(3, 4, 5), peer.readBytes())
        assertEquals(8, dir.listFiles()!!.count { it.name.startsWith("noble-text-privacy-") })
    }

    @Test fun actualEditorRefusesBackgroundReadBeforeWidgetsAndSdkWork() {
        val root = Robolectric.buildActivity(PhotoEditorActivity::class.java).get()
        try {
            PhotoEditorActivity::class.java.getDeclaredMethod("readText").apply { isAccessible = true }.invoke(root)
            assertNull(PhotoEditorActivity::class.java.getDeclaredField("note").apply { isAccessible = true }.get(root))
            val gate = PhotoEditorActivity::class.java.getDeclaredField("textReadGate").apply { isAccessible = true }.get(root) as PhotoTextReadGate
            assertFalse(gate.busy)
        } finally { (PhotoEditorActivity::class.java.getDeclaredField("worker").apply { isAccessible = true }.get(root) as java.util.concurrent.ExecutorService).shutdownNow() }
    }
    @Test fun actualEditorStaleCallbackCannotClearNewReadBusyOrTouchWidgets() {
        val root = Robolectric.buildActivity(PhotoEditorActivity::class.java).get()
        try {
            val gate = PhotoEditorActivity::class.java.getDeclaredField("textReadGate").apply { isAccessible = true }.get(root) as PhotoTextReadGate
            val old = requireNotNull(gate.begin(1, true)); gate.settle(old); val fresh = requireNotNull(gate.begin(3, true))
            val busy = PhotoEditorActivity::class.java.getDeclaredField("readingText").apply { isAccessible = true }; busy.setBoolean(root, true)
            val finish = PhotoEditorActivity::class.java.getDeclaredMethod("finishTextRead", PhotoTextReadGate.Request::class.java, String::class.java, kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
            finish.invoke(root, old, null, { false }); assertTrue(busy.getBoolean(root)); assertTrue(gate.busy)
            finish.invoke(root, fresh, null, { false }); assertFalse(busy.getBoolean(root)); assertFalse(gate.busy)
            assertTrue(gate.isCurrent(fresh, 3, true)); assertNull(PhotoEditorActivity::class.java.getDeclaredField("note").apply { isAccessible = true }.get(root))
        } finally { (PhotoEditorActivity::class.java.getDeclaredField("worker").apply { isAccessible = true }.get(root) as java.util.concurrent.ExecutorService).shutdownNow() }
    }

}
