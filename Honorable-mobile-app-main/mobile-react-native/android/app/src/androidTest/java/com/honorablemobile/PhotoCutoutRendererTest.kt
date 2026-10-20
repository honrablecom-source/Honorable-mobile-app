package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.Color
import app.honorable.editor.PhotoEdit
import app.honorable.editor.PhotoRasterRenderer
import org.junit.Assert.*
import org.junit.Test

/** Must execute on Android; compilation alone is not pixel-output evidence. */
class PhotoCutoutRendererTest {
    @Test fun transparentMatteKeepsForegroundAndOriginalPixels() {
        val original = Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val mask = Bitmap.createBitmap(intArrayOf(-1,0,-1,0),2,2,Bitmap.Config.ARGB_8888)
        val output = PhotoRasterRenderer.render(original,PhotoEdit(personCutout=true),mask)
        try {
            assertEquals(255,Color.alpha(output.getPixel(0,0)))
            assertEquals(0,Color.alpha(output.getPixel(1,0)))
            assertEquals(Color.RED,output.getPixel(0,0))
            assertEquals(Color.RED,original.getPixel(1,0))
        } finally { output.recycle();mask.recycle();original.recycle() }
    }
    @Test fun cropRotationAndCompareUseTheSameSourceAlignedMatte() {
        val original=Bitmap.createBitmap(4,2,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val mask=Bitmap.createBitmap(intArrayOf(-1,-1,0,0,-1,-1,0,0),4,2,Bitmap.Config.ARGB_8888)
        val rotated=PhotoRasterRenderer.render(original,PhotoEdit(rotation=90,personCutout=true),mask)
        val before=PhotoRasterRenderer.render(original,PhotoEdit(),null)
        val cropped=PhotoRasterRenderer.render(original,PhotoEdit(crop=.5f,personCutout=true),mask)
        try {
            assertEquals(2,rotated.width);assertEquals(4,rotated.height)
            assertEquals(255,Color.alpha(rotated.getPixel(0,0)))
            assertEquals(0,Color.alpha(rotated.getPixel(0,3)))
            assertEquals(Color.GREEN,before.getPixel(3,1))
            assertEquals(2,cropped.width);assertEquals(1,cropped.height)
            assertEquals(255,Color.alpha(cropped.getPixel(0,0)))
            assertEquals(0,Color.alpha(cropped.getPixel(1,0)))
        } finally {rotated.recycle();before.recycle();cropped.recycle();mask.recycle();original.recycle()}
    }
    @Test fun cutoutWithoutAValidMaskCannotSilentlyExportOriginal() {
        val original=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
        try {
            try {PhotoRasterRenderer.render(original,PhotoEdit(personCutout=true),null);fail("Missing matte accepted")}
            catch(_:IllegalArgumentException){}
        } finally {original.recycle()}
    }
}
