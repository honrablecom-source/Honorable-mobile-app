package com.honorablemobile
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
class DrawingIntegrationTest {
    @Test fun bundledDrawingEntryAndLicenseExistAndActivityIsPrivate() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(context.packageManager.getActivityInfo(ComponentName(context,HonorableDrawingActivity::class.java),0).exported)
        val html=context.assets.open("drawing/index.html").bufferedReader().use{it.readText()}
        assertTrue(html.contains("editor.js"));assertFalse(html.contains("http://"));assertFalse(html.contains("https://"))
        assertTrue(context.assets.open("drawing/THIRD_PARTY/Excalidraw-MIT.txt").use{it.readBytes()}.isNotEmpty())
    }
    @Test fun realAndroidPngUsesTheBoundedExportContract() {
        val bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888)
        val bytes=ByteArrayOutputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it));it.toByteArray()};bitmap.recycle()
        assertEquals(32 to 24,DrawingPolicy.validatePng(bytes))
        assertEquals(android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP),DrawingPolicy.base64Payload(DrawingPolicy.PNG_PREFIX+android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)))
    }
}
