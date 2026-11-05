package com.honorablemobile

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/** Actual Activity reset protocol with constructor-free recording WebView; no Android lifecycle proof. */
class DrawingResetAdmissionTest {
    private class RecordingWebView(context: Context) : WebView(context) {
        var calls = 0
        var reloads = 0
        var script: String? = null
        var reply: ValueCallback<String>? = null
        override fun evaluateJavascript(value: String, callback: ValueCallback<String>?) {
            calls++; script = value; reply = callback
        }
        override fun reload() { reloads++ }
    }
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
    private fun setup(importing: Boolean = false, exporting: Boolean = false): Pair<HonorableDrawingActivity, RecordingWebView> {
        val activity = unsafe.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity
        val web = unsafe.allocateInstance(RecordingWebView::class.java) as RecordingWebView
        for ((name, value) in listOf("importing" to AtomicBoolean(importing), "exporting" to AtomicBoolean(exporting), "web" to web)) {
            activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
        }
        return activity to web
    }
    private fun request(activity: HonorableDrawingActivity) = activity.javaClass.getDeclaredMethod("requestNewDrawing").apply { isAccessible = true }.invoke(activity) as Boolean
    @Test fun importInProgressRefusesBeforeWebView() { val (a,w)=setup(importing=true); assertFalse(request(a)); assertEquals(0,w.calls) }
    @Test fun exportInProgressRefusesBeforeWebView() { val (a,w)=setup(exporting=true); assertFalse(request(a)); assertEquals(0,w.calls) }
    @Test fun idleRequestsSynchronousEditorGuard() { val (a,w)=setup(); assertTrue(request(a)); assertEquals(1,w.calls); assertEquals("typeof window.honorableNewDrawing === 'function' && window.honorableNewDrawing()",w.script); assertEquals(0,w.reloads) }
    @Test fun editorRefusalKeepsPage() { val (a,w)=setup(); request(a); w.reply!!.onReceiveValue("false"); assertEquals(0,w.reloads) }
    @Test fun editorSuccessReloadsOnce() { val (a,w)=setup(); request(a); w.reply!!.onReceiveValue("true"); assertEquals(1,w.reloads) }
}
