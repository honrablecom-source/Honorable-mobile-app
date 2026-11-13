package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Local official MI-GAN inference; explicit mask, complete review and separate copy. */
internal class PhotoEraseControls(
 private val activity:Activity,private val worker:Executor,
 private val prepareInput:(()->Unit)->Bitmap,private val isCurrent:()->Boolean,
 private val requireWorkerCurrent:()->Unit,private val accountOwned:Boolean,
 private val openCopy:(File)->Unit,private val message:(String)->Unit,private val released:()->Unit,
 private val modelFile:((PhotoEraseModel.Stop)->File)?=null,
 private val infer:(File,IntArray,Int,Int,BooleanArray,PhotoEraseModel.Stop)->IntArray=PhotoEraseModel::erase,
):AutoCloseable{
 private val closed=AtomicBoolean(false);private val running=AtomicBoolean(false)
 private val stop=PhotoEraseModel.Stop();private var releasedOnce=false
 private var dialog:AlertDialog?=null;private var photo:Bitmap?=null;private var result:Bitmap?=null
 private var file:File?=null;private var hash:ByteArray?=null;private var transferred=false
 private var maskView:PhotoEraseMaskView?=null;private val images=mutableListOf<ImageView>()
 private fun live()=!closed.get()&&isCurrent()&&!activity.isFinishing&&!activity.isDestroyed
 private fun dispose(){photo?.recycle();photo=null;result?.recycle();result=null;if(!transferred)file?.delete();file=null}
 private fun releaseOnce(){if(!releasedOnce){releasedOnce=true;released()}}
 override fun close(){
  if(closed.compareAndSet(false,true)){runCatching{stop.cancel()};val old=dialog;dialog=null;old?.dismiss();maskView?.release();maskView=null;images.forEach{it.setImageDrawable(null)};images.clear()}
  if(!running.get()){dispose();releaseOnce()}
 }
 private fun show(next:AlertDialog){if(!live()){close();return};val old=dialog;dialog=null;old?.dismiss();dialog=next;next.setOnDismissListener{if(dialog===next)close()};next.show()}
 private fun body()=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16);setBackgroundColor(Color.WHITE)}
 private fun LinearLayout.note(value:String){addView(TextView(activity).apply{text=value;setTextColor(Color.BLACK);setPadding(0,8,0,12)})}
 private fun scroll(panel:LinearLayout)=ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(panel)}
 private fun fail(){message("Object removal could not finish. Original media and edits were kept.");close()}
 private fun execute(work:(()->Unit)->Unit,done:()->Unit){
  if(!live()||!running.compareAndSet(false,true))return
  try{worker.execute{
   val began=System.nanoTime();val watchdog=Executors.newSingleThreadScheduledExecutor{task->Thread(task,"noble-erase-stop").apply{isDaemon=true}}
   fun current(){stop.check();check(!closed.get());check(System.nanoTime()-began<180_000_000_000L);requireWorkerCurrent()}
   watchdog.scheduleAtFixedRate({if(closed.get()||System.nanoTime()-began>=180_000_000_000L||runCatching(requireWorkerCurrent).isFailure)runCatching{stop.cancel()}},250,250,TimeUnit.MILLISECONDS)
   val outcome=try{runCatching{current();work(::current);current()}}finally{watchdog.shutdownNow()}
   activity.runOnUiThread{running.set(false);if(!live()){close();return@runOnUiThread};outcome.onSuccess{done()}.onFailure{fail()}}
  }}catch(_:Exception){running.set(false);fail()}
 }
 private fun progress(title:String){show(AlertDialog.Builder(activity).setTitle(title).setMessage("Working locally on this device. Stop keeps the original and all current edits.").setNegativeButton("Stop"){_,_->close()}.create())}
 fun open(){
  if(!live()){close();return};progress("Preparing object eraser")
  execute({current->val prepared=prepareInput(current);photo=prepared;require(!prepared.isRecycled&&prepared.width in 16..1024&&prepared.height in 16..1024)},{select()})
 }
    private fun bundledModel(current:()->Unit):File=synchronized(assetLock){
        val folder=File(activity.filesDir,"noble-models/inpaint");val target=File(folder,"migan_pipeline_v2.onnx")
        val valid=try{PhotoEraseModel.verify(target,stop);true}catch(_:IllegalArgumentException){false}
        if(valid)return@synchronized target
        check(folder.isDirectory||folder.mkdirs());val temporary=File.createTempFile("erase-model-",".part",folder)
        try{
            var total=0L
            activity.assets.open(PhotoEraseModel.ASSET).use{input->temporary.outputStream().buffered().use{output->
                val buffer=ByteArray(65536)
                while(true){current();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=PhotoEraseModel.BYTES);output.write(buffer,0,n)}
            }}
            PhotoEraseModel.verify(temporary,stop);current();check(temporary.renameTo(target));target
        }finally{temporary.delete()}
    }

 private fun select(){
  val input=requireNotNull(photo);val panel=body()
  panel.note("Paint over the object to remove. MI-GAN generates replacement texture from nearby context, entirely on this device. It cannot recover what was originally hidden. Leave surrounding image visible; smaller selections usually work better.")
  panel.note("A separate PNG copy uses the complete current edit fitted within 1024 pixels. Original media and current edits stay unchanged. Review the entire result before opening the copy.")
  val mask=PhotoEraseMaskView(activity).apply{setPhoto(input)};maskView=mask
  panel.addView(mask,LinearLayout.LayoutParams(-1,(300*activity.resources.displayMetrics.density).toInt()))
  val size=TextView(activity).apply{text="Brush radius: 16 image pixels";setTextColor(Color.BLACK)};panel.addView(size)
  panel.addView(SeekBar(activity).apply{max=95;progress=15;contentDescription="Eraser brush radius";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
   override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean){mask.radius=(value+1).toFloat();size.text="Brush radius: ${value+1} image pixels"}
   override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){}
  })})
  panel.addView(Button(activity).apply{text="Clear selection";setOnClickListener{mask.clear()}})
  // Percent rectangle provides a precise selection alternative to touch painting.
  val fields=listOf("Left %" to "25","Top %" to "25","Right %" to "75","Bottom %" to "75").map{(label,value)->EditText(activity).apply{hint=label;contentDescription=label;inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText(value);panel.addView(this)}}
  panel.addView(Button(activity).apply{text="Select rectangle instead";setOnClickListener{runCatching{val v=fields.map{it.text.toString().toInt()};mask.rectangle(v[0],v[1],v[2],v[3])}.onFailure{fields.first().error="Use ordered percentages from 0 to 100"}}})
  val setup=AlertDialog.Builder(activity).setTitle("AI object eraser").setView(scroll(panel)).setNegativeButton("Keep editing"){_,_->close()}.setPositiveButton("Remove selected object",null).create()
  setup.setOnShowListener{setup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   if(dialog!==setup||!live()){close();return@setOnClickListener}
   val selected=mask.mask();val count=selected.count{it}
   if(count==0||count.toLong()*4>selected.size.toLong()*3){size.text="Paint an object and leave at least a quarter of the image unselected.";return@setOnClickListener}
   mask.release();maskView=null;progress("Removing selected object with AI")
   execute({current->
    val pixels=IntArray(input.width*input.height);input.getPixels(pixels,0,input.width,0,0,input.width,input.height);current()
    val model=modelFile?.invoke(stop)?:bundledModel(current)
    val output=infer(model,pixels,input.width,input.height,selected,stop);current();require(output.size==pixels.size)
    // Enforce preservation even when the inference provider is replaced in a test.
    for(i in pixels.indices)if(!selected[i])require(output[i]==pixels[i])
    val generated=Bitmap.createBitmap(output,input.width,input.height,Bitmap.Config.ARGB_8888);result=generated
    file=PhotoComparisonFiles.write(activity.cacheDir,current){target->java.io.FileOutputStream(target).use{stream->check(generated.compress(Bitmap.CompressFormat.PNG,100,stream));stream.fd.sync()}}
    hash=MessageDigest.getInstance("SHA-256").digest(requireNotNull(file).readBytes());current()
   },{review()})
  }};show(setup)
 }
 private fun review(){
  val panel=body();panel.note("Review the full before and after. Generated areas can contain incorrect shapes, repeated texture or visible seams. Only painted pixels changed in this working-size copy. Nothing has been applied to your current photo.")
  for((label,image)in listOf("Before object removal" to requireNotNull(photo),"After AI object removal" to requireNotNull(result))){
   panel.note(label);val view=ImageView(activity).apply{contentDescription=label;scaleType=ImageView.ScaleType.FIT_CENTER;setImageBitmap(image);setBackgroundColor(Color.WHITE)}
   panel.addView(view,LinearLayout.LayoutParams(-1,(240*activity.resources.displayMetrics.density).toInt()));images.add(view)
  }
  fun consent(textValue:String)=CheckBox(activity).apply{text=textValue;isChecked=false;setTextColor(Color.BLACK);buttonTintList=ColorStateList.valueOf(Color.BLACK);panel.addView(this)}
  val approved=consent("I reviewed the entire result and want a separate local copy")
  val scope=if(accountOwned)consent("I allow this copy in the device's shared guest editor. It remains accessible after sign-out or switching accounts and is not account-protected.")else null
  val review=AlertDialog.Builder(activity).setTitle("Review AI object removal").setView(scroll(panel)).setNegativeButton("Discard result"){_,_->close()}.setPositiveButton("Open edited copy",null).create()
  review.setOnShowListener{review.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   if(dialog!==review||!live()){close();return@setOnClickListener}
   if(!approved.isChecked){approved.error="Review the full result first";return@setOnClickListener}
   if(scope!=null&&!scope.isChecked){scope.error="Confirm the shared guest copy first";return@setOnClickListener}
   runCatching{
    val output=requireNotNull(file);check(output.isFile&&output.length() in 1..PhotoComparisonFiles.MAX_BYTES)
    check(MessageDigest.getInstance("SHA-256").digest(output.readBytes()).contentEquals(requireNotNull(hash)))
    transferred=true;try{openCopy(output)}catch(failure:Throwable){transferred=false;throw failure}
   }.onFailure{message("The copy could not open. Original media and edits were kept.")};close()
  }};show(review)
 }
 private companion object{val assetLock=Any()}
}
