package com.honorablemobile

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class VideoCompositionExporterTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun fixture(): File = File.createTempFile("composition-source-", ".mp4", context.cacheDir).also { file ->
        instrumentation.context.assets.open("portrait-audio.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }

    private fun digest(file: File): List<Byte> = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()

    @Test fun copiedPortraitAudioDocumentIsReopenedAndSourceIsPreserved() {
        val source = fixture()
        val output = File.createTempFile("composition-output-", ".mp4", context.cacheDir)
        val original = digest(source)
        try {
            VideoCompositionExporter.copyRenderedToDocument(
                context, Uri.fromFile(source), Uri.fromFile(output), source,
                listOf(VideoSegment(0, 3000)), AtomicBoolean(false),
            )
            assertEquals(original, digest(source))
            assertEquals(original, digest(output))
        } finally { output.delete(); source.delete() }
    }

    @Test fun cancellationDuringCopyReportsRemainingPartialFileAndPreservesSource() {
        val source = fixture()
        val output = File.createTempFile("composition-cancelled-", ".mp4", context.cacheDir)
        val original = digest(source)
        val cancelled = AtomicBoolean(false)
        try {
            try {
                VideoCompositionExporter.copyRenderedToDocument(
                    context, Uri.fromFile(source), Uri.fromFile(output), source,
                    listOf(VideoSegment(0, 3000)), cancelled, { cancelled.set(true) },
                )
                fail("Cancelled copy must fail")
            } catch (failure: VideoExportFailure) {
                assertTrue(failure.partialDocumentMayRemain)
            }
            assertEquals(original, digest(source))
        } finally { output.delete(); source.delete() }
    }

    @Test fun cancellationBeforeCopyLeavesSourceAndDestinationUntouched() {
        val source = fixture()
        val output = File.createTempFile("composition-pre-cancel-", ".mp4", context.cacheDir)
        val original = digest(source)
        try {
            try {
                VideoCompositionExporter.copyRenderedToDocument(
                    context, Uri.fromFile(source), Uri.fromFile(output), source,
                    listOf(VideoSegment(0, 3000)), AtomicBoolean(true),
                )
                fail("Cancelled copy must fail")
            } catch (failure: VideoExportFailure) {
                assertFalse(failure.partialDocumentMayRemain)
            }
            assertEquals(0, output.length())
            assertEquals(original, digest(source))
        } finally { output.delete(); source.delete() }
    }

    @Test fun cancellationDuringRenderDoesNotReportSuccessOrChangeSource() {
        val source = fixture()
        val output = File.createTempFile("composition-render-cancel-", ".mp4", context.cacheDir)
        val original = digest(source)
        val reportedSuccess = AtomicBoolean(false)
        try {
            instrumentation.runOnMainSync {
                val exporter = VideoCompositionExporter(context)
                exporter.export(Uri.fromFile(source), Uri.fromFile(output), listOf(VideoSegment(0, 3000))) {
                    reportedSuccess.set(it.isSuccess)
                }
                exporter.cancel()
            }
            assertFalse(reportedSuccess.get())
            assertEquals(original, digest(source))
        } finally { output.delete(); source.delete() }
    }

    @Test fun invalidRenderedMediaFailsVerificationAndPreservesSource() {
        val source = fixture()
        val output = File.createTempFile("composition-invalid-", ".mp4", context.cacheDir)
        val rendered = File.createTempFile("composition-bad-", ".mp4", context.cacheDir).apply {
            writeText("This is not video")
        }
        val original = digest(source)
        try {
            try {
                VideoCompositionExporter.copyRenderedToDocument(
                    context, Uri.fromFile(source), Uri.fromFile(output), rendered,
                    listOf(VideoSegment(0, 3000)), AtomicBoolean(false),
                )
                fail("Invalid saved media must fail")
            } catch (failure: VideoExportFailure) {
                assertTrue(failure.partialDocumentMayRemain)
            }
            assertEquals(original, digest(source))
        } finally { rendered.delete(); output.delete(); source.delete() }
    }
}
