package app.honorable.editor

import android.app.Activity
import app.honorable.auth.SafeBetaTelemetry
import org.json.JSONObject
import android.app.ActivityManager
import android.os.Bundle
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.view.ViewGroup
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.widget.*
import java.util.concurrent.Executors
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

/** Local, non-destructive raster editor. Exports through CREATE_DOCUMENT; never writes source URI. */
class PhotoEditorActivity:Activity(){
 data class Edit(val rotation:Int=0,val crop:Float=1f,val brightness:Float=0f,val contrast:Float=1f,val saturation:Float=1f,val exposure:Float=0f,val personCutout:Boolean=false)
 private var readingText=false;private var enhancing=false;private var cuttingOut=false;private var exportEdit:Edit?=null
 @Volatile private var personMask:Bitmap?=null
 private var cutoutJob:AutoCloseable?=null
 private val openedAt=System.currentTimeMillis()
 private fun tool(name:String){SafeBetaTelemetry.emit(this,"edit_tool_used",JSONObject().put("tool",name))}
 private val sliders=mutableListOf<SeekBar>();private var edit=Edit();private val undo=mutableListOf<Edit>();private val redo=mutableListOf<Edit>();private var before=false
 private lateinit var image:ImageView;private lateinit var note:TextView;private lateinit var source:Uri
 private var bitmap:Bitmap?=null;private var rendered:Bitmap?=null;private val worker=Executors.newSingleThreadExecutor();@Volatile private var revision=0
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);source=Uri.parse(intent.getStringExtra("uri")?:run{finish();return})
 window.statusBarColor=Color.WHITE;window.navigationBarColor=if(android.os.Build.VERSION.SDK_INT>=26)Color.WHITE else Color.BLACK
 window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if(android.os.Build.VERSION.SDK_INT>=26)View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);setPadding(12.dp(),24.dp(),12.dp(),16.dp())}
 fun row()=LinearLayout(this).also{root.addView(it)}
 fun button(parent:LinearLayout,label:String,action:()->Unit){parent.addView(Button(this).apply{text=label;isAllCaps=false;textSize=12f;setTextColor(Color.BLACK);background=GradientDrawable().apply{setColor(Color.WHITE);setStroke(1.dp(),Color.BLACK);cornerRadius=6.dp().toFloat()};setOnClickListener{action()}},LinearLayout.LayoutParams(0,52.dp(),1f))}
 val top=row();button(top,"Back"){finish()};button(top,"Undo"){if(undo.isNotEmpty()){tool("undo");redo.add(edit);edit=undo.removeAt(undo.lastIndex);preview()}};button(top,"Redo"){if(redo.isNotEmpty()){tool("redo");undo.add(edit);edit=redo.removeAt(redo.lastIndex);preview()}}
 image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER};root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
 note=TextView(this).apply{setTextColor(Color.DKGRAY);text="Loading original…"};root.addView(note)
 val actions=row();button(actions,"Compare"){before=!before;preview()};button(actions,"Reset"){tool("reset");change(Edit())};button(top,"Save copy"){if(bitmap!=null&&exportEdit==null&&!cuttingOut){exportEdit=edit;try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/png";putExtra(Intent.EXTRA_TITLE,"Honorable-copy.png")},20)}catch(e:Exception){exportEdit=null;note.text="No app is available to save a copy."}}}
 val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8.dp(),8.dp(),8.dp(),8.dp())};root.addView(panel)
 val groups=mutableListOf<LinearLayout>()
 fun adjust(name:String,max:Int,value:Int,update:(Int)->Edit){
  val group=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE};groups.add(group);panel.addView(group)
  val label=TextView(this).apply{text="$name   0";setTextColor(Color.BLACK);textSize=14f};group.addView(label)
  group.addView(SeekBar(this).apply{this.max=max;progress=value;contentDescription=name;sliders.add(this);progressTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);thumbTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){label.text="$name   ${v-value}"};override fun onStopTrackingTouch(s:SeekBar){tool(name.lowercase());change(update(s.progress))}})},LinearLayout.LayoutParams(-1,48.dp()))
 }
 adjust("Brightness",200,100){edit.copy(brightness=(it-100)*1.28f)};adjust("Contrast",200,100){edit.copy(contrast=it/100f)};adjust("Saturation",200,100){edit.copy(saturation=it/100f)};adjust("Exposure",400,200){edit.copy(exposure=(it-200)/100f)}
 val toolScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false};val rail=LinearLayout(this);toolScroll.addView(rail);root.addView(toolScroll)
 val toolButtons=mutableListOf<Button>()
 listOf("Brightness","Contrast","Color","Exposure").forEachIndexed{i,name->
  val control=Button(this).apply{text=name;isAllCaps=false;textSize=12f;setTextColor(Color.DKGRAY);setBackgroundColor(Color.WHITE);setOnClickListener{groups.forEachIndexed{index,view->view.visibility=if(index==i)View.VISIBLE else View.GONE};toolButtons.forEachIndexed{index,view->view.isSelected=index==i;view.setTextColor(if(index==i)Color.BLACK else Color.GRAY)}}};toolButtons.add(control);rail.addView(control,LinearLayout.LayoutParams(104.dp(),48.dp()))
 }
 button(actions,"Read text"){readText()}
 val smart=row();button(smart,"AI scene enhance"){enhanceScene()};button(smart,"Cut out person (beta)"){cutOutPerson()}
 val transforms=row();button(transforms,"Rotate"){tool("rotate");change(edit.copy(rotation=(edit.rotation+90)%360))};button(transforms,"Crop center"){tool("crop");change(edit.copy(crop=if(edit.crop>.51f)edit.crop-.1f else 1f))};button(transforms,"Monochrome"){tool("monochrome");change(edit.copy(saturation=0f))}

 setContentView(root)
 SafeBetaTelemetry.emit(this,"editor_opened",JSONObject().put("mediaType","IMAGE"));
 worker.execute{try{SafeBetaTelemetry.authorizeEditor(this);val low=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice;if(low)SafeBetaTelemetry.emit(this,"performance_sample",JSONObject().put("metric","PROXY_USE").put("durationMs",0));val loaded=decode(if(low)768 else 1600);runOnUiThread{if(!isDestroyed){bitmap=loaded;preview()}else loaded.recycle()}}catch(e:Exception){runOnUiThread{if(!isDestroyed)note.text="Unable to open this image. Check access and try again."}}catch(e:OutOfMemoryError){runOnUiThread{if(!isDestroyed)note.text="Not enough memory to open this photo. Try a smaller image."}}}
 }
 private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
 private fun change(next:Edit){undo.add(edit);if(undo.size>50)undo.removeAt(0);redo.clear();edit=next;before=false;preview()}
 private fun decode(limit:Int):Bitmap{val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,bounds)};require(bounds.outWidth>0){"Unsupported image"};val runtime=Runtime.getRuntime();val headroom=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory());require(headroom>8L*1024*1024){"Not enough memory. Close other tools and try again."};val low=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice;val maxPixels=(headroom/24).coerceAtMost(if(low)1_500_000L else 6_000_000L);val sample=PhotoDecodeBudget.sampleSize(bounds.outWidth,bounds.outHeight,limit,maxPixels);val options=BitmapFactory.Options().apply{inSampleSize=sample};val decoded=contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,options)}?:error("Unable to decode image");val orientation=runCatching{contentResolver.openInputStream(source).use{android.media.ExifInterface(requireNotNull(it)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1);val matrix=Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)};if(orientation==1)return decoded;val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true);if(oriented!==decoded)decoded.recycle();return oriented}
 private fun render(input:Bitmap,e:Edit):Bitmap=PhotoRasterRenderer.render(input,e,personMask)
 private fun preview() {
  listOf((edit.brightness/1.28f+100).toInt(),(edit.contrast*100).toInt(),(edit.saturation*100).toInt(),(edit.exposure*100+200).toInt()).forEachIndexed{i,v->sliders.getOrNull(i)?.progress=v}
  val input=bitmap?:return
  val state=if(before)Edit()else edit
  image.background=if(state.personCutout)TransparencyGridDrawable(12.dp())else null
  val request=++revision
  val started=System.currentTimeMillis()
  worker.execute {
   if(request!=revision)return@execute
   try {
    val result=render(input,state)
    runOnUiThread {
     if(request==revision&&!isDestroyed){
      val old=rendered;rendered=result
      SafeBetaTelemetry.duration(this,"EDITOR_RESPONSE",started)
      image.setImageBitmap(result);old?.recycle()
      if(!readingText&&!cuttingOut&&exportEdit==null)note.text=if(before)"Original preview" else if(state.personCutout)"Person cutout · check edges before saving a PNG copy" else "Edited preview · original preserved"
     }else result.recycle()
    }
   }catch(e:OutOfMemoryError){
    runOnUiThread{if(request==revision&&!isDestroyed)note.text="Not enough memory for this preview. Try again."}
   }
  }
 }
 private fun cutOutPerson() {
  val input=bitmap?:return
  if(cuttingOut||readingText||enhancing||exportEdit!=null||isFinishing)return
  if(personMask!=null){tool("person_cutout");change(edit.copy(personCutout=true));return}
  cuttingOut=true
  val request=++revision
  note.text="Separating the person on this device…"
  worker.execute {
   try {
    val scale=minOf(1f,768f/maxOf(input.width,input.height))
    val proxy=Bitmap.createScaledBitmap(input,(input.width*scale).toInt().coerceAtLeast(1),(input.height*scale).toInt().coerceAtLeast(1),true)
    val owned=if(proxy===input)requireNotNull(input.copy(Bitmap.Config.ARGB_8888,false))else proxy
    runOnUiThread {
     if(isDestroyed||isFinishing){owned.recycle();return@runOnUiThread}
     runCatching {
      cutoutJob=PersonCutoutProcessor.start(owned){result->
       cutoutJob=null;cuttingOut=false
       val mask=result.getOrNull()
       if(isDestroyed||isFinishing){mask?.recycle();return@start}
       if(mask==null){note.text=if(result.exceptionOrNull() is PersonMaskAlpha.NoClearPerson)"No clear person boundary found. Try a photo with a person." else "Person cutout unavailable. Try again.";return@start}
       personMask=mask
       if(request==revision){tool("person_cutout");change(edit.copy(personCutout=true))}
       else note.text="Cutout ready. Tap Cut out person to apply it to your current edit."
      }
     }.onFailure{owned.recycle();cuttingOut=false;note.text="Person cutout unavailable. Try again."}
    }
   }catch(error:Throwable){runOnUiThread{cuttingOut=false;if(!isDestroyed)note.text="Not enough resources for person cutout. Try a smaller photo."}}
  }
 }
 private fun enhanceScene() {
  val input=bitmap?:return
  if(enhancing||cuttingOut||exportEdit!=null||isFinishing)return
  enhancing=true;note.text="Analyzing this photo on your device…"
  val labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
  labeler.process(InputImage.fromBitmap(input,0))
   .addOnSuccessListener { labels ->
    if(isDestroyed||isFinishing)return@addOnSuccessListener
    val scene=labels.filter{it.confidence>=.55f}.map{it.text.lowercase()}
    val isPerson=scene.any{it.contains("person")||it.contains("face")||it.contains("portrait")}
    val isOutdoor=scene.any{it.contains("landscape")||it.contains("beach")||it.contains("sky")||it.contains("nature")}
    val isNight=scene.any{it.contains("night")||it.contains("dark")}
    val light=sampleLight(input)
    val exposureBoost=when {
     light>=.72f -> 0f
     light<.28f -> if(isNight).38f else .3f
     light<.46f -> .15f
     else -> .05f
    }
    val next=edit.copy(
     exposure=(edit.exposure+exposureBoost).coerceIn(-2f,2f),
     contrast=(edit.contrast*(if(isPerson)1.03f else 1.08f)).coerceIn(0f,2f),
     saturation=(edit.saturation*(if(isOutdoor)1.12f else if(isPerson)1.01f else 1.05f)).coerceIn(0f,2f)
    )
    tool("ai_scene_enhance");change(next)
    note.text="Scene-aware adjustments applied on device. Compare before saving a copy."
   }
   .addOnFailureListener { if(!isDestroyed)note.text="Scene analysis failed. Try manual adjustments." }
   .addOnCompleteListener { enhancing=false;labeler.close() }
 }
 private fun sampleLight(input:Bitmap):Float {
  var total=0.0;var count=0
  val stepX=(input.width/32).coerceAtLeast(1);val stepY=(input.height/32).coerceAtLeast(1)
  for(y in 0 until input.height step stepY)for(x in 0 until input.width step stepX){
   val pixel=input.getPixel(x,y)
   total+=(.2126*Color.red(pixel)+.7152*Color.green(pixel)+.0722*Color.blue(pixel))/255.0
   count++
  }
  return (total/count.coerceAtLeast(1)).toFloat()
 }
 private fun readText() {
  val input=bitmap?:return
  if(readingText||cuttingOut||isFinishing)return
  readingText=true
  val state=edit
  note.text="Reading text on this device…"
  worker.execute {
   try {
    val snapshot=render(input,state)
    runOnUiThread {
     if(isFinishing||isDestroyed){snapshot.recycle();return@runOnUiThread}
     PhotoTextReader.read(this,snapshot){message->readingText=false;note.text=message}
    }
   } catch(e:Exception) {
    runOnUiThread{readingText=false;if(!isDestroyed)note.text="Unable to read this photo. Try again."}
   } catch(e:OutOfMemoryError) {
    runOnUiThread{readingText=false;if(!isDestroyed)note.text="Not enough memory to read this photo. Try a smaller image."}
   }
  }
 }
 @Deprecated("Platform result API")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=20)return
  val state=exportEdit?:edit
  if(resultCode!=RESULT_OK||data?.data==null){exportEdit=null;return}
  val destination=requireNotNull(data.data)
  note.text="Exporting copy…"
  worker.execute {
   var input:Bitmap?=null
   var output:Bitmap?=null
   try {
    SafeBetaTelemetry.authorizeEditor(this)
    val decoded=decode(8192)
    input=decoded
    val result=render(decoded,state)
    output=result
    SafePhotoCopy.save(this,source,destination,result)
    val dimensions="${result.width} × ${result.height}"
    SafeBetaTelemetry.emit(this,"edit_saved",JSONObject().put("mediaType","IMAGE"))
    runOnUiThread{exportEdit=null;if(!isDestroyed)note.text="Copy saved · $dimensions px. Original preserved. Large photos are reduced for memory safety."}
   } catch(e:Exception) {
    SafeBetaTelemetry.emit(this,"client_error",JSONObject().put("errorCode","SAVE_FAILED"))
    runOnUiThread{exportEdit=null;if(!isDestroyed)note.text="Export failed. Choose a local writable destination; check Files for a partial copy."}
   } catch(e:OutOfMemoryError) {
    runOnUiThread{exportEdit=null;if(!isDestroyed)note.text="Not enough memory to save this photo. Close other tools and try again."}
   } finally {output?.recycle();input?.recycle()}
  }
 }
 override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)SafeBetaTelemetry.emit(this,"performance_sample",JSONObject().put("metric","MEMORY_PRESSURE").put("durationMs",0))}
 override fun onDestroy(){SafeBetaTelemetry.emit(this,"editor_closed",JSONObject().put("durationMs",(System.currentTimeMillis()-openedAt).coerceIn(0,3600000)));revision++;cutoutJob?.close();cutoutJob=null;if(::image.isInitialized)image.setImageDrawable(null);rendered?.recycle();rendered=null;worker.execute{bitmap?.recycle();bitmap=null;personMask?.recycle();personMask=null};worker.shutdown();super.onDestroy()}
}
