package com.honorablemobile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import android.widget.*
import androidx.core.content.FileProvider
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.PhotoEditorActivity
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** Trusted bundled drawing code only. No network pages, file chooser, account access or remote collaboration. */
class HonorableDrawingActivity : Activity() {
    private lateinit var web:WebView
    private val worker=Executors.newSingleThreadExecutor()
    private val exporting=AtomicBoolean(false)
    private val csp="default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        if(intent.getStringExtra(EditorAccessMode.EXTRA)!=EditorAccessMode.GUEST_BASIC.name){finish();return}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE)}
        app.honorable.editor.NativeWorkspaceInsets.install(this,root)
        val actions=LinearLayout(this)
        actions.addView(Button(this).apply{text="Close drawing";setOnClickListener{finish()}},LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Button(this).apply{text="New drawing";setOnClickListener{
            if(exporting.get())return@setOnClickListener
            android.app.AlertDialog.Builder(this@HonorableDrawingActivity).setTitle("Start a new drawing?").setMessage("This clears the current drawing and its saved local draft. Export an image first if you want to keep it.")
                .setNegativeButton("Cancel",null).setPositiveButton("New drawing"){_,_->web.evaluateJavascript("(function(){if(window.honorableDrawingBusy)return false;localStorage.removeItem('honorable-drawing-v1');return true;})()"){reset->if(reset=="true"&&!isDestroyed&&!isFinishing)web.reload()}}.show()
        }},LinearLayout.LayoutParams(0,-2,1f))
        root.addView(actions)
        val message=TextView(this).apply{text="Offline drawing · save your draft before leaving";setTextColor(Color.DKGRAY);setPadding(16,8,16,8)}
        web=WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled=true;settings.domStorageEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.blockNetworkLoads=true;settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false);settings.javaScriptCanOpenWindowsAutomatically=false
            settings.cacheMode=WebSettings.LOAD_NO_CACHE
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
                    val uri=request.url
                    val path=DrawingPolicy.assetPath(uri.scheme,uri.host,uri.port,uri.path)
                    if(request.method!="GET"||path==null)return blocked()
                    return try {
                        val mime=when(path.substringAfterLast('.')){"html"->"text/html";"js"->"text/javascript";"css"->"text/css";"json"->"application/json";"woff2"->"font/woff2";"woff"->"font/woff";"ttf"->"font/ttf";"wasm"->"application/wasm";else->"text/plain"}
                        WebResourceResponse(mime,if(mime.startsWith("text/")||mime=="application/json")"UTF-8"else null,200,"OK",mapOf("Content-Security-Policy" to csp,"X-Content-Type-Options" to "nosniff","Cache-Control" to "no-store"),assets.open(path))
                    }catch(_:Exception){blocked()}
                }
                override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame)message.text="Drawing could not load. Update Android System WebView and reopen. Your saved draft stays local."}
            }
            addJavascriptInterface(Bridge(),"HonorableDrawing")
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f));root.addView(message)
        setContentView(root)
        web.loadUrl("https://${DrawingPolicy.HOST}/drawing/index.html")
    }
    private fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(ByteArray(0)))
    inner class Bridge {
        @JavascriptInterface fun usePng(value:String) {
            if(isDestroyed||isFinishing||!exporting.compareAndSet(false,true))return
            try { worker.execute {
                var file:File?=null;var bitmap:Bitmap?=null
                val result=runCatching {
                    check(!isDestroyed&&!Thread.currentThread().isInterrupted)
                    val bytes=Base64.decode(DrawingPolicy.base64Payload(value),Base64.NO_WRAP)
                    val size=DrawingPolicy.validatePng(bytes)
                    val decoded=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size)){"Drawing PNG could not be read"};bitmap=decoded
                    require(decoded.width==size.first&&decoded.height==size.second)
                    val dir=File(cacheDir,"created-images");check(dir.isDirectory||dir.mkdirs())
                    val target=File.createTempFile("honorable-drawing-",".png",dir);file=target
                    target.outputStream().use{check(decoded.compress(Bitmap.CompressFormat.PNG,100,it))}
                    FileProvider.getUriForFile(this@HonorableDrawingActivity,packageName+".creation",target)
                }
                bitmap?.recycle()
                runOnUiThread {
                    exporting.set(false)
                    if(isDestroyed||isFinishing){file?.delete();return@runOnUiThread}
                    result.onSuccess { uri ->
                        runCatching{startActivity(Intent(this@HonorableDrawingActivity,PhotoEditorActivity::class.java).apply{putExtra("uri",uri.toString());putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}.onSuccess{finish()}.onFailure{file?.delete();showError("Could not open the photo editor. Your drawing draft stays on this device.")}
                    }.onFailure{file?.delete();showError(if(it is OutOfMemoryError)"Not enough memory. Try a smaller drawing."else it.message?:"Image creation failed. Your draft stays local.")}
                }
            } } catch(_:java.util.concurrent.RejectedExecutionException){exporting.set(false)}
        }
    }
    private fun showError(message:String){web.evaluateJavascript("window.honorableExportResult && window.honorableExportResult(${JSONObject.quote(message)})",null)}
    override fun onPause(){if(::web.isInitialized)web.evaluateJavascript("window.honorableSaveDraft && window.honorableSaveDraft()",null);super.onPause()}
    override fun onDestroy(){worker.shutdownNow();if(::web.isInitialized){web.removeJavascriptInterface("HonorableDrawing");web.stopLoading();web.destroy()};super.onDestroy()}
}
