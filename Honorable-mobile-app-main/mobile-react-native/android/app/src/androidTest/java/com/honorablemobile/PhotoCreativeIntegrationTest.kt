package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoCreativeIntegrationTest {
    @Test fun everyCreativeToolReachesTheSharedRasterPipeline() {
        val source=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF7090B0.toInt())}
        val settings=listOf(PhotoCreative(vibrance=1f),PhotoCreative(hue=.5f),PhotoCreative(fade=.5f),
            PhotoCreative(grain=1f),PhotoCreative(posterize=3),PhotoCreative(threshold=.5f),
            PhotoCreative(splitTone=1f),PhotoCreative(red=.5f))
        try {
            for(setting in settings) {
                val output=PhotoRasterRenderer.render(source,PhotoEdit(creative=setting),null)
                try {
                    assertEquals(PhotoCreativeKernel(setting,16,16).apply(0xFF7090B0.toInt(),8,8),output.getPixel(8,8))
                    assertEquals(0xFF7090B0.toInt(),source.getPixel(8,8))
                } finally { output.recycle() }
            }
        } finally { source.recycle() }
    }
    @Test fun creativePngExportMatchesRenderedPixelsWithoutModifyingSource() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=File.createTempFile("creative-original-",".png",context.cacheDir)
        val copy=File.createTempFile("creative-copy-",".png",context.cacheDir)
        val source=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF7090B0.toInt())}
        var rendered:Bitmap?=null
        try {
            original.outputStream().use{source.compress(Bitmap.CompressFormat.PNG,100,it)}
            val bytes=original.readBytes()
            val output=PhotoRasterRenderer.render(source,PhotoEdit(creative=PhotoCreative(hue=.2f,grain=.5f,splitTone=.3f)),null)
            rendered=output
            SafePhotoCopy.save(context,Uri.fromFile(original),Uri.fromFile(copy),output)
            val saved=BitmapFactory.decodeFile(copy.path)
            try { assertTrue(output.sameAs(saved));assertArrayEquals(bytes,original.readBytes()) }
            finally { saved.recycle() }
        } finally { rendered?.recycle();source.recycle();copy.delete();original.delete() }
    }
}
