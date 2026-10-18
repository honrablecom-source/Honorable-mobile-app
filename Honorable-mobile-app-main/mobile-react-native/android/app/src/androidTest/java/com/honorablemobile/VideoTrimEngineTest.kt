package com.honorablemobile

import android.net.Uri
import android.os.CancellationSignal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class VideoTrimEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun fixture(): File {
        val source = File(context.cacheDir, "synthetic-portrait-audio.mp4")
        instrumentation.context.assets.open("portrait-audio.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        return source
    }

    private fun digest(file: File): List<Byte> {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val bytes = ByteArray(8192)
            while (true) {
                val count = stream.read(bytes)
                if (count < 0) break
                hash.update(bytes, 0, count)
            }
        }
        return hash.digest().toList()
    }

    @Test fun portraitAudioExportPreservesSourceAndReportsKeyframeTiming() {
        val source = fixture()
        val output = File(context.cacheDir, "synthetic-trimmed.mp4").apply { createNewFile() }
        val before = digest(source)
        try {
            val result = VideoTrimEngine.trim(context, Uri.fromFile(source), Uri.fromFile(output), 1500, 2600, CancellationSignal())
            assertTrue("A preceding keyframe should be reported", result.alignedStartMs in 900..1500)
            assertTrue("The end must be within the requested range", result.actualEndMs in result.alignedStartMs..2600)
            assertEquals(90, result.rotationDegrees)
            assertTrue("Synthetic audio should survive", result.audioIncluded)
            assertTrue("Output should contain media", output.length() > 1000)
            assertEquals("Source bytes changed", before, digest(source))
        } finally { output.delete(); source.delete() }
    }

    @Test fun rejectsSameSourceAndDestinationBeforeWriting() {
        val source = fixture()
        val before = digest(source)
        try {
            try {
                VideoTrimEngine.trim(context, Uri.fromFile(source), Uri.fromFile(source), 0, 1000, CancellationSignal())
                fail("Source identity must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(before, digest(source))
        } finally { source.delete() }
    }

    @Test fun cancelledExportDoesNotChangeSource() {
        val source = fixture()
        val output = File(context.cacheDir, "synthetic-cancelled.mp4").apply { createNewFile() }
        val before = digest(source)
        val cancellation = CancellationSignal().apply { cancel() }
        try {
            try {
                VideoTrimEngine.trim(context, Uri.fromFile(source), Uri.fromFile(output), 0, 1000, cancellation)
                fail("Cancelled export must fail")
            } catch (_: VideoTrimFailure) { }
            assertEquals(before, digest(source))
        } finally { output.delete(); source.delete() }
    }
}
