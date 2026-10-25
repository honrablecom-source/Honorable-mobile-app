package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoEffectsTest {
    private fun effect(e:PhotoEffects,p:Int):Int=intArrayOf(p).also{PhotoEffectsKernel.applyInPlace(it,1,1,e)}[0]
    private fun red(p:Int)=(p ushr 16) and 255
    @Test fun negativeInvertsChannelsWithoutChangingAlpha(){assertEquals(0x7FEFD017,effect(PhotoEffects(negative=true),0x7F102FE8))}
    @Test fun solarizeOnlyInvertsChannelsAboveCutoff(){assertEquals(0xFF207F1F.toInt(),effect(PhotoEffects(solarize=.5f),0xFF2080E0.toInt()))}
    @Test fun sepiaHasKnownWhiteAndBlackEndpoints(){assertEquals(0xFFFFFFEF.toInt(),effect(PhotoEffects(sepia=1f),-1));assertEquals(0xFF000000.toInt(),effect(PhotoEffects(sepia=1f),0xFF000000.toInt()))}
    @Test fun duotoneMapsBlackAndWhiteToChosenColors(){val e=PhotoEffects(duotone=1f,dark=0xFF112233.toInt(),light=0xFFEEDDCC.toInt());assertEquals(e.dark,effect(e,0xFF000000.toInt()));assertEquals(e.light,effect(e,-1))}
    @Test fun gammaHasOppositeLightAndDarkResponses(){assertEquals(16,red(effect(PhotoEffects(gamma=.5f),0xFF404040.toInt())));assertEquals(128,red(effect(PhotoEffects(gamma=2f),0xFF404040.toInt())))}
    @Test fun pixelateAveragesBlocksAndDoesNotUseHiddenTransparentColors(){
        val pixels=IntArray(512*512){i->if(i%2==0)-1 else 0xFF000000.toInt()}
        PhotoEffectsKernel.applyInPlace(pixels,512,512,PhotoEffects(pixelate=2));assertEquals(128,red(pixels[0]));assertEquals(pixels[0],pixels[1])
        val mixed=IntArray(512*512){0x00FF0000};mixed[0]=0xFF0000FF.toInt()
        PhotoEffectsKernel.applyInPlace(mixed,512,512,PhotoEffects(pixelate=2));assertEquals(0xFF0000FF.toInt(),mixed[0]);assertEquals(0x00FF0000,mixed[1])
    }
    @Test fun blurSpreadsAnImpulseSymmetricallyWithoutDarkFringes(){
        val pixels=IntArray(512*512){0xFF000000.toInt()};pixels[256*512+256]=-1
        PhotoEffectsKernel.applyInPlace(pixels,512,512,PhotoEffects(blur=1))
        assertTrue(red(pixels[256*512+256]) in 27..29)
        assertEquals(pixels[255*512+255],pixels[257*512+257]);assertEquals(0,red(pixels[250*512+250]))
    }
    @Test fun edgeOutlineFindsAnEdgeAndLeavesFlatAreasBlack(){
        val pixels=IntArray(9*5){if(it%9<4)0xFF000000.toInt() else -1}
        PhotoEffectsKernel.applyInPlace(pixels,9,5,PhotoEffects(edges=1f))
        assertEquals(0,red(pixels[2*9]));assertTrue(red(pixels[2*9+3])>200);assertEquals(0,red(pixels[2*9+8]))
    }
    @Test fun embossMakesFlatAreasNeutralGray(){assertEquals(0xFF808080.toInt(),effect(PhotoEffects(emboss=1f),0xFF234567.toInt()))}
    @Test fun chromaticOffsetReadsOppositeColorNeighbors(){
        val pixels=IntArray(512*512){i->val x=i%512;0xFF000000.toInt() or ((x%256) shl 16) or (80 shl 8) or (255-x%256)}
        PhotoEffectsKernel.applyInPlace(pixels,512,512,PhotoEffects(chromatic=1));assertEquals(0xFFFF50FE.toInt(),pixels[256*512+256])
    }
    @Test fun halftoneProducesDotsOnWhiteWithoutChangingAlpha(){val pixels=IntArray(12*12){0x7F888888};PhotoEffectsKernel.applyInPlace(pixels,12,12,PhotoEffects(halftone=10));assertEquals(setOf(0,255),pixels.map{red(it)}.toSet());assertTrue(pixels.all{it ushr 24==127})}
    @Test fun scanlinesOnlyDarkenAlternatingBands(){val pixels=IntArray(8*8){0xFF646464.toInt()};PhotoEffectsKernel.applyInPlace(pixels,8,8,PhotoEffects(scanlines=1f));assertEquals(100,red(pixels[0]));assertEquals(35,red(pixels[8]))}
    @Test fun everyEffectKeepsAlphaAndTransparentPixelsBitExact(){
        val effects=listOf(PhotoEffects(negative=true),PhotoEffects(solarize=.5f),PhotoEffects(sepia=.8f),PhotoEffects(duotone=1f),PhotoEffects(gamma=2f),PhotoEffects(pixelate=32),PhotoEffects(blur=20),PhotoEffects(edges=1f),PhotoEffects(emboss=1f),PhotoEffects(chromatic=20),PhotoEffects(halftone=12),PhotoEffects(scanlines=1f))
        val source=IntArray(16*16){i->(i shl 24) or 0x123456}
        for(e in effects){val pixels=source.clone();PhotoEffectsKernel.applyInPlace(pixels,16,16,e);assertEquals(source[0],pixels[0]);assertEquals(source.map{it ushr 24},pixels.map{it ushr 24})}
    }
    @Test fun identityDoesNotChangeAnyBits(){val p=intArrayOf(0x00112233,0x7F445566,-1);val copy=p.clone();PhotoEffectsKernel.applyInPlace(p,3,1,PhotoEffects());assertArrayEquals(copy,p)}
    @Test fun unsafeSettingsAndDimensionsFailBeforeProcessing(){
        for(v in listOf(Float.NaN,Float.POSITIVE_INFINITY,0f,3f))assertTrue(runCatching{PhotoEffects(gamma=v)}.isFailure)
        assertTrue(runCatching{PhotoEffects(pixelate=1)}.isFailure);assertTrue(runCatching{PhotoEffects(halftone=2)}.isFailure)
        assertTrue(runCatching{PhotoEffects(duotone=1f,dark=0)}.isFailure)
        assertTrue(runCatching{PhotoEffectsKernel.applyInPlace(IntArray(3),2,2,PhotoEffects())}.isFailure)
    }
    @Test fun versionSixKeepsEffectsAcrossUndoRedoAndPendingExport(){
        val scope="a".repeat(64);val e=PhotoEdit(effects=PhotoEffects(negative=true,duotone=.3f,pixelate=8,blur=2,chromatic=-3,halftone=5))
        val s=PhotoSessionSnapshot(scope,e,listOf(PhotoEdit()),listOf(e.copy(effects=PhotoEffects(gamma=2f))),pendingExport=e)
        val bytes=PhotoSessionCodec.encode(s);assertEquals(6,java.nio.ByteBuffer.wrap(bytes,4,4).int);assertEquals(s,PhotoSessionCodec.decode(bytes,scope))
        val old=PhotoSessionSnapshot(scope,PhotoEdit(creative=PhotoCreative(grain=.3f)));val previous=PhotoSessionCodec.encode(old)
        assertEquals(5,java.nio.ByteBuffer.wrap(previous,4,4).int);assertEquals(old,PhotoSessionCodec.decode(previous,scope))
    }
    @Test fun savedEffectsFitTheBoundAndKeepTheTargetFraming(){
        val e=PhotoEdit(effects=PhotoEffects(sepia=.7f,emboss=.3f));val preset=PhotoAdjustmentPreset.capture(e)
        val shelf=PhotoPresetCatalog((1..20).map{NamedPhotoPreset("Look $it",preset)})
        val bytes=PhotoPresetCodec.encode(shelf);assertTrue(bytes.size<=PhotoPresetCodec.MAX_BYTES);assertEquals(3,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        assertEquals(shelf,PhotoPresetCodec.decode(bytes));val target=PhotoEdit(rotation=90,finish=PhotoFinish(caption=PhotoCaption("Keep")))
        val applied=preset.applyTo(target);assertEquals(e.effects,applied.effects);assertEquals(target.rotation,applied.rotation);assertEquals(target.finish.caption,applied.finish.caption)
    }
    @Test fun truncatedAndFutureEffectStateIsRejected(){
        val scope="a".repeat(64);val bytes=PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,PhotoEdit(effects=PhotoEffects(negative=true))))
        assertTrue(runCatching{PhotoSessionCodec.decode(bytes.copyOf(bytes.size-1),scope)}.isFailure)
        val future=bytes.clone();future[7]=7;assertTrue(runCatching{PhotoSessionCodec.decode(future,scope)}.isFailure)
        val custom=PhotoEdit(effects=PhotoEffects(dark=0xFFABCDEF.toInt()))
        assertEquals(custom,PhotoSessionCodec.decode(PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,custom)),scope).edit)
    }
}
