package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.Color
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test

class PhotoRegionsIntegrationTest {
    @Test fun sharedRasterUsesFinishedRegionsBeforeAnnotationsAndPreservesSource(){
        val source=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
        val edit=PhotoEdit(regions=PhotoRegions(listOf(PhotoRegion("gray",PhotoRegionEffect.GRAYSCALE,width=.6f,height=.6f,strength=1f,feather=0f))),layers=PhotoLayers(listOf(PhotoObject("front",PhotoObjectType.RECTANGLE,width=.1f,height=.1f,color=Color.GREEN))))
        try{
            val preview=PhotoRasterRenderer.render(source,edit,null);val exported=PhotoRasterRenderer.render(source,edit,null)
            try{
                assertEquals(Color.RED,source.getPixel(35,35));assertEquals(Color.RED,preview.getPixel(0,0))
                val gray=preview.getPixel(35,35);assertEquals(Color.red(gray),Color.green(gray));assertEquals(Color.green(gray),Color.blue(gray))
                assertEquals(Color.GREEN,preview.getPixel(50,50));assertTrue(preview.sameAs(exported))
            }finally{preview.recycle();exported.recycle()}
        }finally{source.recycle()}
    }
}
