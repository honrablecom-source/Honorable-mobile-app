package com.honorablemobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test

/** Real installed host/engines and generated fixtures. No account, gallery or network data.
 * The OS chooser is intercepted: actual private export bytes are checked, not destination delivery.
 * Run image and video methods separately on physical hardware; compile success is not acceptance.
 */
class FullStudioDeviceAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val created = mutableSetOf<File>()

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun findWeb(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWeb(view.getChildAt(it)) }
        else -> null
    }
    private fun js(web: WebView, source: String): String {
        val latch = CountDownLatch(1); var result = "null"
        instrumentation.runOnMainSync { web.evaluateJavascript(source) { result = it ?: "null"; latch.countDown() } }
        check(latch.await(10, TimeUnit.SECONDS)) { "WebView JavaScript callback timed out" }
        return result
    }
    private fun until(label: String, timeout: Long = 120000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!predicate()) { check(SystemClock.elapsedRealtime() < deadline) { label }; SystemClock.sleep(100) }
    }
    private fun task(web: WebView, body: String, timeout: Long = 120000): JSONObject {
        js(web, "window.__nobleAcceptance=null;(async()=>{$body})().then(v=>window.__nobleAcceptance={ok:true,value:v}).catch(e=>window.__nobleAcceptance={ok:false,error:String(e.message||e).slice(0,500)});void 0")
        var answer: JSONObject? = null
        until("Installed editor operation timed out", timeout) {
            val decoded = JSONTokener(js(web, "JSON.stringify(window.__nobleAcceptance)")).nextValue()
            if (decoded is String && decoded != "null") answer = JSONObject(decoded)
            answer != null
        }
        val result = requireNotNull(answer)
        check(result.getBoolean("ok")) { result.optString("error", "Editor operation failed") }
        return result
    }
    private fun exports(): Set<File> = File(context.cacheDir, "created-images").listFiles()?.filter { it.name.startsWith("Noble-editor-") }?.toSet() ?: emptySet()
    private fun export(web: WebView, extension: String, body: String): File {
        val before = exports()
        task(web, "$body;return true;")
        var output: File? = null
        until("Real native $extension export was not produced", 30000) {
            val files = exports() - before
            created.addAll(files)
            output = files.singleOrNull { it.extension == extension && it.length() > 0 }
            output != null
        }
        return requireNotNull(output)
    }
    private fun withHost(kind: String, body: (WebView) -> Unit) {
        val before = exports()
        val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_CHOOSER), Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        val activity = instrumentation.startActivitySync(Intent(context, NobleCreativeEditorActivity::class.java)
            .putExtra(NobleCreativeEditorActivity.EXTRA_KIND, kind).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            var web: WebView? = null
            until("Actual private studio WebView did not open") {
                instrumentation.runOnMainSync { web = findWeb(activity.window.decorView) }; web != null
            }
            val view = requireNotNull(web)
            until("Actual $kind engine did not initialize") {
                js(view, if (kind == "image") "!!window.nobleImage && !document.getElementById('photocraft_loading')" else "Number.isFinite(window.filmcraft?.info?.().startupMs)") == "true"
            }
            body(view)
            val evidence = JSONObject().put("kind", kind).put("status", "PASSED")
                .put("installedApkSha256", hash(File(context.applicationInfo.sourceDir)))
                .put("model", Build.MODEL).put("sdk", Build.VERSION.SDK_INT)
                .put("webView", WebView.getCurrentWebViewPackage()?.versionName)
                .put("chooserIntercepted", true).put("destinationDeliveryVerified", false)
                .put("generatedExports", created.map { JSONObject().put("extension", it.extension).put("bytes", it.length()).put("sha256", hash(it)) })
            File(requireNotNull(context.getExternalFilesDir("noble-acceptance")), "$kind-result.json").writeText(evidence.toString(2))
            android.util.Log.i("NobleDeviceAcceptance", evidence.toString())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync(); instrumentation.removeMonitor(monitor)
            created.addAll(exports() - before)
            created.forEach { it.delete() }
        }
    }
    private fun pixels(file: File): IntArray {
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path)) { "Export PNG was unreadable" }
        try {
            assertEquals(64, bitmap.width); assertEquals(48, bitmap.height)
            return IntArray(64 * 48).also { bitmap.getPixels(it, 0, 64, 0, 0, 64, 48) }
        } finally { bitmap.recycle() }
    }

    @Test fun imageProjectUndoNeuralColorAndNativePngCopy() = withHost("image") { web ->
        task(web, """
            const c=document.createElement('canvas');c.width=64;c.height=48;const g=c.getContext('2d');
            g.fillStyle='#e03030';g.fillRect(0,0,32,48);g.fillStyle='#3060e0';g.fillRect(32,0,32,48);g.clearRect(0,0,4,4);
            const b=await new Promise(r=>c.toBlob(r));window.__fixture=new Uint8Array(await b.arrayBuffer());
            nobleImage.importFile('generated-only.png',window.__fixture);return true;
        """.trimIndent())
        until("Generated image was not imported") { task(web, "return !!(await nobleImage.request('ui.inspect',{})).result.document;").getBoolean("value") }
        val original = pixels(export(web, "png", "await nobleImage.request('app.save',{path:'/exports/device-original.png'})"))
        task(web, "const r=await nobleImage.request('engine.execute',{command:'image.adjustments.invert',params:{}});if(!r.ok)throw Error('Invert failed');return true;")
        val edited = pixels(export(web, "png", "await nobleImage.request('app.save',{path:'/exports/device-edited.png'})"))
        assertTrue(original.indices.count { original[it] != edited[it] } > 1000)
        assertTrue(original.indices.all { original[it] ushr 24 == edited[it] ushr 24 })
        task(web, "await nobleImage.request('engine.execute',{command:'edit.undo',params:{}});return true;")
        assertArrayEquals(original, pixels(export(web, "png", "await nobleImage.request('app.save',{path:'/exports/device-undone.png'})")))
        task(web, "await nobleImage.request('engine.execute',{command:'layer.duplicate',params:{name:'Editable generated copy'}});return true;")
        val project = export(web, "pcraft", "await nobleImage.request('app.save',{path:'/exports/device-layered.pcraft'})")
        val encoded = Base64.encodeToString(project.readBytes(), Base64.NO_WRAP)
        task(web, "nobleImage.importFile('reopened.pcraft',Uint8Array.from(atob('$encoded'),c=>c.charCodeAt(0)));return true;")
        until("Actual saved layered project did not reopen") { task(web, "const s=(await nobleImage.request('ui.inspect',{})).result;return s.views.length>=2&&s.document.layers.length===2;").getBoolean("value") }
        task(web, """
            window.__sourceDocument=JSON.stringify((await nobleImage.request('ui.inspect',{})).result.document);
            document.querySelector('#noble-ai-bar button').click();
            const choice=document.querySelector('[data-tool]');choice.value='color';choice.dispatchEvent(new Event('change'));
            document.querySelector('[data-current]').click();return true;
        """.trimIndent())
        until("Current-document working copy did not prepare") { js(web, "!document.querySelector('[data-run]').disabled") == "true" }
        js(web, "document.querySelector('[data-run]').click();void 0")
        until("Actual on-device neural color graph failed or timed out") { js(web, "!document.querySelector('[data-approve]').disabled") == "true" }
        val stats = task(web, """
            const a=document.querySelector('[data-before]'),b=document.querySelector('[data-after]');
            const x=a.getContext('2d').getImageData(0,0,a.width,a.height).data,y=b.getContext('2d').getImageData(0,0,b.width,b.height).data;
            let changed=0;for(let i=0;i<x.length;i++){if(i%4===3&&x[i]!==y[i])throw Error('AI changed alpha');if(i%4!==3&&x[i]!==y[i])changed++;}
            if(JSON.stringify((await nobleImage.request('ui.inspect',{})).result.document)!==window.__sourceDocument)throw Error('AI mutated original document');
            if(!document.querySelector('[data-export]').disabled)throw Error('Export bypassed review');return changed;
        """.trimIndent()).getInt("value")
        assertTrue("Real neural output must change generated colors", stats > 0)
        val ai = export(web, "png", "document.querySelector('[data-approve]').click();document.querySelector('[data-export]').click()")
        pixels(ai)
    }

    @Test fun videoProjectAndActualH264NativeCopyReopen() = withHost("video") { web ->
        task(web, """
            const c=document.createElement('canvas');c.width=160;c.height=96;const g=c.getContext('2d');
            g.fillStyle='#e03030';g.fillRect(0,0,80,96);g.fillStyle='#3060e0';g.fillRect(80,0,80,96);
            const b=await new Promise(r=>c.toBlob(r));const imported=await filmcraft.importFiles([new File([b],'generated-only.png',{type:'image/png'})]);
            if(!imported.items?.length)throw Error('Generated still import failed');
            await filmcraft.execute('file.newSequence',{fromItem:imported.items[0],name:'Generated device fixture',width:160,height:96,fps:12});return true;
        """.trimIndent())
        val project = export(web, "fcproj", "await filmcraft.execute('file.saveAs',{path:'/exports/device-video.fcproj'})")
        val encoded = Base64.encodeToString(project.readBytes(), Base64.NO_WRAP)
        task(web, "await filmcraft.openProject(new File([Uint8Array.from(atob('$encoded'),c=>c.charCodeAt(0))],'reopened.fcproj',{type:'application/json'}));return true;")
        val movie = export(web, "mp4", """
            const exportJob=await filmcraft.execute('file.exportMedia',{format:'h264',path:'/exports/device-video.mp4',width:160,height:96,fps:12,bitrateKbps:200,audio:false});
            const start=performance.now();let done;
            do{await new Promise(r=>setTimeout(r,100));done=(await filmcraft.execute('jobs.list')).find(j=>j.id===exportJob.job&&j.finished);if(performance.now()-start>100000)throw Error('H264 export timed out');}while(!done);
            if(done.error)throw Error('Actual H264 export failed');
        """.trimIndent())
        val decoder = MediaMetadataRetriever()
        try {
            decoder.setDataSource(movie.path)
            assertEquals("160", decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            assertEquals("96", decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            assertTrue(requireNotNull(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong() >= 1000)
            val frame = requireNotNull(decoder.getFrameAtTime(0)) { "Android could not decode exported video" }; frame.recycle()
        } finally { decoder.release() }
    }
}
