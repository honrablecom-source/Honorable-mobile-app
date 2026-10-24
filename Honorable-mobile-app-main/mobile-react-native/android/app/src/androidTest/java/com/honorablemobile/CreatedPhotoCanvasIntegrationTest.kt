package com.honorablemobile
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class CreatedPhotoCanvasIntegrationTest {
 @Test fun canvasIsReadableWhitePngThroughPrivateProvider(){val context=InstrumentationRegistry.getInstrumentation().targetContext;val uri=CreatedPhotoCanvas.create(context,"square");assertEquals("image/png",context.contentResolver.getType(uri));val bitmap=context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it)};assertNotNull(bitmap);assertEquals(1080,bitmap.width);assertEquals(1080,bitmap.height);assertEquals(Color.WHITE,bitmap.getPixel(540,540));bitmap.recycle();val provider=context.packageManager.resolveContentProvider(context.packageName+".creation",0);assertNotNull(provider);assertFalse(provider!!.exported)}
}
