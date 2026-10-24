package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class PhotoToneTest {
    private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:java.io.IOException){}}
    @Test fun identityCurveAndKernelPreserveEveryChannelAndAlpha(){
        assertArrayEquals(IntArray(256){it},PhotoToneCurve().table())
        val kernel=PhotoToneKernel(PhotoEdit(),256,1)
        for(i in 0..255){val pixel=(i shl 24) or ((255-i) shl 16) or (i shl 8) or 77;assertEquals(pixel,kernel.apply(pixel,i,0))}
        assertFalse(kernel.active)
    }
    @Test fun curvesRemainMonotonicAndPreserveBlackAndWhiteEndpoints(){
        val curve=PhotoToneCurve(.35f,.6f,.8f);val table=curve.table()
        assertEquals(0,table.first());assertEquals(255,table.last());assertTrue(table.toList().zipWithNext().all{it.first<=it.second})
        assertTrue(table[64]>64);assertTrue(table[128]>128)
        val pixel=PhotoToneKernel(PhotoEdit(curve=curve),100,100).apply(0x7F4080C0,0,0)
        assertEquals(0x7F,pixel ushr 24)
    }
    @Test fun malformedCurvesAndMasksRejectNonfiniteOrUnsafeSettings(){
        rejected{PhotoToneCurve(Float.NaN)};rejected{PhotoToneCurve(.7f,.5f,.8f)};rejected{PhotoToneCurve(highlights=1.1f)}
        rejected{PhotoRadialLight(centerX=-.01f)};rejected{PhotoRadialLight(radius=0f)}
        rejected{PhotoRadialLight(feather=Float.POSITIVE_INFINITY)};rejected{PhotoRadialLight(exposure=2.1f)}
    }
    @Test fun localLightAffectsOnlyItsFeatheredCircleAndPreservesAlpha(){
        val e=PhotoEdit(radialLight=PhotoRadialLight(.5f,.5f,.3f,.8f,1f));val k=PhotoToneKernel(e,101,101)
        val pixel=0x60404040
        assertEquals(0x60808080,k.apply(pixel,50,50));assertEquals(pixel,k.apply(pixel,0,0))
        val edge=k.apply(pixel,70,50) and 255
        assertTrue(edge in 65..127)
        val dark=PhotoToneKernel(e.copy(radialLight=e.radialLight!!.copy(exposure=-1f)),101,101).apply(pixel,50,50)
        assertEquals(0x60202020,dark)
    }
    @Test fun sourceAndOutputCoordinatesRoundTripAfterOddCropsAndEveryRotation(){
        for(rotation in listOf(0,90,180,270))for(crop in listOf(1f,.61f,.3f)){
            val g=PhotoToneGeometry(101,79,PhotoEdit(rotation=rotation,crop=crop))
            for(x in listOf(.5f,g.outputWidth/2f,g.outputWidth-.5f))for(y in listOf(.5f,g.outputHeight/2f,g.outputHeight-.5f)){
                val source=g.sourcePoint(x,y);val output=g.outputPoint(source.x,source.y)
                assertEquals(x,output.x,.0001f);assertEquals(y,output.y,.0001f)
            }
        }
    }
    @Test fun radialLightStaysAtTheSameSourcePixelThroughRotationAndCrop(){
        val original=0xFF404040.toInt();val light=PhotoRadialLight(.5f,.5f,.2f,.5f,1f)
        for(rotation in listOf(0,90,180,270))for(crop in listOf(1f,.61f)){
            val edit=PhotoEdit(rotation=rotation,crop=crop,radialLight=light);val g=PhotoToneGeometry(101,79,edit)
            val point=g.outputPoint(50.5f,39.5f);val result=PhotoToneKernel(edit,101,79).apply(original,point.x.toInt(),point.y.toInt())
            assertEquals(128,result and 255)
        }
    }
    @Test fun extendedLifecycleStateRoundTripsEveryHistoryAndFrozenExportField(){
        val scope=PhotoSessionCodec.scope("test","https://example.test","content://photo/test")
        val edit=PhotoEdit(rotation=270,crop=.6f,personCutout=true,curve=PhotoToneCurve(.3f,.55f,.8f),radialLight=PhotoRadialLight(.2f,.7f,.4f,.8f,-.7f))
        val state=PhotoSessionSnapshot(scope,edit,List(50){edit},List(50){edit},true,2,edit.copy(exposure=.4f))
        val bytes=PhotoSessionCodec.encode(state)
        assertEquals(2,ByteBuffer.wrap(bytes).getInt(4));assertTrue(bytes.size<=PhotoSessionCodec.MAX_BYTES)
        assertEquals(state,PhotoSessionCodec.decode(bytes,scope))
        for(length in 0 until bytes.size)rejected{PhotoSessionCodec.decode(bytes.copyOf(length),scope)}
        rejected{PhotoSessionCodec.decode(bytes+byteArrayOf(0),scope)}
    }
    @Test fun legacyStateKeepsVersionOneAndDefaultToneValues(){
        val scope=PhotoSessionCodec.scope("test","https://example.test","content://photo/test")
        val state=PhotoSessionSnapshot(scope,PhotoEdit(exposure=.2f),listOf(PhotoEdit()),activeTool=3)
        val bytes=PhotoSessionCodec.encode(state)
        assertEquals(1,ByteBuffer.wrap(bytes).getInt(4));assertEquals(state,PhotoSessionCodec.decode(bytes,scope))
        rejected{PhotoSessionCodec.decode(ByteArray(PhotoSessionCodec.MAX_BYTES+1),scope)}
    }
    @Test fun pixelStatisticsIgnoreTransparentContentAndRejectEmptyVisibility(){
        val stats=PhotoLightStats.measure(intArrayOf(0x00111111,0xFF404040.toInt(),0xFF404040.toInt()))
        assertEquals(64/255f,stats.median,.005f)
        rejected{PhotoLightStats.measure(intArrayOf(0x00111111))}
    }
    @Test fun adaptiveLooksPreserveGeometryMaskAndExistingLocalLight(){
        val base=PhotoEdit(rotation=90,crop=.7f,personCutout=true,radialLight=PhotoRadialLight(exposure=.3f))
        for(look in PhotoLook.values()){
            val result=PhotoAutoLook.apply(base,PhotoLightStats(.1f,.3f,.8f),setOf("Person","Beach"),look)
            assertEquals(base.rotation,result.rotation);assertEquals(base.crop,result.crop,0f);assertTrue(result.personCutout)
            assertEquals(base.radialLight,result.radialLight);assertTrue(result.exposure in -2f..2f)
            assertTrue(result.curve.table().toList().zipWithNext().all{it.first<=it.second})
            if(look==PhotoLook.MONOCHROME)assertEquals(0f,result.saturation,0f)
        }
    }
    @Test fun highlightProtectionLimitsExposureAndNightKeepsItsLighting(){
        val bright=PhotoAutoLook.apply(PhotoEdit(),PhotoLightStats(.05f,.2f,.98f),emptySet(),PhotoLook.AUTO)
        assertTrue(bright.exposure<=.08f)
        val night=PhotoAutoLook.apply(PhotoEdit(),PhotoLightStats(.01f,.05f,.6f),setOf("Night"),PhotoLook.AUTO)
        assertTrue(night.exposure<=.25f)
    }
}
