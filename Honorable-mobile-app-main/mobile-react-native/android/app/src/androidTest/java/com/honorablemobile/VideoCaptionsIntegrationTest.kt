package com.honorablemobile

import android.graphics.Color
import android.net.Uri
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.media3.common.util.Size
import androidx.media3.effect.OverlayEffect
import org.junit.Assert.*
import org.junit.Test

class VideoCaptionsIntegrationTest {
    @Test fun fullLengthCaptionsFitPortraitAndLandscapeWithoutTitleEllipsis() {
        for(size in listOf(Size(720,1280),Size(1280,720))) {
            val overlay=VideoCaptionOverlay(listOf(TimelineCaption(0,1_000_000,"字".repeat(240))))
            overlay.configure(size)
            val bitmap=overlay.getBitmap(0)
            val scale=size.width*.84f/bitmap.width
            assertTrue(bitmap.width<=1024);assertTrue(bitmap.height*scale<=size.height*.30f)
            overlay.release()
        }
    }
    @Test fun captionOverlayIsCompositionWideAndRunsAfterPictureFade() {
        val clip=VideoSegment(0,5000,fadeEdges=true,captions=listOf(VideoCaption(1000,2000,"Caption")))
        val composition=VideoCompositionExporter.composition(Uri.parse("content://synthetic/video"),listOf(clip))
        assertTrue(composition.effects.videoEffects[0] is VideoTimelineFade)
        assertTrue(composition.effects.videoEffects[1] is OverlayEffect)
    }
    @Test fun overlayIsTransparentBeforeAndAfterCueAndRebuildsAfterSeeking() {
        val overlay=VideoCaptionOverlay(listOf(TimelineCaption(1_000_000,2_000_000,"Caption")))
        overlay.configure(Size(720,1280))
        assertEquals(0f,overlay.getOverlaySettings(0).alphaScale,0f)
        assertEquals(0,Color.alpha(overlay.getBitmap(0).getPixel(0,0)))
        val active=overlay.getBitmap(1_000_000);assertTrue(active.width>1);assertEquals(1f,overlay.getOverlaySettings(1_000_000).alphaScale,0f)
        assertSame(active,overlay.getBitmap(1_500_000))
        assertEquals(0f,overlay.getOverlaySettings(2_000_000).alphaScale,0f)
        assertTrue(overlay.getBitmap(1_000_000).width>1)
        overlay.release()
    }
    @Test fun subtitleCopyKeepsOriginalBytesAndVerifiesUnicodeOutput() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=File.createTempFile("caption-original", ".srt",context.cacheDir)
        val output=File.createTempFile("caption-output", ".srt",context.cacheDir)
        try {
            original.writeText("original")
            val bytes="1\n00:00:00,000 --> 00:00:01,000\n世界\n".toByteArray()
            VideoSubtitleDocuments.writeCopy(context,Uri.fromFile(output),bytes,setOf(Uri.fromFile(original)))
            assertArrayEquals(bytes,output.readBytes());assertEquals("original",original.readText())
        } finally { output.delete();original.delete() }
    }
    @Test fun subtitleCopyRefusesHardLinkAliasBeforeTruncatingAnOriginal() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=File.createTempFile("caption-original", ".srt",context.cacheDir)
        val alias=File.createTempFile("caption-alias", ".srt",context.cacheDir)
        try {
            original.writeText("keep original");check(alias.delete());Os.link(original.path,alias.path)
            assertTrue(runCatching{VideoSubtitleDocuments.writeCopy(context,Uri.fromFile(alias),"replacement".toByteArray(),setOf(Uri.fromFile(original)))}.isFailure)
            assertEquals("keep original",original.readText());assertEquals("keep original",alias.readText())
        } finally { alias.delete();original.delete() }
    }
}
