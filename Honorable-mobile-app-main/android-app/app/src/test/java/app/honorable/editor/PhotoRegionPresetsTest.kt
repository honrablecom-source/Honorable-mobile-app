package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test

class PhotoRegionPresetsTest{
    private fun recipe(name:String="Local look")=NamedPhotoRegions(name,PhotoRegions(listOf(PhotoRegion("area",PhotoRegionEffect.GRAYSCALE,PhotoRegionShape.ELLIPSE,width=.6f,feather=.3f))))
    @Test fun roundTripAndTargetProtection(){
        val shelf=PhotoRegionPresetCatalog(listOf(recipe()));assertEquals(shelf,PhotoRegionPresetCodec.decode(PhotoRegionPresetCodec.encode(shelf)))
        val target=PhotoEdit(rotation=90,exposure=.5f,selectiveColor=PhotoSelectiveColor(enabled=true,shift=60f),layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Keep text"))))
        val applied=recipe().applyTo(target);assertEquals(target.copy(regions=recipe().regions),applied)
        assertEquals(.3f,applied.regions.values.first().feather,0f)
    }
    @Test fun rejectsDuplicateNamesEmptyRegionsAndDamagedData(){
        val shelf=PhotoRegionPresetCatalog(listOf(recipe()));assertThrows(IllegalArgumentException::class.java){shelf.add(recipe("LOCAL LOOK"))}
        assertThrows(IllegalArgumentException::class.java){NamedPhotoRegions("Empty",PhotoRegions())}
        assertThrows(IllegalArgumentException::class.java){recipe("bad\nname")}
        val bytes=PhotoRegionPresetCodec.encode(shelf);bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();assertThrows(IllegalArgumentException::class.java){PhotoRegionPresetCodec.decode(bytes)}
        assertEquals(0,shelf.remove("LOCAL LOOK").entries.size)
    }
    @Test fun maximumCatalogBoundedAndIndependent(){
        val mutable=mutableListOf(recipe());val shelf=PhotoRegionPresetCatalog(mutable);mutable.clear();assertEquals(1,shelf.entries.size)
        val entries=List(20){recipe("Recipe $it").copy(regions=PhotoRegions(List(8){index->PhotoRegion("x".repeat(60)+index,PhotoRegionEffect.BLUR)}))}
        val bytes=PhotoRegionPresetCodec.encode(PhotoRegionPresetCatalog(entries));assertTrue(bytes.size<=PhotoRegionPresetCodec.MAX_BYTES);assertEquals(20,PhotoRegionPresetCodec.decode(bytes).entries.size)
        assertThrows(IllegalArgumentException::class.java){PhotoRegionPresetCatalog(entries+recipe("Overflow"))}
    }
}
