package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Creative20IntegrationTest {
    @Test fun allTwelvePhotoEffectsReachTheActualBitmapRenderer(){
        val source=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF7090B0.toInt())}
        val settings=listOf(PhotoEffects(negative=true),PhotoEffects(solarize=.5f),PhotoEffects(sepia=.5f),PhotoEffects(duotone=1f),
            PhotoEffects(gamma=2f),PhotoEffects(pixelate=12),PhotoEffects(blur=4),PhotoEffects(edges=1f),PhotoEffects(emboss=1f),
            PhotoEffects(chromatic=3),PhotoEffects(halftone=10),PhotoEffects(scanlines=1f))
        try{for(e in settings){
            val expected=IntArray(16*16){0xFF7090B0.toInt()};PhotoEffectsKernel.applyInPlace(expected,16,16,e)
            val result=PhotoRasterRenderer.render(source,PhotoEdit(effects=e),null)
            try{val actual=IntArray(16*16);result.getPixels(actual,0,16,0,0,16,16);assertArrayEquals(expected,actual);assertEquals(0xFF7090B0.toInt(),source.getPixel(8,8))}
            finally{result.recycle()}
        }}finally{source.recycle()}
    }
    @Test fun newEffectsPreservePersonAlphaAndExportASeparateMatchingCopy(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF808080.toInt())}
        val mask=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(0x80000000.toInt())}
        val original=File.createTempFile("effects-original-",".png",context.cacheDir)
        val copy=File.createTempFile("effects-copy-",".png",context.cacheDir)
        var output:Bitmap?=null
        try{
            original.outputStream().use{source.compress(Bitmap.CompressFormat.PNG,100,it)};val bytes=original.readBytes()
            val rendered=PhotoRasterRenderer.render(source,PhotoEdit(personCutout=true,effects=PhotoEffects(sepia=.4f,blur=4,scanlines=.2f)),mask);output=rendered
            assertEquals(128,rendered.getPixel(8,8) ushr 24)
            SafePhotoCopy.save(context,Uri.fromFile(original),Uri.fromFile(copy),rendered)
            val decoded=BitmapFactory.decodeFile(copy.path)
            try{assertTrue(rendered.sameAs(decoded));assertArrayEquals(bytes,original.readBytes())}finally{decoded.recycle()}
        }finally{output?.recycle();mask.recycle();source.recycle();copy.delete();original.delete()}
    }
    @Test fun reorderedRepeatedClipsReachTheSharedVideoCompositionInOrder(){
        val clips=listOf(VideoSegment(0,1000),VideoSegment(2000,4000))
        val proposal=VideoBatchTools.repeatSequence(VideoBatchTools.reverseOrder(clips),2)
        val composition=VideoCompositionExporter.composition(Uri.parse("content://synthetic/video"),proposal)
        val items=composition.sequences.single().editedMediaItems
        assertEquals(4,items.size)
        assertEquals(listOf(2000L,0L,2000L,0L),items.map{it.mediaItem.clippingConfiguration.startPositionMs})
        assertEquals(listOf(4000L,1000L,4000L,1000L),items.map{it.mediaItem.clippingConfiguration.endPositionMs})
    }
}
