package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Synthetic fixture only. Authored for the broad Android batch; never substitute compilation for execution. */
class DrawingPhotoCopyIntegrationTest {
    @Test fun localPreparedCopyPreservesAlphaDimensionsAndOriginalBytes(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"created-images").apply{mkdirs()}
        val source=File.createTempFile("photo-copy-test-",".png",directory)
        val bitmap=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888)
        try{
            bitmap.eraseColor(Color.argb(128,0,0,255));bitmap.setPixel(0,0,Color.RED)
            source.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            val original=source.readBytes();val uri=FileProvider.getUriForFile(context,context.packageName+".creation",source)
            val data=DrawingPhotoCopy.prepare(context,uri){false};assertTrue(data.startsWith("data:image/png;base64,"))
            val bytes=Base64.decode(data.substringAfter(','),Base64.NO_WRAP);assertTrue(bytes.size<=DrawingPhotoPolicy.MAX_BYTES)
            val result=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
            try{assertEquals(64,result.width);assertEquals(48,result.height);assertEquals(Color.RED,result.getPixel(0,0));assertEquals(128,Color.alpha(result.getPixel(30,30)))}finally{result.recycle()}
            assertArrayEquals(original,source.readBytes())
        }finally{bitmap.recycle();source.delete()}
    }
    @Test fun cancellationAndNonContentSourcesNeverProduceACopy(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(runCatching{DrawingPhotoCopy.prepare(context,android.net.Uri.parse("content://synthetic/not-opened")){true}}.isFailure)
        for(source in listOf("file:///synthetic.png","https://example.com/synthetic.png","data:image/png;base64,AA=="))assertTrue(runCatching{DrawingPhotoCopy.prepare(context,android.net.Uri.parse(source)){false}}.isFailure)
    }
}
