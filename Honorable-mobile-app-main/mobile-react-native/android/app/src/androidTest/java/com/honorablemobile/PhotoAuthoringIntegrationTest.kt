package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoAuthoringIntegrationTest {
    @Test fun allTenCompositionToolsRenderWithoutChangingInput() {
        val source=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF7090B0.toInt())}
        val settings=listOf(PhotoComposition(padding=.2f,canvasColor=-1),PhotoComposition(frame=.1f),PhotoComposition(shadow=.8f),
            PhotoComposition(reflection=.4f),PhotoComposition(shadow=Float.MIN_VALUE),PhotoComposition(shape=4),PhotoComposition(tiles=3),PhotoComposition(sectors=6),
            PhotoComposition(perspective=.3f),PhotoComposition(watermark="Noble"),PhotoComposition(keyColor=0x7090B0))
        try { for(c in settings) {
            val out=PhotoRasterRenderer.render(source,PhotoEdit(composition=c),null)
            try { assertTrue(out.width>0&&out.height>0);assertTrue(out.width.toLong()*out.height<=64*48);assertEquals(0xFF7090B0.toInt(),source.getPixel(32,24)) }
            finally {out.recycle()}
        }}finally{source.recycle()}
    }
    @Test fun colorKeyTransparencyAndPngCopyKeepOriginalBytes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=File.createTempFile("authoring-original-",".png",context.cacheDir)
        val copy=File.createTempFile("authoring-copy-",".png",context.cacheDir)
        val source=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF00FF00.toInt());setPixel(16,16,0xFFFF0000.toInt())}
        var result:Bitmap?=null
        try {
            original.outputStream().use{source.compress(Bitmap.CompressFormat.PNG,100,it)};val bytes=original.readBytes()
            val out=PhotoRasterRenderer.render(source,PhotoEdit(composition=PhotoComposition(keyColor=0x00FF00)),null);result=out
            assertEquals(0,out.getPixel(0,0) ushr 24);assertEquals(0xFFFF0000.toInt(),out.getPixel(16,16))
            SafePhotoCopy.save(context,Uri.fromFile(original),Uri.fromFile(copy),out)
            val saved=BitmapFactory.decodeFile(copy.path)
            try{assertTrue(out.sameAs(saved));assertArrayEquals(bytes,original.readBytes())}finally{saved.recycle()}
        }finally{result?.recycle();source.recycle();copy.delete();original.delete()}
    }
    @Test fun frameDoesNotFillTheTransparentInteriorOfAShapeMask() {
        val source=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(-1)}
        try {
            val state=PhotoComposition(shape=1,frame=.1f)
            val p=PhotoCompositionPlan.create(64,64,state)
            val out=PhotoRasterRenderer.render(source,PhotoEdit(composition=state),null)
            try{assertEquals(0,out.getPixel((p.left+2*p.scale).toInt(),(p.top+2*p.scale).toInt()) ushr 24)}finally{out.recycle()}
        }finally{source.recycle()}
    }
}
