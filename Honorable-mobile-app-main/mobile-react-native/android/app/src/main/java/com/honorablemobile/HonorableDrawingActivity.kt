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
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** Trusted bundled drawing code and explicit local photo selection. No network pages, file access or remote collaboration. */
class HonorableDrawingActivity : Activity() {
    private lateinit var web:WebView
    private lateinit var nativeMessage:TextView
    private var interruptedFileKind:String?=null
    private val fileStateKey="noble-drawing-file-operation"
    private val idleMessage="Offline drawing · save your draft before leaving"
    private fun interruptedFileMessage(kind:String)=if(kind=="save")"Project file save was interrupted. Check the destination for an incomplete file, then save again if needed. Check your drawing before continuing."else"Project file selection was interrupted. Check your drawing, then choose the file again."
    private fun clearFileNotice(){interruptedFileKind=null;if(::nativeMessage.isInitialized)nativeMessage.text=idleMessage}
    private val worker=Executors.newSingleThreadExecutor()
    private val exporting=AtomicBoolean(false)
    private val importing=AtomicBoolean(false)
    private val operationLock=Any()
    private fun claimImport()=synchronized(operationLock){resetDispatch==null&&!exporting.get()&&importing.compareAndSet(false,true)}
    private fun claimExport()=synchronized(operationLock){resetDispatch==null&&!importing.get()&&exporting.compareAndSet(false,true)}
    private class ResetReview(val generation:Long)
    private var resetGeneration=0L
    private var resetReview:ResetReview?=null
    private var resetDialog:android.app.AlertDialog?=null
    private class ResetDispatch { var replied=false;var reset=false }
    @Volatile private var resetDispatch:ResetDispatch?=null
    @Volatile private var printForeground=false
    @Volatile private var printClosed=false
    @Volatile private var printUpload:DrawingPrintUpload?=null
    @Volatile private var printPreparing:DrawingPrintUpload?=null
    private class PrintOperation(val id:Int,val adapter:DrawingPrintAdapter){var job:android.print.PrintJob?=null}
    @Volatile private var activePrint:PrintOperation?=null
    private class SvgOperation(val upload:DrawingSvgUpload){var preparing=false}
    @Volatile private var svgOperation:SvgOperation?=null
    private class ImageOperation { var launching=false }
    @Volatile private var imageOperation:ImageOperation?=null
    private class PhotoOperation(val id:Int,val leased:Boolean){var received=false;var started=false;var ready=false;var uri:android.net.Uri?=null}
    @Volatile private var photoOperation:PhotoOperation?=null
    private data class ProjectOperation(val id:Int,val saving:Boolean,val value:String?=null){
        var received=false;var started=false;var uri:android.net.Uri?=null
    }
    private data class ProjectRead(val id:Int,val text:String)
    @Volatile private var projectUpload:DrawingProjectUpload?=null
    @Volatile private var projectRead:ProjectRead?=null
    @Volatile private var projectOperation:ProjectOperation?=null
    private class VectorOperation(val id:Int,val code:Int){var received=false;var started=false;var uri:android.net.Uri?=null;var text:String?=null}
    @Volatile private var vectorOperation:VectorOperation?=null
    private var lastVectorCode=0
    private val csp="default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        if(intent.getStringExtra(EditorAccessMode.EXTRA)!=EditorAccessMode.GUEST_BASIC.name){finish();return}
        interruptedFileKind=state?.getString(fileStateKey)?.takeIf{it=="save"||it=="open"}
        lastVectorCode=state?.getInt("noble-drawing-vector-code",0)?.takeIf{DrawingVectorFiles.acceptsCode(it)}?:0
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE)}
        app.honorable.editor.NativeWorkspaceInsets.install(this,root)
        val actions=LinearLayout(this)
        actions.addView(Button(this).apply{text="Close drawing";setOnClickListener{finish()}},LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(Button(this).apply{text="New drawing";setOnClickListener{
            showResetReview()
        }},LinearLayout.LayoutParams(0,-2,1f))
        root.addView(actions)
        val message=TextView(this).apply{text=interruptedFileKind?.let{interruptedFileMessage(it)}?:idleMessage;accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE;setTextColor(Color.DKGRAY);setPadding(16,8,16,8)}
        if(state?.getBoolean("noble-drawing-print-pending")==true)message.text="Printing was interrupted by screen recreation. Check Android print status before retrying; your saved drawing draft stays here."
        if(state?.getBoolean("noble-drawing-svg-pending")==true)message.text="SVG sharing was interrupted by screen recreation. Check your drawing and share again when ready."
        if(state?.getBoolean("noble-drawing-photo-pending")==true)message.text="Photo selection or preparation was interrupted. Your saved drawing stays here; choose the photo again when ready."
        if(state?.getBoolean("noble-drawing-vector-pending")==true)message.text="SVG artwork selection was interrupted. Choose the file again; your drawing is kept."
        nativeMessage=message
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
    private fun canResetDrawing()=printForeground&&!printClosed&&!isDestroyed&&!isFinishing&&!exporting.get()&&!importing.get()&&resetDispatch==null
    private fun beginResetReview():ResetReview? {
        if(!canResetDrawing()||resetReview!=null)return null
        return ResetReview(resetGeneration).also{resetReview=it}
    }
    private fun approveResetReview(review:ResetReview):Boolean {
        if(resetReview!==review||review.generation!=resetGeneration||!canResetDrawing())return false
        resetReview=null;resetDialog=null
        return requestNewDrawing()
    }
    private fun showResetReview(){
        val review=beginResetReview()?:run{nativeMessage.text="Finish the current operation before starting a new drawing.";return}
        runCatching{
            resetDialog=android.app.AlertDialog.Builder(this).setTitle("Start a new drawing?").setMessage("This clears the current drawing and its local draft. Save a named composition in Projects or export an image first to keep your work. Named compositions and components stay on this device.")
                .setNegativeButton("Cancel",null).setPositiveButton("New drawing"){_,_->
                    if(resetReview===review&&review.generation==resetGeneration&&!approveResetReview(review)&&printForeground&&!printClosed)nativeMessage.text="The drawing was kept. Open New drawing again when ready."
                }.setOnDismissListener{if(resetReview===review){resetReview=null;resetDialog=null}}.create().also{it.show()}
        }.onFailure{if(resetReview===review){resetReview=null;resetDialog=null};nativeMessage.text="The drawing was kept. New drawing could not open; try again."}
    }
    /** Invalidate first so a dismissed/replayed approval cannot act after resume. */
    private fun invalidateResetReview(){
        resetGeneration++
        resetReview=null
        val dialog=resetDialog;resetDialog=null;runCatching{dialog?.dismiss()}
    }
    /** An already approved reset may have cleared storage: finish it once, only in foreground. */
    private fun finishResetDispatch(){
        val dispatch=synchronized(operationLock){
            val current=resetDispatch
            if(current==null||!current.replied||!printForeground||printClosed||isDestroyed||isFinishing)return
            resetDispatch=null;current
        }
        if(dispatch.reset){
            clearFileNotice()
            runCatching{web.reload()}.onFailure{if(::nativeMessage.isInitialized)nativeMessage.text="The draft was cleared. Close and reopen the drawing to continue."}
        }else if(::nativeMessage.isInitialized)nativeMessage.text="The drawing was kept. Wait for the canvas or current operation, then try again."
    }
    /** Confirm again after the dialog and own exactly one asynchronous WebView reply. */
    private fun requestNewDrawing():Boolean {
        val dispatch=ResetDispatch()
        synchronized(operationLock){if(!canResetDrawing())return false;resetDispatch=dispatch}
        return try {
            web.evaluateJavascript("typeof window.honorableNewDrawing === 'function' && window.honorableNewDrawing()"){reset->
                val owned=synchronized(operationLock){if(resetDispatch!==dispatch||dispatch.replied||printClosed||isDestroyed||isFinishing)false else{dispatch.replied=true;dispatch.reset=reset=="true";true}}
                if(owned)finishResetDispatch()
            }
            true
        }catch(_:Exception){synchronized(operationLock){if(resetDispatch===dispatch)resetDispatch=null};false}
    }
    private fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(ByteArray(0)))
    private fun beginImageOperation():ImageOperation?=synchronized(operationLock){
        if(!printForeground||printClosed||isDestroyed||isFinishing||imageOperation!=null||!claimExport())return@synchronized null
        ImageOperation().also{imageOperation=it}
    }
    private fun currentImageOperation(value:ImageOperation)=synchronized(operationLock){imageOperation===value&&printForeground&&!printClosed&&!isDestroyed&&!isFinishing}
    private fun releaseImageOperation(value:ImageOperation):Boolean=synchronized(operationLock){
        if(imageOperation!==value)false else{imageOperation=null;exporting.set(false);true}
    }
    private fun abandonImageOperation():Boolean=synchronized(operationLock){
        val value=imageOperation?:return@synchronized false
        if(value.launching)false else releaseImageOperation(value)
    }
    private fun launchImageOperation(value:ImageOperation):Boolean=synchronized(operationLock){
        if(!currentImageOperation(value)||value.launching)false else{value.launching=true;true}
    }
    private fun currentPrintUpload(value:DrawingPrintUpload)=synchronized(operationLock){printUpload===value&&printForeground&&!printClosed&&!isDestroyed&&!isFinishing}
    private fun releasePrintUpload(value:DrawingPrintUpload,error:String?=null){
        val owned=synchronized(operationLock){if(printUpload!==value)false else{printUpload=null;if(printPreparing===value)printPreparing=null;exporting.set(false);true}}
        if(owned&&error!=null)runOnUiThread{printReply(value.id,error)}
    }
    private fun preparePrint(value:DrawingPrintUpload){
        if(!currentPrintUpload(value)){releasePrintUpload(value,"Printing stopped while the drawing was not active. Try again.");return}
        try{worker.execute{
            val prepared=runCatching{
                check(currentPrintUpload(value)){"Printing was cancelled"}
                val payload=DrawingPolicy.base64Payload(value.finish())
                val bytes=Base64.decode(payload,Base64.NO_WRAP)
                require(Base64.encodeToString(bytes,Base64.NO_WRAP)==payload){"Artwork encoding is invalid"}
                val size=DrawingPolicy.validatePng(bytes)
                check(currentPrintUpload(value)){"Printing was cancelled"}
                val bitmap=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inPreferredConfig=Bitmap.Config.ARGB_8888;inMutable=false})){"Artwork image could not be read"}
                try{require(bitmap.width==size.first&&bitmap.height==size.second);check(currentPrintUpload(value)){"Printing was cancelled"};bitmap}
                catch(failure:Throwable){bitmap.recycle();throw failure}
            }
            runOnUiThread{prepared.onSuccess{bitmap->
                if(!currentPrintUpload(value)){bitmap.recycle();releasePrintUpload(value,"Printing was cancelled. The drawing is unchanged.")}
                else showPrintSheet(value,bitmap)
            }.onFailure{releasePrintUpload(value,if(it is OutOfMemoryError)"Not enough memory. Try a smaller drawing."else "Artwork could not be prepared for printing. Try again; your drawing is unchanged.")}}
        }}catch(_:java.util.concurrent.RejectedExecutionException){releasePrintUpload(value,"Printing stopped. Open the drawing and try again.")}
    }
    private fun showPrintSheet(value:DrawingPrintUpload,bitmap:Bitmap){
        if(!currentPrintUpload(value)){bitmap.recycle();releasePrintUpload(value,"Printing was cancelled.");return}
        lateinit var operation:PrintOperation
        val adapter=DrawingPrintAdapter(this,bitmap){error->
            val owned=synchronized(operationLock){if(activePrint!==operation)false else{activePrint=null;exporting.set(false);true}}
            if(owned)printReply(operation.id,error)
        }
        operation=PrintOperation(value.id,adapter)
        val owned=synchronized(operationLock){if(!currentPrintUpload(value))false else{printUpload=null;printPreparing=null;activePrint=operation;true}}
        if(!owned){adapter.close("Printing was cancelled.");return}
        nativeMessage.text="Choose Save as PDF or a printer in Android's print options. The drawing stays editable; delivery is not confirmed here."
        try{
            val manager=getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager?:error("Print service unavailable")
            operation.job=requireNotNull(manager.print("Noble artwork",adapter,android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).setColorMode(android.print.PrintAttributes.COLOR_MODE_COLOR).build()))
        }catch(_:Exception){adapter.close("Android print options could not open. Your drawing is unchanged. Try again.")}
    }
    private fun printReply(requestId:Int,error:String?=null){
        if(printClosed||isDestroyed||isFinishing||!::web.isInitialized)return
        val result=JSONObject().put("requestId",requestId).put("closed",error==null)
        if(error!=null)result.put("error",error)
        nativeMessage.text=error?:"Print dialog closed. Check Android print status or the saved PDF; delivery is not confirmed."
        web.evaluateJavascript("window.honorablePrintResult && window.honorablePrintResult($result)",null)
    }
    private fun currentSvgOperation(value:SvgOperation)=synchronized(operationLock){svgOperation===value&&printForeground&&!printClosed&&!isDestroyed&&!isFinishing}
    private fun releaseSvgOperation(value:SvgOperation,error:String?=null){
        val owned=synchronized(operationLock){if(svgOperation!==value)false else{svgOperation=null;exporting.set(false);true}}
        if(owned&&error!=null)runOnUiThread{svgShareReply(value.upload.id,error=error)}
    }
    private fun prepareSvgShare(value:SvgOperation){
        if(!currentSvgOperation(value)){releaseSvgOperation(value,"SVG sharing stopped while the drawing was not active. Try again.");return}
        try{worker.execute{
            val prepared=runCatching{DrawingSvgShare.prepare(File(cacheDir,"created-images"),value.upload.finish()){
                check(currentSvgOperation(value)&&!Thread.currentThread().isInterrupted){"SVG sharing was cancelled"}
            }}
            runOnUiThread{prepared.onSuccess{file->
                if(!currentSvgOperation(value)){file.delete();releaseSvgOperation(value,"SVG sharing was cancelled. Your drawing stays here.");return@onSuccess}
                val shared=runCatching{
                    val uri=FileProvider.getUriForFile(this@HonorableDrawingActivity,packageName+".creation",file)
                    val send=Intent(Intent.ACTION_SEND).apply{type="image/svg+xml";putExtra(Intent.EXTRA_STREAM,uri);clipData=android.content.ClipData.newUri(contentResolver,"Noble SVG artwork",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                    startActivity(Intent.createChooser(send,"Share SVG artwork"))
                }
                releaseSvgOperation(value)
                shared.onSuccess{svgShareReply(value.upload.id,opened=true)}.onFailure{file.delete();svgShareReply(value.upload.id,error="The SVG share sheet could not open. Your drawing stays here; try again.")}
            }.onFailure{releaseSvgOperation(value,if(it is OutOfMemoryError)"Not enough memory. Prepare a smaller SVG."else "SVG file preparation failed. Your drawing stays here; prepare the SVG and try again.")}}
        }}catch(_:java.util.concurrent.RejectedExecutionException){releaseSvgOperation(value,"SVG sharing stopped. Open the drawing and try again.")}
    }
    private fun svgShareReply(requestId:Int,opened:Boolean=false,error:String?=null){
        if(printClosed||isDestroyed||isFinishing||!::web.isInitialized)return
        val result=JSONObject().put("requestId",requestId).put("opened",opened)
        if(error!=null)result.put("error",error)
        nativeMessage.text=error?:"SVG share sheet opened. Saving or sending depends on the app you choose."
        web.evaluateJavascript("window.honorableSvgShareResult && window.honorableSvgShareResult($result)",null)
    }
    inner class Bridge {
        @JavascriptInterface fun beginSvgShare(requestId:Int,length:Int):Boolean=synchronized(operationLock){
            if(!printForeground||printClosed||isDestroyed||isFinishing||svgOperation!=null||!claimExport())return@synchronized false
            runCatching{SvgOperation(DrawingSvgUpload(requestId,length))}.fold({svgOperation=it;true},{exporting.set(false);false})
        }
        @JavascriptInterface fun appendSvgShareChunk(requestId:Int,value:String):Boolean=synchronized(operationLock){
            val operation=svgOperation?.takeIf{it.upload.id==requestId}?:return@synchronized false
            if(!currentSvgOperation(operation)||operation.preparing)return@synchronized false
            runCatching{operation.upload.append(value);true}.getOrElse{svgOperation=null;exporting.set(false);false}
        }
        @JavascriptInterface fun finishSvgShare(requestId:Int){runOnUiThread{
            val operation=synchronized(operationLock){svgOperation?.takeIf{it.upload.id==requestId&&!it.preparing}?.also{it.preparing=true}}?:return@runOnUiThread
            prepareSvgShare(operation)
        }}
        @JavascriptInterface fun releaseSvgShare(requestId:Int){
            val operation=synchronized(operationLock){svgOperation?.takeIf{it.upload.id==requestId}}?:return
            releaseSvgOperation(operation)
        }

        @JavascriptInterface fun beginPrintImage(requestId:Int,length:Int):Boolean=synchronized(operationLock){
            if(!printForeground||printClosed||isDestroyed||isFinishing||printUpload!=null||activePrint!=null||!claimExport())return@synchronized false
            runCatching{DrawingPrintUpload(requestId,length)}.fold({printUpload=it;true},{exporting.set(false);false})
        }
        @JavascriptInterface fun appendPrintImageChunk(requestId:Int,value:String):Boolean=synchronized(operationLock){
            val upload=printUpload?.takeIf{it.id==requestId}?:return@synchronized false
            if(!currentPrintUpload(upload)||printPreparing===upload)return@synchronized false
            runCatching{upload.append(value);true}.getOrElse{printUpload=null;exporting.set(false);false}
        }
        @JavascriptInterface fun finishPrintImage(requestId:Int){runOnUiThread{
            val upload=synchronized(operationLock){
                printUpload?.takeIf{it.id==requestId&&printPreparing!==it}?.also{printPreparing=it}
            }?:return@runOnUiThread
            preparePrint(upload)
        }}
        @JavascriptInterface fun releasePrintImage(requestId:Int){
            val upload=synchronized(operationLock){printUpload?.takeIf{it.id==requestId}}?:return
            releasePrintUpload(upload)
        }

        @JavascriptInterface fun choosePhoto(requestId:Int) {runOnUiThread {
            if(!DrawingPhotoPolicy.acceptsRequest(requestId)||printClosed||isDestroyed||isFinishing)return@runOnUiThread
            val operation=beginPhotoOperation(requestId,false)?:run{photoReply(requestId,error="Open the drawing and finish the current operation before choosing a photo.");return@runOnUiThread}
            openPhotoPicker(operation)
        }}
        @JavascriptInterface fun choosePhotoOwned(requestId:Int):Boolean {
            val operation=beginPhotoOperation(requestId,true)?:return false
            return try{runOnUiThread{openPhotoPicker(operation)};true}catch(_:Exception){releasePhotoOperation(operation);false}
        }
        @JavascriptInterface fun isPhotoImportCurrent(requestId:Int):Boolean=synchronized(operationLock){
            photoOperation?.let{it.id==requestId&&it.leased&&it.ready&&currentPhotoOperation(it)}?:false
        }
        @JavascriptInterface fun releasePhotoImport(requestId:Int):Boolean=synchronized(operationLock){
            val operation=photoOperation?.takeIf{it.id==requestId&&it.leased}?:return@synchronized false
            releasePhotoOperation(operation)
        }
        @JavascriptInterface fun beginProjectFileSave(requestId:Int,name:String,length:Int):Boolean=synchronized(operationLock){
            if(!printForeground||printClosed||isDestroyed||isFinishing||!DrawingProjectFiles.acceptsRequest(requestId)||!claimImport())return@synchronized false
            runCatching{DrawingProjectUpload(requestId,name,length)}.fold({upload->projectUpload=upload;runOnUiThread{if(currentProjectUpload(upload))clearFileNotice()};true},{importing.set(false);false})
        }
        @JavascriptInterface fun appendProjectFileChunk(requestId:Int,value:String):Boolean=synchronized(operationLock){
            val upload=projectUpload?.takeIf{it.id==requestId}?:return@synchronized false
            if(!currentProjectUpload(upload))return@synchronized false
            runCatching{upload.append(value);true}.getOrElse{releaseProjectUpload(upload);false}
        }
        @JavascriptInterface fun finishProjectFileSave(requestId:Int){runOnUiThread{
            val upload=projectUpload?.takeIf{it.id==requestId}?:return@runOnUiThread
            if(!currentProjectUpload(upload)){if(releaseProjectUpload(upload))projectReply(requestId,error="Project transfer stopped. Your drawing stays here; save again when active.");return@runOnUiThread}
            val value=synchronized(operationLock){
                if(!currentProjectUpload(upload))return@runOnUiThread
                runCatching{upload.finish()}.also{projectUpload=null}
            }
            value.onSuccess{openProjectPicker(ProjectOperation(requestId,true,it),DrawingProjectFiles.filename(upload.name))}
                .onFailure{importing.set(false);projectReply(requestId,error="Project transfer is incomplete or too large. Save again.")}
        }}
        @JavascriptInterface fun chooseVectorFile(requestId:Int):Boolean=synchronized(operationLock){
            if(!DrawingVectorFiles.acceptsRequest(requestId)||!printForeground||printClosed||isDestroyed||isFinishing||vectorOperation!=null||lastVectorCode>=DrawingVectorFiles.LAST_CODE||!claimImport())return@synchronized false
            val operation=VectorOperation(requestId,DrawingVectorFiles.nextCode(lastVectorCode));lastVectorCode=operation.code;vectorOperation=operation
            try{runOnUiThread{openVectorPicker(operation)};true}catch(_:Exception){releaseVectorOperation(operation);false}
        }
        @JavascriptInterface fun readVectorFileChunk(requestId:Int,offset:Int):String?=synchronized(operationLock){
            val operation=vectorOperation?.takeIf{it.id==requestId&&currentVectorOperation(it)}?:return@synchronized null
            val text=operation.text?:return@synchronized null
            runCatching{DrawingVectorFiles.chunk(text,offset)}.getOrNull()
        }
        @JavascriptInterface fun releaseVectorFile(requestId:Int):Boolean=synchronized(operationLock){
            val operation=vectorOperation?.takeIf{it.id==requestId}?:return@synchronized false
            releaseVectorOperation(operation)
        }
        @JavascriptInterface fun chooseProjectFile(requestId:Int){runOnUiThread{
            if(!DrawingProjectFiles.acceptsRequest(requestId)||printClosed||isDestroyed||isFinishing)return@runOnUiThread
            if(!printForeground){projectReply(requestId,error="Open the drawing before choosing a project file.");return@runOnUiThread}
            if(!claimImport()){projectReply(requestId,error="Another image or file operation is running.");return@runOnUiThread}
            openProjectPicker(ProjectOperation(requestId,false))
        }}
        @JavascriptInterface fun readProjectFileChunk(requestId:Int,offset:Int):String? {
            val read=projectRead?.takeIf{it.id==requestId}?:return null
            return runCatching{DrawingProjectFiles.chunk(read.text,offset)}.getOrNull()
        }
        @JavascriptInterface fun releaseProjectFile(requestId:Int){runOnUiThread{
            if(projectUpload?.id==requestId||projectRead?.id==requestId){projectUpload=null;projectRead=null;importing.set(false)}
        }}
        @JavascriptInterface fun usePng(value:String):Boolean {
            val operation=beginImageOperation()?:return false
            return try { worker.execute {
                var file:File?=null;var bitmap:Bitmap?=null
                fun active(){check(currentImageOperation(operation)&&!Thread.currentThread().isInterrupted){"Image preparation cancelled"}}
                val result=runCatching {
                    try {
                        active()
                        val payload=DrawingPolicy.base64Payload(value)
                        val bytes=Base64.decode(payload,Base64.NO_WRAP)
                        require(Base64.encodeToString(bytes,Base64.NO_WRAP)==payload){"Invalid image encoding"}
                        val size=DrawingPolicy.validatePng(bytes)
                        active()
                        val decoded=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size)){"Drawing PNG could not be read"};bitmap=decoded
                        require(decoded.width==size.first&&decoded.height==size.second)
                        active()
                        val dir=File(cacheDir,"created-images");check(dir.isDirectory||dir.mkdirs())
                        val target=File.createTempFile("honorable-drawing-",".png",dir);file=target
                        FileOutputStream(target).use{output->active();check(decoded.compress(Bitmap.CompressFormat.PNG,100,output));active();output.fd.sync()}
                        require(target.length() in 33..DrawingPolicy.MAX_BYTES.toLong()){"Prepared image exceeds its file budget"}
                        active()
                        FileProvider.getUriForFile(this@HonorableDrawingActivity,packageName+".creation",target)
                    }finally{bitmap?.recycle()}
                }
                runOnUiThread {
                    if(!currentImageOperation(operation)){file?.delete();return@runOnUiThread}
                    result.onSuccess { uri ->
                        if(!launchImageOperation(operation)){file?.delete();return@onSuccess}
                        runCatching{startActivity(Intent(this@HonorableDrawingActivity,PhotoEditorActivity::class.java).apply{putExtra("uri",uri.toString());putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}
                            .onSuccess{releaseImageOperation(operation);finish()}
                            .onFailure{file?.delete();if(releaseImageOperation(operation))showError("Could not open the photo editor. Your drawing draft stays on this device.")}
                    }.onFailure{failure->file?.delete();if(releaseImageOperation(operation))showError(if(failure is OutOfMemoryError)"Not enough memory. Try a smaller drawing."else "Image creation failed. Your draft stays local; try again.")}
                }
            };true } catch(_:java.util.concurrent.RejectedExecutionException){releaseImageOperation(operation);false}
        }

    }
    private fun currentVectorOperation(operation:VectorOperation)=vectorOperation===operation&&printForeground&&!printClosed&&!isDestroyed&&!isFinishing
    private fun releaseVectorOperation(operation:VectorOperation):Boolean=synchronized(operationLock){
        if(vectorOperation!==operation)false else{vectorOperation=null;operation.text=null;operation.uri=null;importing.set(false);true}
    }
    private fun vectorReply(id:Int,length:Int?=null,cancelled:Boolean=false,error:String?=null){
        if(printClosed||isDestroyed||isFinishing||!::web.isInitialized)return
        val reply=JSONObject().put("requestId",id).put("cancelled",cancelled)
        if(length!=null)reply.put("length",length)
        if(error!=null)reply.put("error",error)
        web.evaluateJavascript("window.honorableVectorFileResult && window.honorableVectorFileResult($reply)",null)
    }
    private fun vectorPickerIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
        addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_LOCAL_ONLY,true)
        putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/svg+xml","application/xml","text/xml"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    private fun openVectorPicker(operation:VectorOperation){
        if(!currentVectorOperation(operation)){if(releaseVectorOperation(operation))vectorReply(operation.id,error="Open the drawing before choosing SVG artwork.");return}
        runCatching{startActivityForResult(vectorPickerIntent(),operation.code)}.onFailure{if(releaseVectorOperation(operation))vectorReply(operation.id,error="The SVG artwork picker could not open.")}
    }
    private fun vectorResult(code:Int,result:Int,data:Intent?){
        if(printClosed||isDestroyed||isFinishing)return
        val operation=vectorOperation?.takeIf{it.code==code&&!it.received}?:return
        operation.received=true;val uri=data?.data
        if(result!=RESULT_OK||uri==null){if(releaseVectorOperation(operation))vectorReply(operation.id,cancelled=true);return}
        if(uri.scheme!="content"){if(releaseVectorOperation(operation))vectorReply(operation.id,error="Choose an SVG artwork file in Files.");return}
        operation.uri=uri;startVectorRead(operation)
    }
    /** Files can deliver metadata before onResume. No provider reading occurs before foreground. */
    private fun startVectorRead(operation:VectorOperation){
        val uri=operation.uri?:return
        if(!currentVectorOperation(operation)||!operation.received||operation.started)return
        operation.started=true;val started=System.nanoTime()
        try{worker.execute{
            fun stopped()=!currentVectorOperation(operation)||System.nanoTime()-started>=10_000_000_000L
            val result=runCatching{check(!stopped());requireNotNull(contentResolver.openInputStream(uri)).use{DrawingVectorFiles.read(it,::stopped)}}
            runOnUiThread{
                if(!currentVectorOperation(operation))return@runOnUiThread
                result.onSuccess{text->
                    synchronized(operationLock){if(!currentVectorOperation(operation))return@onSuccess;operation.text=text;operation.uri=null}
                    vectorReply(operation.id,length=text.length)
                }.onFailure{if(releaseVectorOperation(operation))vectorReply(operation.id,error="This SVG file could not be read as bounded UTF-8 text.")}
            }
        }}catch(_:java.util.concurrent.RejectedExecutionException){if(releaseVectorOperation(operation))vectorReply(operation.id,error="SVG reading stopped. Choose the file again.")}
    }
    private fun cancelVectorRead(){
        val operation=vectorOperation?.takeIf{it.started}?:return
        if(releaseVectorOperation(operation))vectorReply(operation.id,error="SVG reading stopped while the drawing was inactive. Choose the file again.")
    }
    private fun currentProjectUpload(value:DrawingProjectUpload)=synchronized(operationLock){projectUpload===value&&printForeground&&!printClosed&&!isDestroyed&&!isFinishing}
    private fun releaseProjectUpload(value:DrawingProjectUpload):Boolean=synchronized(operationLock){if(projectUpload!==value)false else{projectUpload=null;importing.set(false);true}}
    private fun currentProjectOperation(value:ProjectOperation)=projectOperation===value&&printForeground&&!printClosed&&!isDestroyed&&!isFinishing
    private fun releaseProjectOperation(value:ProjectOperation,read:ProjectRead?=null):Boolean=synchronized(operationLock){
        if(projectOperation!==value)false else{projectOperation=null;projectRead=read;if(read==null)importing.set(false);true}
    }
    private fun cancelProjectTransfer(){
        projectUpload?.let{upload->if(releaseProjectUpload(upload)){interruptedFileKind="save";projectReply(upload.id,error="Project transfer stopped. Your drawing stays here; save again when active.")}}
        val operation=projectOperation?.takeIf{it.started}?:return
        if(releaseProjectOperation(operation)){
            interruptedFileKind=if(operation.saving)"save"else"open"
            if(::nativeMessage.isInitialized)nativeMessage.text=interruptedFileMessage(requireNotNull(interruptedFileKind))
            projectReply(operation.id,error=if(operation.saving)"Project save was interrupted. Check the selected destination for an incomplete file; your drawing stays here."else "Project reading stopped. Your drawing stays here; choose the file again when active.")
        }
    }
    private fun openProjectPicker(operation:ProjectOperation,filename:String?=null){
        if(!printForeground||printClosed||isDestroyed||isFinishing){importing.set(false);projectReply(operation.id,error="Open the drawing before choosing a project file.");return}
        clearFileNotice()
        projectOperation=operation
        runCatching{startActivityForResult(Intent(if(operation.saving)Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_LOCAL_ONLY,true)
            if(operation.saving){type="application/json";putExtra(Intent.EXTRA_TITLE,filename)}
            else {type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/json","text/plain","application/octet-stream"))}
            addFlags(if(operation.saving)Intent.FLAG_GRANT_WRITE_URI_PERMISSION else Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },if(operation.saving)DrawingProjectFiles.SAVE_REQUEST else DrawingProjectFiles.OPEN_REQUEST)}.onFailure{
            if(releaseProjectOperation(operation))projectReply(operation.id,error="The project file picker could not open. Try again.")
        }
    }
    private fun projectResult(requestCode:Int,resultCode:Int,data:Intent?){
        if(printClosed||isDestroyed||isFinishing)return
        val operation=projectOperation?:run{
            interruptedFileKind=if(requestCode==DrawingProjectFiles.SAVE_REQUEST&&resultCode==RESULT_OK)"save" else "open"
            if(::nativeMessage.isInitialized)nativeMessage.text=interruptedFileMessage(requireNotNull(interruptedFileKind))
            return
        }
        if(requestCode!=(if(operation.saving)DrawingProjectFiles.SAVE_REQUEST else DrawingProjectFiles.OPEN_REQUEST)||operation.received)return
        operation.received=true
        val uri=data?.data
        if(resultCode!=RESULT_OK||uri==null){if(releaseProjectOperation(operation))projectReply(operation.id,cancelled=true);return}
        if(uri.scheme!="content"){if(releaseProjectOperation(operation))projectReply(operation.id,error="Choose a project file in Files.");return}
        operation.uri=uri
        startProjectOperation(operation)
    }
    /** Picker metadata may arrive before onResume; provider I/O starts only once in foreground. */
    private fun startProjectOperation(operation:ProjectOperation){
        val uri=operation.uri?:return
        if(!currentProjectOperation(operation)||!operation.received||operation.started)return
        operation.started=true
        try{worker.execute{
            fun active(){check(currentProjectOperation(operation)&&!Thread.currentThread().isInterrupted){"Project transfer stopped"}}
            val result=runCatching{
                active()
                if(operation.saving){
                    // Validate before opening a potentially truncating provider stream.
                    val bytes=DrawingProjectFiles.bytes(requireNotNull(operation.value));active()
                    requireNotNull(contentResolver.openOutputStream(uri,"wt")).use{DrawingProjectFiles.write(it,bytes){!currentProjectOperation(operation)}};null
                }else {active();requireNotNull(contentResolver.openInputStream(uri)).use{DrawingProjectFiles.read(it){!currentProjectOperation(operation)}}}
            }
            runOnUiThread{
                if(!currentProjectOperation(operation))return@runOnUiThread
                result.onSuccess{value->
                    val read=value?.let{ProjectRead(operation.id,it)}
                    if(releaseProjectOperation(operation,read)){if(read!=null)projectReply(operation.id,length=read.text.length)else projectReply(operation.id,saved=true)}
                }.onFailure{
                    if(releaseProjectOperation(operation))projectReply(operation.id,error=if(operation.saving)"Project file could not be saved completely. Check the selected destination. Your drawing stays here."else"This file could not be read as bounded UTF-8 text. Choose a Noble project file.")
                }
            }
        }}catch(_:java.util.concurrent.RejectedExecutionException){if(releaseProjectOperation(operation))projectReply(operation.id,error="Project file operation stopped. Try again.")}
    }
    private fun projectReply(requestId:Int,length:Int?=null,saved:Boolean=false,cancelled:Boolean=false,error:String?=null){
        if(isDestroyed||isFinishing||!::web.isInitialized)return
        val result=JSONObject().put("requestId",requestId).put("saved",saved).put("cancelled",cancelled)
        if(length!=null)result.put("length",length)
        if(error!=null)result.put("error",error)
        web.evaluateJavascript("window.honorableProjectFileResult && window.honorableProjectFileResult($result)",null)
    }
    private fun beginPhotoOperation(requestId:Int,leased:Boolean):PhotoOperation?=synchronized(operationLock){
        if(!DrawingPhotoPolicy.acceptsRequest(requestId)||!printForeground||printClosed||isDestroyed||isFinishing||photoOperation!=null||!claimImport())return@synchronized null
        PhotoOperation(requestId,leased).also{photoOperation=it}
    }
    private fun currentPhotoOperation(value:PhotoOperation)=photoOperation===value&&printForeground&&!printClosed&&!isDestroyed&&!isFinishing
    private fun releasePhotoOperation(value:PhotoOperation):Boolean=synchronized(operationLock){if(photoOperation!==value)false else{photoOperation=null;importing.set(false);true}}
    private fun cancelPhotoTransfer(){
        val operation=photoOperation?.takeIf{it.started}?:return
        if(releasePhotoOperation(operation))photoReply(operation.id,error="Photo preparation stopped. Your drawing stays here; choose the photo again when active.")
    }
    private fun openPhotoPicker(operation:PhotoOperation){
        if(!currentPhotoOperation(operation)){if(releasePhotoOperation(operation))photoReply(operation.id,error="Open the drawing before choosing a photo.");return}
        runCatching{startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},DrawingPhotoPolicy.REQUEST_CODE)}
            .onFailure{if(releasePhotoOperation(operation))photoReply(operation.id,error="The photo picker could not open. Try again.")}
    }
    private fun photoResult(resultCode:Int,data:Intent?){
        if(printClosed||isDestroyed||isFinishing)return
        val operation=photoOperation?.takeIf{!it.received}?:return
        operation.received=true
        val uri=data?.data?:data?.clipData?.takeIf{it.itemCount==1}?.getItemAt(0)?.uri
        if(resultCode!=RESULT_OK||uri==null){if(releasePhotoOperation(operation))photoReply(operation.id,cancelled=true);return}
        if(!DrawingPhotoPolicy.acceptsSource(uri.scheme)){if(releasePhotoOperation(operation))photoReply(operation.id,error="Choose a photo from Photos or Files.");return}
        operation.uri=uri;startPhotoOperation(operation)
    }
    private fun deliverPhotoCopy(operation:PhotoOperation,dataURL:String){
        if(!currentPhotoOperation(operation))return
        if(operation.leased){
            val owned=synchronized(operationLock){if(!currentPhotoOperation(operation)||operation.ready)false else{operation.ready=true;true}}
            if(owned)photoReply(operation.id,dataURL=dataURL)
        }else if(releasePhotoOperation(operation))photoReply(operation.id,dataURL=dataURL)
    }
    private fun startPhotoOperation(operation:PhotoOperation){
        val uri=operation.uri?:return
        if(!currentPhotoOperation(operation)||!operation.received||operation.started)return
        operation.started=true
        try{worker.execute{
            val prepared=runCatching{check(currentPhotoOperation(operation)&&!Thread.currentThread().isInterrupted);DrawingPhotoCopy.prepare(this,uri){!currentPhotoOperation(operation)}}
            runOnUiThread{
                if(!currentPhotoOperation(operation))return@runOnUiThread
                prepared.onSuccess{dataURL->deliverPhotoCopy(operation,dataURL)
                }.onFailure{if(releasePhotoOperation(operation))photoReply(operation.id,error=if(it is OutOfMemoryError)"Not enough memory. Close other tools and try again."else"This photo could not be prepared. Try another photo.")}
            }
        }}catch(_:java.util.concurrent.RejectedExecutionException){if(releasePhotoOperation(operation))photoReply(operation.id,error="Photo preparation stopped. Try again.")}
    }
    @Deprecated("Uses the Activity result API for this local picker")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(DrawingVectorFiles.acceptsCode(requestCode)){vectorResult(requestCode,resultCode,data);return}
        if(requestCode==DrawingProjectFiles.OPEN_REQUEST||requestCode==DrawingProjectFiles.SAVE_REQUEST){projectResult(requestCode,resultCode,data);return}
        if(requestCode==DrawingPhotoPolicy.REQUEST_CODE)photoResult(resultCode,data)
    }
    private fun photoReply(requestId:Int,dataURL:String?=null,error:String?=null,cancelled:Boolean=false){
        if(isDestroyed||isFinishing||!::web.isInitialized)return
        val result=JSONObject().put("requestId",requestId).put("cancelled",cancelled)
        if(dataURL!=null)result.put("dataURL",dataURL)
        if(error!=null)result.put("error",error)
        web.evaluateJavascript("window.honorablePhotoImportResult && window.honorablePhotoImportResult($result)",null)
    }
    private fun showError(message:String){if(printClosed||isDestroyed||isFinishing||!::web.isInitialized)return;web.evaluateJavascript("window.honorableExportResult && window.honorableExportResult(${JSONObject.quote(message)})",null)}
    override fun onSaveInstanceState(outState:Bundle){
        // Add only an operation label here; no project text, photo bytes or URIs are added by this override.
        val kind=projectOperation?.let{if(it.saving)"save"else"open"}?:if(projectUpload!=null)"save"else if(projectRead!=null)"open"else interruptedFileKind
        if(kind!=null)outState.putString(fileStateKey,kind)
        if(printUpload!=null||activePrint!=null)outState.putBoolean("noble-drawing-print-pending",true)
        if(photoOperation!=null)outState.putBoolean("noble-drawing-photo-pending",true)
        if(svgOperation!=null)outState.putBoolean("noble-drawing-svg-pending",true)
        outState.putInt("noble-drawing-vector-code",synchronized(operationLock){lastVectorCode})
        if(vectorOperation!=null)outState.putBoolean("noble-drawing-vector-pending",true)
        super.onSaveInstanceState(outState)
    }
    override fun onResume(){super.onResume();if(!printClosed){printForeground=true;finishResetDispatch();vectorOperation?.let{startVectorRead(it)};projectOperation?.let{startProjectOperation(it)};photoOperation?.let{startPhotoOperation(it)}}}
    override fun onPause(){printForeground=false;invalidateResetReview();cancelVectorRead();cancelProjectTransfer();cancelPhotoTransfer();if(abandonImageOperation())showError("Image preparation stopped before opening the editor. Your drawing draft stays here; try again when active.");svgOperation?.let{releaseSvgOperation(it,"SVG sharing stopped before its sheet opened. Try again when the drawing is active.")};printUpload?.let{releasePrintUpload(it,"Printing stopped before the print sheet opened. Try again when the drawing is active.")};if(::web.isInitialized)web.evaluateJavascript("window.honorableSaveDraft && window.honorableSaveDraft()",null);super.onPause()}
    override fun onDestroy(){printClosed=true;printForeground=false;invalidateResetReview();synchronized(operationLock){resetDispatch=null};imageOperation?.let{releaseImageOperation(it)};svgOperation?.let{releaseSvgOperation(it)};printUpload?.let{releasePrintUpload(it)};activePrint?.let{runCatching{it.job?.cancel()};it.adapter.close("Drawing closed. Check Android print status before retrying.")};vectorOperation?.let{releaseVectorOperation(it)};projectUpload=null;projectRead=null;projectOperation=null;photoOperation=null;importing.set(false);worker.shutdownNow();if(::web.isInitialized){web.removeJavascriptInterface("HonorableDrawing");web.stopLoading();web.destroy()};super.onDestroy()}
}
