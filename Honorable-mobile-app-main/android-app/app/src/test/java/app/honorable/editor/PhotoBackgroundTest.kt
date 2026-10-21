package app.honorable.editor

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class PhotoBackgroundTest {
    private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    @Test fun matteEndpointsAndSoftEdgesCompositeWithoutChangingInputs(){
        val red=0xFFFF0000.toInt();val blue=0xFF0000FF.toInt()
        assertEquals(red,PhotoBackgroundMath.composite(red,blue,255))
        assertEquals(blue,PhotoBackgroundMath.composite(red,blue,0))
        val mixed=PhotoBackgroundMath.composite(red,blue,128)
        assertEquals(255,mixed ushr 24);assertEquals(128,(mixed ushr 16) and 255);assertEquals(127,mixed and 255)
        assertEquals(0xFFFF0000.toInt(),red)
    }
    @Test fun partialAndZeroSourceAlphaUseCorrectStraightAlphaComposition(){
        assertEquals(0xFF80007F.toInt(),PhotoBackgroundMath.composite(0x80FF0000.toInt(),0xFF0000FF.toInt(),255))
        assertEquals(0,PhotoBackgroundMath.composite(0x00FF0000,0x000000FF,100))
        assertEquals(0x60402010,PhotoBackgroundMath.composite(0x00000000,0x60402010,255))
        assertEquals(0x80402010.toInt(),PhotoBackgroundMath.composite(0x80402010.toInt(),0,255))
    }
    @Test fun samplingUsesPixelCentersClampsEdgesAndInterpolatesMatte(){
        val pixels=intArrayOf(0xFF000000.toInt(),0x00000000)
        assertEquals(pixels[0],PhotoBackgroundMath.sample(pixels,2,1,.5f,.5f))
        assertEquals(pixels[1],PhotoBackgroundMath.sample(pixels,2,1,1.5f,.5f))
        assertEquals(128,PhotoBackgroundMath.sample(pixels,2,1,1f,.5f) ushr 24)
        assertEquals(pixels[0],PhotoBackgroundMath.sample(pixels,2,1,-5f,9f))
    }
    @Test fun blurExcludesForegroundColorsAndNeverMutatesInputOrMask(){
        val pixels=IntArray(17*13){0xFF0000FF.toInt()};val mask=IntArray(pixels.size)
        for(y in 3..9)for(x in 5..11){pixels[y*17+x]=0xFFFF0000.toInt();mask[y*17+x]=255}
        val original=pixels.clone();val originalMask=mask.clone()
        val result=PhotoBackgroundMath.blur(pixels,mask,17,13,4)
        for(pixel in result)assertEquals(0xFF0000FF.toInt(),pixel)
        assertArrayEquals(original,pixels);assertArrayEquals(originalMask,mask)
    }
    @Test fun blurSoftensBackgroundAndHandlesThinOrFullyMaskedProxies(){
        val pixels=IntArray(41){if(it<20)0xFF000000.toInt() else -1}
        val small=PhotoBackgroundMath.blur(pixels,IntArray(41),41,1,1)
        val large=PhotoBackgroundMath.blur(pixels,IntArray(41),41,1,8)
        assertTrue((small[19] and 255) in 1..254)
        assertTrue((large[10] and 255)>(small[10] and 255))
        assertArrayEquals(pixels,PhotoBackgroundMath.blur(pixels,IntArray(41){255},41,1,24))
        assertArrayEquals(intArrayOf(0xFF123456.toInt()),PhotoBackgroundMath.blur(intArrayOf(0xFF123456.toInt()),intArrayOf(0),1,1,24))
    }
    @Test fun unsafeSettingsAndUnboundedScratchAreRejected(){
        rejected{PhotoBackground(strength=0)};rejected{PhotoBackground(strength=101)};rejected{PhotoBackground(color=0x00FFFFFF)}
        rejected{PhotoEdit(personCutout=true,background=PhotoBackground())}
        rejected{PhotoBackgroundMath.blur(IntArray(385),IntArray(385),385,1,1)}
        rejected{PhotoBackgroundMath.blur(intArrayOf(-1),intArrayOf(256),1,1,1)}
        rejected{PhotoBackgroundMath.composite(-1,-1,-1)}
        assertTrue(PhotoEdit(background=PhotoBackground()).needsPersonMask)
        assertFalse(PhotoEdit().needsPersonMask)
    }
    @Test fun backgroundBlurRetainsPartialAlphaAndIgnoresInvisibleColor(){
        assertEquals(0x800000FF.toInt(),PhotoBackgroundMath.sample(intArrayOf(0xFF0000FF.toInt(),0x00FF0000),2,1,1f,.5f))
        val pixels=intArrayOf(0x800000FF.toInt(),0x00FF0000,0x800000FF.toInt())
        val blurred=PhotoBackgroundMath.blur(pixels,IntArray(3),3,1,3)
        for(pixel in blurred){assertEquals(0,(pixel ushr 16) and 255);assertEquals(255,pixel and 255);assertTrue((pixel ushr 24) in 1..128)}
        val constant=IntArray(7){0x60123456}
        assertArrayEquals(constant,PhotoBackgroundMath.blur(constant,IntArray(7),7,1,24))
    }
    @Test fun versionThreePreservesAllHistoryAndFrozenBackgroundExportWithinBudget(){
        val scope=PhotoSessionCodec.scope("synthetic-owner","https://example.test","content://synthetic/1")
        val edit=PhotoEdit(rotation=270,crop=.6f,curve=PhotoToneCurve(.3f,.6f,.8f),radialLight=PhotoRadialLight(),background=PhotoBackground(strength=79))
        val solid=edit.copy(background=PhotoBackground(PhotoBackgroundMode.SOLID,color=0xFF808080.toInt()))
        val snapshot=PhotoSessionSnapshot(scope,edit,List(50){solid},List(50){edit},true,3,solid)
        val bytes=PhotoSessionCodec.encode(snapshot)
        assertEquals(3,ByteBuffer.wrap(bytes).getInt(4));assertTrue(bytes.size<=PhotoSessionCodec.MAX_BYTES)
        assertEquals(snapshot,PhotoSessionCodec.decode(bytes,scope))
        for(length in 0 until bytes.size)try{PhotoSessionCodec.decode(bytes.copyOf(length),scope);fail("Truncation accepted")}catch(_:IllegalArgumentException){}catch(_:java.io.IOException){}
        rejected{PhotoSessionCodec.decode(bytes+byteArrayOf(0),scope)}
    }
    @Test fun historyAloneActivatesVersionThreeAndPreviousFormatsStillRoundTrip(){
        val scope=PhotoSessionCodec.scope("synthetic-owner","https://example.test","content://synthetic/1")
        val snapshots=listOf(
            PhotoSessionSnapshot(scope,PhotoEdit()),
            PhotoSessionSnapshot(scope,PhotoEdit(curve=PhotoToneCurve(.3f,.5f,.8f))),
            PhotoSessionSnapshot(scope,PhotoEdit(),undo=listOf(PhotoEdit(background=PhotoBackground())))
        )
        snapshots.forEachIndexed{i,snapshot->val bytes=PhotoSessionCodec.encode(snapshot);assertEquals(i+1,ByteBuffer.wrap(bytes).getInt(4));assertEquals(snapshot,PhotoSessionCodec.decode(bytes,scope))}
    }
}
