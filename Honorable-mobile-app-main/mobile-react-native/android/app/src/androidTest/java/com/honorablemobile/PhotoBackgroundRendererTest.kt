package com.honorablemobile

import android.graphics.Bitmap
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test

class PhotoBackgroundRendererTest {
    @Test fun solidBackgroundAndPersonMatteStayAlignedThroughCropAndEveryRotation(){
        val source=Bitmap.createBitmap(11,9,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFFFF0000.toInt())}
        val mask=Bitmap.createBitmap(11,9,Bitmap.Config.ARGB_8888)
        for(y in 0..8)for(x in 0..4)mask.setPixel(x,y,0xFF000000.toInt())
        try{for(rotation in listOf(0,90,180,270)){
            val edit=PhotoEdit(rotation=rotation,crop=.8f,background=PhotoBackground(PhotoBackgroundMode.SOLID))
            val g=PhotoToneGeometry(source.width,source.height,edit);val person=g.outputPoint(3.5f,4.5f);val backdrop=g.outputPoint(7.5f,4.5f)
            val output=PhotoRasterRenderer.render(source,edit,mask)
            try{assertEquals(0xFFFF0000.toInt(),output.getPixel(person.x.toInt(),person.y.toInt()));assertEquals(-1,output.getPixel(backdrop.x.toInt(),backdrop.y.toInt()))}finally{output.recycle()}
        };assertEquals(0xFFFF0000.toInt(),source.getPixel(7,4));assertEquals(0,mask.getPixel(7,4))}finally{source.recycle();mask.recycle()}
    }
    @Test fun blurKeepsPersonSharpAndDoesNotBleedPersonColorIntoBackground(){
        val source=Bitmap.createBitmap(21,21,Bitmap.Config.ARGB_8888).apply{eraseColor(0xFF0000FF.toInt())}
        val mask=Bitmap.createBitmap(21,21,Bitmap.Config.ARGB_8888)
        for(y in 6..14)for(x in 6..14){source.setPixel(x,y,0xFFFF0000.toInt());mask.setPixel(x,y,0xFF000000.toInt())}
        val output=PhotoRasterRenderer.render(source,PhotoEdit(background=PhotoBackground(strength=60)),mask)
        try{assertEquals(0xFFFF0000.toInt(),output.getPixel(10,10));assertEquals(0xFF0000FF.toInt(),output.getPixel(4,10))}finally{output.recycle();source.recycle();mask.recycle()}
    }
    @Test fun backgroundRenderingRequiresTheMaskInsteadOfSilentlySavingUneditedPixels(){
        val source=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
        try{try{PhotoRasterRenderer.render(source,PhotoEdit(background=PhotoBackground()),null);fail("Missing matte accepted")}catch(_:IllegalArgumentException){}}finally{source.recycle()}
    }
}
