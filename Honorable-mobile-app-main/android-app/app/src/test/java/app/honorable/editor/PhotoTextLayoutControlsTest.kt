package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
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
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoTextLayoutControlsTest {
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun next() { tasks.removeFirst().run(); shadowOf(Looper.getMainLooper()).idle() }
    }
    private lateinit var lifecycle: org.robolectric.android.controller.ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var original: Bitmap
    private lateinit var queue: Queue
    private lateinit var controls: PhotoTextLayoutControls
    private val base = PhotoEdit()
    private var current = true
    private var released = 0
    private var reads = 0
    private val inputs = mutableListOf<Bitmap>()
    private val snapshots = mutableListOf<Bitmap>()
    private val applied = mutableListOf<PhotoTextLayout.Plan>()
    private val messages = mutableListOf<String>()
    private var readerOverride: ((Bitmap, () -> Unit, () -> Unit) -> PhotoTextLayout.Batch)? = null
    private var applyResult = true
    private fun lines() = listOf(
        PhotoTextLayout.RawLine("FIRST", PhotoTextLayout.Box(20, 30, 180, 75)),
        PhotoTextLayout.RawLine("SECOND", PhotoTextLayout.Box(200, 130, 380, 180)))
    @Before fun start() {
        lifecycle = Robolectric.buildActivity(Activity::class.java).setup(); activity = lifecycle.get(); queue = Queue()
        original = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(0xffcc8877.toInt()) }
        controls = make(); controls.open(); idle()
    }
    private fun make(edit: PhotoEdit = base, executor: Executor = queue) = PhotoTextLayoutControls(activity, edit, executor,
        prepareInput = { check -> check(); original.copy(Bitmap.Config.ARGB_8888, false).also { inputs += it } },
        isCurrent = { current }, requireWorkerCurrent = { check(current) },
        apply = { plan -> applied += plan; applyResult }, message = { messages += it }, released = { released++ },
        read = { bitmap, check, settled ->
            reads++; snapshots += bitmap
            readerOverride?.invoke(bitmap, check, settled) ?: try { check(); PhotoTextLayout.Batch.capture(bitmap.width, bitmap.height, lines()) }
                finally { bitmap.recycle(); settled() }
        })
    @After fun finish() { controls.close(); while (queue.tasks.isNotEmpty()) queue.next(); original.recycle(); lifecycle.pause().stop().destroy() }
    private fun idle() { shadowOf(Looper.getMainLooper()).idle() }
    private fun dialog() = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun check(label: String) = views(dialog().window!!.decorView).filterIsInstance<CheckBox>().single { it.text == label }
    private fun field(id: Int) = views(dialog().window!!.decorView).filterIsInstance<EditText>().single { it.hint == "Correct line $id" }
    private fun recognize() { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); queue.next() }
    private fun proof(): AlertDialog {
        recognize(); check("Use line 1").isChecked = true; field(1).setText("Corrected & <title>")
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); queue.next(); return dialog()
    }

    @Test fun setupDoesNoReadingAndAllCandidatesStartUnchecked() {
        assertEquals(0, reads); assertTrue(queue.tasks.isEmpty()); controls.open(); assertEquals(0, reads)
        recognize(); assertFalse(check("Use line 1").isChecked); assertFalse(check("Use line 2").isChecked)
        assertFalse(dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertEquals("FIRST", field(1).text.toString()); assertEquals("SECOND", field(2).text.toString())
        assertTrue(inputs.single().isRecycled); assertTrue(snapshots.single().isRecycled); assertFalse(original.isRecycled)
    }

    @Test fun exactPreviewMatchesParentRendererAndFreshConfirmationInsertsOnlyChosenTextOnce() {
        val proof = proof(); val positive = proof.getButton(AlertDialog.BUTTON_POSITIVE)
        val confirmation = check("I reviewed all selected wording and the complete layout")
        assertFalse(confirmation.isChecked); assertFalse(positive.isEnabled); positive.performClick(); assertTrue(applied.isEmpty())
        val image = views(proof.window!!.decorView).filterIsInstance<ImageView>().single { it.contentDescription?.startsWith("Complete editable lettering preview") == true }
        val preview = (image.drawable as BitmapDrawable).bitmap
        val pixels = IntArray(preview.width * preview.height); preview.getPixels(pixels, 0, preview.width, 0, 0, preview.width, preview.height)
        confirmation.isChecked = true; positive.performClick(); positive.performClick(); idle()
        val plan = applied.single(); assertEquals(listOf(1), plan.selected.map { it.id }); assertEquals("Corrected & <title>", plan.selected.single().text)
        assertEquals(2, plan.inserted.size); assertTrue(base.layers.objects.isEmpty()); assertEquals(1, released)
        val actual = PhotoRasterRenderer.render(original, plan.edit, null)
        try { val expected = IntArray(actual.width * actual.height); actual.getPixels(expected, 0, actual.width, 0, 0, actual.width, actual.height); assertArrayEquals(expected, pixels) }
        finally { actual.recycle() }
        assertTrue(preview.isRecycled); assertFalse(original.isRecycled)
    }

    @Test fun backKeepsCorrectionsButInvalidatesOldApplyAndResetsConfirmation() {
        val first = proof(); val old = first.getButton(AlertDialog.BUTTON_POSITIVE); check("I reviewed all selected wording and the complete layout").isChecked = true
        first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle(); old.performClick()
        assertTrue(applied.isEmpty()); assertEquals("Corrected & <title>", field(1).text.toString()); assertTrue(check("Use line 1").isChecked)
        field(1).setText("Changed wording"); dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); queue.next()
        assertFalse(check("I reviewed all selected wording and the complete layout").isChecked)
        check("I reviewed all selected wording and the complete layout").isChecked = true; dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("Changed wording", applied.single().selected.single().text)
    }

    @Test fun invalidCorrectionReturnsToFormWithoutSilentlyTruncatingOrLosingText() {
        recognize(); check("Use line 1").isChecked = true; field(1).setText("A".repeat(250))
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); queue.next()
        assertEquals(250, field(1).text.length); assertTrue(check("Use line 1").isChecked); assertTrue(applied.isEmpty()); assertEquals(0, released)
        field(1).setText("Fixed"); dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); queue.next()
        check("I reviewed all selected wording and the complete layout").isChecked = true; dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("Fixed", applied.single().selected.single().text)
    }

    @Test fun queuedStopRetainsBusyLeaseUntilWorkerSettlesWithoutReading() {
        val retained = dialog().getButton(AlertDialog.BUTTON_POSITIVE); retained.performClick(); idle()
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle(); retained.performClick()
        assertEquals(0, released); assertEquals(1, queue.tasks.size); queue.next()
        assertEquals(0, reads); assertEquals(1, released); assertTrue(applied.isEmpty())
    }

    @Test fun sdkPendingStopCannotReleaseParentOrRecycleSdkInputUntilActualSettlement() {
        var settleSdk: (() -> Unit)? = null
        readerOverride = { bitmap, _, settled ->
            settleSdk = { bitmap.recycle(); settled() }
            controls.close(); assertFalse(bitmap.isRecycled); throw CancellationException()
        }
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); queue.next()
        assertEquals(0, released); assertFalse(snapshots.single().isRecycled); assertTrue(applied.isEmpty())
        requireNotNull(settleSdk).invoke(); idle(); controls.close(); assertEquals(1, released); assertTrue(snapshots.single().isRecycled)
    }

    @Test fun staleOwnerBeforeQueuedRecognitionNeverReadsOrPublishes() {
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(); current = false; queue.next()
        assertEquals(0, reads); assertEquals(1, released); assertFalse(dialog().isShowing); assertTrue(applied.isEmpty())
    }

    @Test fun ownerChangedAfterReviewCannotApplyAndCanDismissSafely() {
        val review = proof(); check("I reviewed all selected wording and the complete layout").isChecked = true
        current = false; review.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle()
        assertTrue(applied.isEmpty()); assertFalse(review.isShowing); assertEquals(1, released)
    }

    @Test fun pauseClosesProofAndRetainedButtonsCannotApply() {
        val review = proof(); val retained = review.getButton(AlertDialog.BUTTON_POSITIVE); check("I reviewed all selected wording and the complete layout").isChecked = true
        lifecycle.pause(); idle(); retained.performClick(); assertTrue(applied.isEmpty()); assertFalse(review.isShowing); assertEquals(1, released)
    }

    @Test fun rejectedWorkerReleasesWithoutInputOrModelWork() {
        controls.close(); released = 0; controls = make(executor = Executor { throw RejectedExecutionException() }); controls.open()
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle()
        assertEquals(0, reads); assertTrue(inputs.isEmpty()); assertEquals(1, released); assertTrue(messages.single().contains("kept"))
    }

    @Test fun parentRejectsApplyWithoutRetryOrAnySecondUse() {
        applyResult = false; val review = proof(); check("I reviewed all selected wording and the complete layout").isChecked = true
        val retained = review.getButton(AlertDialog.BUTTON_POSITIVE); retained.performClick(); retained.performClick()
        assertEquals(1, applied.size); assertEquals(1, released); assertTrue(messages.single().contains("changed"))
    }

    @Test fun fullExistingLayerBudgetRefusesRecognitionAndPreservesObjects() {
        controls.close(); released = 0
        val existing = PhotoEdit(layers = PhotoLayers(List(31) { PhotoObject("old-$it", PhotoObjectType.RECTANGLE) }))
        controls = make(existing); controls.open(); assertEquals(0, reads); assertEquals(1, released); assertEquals(31, existing.layers.objects.size)
    }

    @Test fun sourceAlphaIsMattedWhiteOnIndependentSdkSnapshot() {
        original.eraseColor(Color.TRANSPARENT)
        readerOverride = { bitmap, _, settled ->
            try { assertNotSame(original, bitmap); assertEquals(-1, bitmap.getPixel(0, 0)); PhotoTextLayout.Batch.capture(bitmap.width, bitmap.height, lines()) }
            finally { bitmap.recycle(); settled() }
        }
        recognize(); assertEquals(0, original.getPixel(0, 0)); assertFalse(original.isRecycled)
    }

    private fun rootField(name: String) = PhotoEditorActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Test fun actualEntryOpensForCurrentGuestAndRejectsChangedOwnershipBeforeAnyOcr() {
        for (flag in listOf("cancel", "generation", "mode", "source", "state", "bitmap", "mask", "scope", "originals")) {
            val root = Robolectric.buildActivity(PhotoEditorActivity::class.java).get()
            val bitmap = original.copy(Bitmap.Config.ARGB_8888, false); var extra: Bitmap? = null
            val executor = rootField("worker").get(root) as ExecutorService
            try {
                val source = Uri.parse("content://generated/source")
                val guest = "00000000-0000-4000-8000-000000000001"
                val scope = requireNotNull(EditorAccessMode.GUEST_BASIC.lifecycleScope(source.toString(), guest) { null })
                rootField("bitmap").set(root, bitmap); rootField("source").set(root, source); rootField("guestInstance").set(root, guest)
                rootField("edit").set(root, base); rootField("renderedEdit").set(root, base); rootField("loadedScope").set(root, scope)
                rootField("accessMode").set(root, EditorAccessMode.GUEST_BASIC); rootField("copyForeground").setBoolean(root, true)
                rootField("note").set(root, TextView(root))
                PhotoEditorActivity::class.java.getDeclaredMethod("openAiTextLayout").apply { isAccessible = true }.invoke(root)
                val owner = rootField("textLayoutControls").get(root) as? PhotoTextLayoutControls
                assertNotNull("$flag: the genuine current entry must open", owner)
                assertTrue(rootField("photoProjectBusy").getBoolean(root)); assertTrue(dialog().isShowing)
                var called = 0
                val probe: (Bitmap, () -> Unit, () -> Unit) -> PhotoTextLayout.Batch = { input, check, settled ->
                    called++
                    try { check(); PhotoTextLayout.Batch.capture(input.width, input.height, lines()) }
                    finally { input.recycle(); settled() }
                }
                PhotoTextLayoutControls::class.java.getDeclaredField("read").apply { isAccessible = true }.set(owner, probe)
                when (flag) {
                    "generation" -> rootField("copyGeneration").setLong(root, 1L)
                    "mode" -> rootField("accessMode").set(root, EditorAccessMode.ACCOUNT)
                    "source" -> rootField("source").set(root, Uri.parse("content://generated/different"))
                    "state" -> rootField("edit").set(root, base.copy())
                    "bitmap" -> { extra = bitmap.copy(Bitmap.Config.ARGB_8888, false); rootField("bitmap").set(root, extra) }
                    "mask" -> { extra = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); rootField("personMask").set(root, extra) }
                    "scope" -> rootField("loadedScope").set(root, "0".repeat(64))
                    "originals" -> rootField("protectedOriginals").set(root, listOf(Uri.parse("content://generated/protected")))
                }
                dialog().getButton(if (flag == "cancel") AlertDialog.BUTTON_NEGATIVE else AlertDialog.BUTTON_POSITIVE).performClick()
                executor.submit {}.get(10, TimeUnit.SECONDS); idle()
                assertEquals("$flag must not start OCR", 0, called)
                assertNull(rootField("textLayoutControls").get(root)); assertFalse(rootField("photoProjectBusy").getBoolean(root)); assertFalse(bitmap.isRecycled)
            } finally {
                (rootField("textLayoutControls").get(root) as? PhotoTextLayoutControls)?.close()
                executor.shutdownNow(); extra?.recycle(); bitmap.recycle()
            }
        }
    }

    @Test fun actualRootCommitsOneHistoryEntryAndActualUndoRestoresPreviousEdit() {
        val root = Robolectric.buildActivity(PhotoEditorActivity::class.java).get(); val bitmap = original.copy(Bitmap.Config.ARGB_8888, false)
        val worker = rootField("worker").get(root) as ExecutorService
        val plan = PhotoTextLayout.prepare(base, PhotoTextLayout.Batch.capture(400, 240, lines()), mapOf(1 to "HELLO"), PhotoTextLayoutTypography)
        try {
            rootField("bitmap").set(root, bitmap); rootField("edit").set(root, base); rootField("copyForeground").setBoolean(root, true)
            rootField("accessMode").set(root, EditorAccessMode.GUEST_BASIC); rootField("photoProjectBusy").setBoolean(root, true); rootField("textLayoutControls").set(root, controls)
            rootField("image").set(root, ImageView(root)); rootField("note").set(root, TextView(root))
            @Suppress("UNCHECKED_CAST") val undo = rootField("undo").get(root) as MutableList<PhotoEdit>
            @Suppress("UNCHECKED_CAST") val redo = rootField("redo").get(root) as MutableList<PhotoEdit>
            redo += PhotoEdit(brightness = 3f)
            val commit = PhotoEditorActivity::class.java.getDeclaredMethod("applyPhotoTextLayout", PhotoTextLayoutControls::class.java, PhotoTextLayout.Plan::class.java, kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
            assertEquals(true, commit.invoke(root, controls, plan, { true })); assertSame(plan.edit, rootField("edit").get(root)); assertEquals(listOf(base), undo); assertTrue(redo.isEmpty())
            assertEquals(false, commit.invoke(root, controls, plan, { true })); assertEquals(1, undo.size)
            PhotoEditorActivity::class.java.getDeclaredMethod("navigateHistory", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(root, false)
            worker.submit {}.get(10, TimeUnit.SECONDS); idle()
            assertSame(base, rootField("edit").get(root)); assertTrue(undo.isEmpty()); assertEquals(listOf(plan.edit), redo); assertEquals(base, rootField("renderedEdit").get(root))
            assertFalse(bitmap.isRecycled)
        } finally { worker.shutdownNow(); (rootField("rendered").get(root) as? Bitmap)?.recycle(); bitmap.recycle() }
    }

    @Test fun actualRootRejectsEqualButDifferentBaseAndFailedOwnerBeforeClearingBusy() {
        val root = Robolectric.buildActivity(PhotoEditorActivity::class.java).get(); val bitmap = original.copy(Bitmap.Config.ARGB_8888, false)
        try {
            rootField("bitmap").set(root, bitmap); rootField("copyForeground").setBoolean(root, true); rootField("accessMode").set(root, EditorAccessMode.GUEST_BASIC)
            rootField("photoProjectBusy").setBoolean(root, true); rootField("textLayoutControls").set(root, controls)
            val plan = PhotoTextLayout.prepare(base, PhotoTextLayout.Batch.capture(400, 240, lines()), mapOf(1 to "HELLO"), PhotoTextLayoutTypography)
            val commit = PhotoEditorActivity::class.java.getDeclaredMethod("applyPhotoTextLayout", PhotoTextLayoutControls::class.java, PhotoTextLayout.Plan::class.java, kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
            rootField("edit").set(root, base.copy()); assertEquals(false, commit.invoke(root, controls, plan, { true }))
            rootField("edit").set(root, base); assertEquals(false, commit.invoke(root, controls, plan, { false }))
            assertTrue(rootField("photoProjectBusy").getBoolean(root)); assertSame(base, rootField("edit").get(root)); assertNull(rootField("note").get(root))
        } finally { (rootField("worker").get(root) as ExecutorService).shutdownNow(); bitmap.recycle() }
    }
}
