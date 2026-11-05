package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoRegionsTest {
    private val scope="a".repeat(64)
    @Test fun regionCoverageAndBoundedCollection(){
        val rect=PhotoRegion("zone",PhotoRegionEffect.GRAYSCALE,width=.4f,height=.4f,feather=.2f)
        assertEquals(1f,rect.coverage(.5f,.5f),0f);assertEquals(0f,rect.coverage(0f,0f),0f)
        assertTrue(rect.coverage(.69f,.5f) in .01f.. .99f)
        val ellipse=rect.copy(shape=PhotoRegionShape.ELLIPSE,feather=0f)
        assertEquals(0f,ellipse.coverage(.69f,.69f),0f)
        assertThrows(IllegalArgumentException::class.java){PhotoRegions(List(9){rect.copy(id="zone_$it")})}
        assertThrows(IllegalArgumentException::class.java){PhotoRegions(listOf(rect,rect))}
        assertThrows(IllegalArgumentException::class.java){rect.copy(x=Float.NaN)}
    }
    @Test fun effectsOnlyChangeCoveredPixelsAndKeepAlpha(){
        val source=IntArray(20*20){0x7fff0000};val original=source.copyOf()
        PhotoRegionKernel.applyInPlace(source,20,20,PhotoRegions(listOf(PhotoRegion("zone",PhotoRegionEffect.GRAYSCALE,width=.4f,height=.4f,strength=1f,feather=0f))))
        assertEquals(original[0],source[0]);assertNotEquals(original[210],source[210]);assertTrue(source.all{it ushr 24==127})
        assertEquals((source[210] ushr 16)and 255,source[210]and 255)
        val disabled=original.copyOf();PhotoRegionKernel.applyInPlace(disabled,20,20,PhotoRegions(listOf(PhotoRegion("off",PhotoRegionEffect.BLUR,enabled=false))))
        assertArrayEquals(original,disabled)
    }
    @Test fun pixelationAndBlurRemainWithinRegionAndHandleClippedEdges(){
        for(effect in listOf(PhotoRegionEffect.PIXELATE,PhotoRegionEffect.BLUR)){
            val pixels=IntArray(256*256){if(it%256<128)0xff000000.toInt()else 0xffffffff.toInt()};val before=pixels.copyOf()
            PhotoRegionKernel.applyInPlace(pixels,256,256,PhotoRegions(listOf(PhotoRegion("zone",effect,x=.5f,y=.5f,width=.5f,height=.5f,strength=1f,feather=0f))))
            assertEquals(before[0],pixels[0]);assertEquals(before.last(),pixels.last());assertTrue(pixels.all{it ushr 24==255})
            if(effect==PhotoRegionEffect.BLUR)assertNotEquals(before[128*256+127],pixels[128*256+127])
            PhotoRegionKernel.applyInPlace(pixels,256,256,PhotoRegions(listOf(PhotoRegion("edge",effect,x=0f,y=0f,strength=1f))))
        }
    }
    @Test fun versionTenPreservesCurrentHistoryAndFrozenExport(){
        val edit=PhotoEdit(regions=PhotoRegions(listOf(PhotoRegion("local",PhotoRegionEffect.BLUR,shape=PhotoRegionShape.ELLIPSE))))
        val state=PhotoSessionSnapshot(scope,edit,listOf(PhotoEdit()),listOf(edit),false,0,edit)
        val bytes=PhotoSessionCodec.encode(state);assertEquals(10,java.io.DataInputStream(bytes.inputStream()).run{readInt();readInt()})
        assertEquals(state,PhotoSessionCodec.decode(bytes,scope))
        val plain=PhotoSessionCodec.decode(PhotoSessionCodec.encode(state.copy(edit=PhotoEdit(),undo=emptyList(),redo=emptyList(),pendingExport=null)),scope)
        assertTrue(plain.edit.regions.values.isEmpty())
    }
    @Test fun savedLooksPreserveTargetRegions(){
        val target=PhotoEdit(regions=PhotoRegions(listOf(PhotoRegion("keep",PhotoRegionEffect.GRAYSCALE))))
        val look=PhotoAdjustmentPreset.capture(PhotoEdit(exposure=1f))
        assertEquals(target.regions,look.applyTo(target).regions)
    }
}
