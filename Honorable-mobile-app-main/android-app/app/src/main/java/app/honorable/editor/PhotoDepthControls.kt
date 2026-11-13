package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real local depth inference and explicit copy review before opening the editable 3D workspace. */
internal class PhotoDepthControls(
    private val activity:Activity,private val worker:Executor,
    private val prepareInput:(()->Unit)->Bitmap,
    private val isCurrent:()->Boolean,private val requireWorkerCurrent:()->Unit,
    private val accountOwned:Boolean,private val openScene:(File)->Unit,
    private val message:(String)->Unit,private val released:()->Unit,
    private val modelFile:((PhotoDepthModel.Stop)->File)?=null,
    private val infer:(File,IntArray,Int,Int,PhotoDepthModel.Stop)->PhotoDepthMesh.Grid=PhotoDepthModel::estimate,
):AutoCloseable {
    private data class Prepared(val file:File,val hash:ByteArray,val photo:Bitmap,val depth:Bitmap){
        fun dispose(keepFile:Boolean){if(!keepFile)file.delete();photo.recycle();depth.recycle()}
    }
    private val closed=AtomicBoolean(false)
    private val running=AtomicBoolean(false)
    private val stop=PhotoDepthModel.Stop()
    private var releasedOnce=false
    private var dialog:AlertDialog?=null
    private var prepared:Prepared?=null
    private var transferred=false
    private val images=mutableListOf<ImageView>()
    private fun live()=!closed.get()&&isCurrent()&&!activity.isFinishing&&!activity.isDestroyed
    private fun releaseOnce(){if(!releasedOnce){releasedOnce=true;released()}}
    override fun close(){
        if(closed.compareAndSet(false,true)){
            runCatching{stop.cancel()};val old=dialog;dialog=null;old?.dismiss()
            images.forEach{it.setImageDrawable(null)};images.clear()
            prepared?.dispose(transferred);prepared=null
        }
        if(!running.get())releaseOnce()
    }
    private fun show(next:AlertDialog){if(!live()){close();return};val old=dialog;dialog=null;old?.dismiss();dialog=next;next.setOnDismissListener{if(dialog===next)close()};next.show()}
    private fun body()=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(28,18,28,18);setBackgroundColor(Color.WHITE)}
    private fun LinearLayout.note(value:String){addView(TextView(activity).apply{text=value;setTextColor(Color.BLACK);setPadding(0,8,0,12)})}
    private fun scroll(view:LinearLayout)=ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(view)}
    fun open(){
        if(!live()){close();return};val panel=body()
        panel.note("Turn the current edited photo into a textured depth surface using the bundled Depth Anything V2 Small AI model. Then adjust its depth, material and camera motion in the 3D workspace. Everything runs on this device; no photo or query is uploaded.")
        panel.note("This estimates relative depth from one picture. It creates a 2.5D surface, not a complete object or unseen sides. Edges, reflections and transparent objects may distort. The complete edited image is fitted within512pixels for the texture; originals and current edits stay unchanged. Check the depth preview before making a new copy.")
        val setup=AlertDialog.Builder(activity).setTitle("AI depth photo").setView(scroll(panel)).setNegativeButton("Keep editing"){_,_->close()}.setPositiveButton("Build depth photo",null).create()
        setup.setOnShowListener{setup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{if(dialog===setup&&live())start()else close()}};show(setup)
    }
    private fun bundledModel(current:()->Unit):File=synchronized(assetLock){
        val folder=File(activity.filesDir,"noble-models/depth");val target=File(folder,"model_quantized.onnx")
        val valid=try{PhotoDepthModel.verify(target,stop);true}catch(_:IllegalArgumentException){false}
        if(valid)return@synchronized target
        check(folder.isDirectory||folder.mkdirs());val temporary=File.createTempFile("depth-model-",".part",folder)
        try{
            var total=0L
            activity.assets.open(PhotoDepthModel.ASSET).use{input->temporary.outputStream().buffered().use{output->
                val buffer=ByteArray(65536)
                while(true){current();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=PhotoDepthModel.BYTES);output.write(buffer,0,n)}
            }}
            PhotoDepthModel.verify(temporary,stop);current();check(temporary.renameTo(target));target
        }finally{temporary.delete()}
    }
    private fun start(){
        if(!live()||!running.compareAndSet(false,true))return
        val progress=AlertDialog.Builder(activity).setTitle("Creating local AI depth").setMessage("Preparing the edited photo and estimating its depth. Stop keeps the original and all edits; the worker retains its resources until it settles.").setNegativeButton("Stop"){_,_->close()}.create();show(progress)
        try{worker.execute{
            val began=System.nanoTime();val watchdog=Executors.newSingleThreadScheduledExecutor{task->Thread(task,"noble-depth-stop").apply{isDaemon=true}}
            fun current(){stop.check();check(!closed.get());check(System.nanoTime()-began<180_000_000_000L){"Depth inference took too long"};requireWorkerCurrent()}
            watchdog.scheduleAtFixedRate({if(closed.get()||System.nanoTime()-began>=180_000_000_000L||runCatching(requireWorkerCurrent).isFailure)runCatching{stop.cancel()}},250,250,TimeUnit.MILLISECONDS)
            val result=try{runCatching{
                current();val photo=prepareInput(::current);var depthPreview:Bitmap?=null;var file:File?=null;var delivered=false
                try{
                    require(!photo.isRecycled&&photo.width in 1..512&&photo.height in 1..512)
                    val pixels=IntArray(photo.width*photo.height);photo.getPixels(pixels,0,photo.width,0,0,photo.width,photo.height);current()
                    val model=modelFile?.invoke(stop)?:bundledModel(::current)
                    val grid=infer(model,pixels,photo.width,photo.height,stop);current()
                    val png=ByteArrayOutputStream().use{output->check(photo.compress(Bitmap.CompressFormat.PNG,100,output));output.toByteArray()}
                    val glb=PhotoDepthMesh.encode(grid,png);current()
                    val folder=File(activity.cacheDir,"noble-depth-handoff");check(folder.isDirectory||folder.mkdirs())
                    file=File.createTempFile("depth-",".glb",folder).also{it.outputStream().use{out->out.write(glb)}}
                    val colors=grid.depths().map{value->val gray=(value*255).toInt().coerceIn(0,255);Color.rgb(gray,gray,gray)}.toIntArray()
                    depthPreview=Bitmap.createBitmap(colors,grid.columns,grid.rows,Bitmap.Config.ARGB_8888)
                    current();Prepared(requireNotNull(file),MessageDigest.getInstance("SHA-256").digest(glb),photo,requireNotNull(depthPreview)).also{delivered=true}
                }finally{if(!delivered){file?.delete();depthPreview?.recycle();photo.recycle()}}
            }}finally{watchdog.shutdownNow();running.set(false)}
            activity.runOnUiThread{
                if(!live()){result.getOrNull()?.dispose(false);close();return@runOnUiThread}
                result.onSuccess{prepared=it;review(it)}.onFailure{message("Depth photo was not created. Check the current photo and try again; original media and edits were kept.");close()}
            }
        }}catch(_:Exception){running.set(false);message("Depth creation could not start. Original media and edits were kept.");close()}
    }
    private fun review(result:Prepared){
        if(!live()){close();return};val panel=body()
        panel.note("Review the edited working photo and its AI depth estimate (lighter is nearer). The next step opens a separate editable 2.5D scene for parallax animation, material changes and GLB export. It does not change this photo or generate hidden surfaces.")
        for((title,bitmap)in listOf("Working photo" to result.photo,"Estimated relative depth" to result.depth)){
            panel.note(title);val view=ImageView(activity).apply{contentDescription=title;scaleType=ImageView.ScaleType.FIT_CENTER;setImageBitmap(bitmap);setBackgroundColor(Color.WHITE)}
            panel.addView(view,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(180*activity.resources.displayMetrics.density).toInt()));images.add(view)
        }
        val confirmed=CheckBox(activity).apply{text="I reviewed the depth and want a separate local 3D copy";isChecked=false;setTextColor(Color.BLACK);buttonTintList=ColorStateList.valueOf(Color.BLACK);panel.addView(this)}
        val scope=if(accountOwned)CheckBox(activity).apply{
            text="I allow this image copy in the device's shared creation workspace. Saved copies are accessible after sign-out or switching accounts; they are not account-protected."
            isChecked=false;setTextColor(Color.BLACK);buttonTintList=ColorStateList.valueOf(Color.BLACK);panel.addView(this)
        }else null
        if(!accountOwned)panel.note("The creation workspace saves locally on this device. Nothing is saved there until you choose to save or add a preview.")
        val review=AlertDialog.Builder(activity).setTitle("Review AI depth photo").setView(scroll(panel)).setNegativeButton("Keep photo only"){_,_->close()}.setPositiveButton("Open 3D workspace",null).create()
        review.setOnShowListener{review.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(dialog!==review||!live()){close();return@setOnClickListener}
            if(!confirmed.isChecked){confirmed.error="Review the depth first";return@setOnClickListener}
            if(scope!=null&&!scope.isChecked){scope.error="Confirm the shared workspace copy first";return@setOnClickListener}
            val opened=runCatching{
                check(result.file.isFile&&result.file.length()<=PhotoDepthMesh.MAX_PAYLOAD_BYTES)
                check(MessageDigest.getInstance("SHA-256").digest(result.file.readBytes()).contentEquals(result.hash)){"Depth copy changed"}
                transferred=true
                try{openScene(result.file)}catch(failure:Throwable){transferred=false;throw failure}
            }
            opened.onFailure{message("The 3D workspace could not open. Original media and edits were kept.")};close()
        }};show(review)
    }
    private companion object {val assetLock=Any()}
}
