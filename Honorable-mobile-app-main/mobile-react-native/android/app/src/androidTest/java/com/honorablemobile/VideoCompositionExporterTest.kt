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
    @Test fun compositionResolvesEachClipToItsOwnDocument() {
        val first = Uri.parse("content://test/first")
        val second = Uri.parse("content://test/second")
        val composition = VideoCompositionExporter.composition(mapOf("primary" to first, "second" to second),
            listOf(VideoSegment(0, 1000, sourceId="second"), VideoSegment(0,1000)))
        val items = composition.sequences.single().editedMediaItems
        assertEquals(listOf(second, first), items.map { it.mediaItem.localConfiguration!!.uri })
    }

    @Test fun destinationAliasOfAnyImportedOriginalIsRejectedBeforeTruncation() {
        val first = fixture(); val second = fixture()
        val original = digest(second)
        val alias = File(second.parentFile, second.name+"-alias")
        try {
            android.system.Os.link(second.absolutePath, alias.absolutePath)
            try {
                VideoCompositionExporter.copyRenderedToDocument(context,
                    mapOf("primary" to Uri.fromFile(first), "second" to Uri.fromFile(second)),
                    Uri.fromFile(alias), first, listOf(VideoSegment(0,3000)), AtomicBoolean(false))
                fail("An imported original alias must be rejected")
            } catch(failure: VideoExportFailure) { assertFalse(failure.partialDocumentMayRemain) }
            assertEquals(original, digest(second)); assertEquals(original, digest(alias))
        } finally { alias.delete(); second.delete(); first.delete() }
    }

    @Test fun trimmedCreativeClipKeepsFullSourceDurationAndBothEffectTypes() {
        val composition = VideoCompositionExporter.composition(mapOf("primary" to Uri.parse("content://video/1")),
            listOf(VideoSegment(5000,10_000,2f,.5f,title="Title",fadeEdges=true)),mapOf("primary" to 20_000L))
        val item = composition.sequences.single().editedMediaItems.single()
        assertEquals(20_000_000L,item.durationUs)
        assertEquals(2_500_000L,item.presentationDurationUs)
        assertEquals(1,item.effects.audioProcessors.size)
        assertEquals(1,item.effects.videoEffects.size)
        assertEquals(1,composition.effects.videoEffects.size)
    }

    @Test fun titleBitmapIsBoundedWrappedAndReleased() {
        val overlay=VideoTitleOverlay("A long title with several words and a second line")
        overlay.configure(androidx.media3.common.util.Size(3840,2160))
        val bitmap=overlay.getBitmap(0)
        assertTrue(bitmap.width<=1024 && bitmap.height<=512)
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(bitmap.width/2,bitmap.height/2))>0)
        overlay.release();assertTrue(bitmap.isRecycled)
    }

    @Test fun soundtrackIsASeparateLoopingAudioSequence() {
        val music=VideoSoundtrack("content://audio/music",3000,.25f)
        val composition=VideoCompositionExporter.composition(mapOf("primary" to Uri.parse("content://video/source")),
            listOf(VideoSegment(0,10_000)),mapOf("primary" to 10_000L),music)
        assertEquals(2,composition.sequences.size)
        assertFalse(composition.sequences[0].isLooping)
        val sequence=composition.sequences[1];assertTrue(sequence.isLooping)
        val item=sequence.editedMediaItems.single()
        assertTrue(item.removeVideo);assertFalse(item.removeAudio)
        assertEquals(3_000_000L,item.durationUs)
        assertEquals(Uri.parse(music.uri),item.mediaItem.localConfiguration!!.uri)
        assertEquals(1,item.effects.audioProcessors.size)
    }

    @Test fun soundtrackOriginalAliasCannotBeOverwritten() {
        val video=fixture();val audio=fixture();val before=digest(audio)
        val alias=File(audio.parentFile,audio.name+"-audio-alias")
        try {
            android.system.Os.link(audio.absolutePath,alias.absolutePath)
            try {
                VideoCompositionExporter.copyRenderedToDocument(context,
                    mapOf("primary" to Uri.fromFile(video),"__soundtrack__" to Uri.fromFile(audio)),
                    Uri.fromFile(alias),video,listOf(VideoSegment(0,3000)),AtomicBoolean(false))
                fail("Soundtrack aliases must be protected")
            } catch(failure: VideoExportFailure) { assertFalse(failure.partialDocumentMayRemain) }
            assertEquals(before,digest(audio))
        } finally { alias.delete();audio.delete();video.delete() }
    }

}
