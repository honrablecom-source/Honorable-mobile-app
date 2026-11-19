package com.honorablemobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.EditorAccessMode
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class BeginnerPhotoStoryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }

    @Test fun reorderThreePhotosAndCreateNineSecondProject() {
        val id = UUID.randomUUID().toString().take(8)
        val folder = File(context.cacheDir, "created-images/story-$id")
        check(folder.mkdirs())
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val inputs = colors.mapIndexed { index, color ->
            File(folder, "scene-$index.png").also { file ->
                val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(color)
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
            }
        }
        val originals = inputs.map { it.readBytes() }
        val sources = inputs.map { FileProvider.getUriForFile(context, context.packageName + ".creation", it).toString() }
        val name = "Story test $id"
        val store = PhotoVideoProjectStore(context)
        val creator = instrumentation.startActivitySync(Intent(context, PhotoVideoCreatorActivity::class.java)
            .putExtra(EditorAccessMode.EXTRA, EditorAccessMode.GUEST_BASIC.name)
            .putStringArrayListExtra(PhotoVideoEntry.EXTRA_PHOTOS, ArrayList(sources))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val monitor = instrumentation.addMonitor(HonorableVideoEditorActivity::class.java.name, null, false)
        try {
            fun widgets() = descendants(creator.window.decorView)
            instrumentation.runOnMainSync {
                widgets().filterIsInstance<EditText>().single { it.contentDescription == "Video project name" }.setText(name)
                widgets().filterIsInstance<Spinner>().single { it.contentDescription == "Default photo hold" }.setSelection(0)
                widgets().filterIsInstance<Button>().first { it.contentDescription?.startsWith("Later photo 1:") == true }.performClick()
                val labels = widgets().filterIsInstance<TextView>().map { it.text.toString() }.toList()
                for (time in listOf(0, 3, 6)) assertTrue(labels.any { it.endsWith("\nStarts at $time s") })
                widgets().filterIsInstance<Button>().single { it.text == "Create editable video project" }.performClick()
            }
            val editor = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 120000)) {
                "The creator did not open the prepared project for editing"
            }
            try {
                val project = store.load(store.list().single { it.name == name }.id)
                assertEquals(3, project.assets.size)
                for ((index, channel) in listOf(1, 0, 2).withIndex()) {
                    val reader = MediaMetadataRetriever()
                    try {
                        reader.setDataSource(store.sourceFile(project.id, index).absolutePath)
                        val duration = requireNotNull(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
                        assertTrue("Wrong hold time", duration in 2900..3100)
                        val frame = requireNotNull(reader.getFrameAtTime(500000))
                        try {
                            val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
                            val channels = listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                            assertTrue("Decoded scene order changed", channels[channel] > 180 && channels.filterIndexed { i, _ -> i != channel }.all { it < 80 })
                        } finally { frame.recycle() }
                    } finally { reader.release() }
                }
                inputs.forEachIndexed { index, file -> assertArrayEquals(originals[index], file.readBytes()) }
            } finally { instrumentation.runOnMainSync { editor.finish() } }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { creator.finish() }
            store.list().filter { it.name == name }.forEach { store.delete(it.id) {} }
            inputs.forEach { it.delete() }
            folder.delete()
        }
    }
}
