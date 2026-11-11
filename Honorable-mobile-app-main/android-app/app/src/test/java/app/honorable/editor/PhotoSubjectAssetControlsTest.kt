package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/** Actual Android widgets/files/PNG/ZIP, with explicitly synthetic injected model results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PhotoSubjectAssetControlsTest {
    private class Queue : Executor {
        val pending = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { pending.add(command) }
        fun drain() { while (pending.isNotEmpty()) { pending.remove().run(); shadowOf(Looper.getMainLooper()).idle() } }
    }
    private val lifecycle = Robolectric.buildActivity(Activity::class.java)
    private lateinit var activity: Activity
    private lateinit var controls: PhotoSubjectAssetControls
    private lateinit var original: File
    private val queue = Queue()
    private var current = true
    private var calls = 0
    private var released = 0
    private var stopDuringInference = false
    private var unavailable = false
    private var shared: File? = null
    private var input: Bitmap? = null
    private val messages = mutableListOf<String>()
    private fun pixels() = IntArray(8) { 0xff000000.toInt() or (0x112233 + it * 0x010203) }
    private fun folder() = File(activity.cacheDir, "created-images")
    private fun files() = folder().listFiles().orEmpty().filter { it.name.startsWith("noble-subject-") }
    private fun dialog() = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun check(label: String) = all(dialog().window!!.decorView).filterIsInstance<CheckBox>().single { it.text.toString() == label }
    private fun positive() = dialog().getButton(AlertDialog.BUTTON_POSITIVE)
    private fun previewBitmaps() = all(dialog().window!!.decorView).filterIsInstance<ImageView>()
        .filter { it.contentDescription?.toString()?.startsWith("AI subject ") == true }
        .map { requireNotNull(it.drawable as? BitmapDrawable) { "A cutout preview must remain visible" }.bitmap }
    @Before fun setup() {
        activity = lifecycle.setup().get(); folder().mkdirs(); original = File(folder(), "keep-original-canary.png").apply { writeBytes(byteArrayOf(8, 6, 7, 5)) }
        controls = PhotoSubjectAssetControls(activity, queue,
            prepareInput = { guard -> guard(); Bitmap.createBitmap(pixels(), 4, 2, Bitmap.Config.ARGB_8888).also { input = it } },
            isCurrent = { current }, requireWorkerCurrent = { check(current) },
            share = { file -> shared = file; controls.close() }, message = { messages += it }, released = { released++ },
            infer = { _, image, consent, _, _ ->
                try {
                    calls++; assertTrue(consent)
                    if (unavailable) throw PhotoSubjectSegmenter.ModelUnavailable()
                    val source = PhotoSubjectAssets.Source.capture(4, 2, pixels())
                    val masks = listOf(PhotoSubjectAssets.Mask(0, 0, 2, 2, floatArrayOf(1f, .5f, 0f, .25f)), PhotoSubjectAssets.Mask(2, 0, 2, 1, floatArrayOf(.75f, 1f)), PhotoSubjectAssets.Mask(1, 1, 3, 1, floatArrayOf(1f, 0f, .5f)))
                    if (stopDuringInference) controls.close()
                    PhotoSubjectSegmenter.Batch(source, PhotoSubjectAssets.prepare(source, masks))
                } finally { image.recycle() }
            })
    }
    @After fun cleanup() {
        controls.close(); queue.drain(); shared?.delete(); files().forEach { it.delete() }; original.delete(); lifecycle.pause().stop().destroy()
    }
    private fun detect() { controls.open(); check("Allow AI model setup and SDK diagnostics").isChecked = true; positive().performClick(); queue.drain() }
    @Test fun consentStartsOffAndNoModelOrInputWorkRunsBeforeExplicitFindSubjects() {
        controls.open(); assertFalse(check("Allow AI model setup and SDK diagnostics").isChecked); assertFalse(positive().isEnabled)
        positive().performClick(); assertTrue(queue.pending.isEmpty()); assertEquals(0, calls); assertNull(input)
        check("Allow AI model setup and SDK diagnostics").isChecked = true; val old = positive(); old.performClick(); old.performClick(); assertEquals(1, queue.pending.size)
        queue.drain(); assertEquals(1, calls); assertTrue(input!!.isRecycled); assertNull(shared)
    }
    @Test fun selectedSubsetExportsOnlyReviewedPngsAndSurvivesPauseWhilePrivateStagesAreRemoved() {
        detect(); assertFalse(check("Include subject 1").isChecked); assertFalse(check("Include subject 2").isChecked); assertFalse(check("Include subject 3").isChecked)
        assertEquals(listOf(2 to 2, 2 to 1, 3 to 1), previewBitmaps().map { it.width to it.height })
        assertEquals(0, previewBitmaps().first().getPixel(0, 1))
        positive().performClick(); assertTrue(queue.pending.isEmpty()); assertTrue(files().none { it.extension == "zip" })
        check("Include subject 1").isChecked = true; check("Include subject 3").isChecked = true; positive().performClick(); queue.drain()
        assertNull(shared); assertTrue(dialog().isShowing); assertTrue(files().any { it.extension == "zip" })
        val reviewed = previewBitmaps().map { bitmap -> IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) } }
        assertEquals(2, reviewed.size)
        positive().performClick(); queue.drain(); val zip = requireNotNull(shared)
        assertTrue(zip.isFile); assertEquals(1, released); assertTrue(files().none { it.extension == "png" })
        ZipFile(zip).use { archive ->
            assertEquals(listOf("subjects/001.png", "subjects/003.png", "manifest.json"), archive.entries().toList().map { it.name })
            listOf("subjects/001.png", "subjects/003.png").forEachIndexed { index, name ->
                val bytes = archive.getInputStream(archive.getEntry(name)).use { it.readBytes() }
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                try { val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height); assertArrayEquals(reviewed[index], pixels) }
                finally { bitmap.recycle() }
            }
        }
        assertArrayEquals(byteArrayOf(8, 6, 7, 5), original.readBytes()); controls.close(); assertEquals(1, released); assertTrue(zip.isFile)
    }
    @Test fun stoppingBeforeQueuedWorkPreventsInferenceAndCannotBeRestartedByAnOldButton() {
        controls.open(); check("Allow AI model setup and SDK diagnostics").isChecked = true; val old = positive(); old.performClick()
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle(); old.performClick(); queue.drain()
        assertEquals(0, calls); assertNull(input); assertNull(shared); assertEquals(1, released); assertTrue(files().isEmpty())
    }
    @Test fun lateModelResultAfterCloseCannotAllocateFilesPresentCandidatesOrShare() {
        stopDuringInference = true; detect(); assertEquals(1, calls); assertTrue(input!!.isRecycled)
        assertNull(shared); assertEquals(1, released); assertTrue(files().isEmpty()); assertArrayEquals(byteArrayOf(8, 6, 7, 5), original.readBytes())
    }
    @Test fun unavailableModelFailsClearlyAndLeavesOriginals() {
        unavailable = true; detect(); assertEquals(1, released); assertNull(shared); assertTrue(files().isEmpty()); assertTrue(messages.any { it.contains("Google Play services are unavailable") })
        assertArrayEquals(byteArrayOf(8, 6, 7, 5), original.readBytes())
    }
    @Test fun changedOwnerCannotReachAReviewedShare() {
        detect(); val old = positive(); current = false; check("Include subject 1").isChecked = true; old.performClick(); queue.drain()
        assertNull(shared); assertTrue(files().none { it.extension == "zip" }); controls.close(); assertEquals(1, released)
    }
    @Test fun tamperedPrivatePngRefusesPackCreationAndCleansOnlyOwnedStages() {
        detect(); files().filter { it.extension == "png" }.forEach { it.writeBytes(byteArrayOf(1, 2, 3)) }
        check("Include subject 1").isChecked = true; positive().performClick(); queue.drain()
        assertNull(shared); assertEquals(1, released); assertTrue(files().isEmpty()); assertArrayEquals(byteArrayOf(8, 6, 7, 5), original.readBytes())
    }
    @Test fun actualGoogleTaskKeepsSdkOwnedBitmapUntilSuccessOrFailureCompletes() {
        for (fail in listOf(false, true)) {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888); val completion = TaskCompletionSource<Unit>(); var cleanups = 0
            PhotoSubjectSegmenter.releaseAfter(completion.task) { cleanups++; bitmap.recycle() }
            assertFalse(bitmap.isRecycled); assertEquals(0, cleanups)
            if (fail) completion.setException(IllegalStateException("Synthetic model failure")) else completion.setResult(Unit)
            assertTrue(bitmap.isRecycled); assertEquals(1, cleanups)
        }
        var done = 0; PhotoSubjectSegmenter.releaseAfter(Tasks.forResult(Unit)) { done++ }; assertEquals(1, done)
    }
    @Test fun actualPhotoEditorEntryRefusesBusyBackgroundDestroyedAndUninitializedModesBeforeWidgetsOrModelWork() {
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
                    "existing" -> set("subjectAssetControls", controls)
                    else -> Activity::class.java.getDeclaredField(if (flag == "destroyed") "mDestroyed" else "mFinished").apply { isAccessible = true }.setBoolean(root, true)
                }
                PhotoEditorActivity::class.java.getDeclaredMethod("openSubjectAssetPack").apply { isAccessible = true }.invoke(root)
                assertNull("$flag must not touch status widgets", PhotoEditorActivity::class.java.getDeclaredField("note").apply { isAccessible = true }.get(root))
                assertTrue(queue.pending.isEmpty()); assertEquals(0, calls)
            } finally {
                bitmap.recycle()
                (PhotoEditorActivity::class.java.getDeclaredField("worker").apply { isAccessible = true }.get(root) as java.util.concurrent.ExecutorService).shutdownNow()
            }
        }
    }
}
