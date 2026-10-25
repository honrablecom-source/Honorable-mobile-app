package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoCreativeTest {
    private fun pixel(edit: PhotoCreative, color: Int = 0x7F7090B0) = PhotoCreativeKernel(edit, 100, 100).apply(color, 50, 50)
    private fun red(p: Int) = (p ushr 16) and 255
    private fun green(p: Int) = (p ushr 8) and 255
    private fun blue(p: Int) = p and 255
    @Test fun identityAndTransparentPixelsAreBitExact() {
        for (p in listOf(0, 0x00FFFFFF, -1, 0x7F7090B0)) assertEquals(p, pixel(PhotoCreative(), p))
        assertEquals(0x00112233, pixel(PhotoCreative(grain=1f, hue=1f, fade=1f), 0x00112233))
    }
    @Test fun allToolsPreserveAlpha() {
        val settings=PhotoCreative(vibrance=1f,hue=-.8f,fade=.7f,grain=1f,posterize=6,splitTone=1f,red=1f,green=-1f,blue=.5f)
        for(alpha in 1..255 step 17)for(color in listOf(0,0xFFFFFF,0xAA0022,0x33FF11))
            assertEquals(alpha,pixel(settings,(alpha shl 24) or color) ushr 24)
    }
    @Test fun vibranceTargetsMutedColorsAndLeavesGrayNeutral() {
        val muted=0xFF8090A0.toInt();val out=pixel(PhotoCreative(vibrance=1f),muted)
        assertTrue(blue(out)-red(out)>blue(muted)-red(muted))
        assertEquals(0xFF888888.toInt(),pixel(PhotoCreative(vibrance=1f),0xFF888888.toInt()))
    }
    @Test fun hueRotatesColorWithoutChangingGray() {
        assertNotEquals(0xFFAA4411.toInt(),pixel(PhotoCreative(hue=.5f),0xFFAA4411.toInt()))
        val gray=pixel(PhotoCreative(hue=.5f),0xFF777777.toInt())
        assertTrue(kotlin.math.abs(red(gray)-green(gray))<=1);assertTrue(kotlin.math.abs(red(gray)-blue(gray))<=1)
    }
    @Test fun fadeLiftsBlackAndRetainsWhite() {
        assertEquals(56,red(pixel(PhotoCreative(fade=1f),0xFF000000.toInt())))
        assertEquals(-1,pixel(PhotoCreative(fade=1f),-1))
    }
    @Test fun grainIsDeterministicAndUsesNormalizedCoordinates() {
        val k=PhotoCreativeKernel(PhotoCreative(grain=1f),100,100)
        val larger=PhotoCreativeKernel(PhotoCreative(grain=1f),200,200)
        assertEquals(k.apply(0xFF888888.toInt(),20,30),k.apply(0xFF888888.toInt(),20,30))
        assertEquals(k.apply(0xFF888888.toInt(),20,30),larger.apply(0xFF888888.toInt(),40,60))
        assertTrue((0..30).map{k.apply(0xFF888888.toInt(),it,30)}.distinct().size>5)
    }
    @Test fun posterizeProducesOnlyRequestedLevels() {
        val allowed=setOf(0,85,170,255)
        for(v in 0..255) {
            val out=pixel(PhotoCreative(posterize=4),-0x1000000 or (v shl 16) or (v shl 8) or v)
            assertTrue(red(out) in allowed);assertEquals(red(out),green(out));assertEquals(red(out),blue(out))
        }
    }
    @Test fun thresholdHasExplicitOffAndRealBlackWhiteOutput() {
        assertEquals(0xFF000000.toInt(),pixel(PhotoCreative(threshold=.5f),0xFF777777.toInt()))
        assertEquals(-1,pixel(PhotoCreative(threshold=.5f),0xFF999999.toInt()))
        assertEquals(-1,pixel(PhotoCreative(threshold=0f),0xFF000000.toInt()))
        assertEquals(-1,pixel(PhotoCreative(threshold=1f),-1))
    }
    @Test fun splitToneSeparatesWarmHighlightsFromCoolShadows() {
        val shadow=pixel(PhotoCreative(splitTone=1f),0xFF333333.toInt())
        val light=pixel(PhotoCreative(splitTone=1f),0xFFCCCCCC.toInt())
        assertTrue(blue(shadow)>red(shadow));assertTrue(red(light)>blue(light))
    }
    @Test fun rgbBalanceChangesOnlySelectedChannels() {
        val out=pixel(PhotoCreative(red=1f),0x7F406080)
        assertEquals(128,red(out));assertEquals(96,green(out));assertEquals(128,blue(out))
    }
    @Test fun invalidModelsFailBeforeRendering() {
        for(v in listOf(Float.NaN,Float.POSITIVE_INFINITY,1.1f,-1.1f))assertTrue(runCatching{PhotoCreative(hue=v)}.isFailure)
        assertTrue(runCatching{PhotoCreative(posterize=1)}.isFailure)
        assertTrue(runCatching{PhotoCreative(threshold=-.1f)}.isFailure)
        assertTrue(runCatching{PhotoCreative(grain=-.1f)}.isFailure)
    }
    @Test fun lifecycleRoundTripPreservesCreativeHistoryAndPendingExport() {
        val scope="a".repeat(64)
        val e=PhotoEdit(creative=PhotoCreative(vibrance=.5f,hue=.2f,grain=.3f,posterize=4,threshold=.5f,splitTone=.4f,red=.2f))
        val s=PhotoSessionSnapshot(scope,e,listOf(PhotoEdit()),listOf(e.copy(creative=PhotoCreative(fade=.4f))),pendingExport=e)
        val bytes=PhotoSessionCodec.encode(s)
        assertEquals(5,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        assertEquals(s,PhotoSessionCodec.decode(bytes,scope))
        assertTrue(runCatching{PhotoSessionCodec.decode(bytes.copyOf(bytes.size-1),scope)}.isFailure)
    }
    @Test fun legacySessionAndLooksStillDecode() {
        val scope="a".repeat(64);val legacy=PhotoSessionSnapshot(scope,PhotoEdit(finish=PhotoFinish(warmth=.2f)))
        val bytes=PhotoSessionCodec.encode(legacy)
        assertEquals(4,java.nio.ByteBuffer.wrap(bytes,4,4).int);assertEquals(legacy,PhotoSessionCodec.decode(bytes,scope))
        val shelf=PhotoPresetCatalog(listOf(NamedPhotoPreset("Old",PhotoAdjustmentPreset(warmth=.2f))))
        val encoded=PhotoPresetCodec.encode(shelf)
        assertEquals(1,java.nio.ByteBuffer.wrap(encoded,4,4).int);assertEquals(shelf,PhotoPresetCodec.decode(encoded))
    }
    @Test fun savedLooksKeepCreativeToolsWithoutCopyingGeometryOrText() {
        val source=PhotoEdit(creative=PhotoCreative(grain=.8f,blue=-.5f))
        val shelf=PhotoPresetCatalog(listOf(NamedPhotoPreset("Film",PhotoAdjustmentPreset.capture(source))))
        val encoded=PhotoPresetCodec.encode(shelf)
        assertEquals(2,java.nio.ByteBuffer.wrap(encoded,4,4).int)
        val decoded=PhotoPresetCodec.decode(encoded);assertEquals(shelf,decoded)
        val target=PhotoEdit(rotation=90,finish=PhotoFinish(caption=PhotoCaption("Keep")))
        val applied=decoded.entries.single().adjustments.applyTo(target)
        assertEquals(source.creative,applied.creative);assertEquals(target.rotation,applied.rotation);assertEquals(target.finish.caption,applied.finish.caption)
    }
}
