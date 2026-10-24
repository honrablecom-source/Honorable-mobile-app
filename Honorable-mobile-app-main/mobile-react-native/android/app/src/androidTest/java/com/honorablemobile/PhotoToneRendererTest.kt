package com.honorablemobile

import android.graphics.Bitmap
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test

class PhotoToneRendererTest {
    @Test fun outputUsesTheSameToneCurveAndPreservesSourceAndMaskAlpha(){
        val source=Bitmap.createBitmap(11,11,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF404040.toInt())}
        val mask=Bitmap.createBitmap(11,11,Bitmap.Config.ARGB_8888).apply{eraseColor(0x80000000.toInt())}
        val edit=PhotoEdit(personCutout=true,curve=PhotoToneCurve(.4f,.6f,.8f),radialLight=PhotoRadialLight(exposure=.3f))
        val output=PhotoRasterRenderer.render(source,edit,mask)
        try{assertEquals(0xFF404040.toInt(),source.getPixel(5,5));assertEquals(128,output.getPixel(5,5) ushr 24);assertTrue((output.getPixel(5,5) and 255)>64)}
        finally{output.recycle();source.recycle();mask.recycle()}
    }
    @Test fun croppedAndRotatedLocalLightMatchesTheSameSourceAnchor(){
        val source=Bitmap.createBitmap(101,79,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF404040.toInt())}
        try{for(rotation in listOf(0,90,180,270)){
            val edit=PhotoEdit(rotation=rotation,crop=.61f,radialLight=PhotoRadialLight(exposure=1f))
            val geometry=PhotoToneGeometry(source.width,source.height,edit);val point=geometry.outputPoint(50.5f,39.5f)
            val output=PhotoRasterRenderer.render(source,edit,null)
            try{assertEquals(geometry.outputWidth,output.width);assertEquals(geometry.outputHeight,output.height);assertTrue((output.getPixel(point.x.toInt(),point.y.toInt()) and 255)>=126)}finally{output.recycle()}
        }}finally{source.recycle()}
    }
}
