package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class PhotoFinishTest {
    private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:java.io.IOException){}}
    @Test fun aspectRatiosMirrorsAndEveryRotationKeepSourceCoordinatesInvertible(){
        for(rotation in listOf(0,90,180,270))for(ratio in listOf(0f,1f,.8f,16f/9,9f/16))for(mx in listOf(false,true))for(my in listOf(false,true)){
            val g=PhotoToneGeometry(301,239,PhotoEdit(rotation=rotation,crop=.7f,finish=PhotoFinish(ratio,mx,my)))
            if(ratio!=0f)assertEquals(ratio,g.outputWidth.toFloat()/g.outputHeight,.025f)
            for(x in listOf(.5f,g.outputWidth/2f))for(y in listOf(.5f,g.outputHeight/2f)){
                val source=g.sourcePoint(x,y);val output=g.outputPoint(source.x,source.y)
                assertEquals(x,output.x,.0001f);assertEquals(y,output.y,.0001f)
                assertTrue(source.x in 0f..301f&&source.y in 0f..239f)
            }
        }
    }
    @Test fun defaultFinishLeavesPixelsAndAlphaExactlyUnchanged(){
        val k=PhotoFinishKernel(PhotoFinish(),10,10);assertFalse(k.active)
        for(a in listOf(0,64,128,255)){val p=(a shl 24)or 0x123456;assertEquals(p,k.apply(p,4,5))}
    }
    @Test fun whiteBalanceChangesRelevantChannelsAndRetainsAlpha(){
        val warm=PhotoFinishKernel(PhotoFinish(warmth=1f),10,10).apply(0x60404040,5,5)
        assertTrue(((warm ushr 16)and 255)>(warm and 255));assertEquals(0x60,warm ushr 24)
        val tint=PhotoFinishKernel(PhotoFinish(tint=1f),10,10).apply(0x60404040,5,5)
        assertTrue(((tint ushr 8)and 255)>(tint and 255))
    }
    @Test fun tonalControlsAndVignetteAffectTheirIntendedRegions(){
        val lift=PhotoFinishKernel(PhotoFinish(shadows=1f),101,101)
        assertTrue((lift.apply(0xFF202020.toInt(),50,50)and 255)>32)
        val highlights=PhotoFinishKernel(PhotoFinish(highlights=-1f),101,101)
        assertTrue((highlights.apply(0xFFE0E0E0.toInt(),50,50)and 255)<224)
        val vignette=PhotoFinishKernel(PhotoFinish(vignette=1f),101,101)
        assertEquals(128,vignette.apply(0xFF808080.toInt(),50,50)and 255)
        assertTrue((vignette.apply(0xFF808080.toInt(),0,0)and 255)<64)
    }
    @Test fun sharpeningPreservesFlatColorsAndIgnoresInvisibleNeighborColor(){
        val p=0x60808080
        assertEquals(p,PhotoFinishKernel.sharp(p,p,p,p,p,1f))
        assertEquals(p,PhotoFinishKernel.sharp(p,0x00FF0000,0x0000FF00,0x000000FF,p,1f))
        assertTrue((PhotoFinishKernel.sharp(0xFF808080.toInt(),0xFF404040.toInt(),0xFF404040.toInt(),0xFF404040.toInt(),0xFF404040.toInt(),1f)and 255)>128)
    }
    @Test fun invalidFinishingAndCaptionPayloadsAreRejected(){
        rejected{PhotoFinish(ratio=Float.NaN)};rejected{PhotoFinish(ratio=.1f)};rejected{PhotoFinish(warmth=2f)};rejected{PhotoFinish(sharpen=Float.POSITIVE_INFINITY)}
        rejected{PhotoCaption("")};rejected{PhotoCaption("A".repeat(121))};rejected{PhotoCaption("A\nB\nC\nD")};rejected{PhotoCaption("A",size=Float.NaN)}
        rejected{PhotoCaption("\uD800")};rejected{PhotoCaption("A",x=0f)}
    }
    @Test fun versionFourPreservesFullHistoryPendingExportAndBoundedUnicodeText(){
        val scope=PhotoSessionCodec.scope("synthetic","https://example.test","content://synthetic/1")
        val text=String(Character.toChars(0x1F600)).repeat(60)
        val edit=PhotoEdit(rotation=90,personCutout=true,curve=PhotoToneCurve(.3f,.5f,.8f),radialLight=PhotoRadialLight(),finish=PhotoFinish(.8f,true,true,.4f,-.2f,.3f,-.3f,.5f,.4f,PhotoCaption(text)))
        val snapshot=PhotoSessionSnapshot(scope,edit,List(50){edit},List(50){edit},true,3,edit)
        val bytes=PhotoSessionCodec.encode(snapshot);assertEquals(4,ByteBuffer.wrap(bytes).getInt(4));assertTrue(bytes.size<=PhotoSessionCodec.MAX_BYTES)
        assertEquals(snapshot,PhotoSessionCodec.decode(bytes,scope))
        for(n in listOf(0,7,20,bytes.size-1))rejected{PhotoSessionCodec.decode(bytes.copyOf(n),scope)}
        rejected{PhotoSessionCodec.decode(bytes+byteArrayOf(0),scope)}
    }
    @Test fun localLightFollowsTheSameOriginalAnchorAfterRatioAndMirroring(){
        for(rotation in listOf(0,90,180,270)){
            val e=PhotoEdit(rotation=rotation,radialLight=PhotoRadialLight(exposure=1f),finish=PhotoFinish(ratio=1f,mirrorX=true,mirrorY=true))
            val g=PhotoToneGeometry(101,79,e);val p=g.outputPoint(50.5f,39.5f)
            assertEquals(128,PhotoToneKernel(e,101,79).apply(0xFF404040.toInt(),p.x.toInt(),p.y.toInt())and 255)
        }
    }
}
