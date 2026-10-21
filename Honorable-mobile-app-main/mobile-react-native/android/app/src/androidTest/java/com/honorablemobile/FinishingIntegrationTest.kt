package com.honorablemobile

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FinishingIntegrationTest {
    @Test fun photoRatioAndMirrorKeepTheSameSourceMatteAndOriginalBytes(){
        val source=Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFFFF0000.toInt())}
        val mask=Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888)
        for(y in 0..7)for(x in 0..5)mask.setPixel(x,y,0xFF000000.toInt())
        val edit=PhotoEdit(personCutout=true,finish=PhotoFinish(ratio=1f,mirrorX=true))
        val output=PhotoRasterRenderer.render(source,edit,mask)
        try{assertEquals(8,output.width);assertEquals(8,output.height);assertEquals(0,output.getPixel(1,3) ushr 24);assertEquals(255,output.getPixel(6,3) ushr 24);assertEquals(0xFFFF0000.toInt(),source.getPixel(1,3))}finally{output.recycle();source.recycle();mask.recycle()}
    }
    @Test fun jpegExportFlattensAlphaToWhiteAndPreservesTheSource(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File.createTempFile("synthetic-source-",".png",context.cacheDir);val target=File.createTempFile("synthetic-export-",".jpg",context.cacheDir)
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(0x80FF0000.toInt())}
        try{source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};val bytes=source.readBytes()
            SafePhotoCopy.save(context,Uri.fromFile(source),Uri.fromFile(target),bitmap,SafePhotoCopy.Encoding.JPEG,85)
            assertArrayEquals(bytes,source.readBytes());val decoded=android.graphics.BitmapFactory.decodeFile(target.absolutePath)
            try{assertEquals(255,decoded.getPixel(16,16) ushr 24);assertTrue(((decoded.getPixel(16,16) ushr 8)and 255) in 110..145)}finally{decoded.recycle()}
        }finally{bitmap.recycle();source.delete();target.delete()}
    }
    @Test fun sharedVideoCompositionCarriesTransformsTitlesAndMasterAudioFade(){
        val clip=VideoSegment(0,2000,title="Title",grade=VideoGrade(warmth=.3f),layout=VideoLayout(2,true,90,true),titleStyle=VideoTitleStyle(0,.08f),audioFade=true)
        val composition=VideoCompositionExporter.composition(Uri.parse("content://synthetic/video"),listOf(clip))
        assertEquals(4,composition.sequences.single().editedMediaItems.single().effects.videoEffects.size)
        assertEquals(1,composition.effects.audioProcessors.size)
        val fade=VideoProjectAudioFade(2_000_000);assertEquals(1f,fade.getGainFactorAtSamplePosition(48000,48000),0f)
        assertTrue(fade.isUnityUntil(72000,48000)>72000)
    }
}
