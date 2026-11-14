package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executors

/** Private, local-only host for the actual full editor bundles. Never mounts account/search. */
class NobleCreativeEditorActivity : Activity() {
    companion object { const val EXTRA_KIND = "noble.creative.kind"; private const val PICK = 7601 }
    private lateinit var kind: String
    private lateinit var message: TextView
    private var web: WebView? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    @Volatile private var foreground = false
    @Volatile private var closed = false
    private var upload: CreativeEditorShare? = null
    @Volatile private var closeDialog: AlertDialog? = null
    private class Picker(val callback: ValueCallback<Array<Uri>>) { var ready = false; var uris: Array<Uri>? = null }
    @Volatile private var picker: Picker? = null
    private val imported = mutableSetOf<String>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        kind = CreativeEditorPolicy.kind(intent.getStringExtra(EXTRA_KIND)) ?: run { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        app.honorable.editor.NativeWorkspaceInsets.install(this, root)
        root.addView(Button(this).apply { text = "Close ${if(kind=="image")"image" else "video"} editor"; setTextColor(Color.BLACK); setOnClickListener { reviewClose() } })
        message = TextView(this).apply { text = "Opening the local editor…"; setTextColor(Color.DKGRAY); setPadding(16,8,16,8); accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        root.addView(message)
        setContentView(root)
        worker.execute {
            val valid = CreativeEditorPolicy.verified(kind) { assets.open(it) }
            runOnUiThread {
                if (closed || isDestroyed || isFinishing) return@runOnUiThread
                if (!valid) { message.text = "These editor files are missing or outdated. Update Noble. Your existing projects are kept."; return@runOnUiThread }
                openEditor(root)
            }
        }
    }
    private fun active() = foreground && !closed && !isDestroyed && !isFinishing
    @Suppress("SetJavaScriptEnabled")
    private fun openEditor(root: LinearLayout) {
        val view = WebView(this)
        web = view
        view.setBackgroundColor(Color.WHITE)
        view.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = true
            blockNetworkLoads = true; mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        view.addJavascriptInterface(Bridge(), "NobleCreative")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri=request.url
                // The bundled runner may reload its own entry to recover from a failed GPU backend.
                return !(request.isForMainFrame && uri.scheme=="https" && uri.host==CreativeEditorPolicy.HOST && uri.port in setOf(-1,443) && uri.path=="/$kind/index.html")
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                // Only exact URIs explicitly selected by this user may reach Android's content reader.
                if (request.method == "GET" && uri.scheme == "content" && synchronized(lock) { uri.toString() in imported }) return null
                val path = CreativeEditorPolicy.assetPath(kind, uri.scheme, uri.host, uri.port, uri.path)
                if (request.method != "GET" || path == null) return blocked()
                return runCatching {
                    val mime = CreativeEditorPolicy.mime(path)
                    WebResourceResponse(mime, if(mime.startsWith("text/")||mime=="application/json")"UTF-8" else null, 200, "OK",
                        mapOf("Content-Security-Policy" to CreativeEditorPolicy.CSP, "X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-store", "Cross-Origin-Resource-Policy" to "same-origin"), assets.open(path))
                }.getOrElse { blocked() }
            }
            override fun onPageFinished(view: WebView, url: String) {
                if(active()) message.text = "Local editor · save your project before closing. Local recovery and imported copies can remain after sign-out."
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if(request.isForMainFrame && !closed) message.text = "The editor could not load. Your existing files are kept. Reopen with an updated Android System WebView."
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                if(!active()||picker!=null||synchronized(lock){upload!=null}||closeDialog!=null){ callback.onReceiveValue(null); return true }
                val value=Picker(callback); picker=value
                return try {
                    val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.mode==FileChooserParams.MODE_OPEN_MULTIPLE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivityForResult(intent,PICK); true
                }catch(_:Exception){picker=null;callback.onReceiveValue(null);message.text="File selection is unavailable. Your project is kept.";true}
            }
            override fun onPermissionRequest(request: android.webkit.PermissionRequest) { request.deny() }
        }
        root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        view.loadUrl("https://${CreativeEditorPolicy.HOST}/$kind/index.html")
    }
    private fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(ByteArray(0)))
    @Deprecated("Android compatibility callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=PICK||closed)return
        val value=picker?:return
        val chosen=if(resultCode==RESULT_OK) buildList {
            data?.clipData?.let { clips -> for(i in 0 until clips.itemCount) add(clips.getItemAt(i).uri) }
            if(isEmpty())data?.data?.let{add(it)}
        }.distinct() else emptyList()
        worker.execute {
            val approved=runCatching {
                require(chosen.size<=8)
                var total=0L
                for(uri in chosen){
                    require(uri.scheme=="content")
                    val bytes=contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use{cursor->
                        val column=cursor.getColumnIndex(OpenableColumns.SIZE)
                        if(cursor.moveToFirst()&&column>=0&&!cursor.isNull(column))cursor.getLong(column) else -1L
                    }?:-1L
                    require(bytes in 1..CreativeEditorPolicy.MAX_IMPORT);total+=bytes
                    require(total<=2*CreativeEditorPolicy.MAX_IMPORT)
                }
                chosen.toTypedArray()
            }.getOrNull()
            runOnUiThread {
                if(closed||picker!==value)return@runOnUiThread
                if(approved==null)message.text="Choose up to 8 readable files, each up to 128 MB and up to 256 MB together. Your project is kept."
                value.uris=approved?.takeIf{it.isNotEmpty()};value.ready=true;settlePicker()
            }
        }
    }
    private fun settlePicker(){
        if(!active())return
        val value=picker?.takeIf{it.ready}?:return
        picker=null
        synchronized(lock){value.uris?.forEach{imported.add(it.toString())}}
        value.callback.onReceiveValue(value.uris)
    }
    private fun result(id:Int,opened:Boolean,error:String?=null){
        if(!active())return
        val value=org.json.JSONObject().put("id",id).put("opened",opened).put("error",error)
        web?.evaluateJavascript("window.nobleCreativeSaveResult && window.nobleCreativeSaveResult($value)",null)
    }
    private inner class Bridge {
        @JavascriptInterface fun beginSave(id:Int,length:Int,title:String):Boolean=synchronized(lock){
            if(!active()||upload!=null||picker!=null||closeDialog!=null)return@synchronized false
            runCatching{CreativeEditorShare(File(cacheDir,"created-images"),id,length,CreativeEditorShare.title(title)).also{upload=it};true}.getOrDefault(false)
        }
        @JavascriptInterface fun appendSave(id:Int,value:String):Boolean=synchronized(lock){
            val current=upload?.takeIf{it.id==id}?:return@synchronized false
            if(!active()){current.cancel();upload=null;return@synchronized false}
            current.append(value).also{if(!it)upload=null}
        }
        @JavascriptInterface fun cancelSave(id:Int){synchronized(lock){upload?.takeIf{it.id==id}?.let{it.cancel();upload=null}}}
        @JavascriptInterface fun finishSave(id:Int):Boolean {
            val finished=synchronized(lock){
                val value=upload?.takeIf{it.id==id}?:return false
                if(!active()){value.cancel();upload=null;return false}
                val file=runCatching{value.finish()}.getOrElse{value.cancel();upload=null;return false}
                upload=null;file to value.title
            }
            runOnUiThread {
                if(!active()){finished.first.delete();return@runOnUiThread}
                runCatching {
                    val uri=FileProvider.getUriForFile(this@NobleCreativeEditorActivity,"$packageName.creation",finished.first)
                    val send=Intent(Intent.ACTION_SEND).setType(CreativeEditorShare.contentType(finished.second)).putExtra(Intent.EXTRA_STREAM,uri)
                        .putExtra(Intent.EXTRA_TITLE,finished.second).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .apply{clipData=ClipData.newUri(contentResolver,"Noble editor copy",uri)}
                    startActivity(Intent.createChooser(send,"Keep editor copy"))
                    result(id,true)
                }.onFailure{finished.first.delete();result(id,false,"Sharing could not open. Your project is kept; export again when ready.")}
            }
            return true
        }
    }
    private fun reviewClose(){
        if(!active()||closeDialog!=null)return
        val dialog=AlertDialog.Builder(this).setTitle("Close this editor?")
            .setMessage("Save or export your project first to keep your latest work. Unsaved edits may be lost. Original files stay unchanged.")
            .setNegativeButton("Keep editing",null).setPositiveButton("Close"){_,_->if(active()&&closeDialog!=null)finish()}.create()
        closeDialog=dialog;dialog.setOnDismissListener{if(closeDialog===dialog)closeDialog=null};dialog.show()
    }
    @Deprecated("Android compatibility callback")
    override fun onBackPressed(){reviewClose()}
    override fun onResume(){super.onResume();foreground=true;settlePicker()}
    override fun onPause(){
        foreground=false
        closeDialog?.dismiss();closeDialog=null
        synchronized(lock){upload?.cancel();upload=null}
        web?.evaluateJavascript("window.nobleCreativeStopTransfers && window.nobleCreativeStopTransfers()",null)
        super.onPause()
    }
    override fun onDestroy(){
        closed=true;foreground=false
        synchronized(lock){upload?.cancel();upload=null;imported.clear()}
        picker?.callback?.onReceiveValue(null);picker=null
        closeDialog?.dismiss();closeDialog=null
        web?.let{it.removeJavascriptInterface("NobleCreative");it.stopLoading();it.destroy()};web=null
        worker.shutdownNow();super.onDestroy()
    }
}
