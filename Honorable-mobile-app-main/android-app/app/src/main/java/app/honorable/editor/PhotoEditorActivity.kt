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

 private var activeTool=-1
 private var restoredSession:PhotoSessionSnapshot?=null
 private var pendingDestination:Uri?=null
 private var exportInProgress=false
 private var waitingForPicker=false
 private var loadedScope:String?=null
 private fun currentScope():String?=runCatching {
  val session=app.honorable.auth.SecureAccountStore(this).read()?:return@runCatching null
  if(session.optLong("expiresAt")<=System.currentTimeMillis())return@runCatching null
  val account=session.optJSONObject("cached")?.optJSONObject("account")?.optString("accountId").orEmpty()
  val api=session.optString("apiUrl")
  if(account.isBlank()||!api.startsWith("https://"))return@runCatching null
  PhotoSessionCodec.scope(account,api,source.toString())
 }.getOrNull()
 private var readingText=false;private var enhancing=false;private var cuttingOut=false;private var exportEdit:PhotoEdit?=null
 @Volatile private var personMask:Bitmap?=null
 private var cutoutJob:AutoCloseable?=null
 private val openedAt=System.currentTimeMillis()
 private fun tool(name:String){SafeBetaTelemetry.emit(this,"edit_tool_used",JSONObject().put("tool",name))}
 private val sliders=mutableListOf<SeekBar>();private var edit=PhotoEdit();private val undo=mutableListOf<PhotoEdit>();private val redo=mutableListOf<PhotoEdit>();private var before=false
 private lateinit var image:ImageView;private lateinit var note:TextView;private lateinit var source:Uri
 private var bitmap:Bitmap?=null;private var rendered:Bitmap?=null;private val worker=Executors.newSingleThreadExecutor();@Volatile private var revision=0
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);source=Uri.parse(intent.getStringExtra("uri")?:run{finish();return})
 restoredSession=runCatching{val bytes=savedInstanceState?.getByteArray("photoSession")?:return@runCatching null
  PhotoSessionCodec.decode(bytes,currentScope()?:return@runCatching null)}.getOrNull()
 exportEdit=restoredSession?.pendingExport;waitingForPicker=exportEdit!=null
 window.statusBarColor=Color.WHITE;window.navigationBarColor=if(android.os.Build.VERSION.SDK_INT>=26)Color.WHITE else Color.BLACK
 window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if(android.os.Build.VERSION.SDK_INT>=26)View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);setPadding(12.dp(),4.dp(),12.dp(),8.dp())}
 fun row(parent:LinearLayout=root)=LinearLayout(this).also{parent.addView(it)}
 fun button(parent:LinearLayout,label:String,action:()->Unit):Button {
  val control=Button(this).apply{text=label;contentDescription=label;isAllCaps=false;textSize=12f;minWidth=0;minimumWidth=0;setPadding(6.dp(),4.dp(),6.dp(),4.dp());setTextColor(Color.BLACK);background=GradientDrawable().apply{setColor(Color.WHITE);setStroke(1.dp(),Color.BLACK);cornerRadius=8.dp().toFloat()};setOnClickListener{action()}}
  parent.addView(control,LinearLayout.LayoutParams(0,48.dp(),1f).apply{setMargins(2.dp(),2.dp(),2.dp(),2.dp())})
  return control
 }
 val top=row()
 button(top,"‹"){finish()}.apply{contentDescription="Close photo editor";layoutParams=LinearLayout.LayoutParams(48.dp(),48.dp())}
 top.addView(TextView(this).apply{text="Honorable / Photo";textSize=17f;setTextColor(Color.BLACK);gravity=android.view.Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,48.dp(),1f))
 val export=button(top,"Export"){if(bitmap!=null&&exportEdit==null&&!cuttingOut){exportEdit=edit;waitingForPicker=true;try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/png";putExtra(Intent.EXTRA_TITLE,"Honorable-copy.png")},20)}catch(e:Exception){waitingForPicker=false;exportEdit=null;note.text="No app is available to save a copy."}}}
 export.apply{contentDescription="Save copy of photo";layoutParams=LinearLayout.LayoutParams(88.dp(),48.dp());setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.BLACK);cornerRadius=8.dp().toFloat()}}
 image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(4.dp(),8.dp(),4.dp(),8.dp())}
 root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
 val transport=row()
 button(transport,"Compare"){before=!before;preview()}
 button(transport,"Undo"){if(undo.isNotEmpty()){tool("undo");redo.add(edit);edit=undo.removeAt(undo.lastIndex);preview()}}
 button(transport,"Redo"){if(redo.isNotEmpty()){tool("redo");undo.add(edit);edit=redo.removeAt(redo.lastIndex);preview()}}
 note=TextView(this).apply{setTextColor(Color.DKGRAY);text="Loading original…";textSize=12f;maxLines=2;setPadding(4.dp(),8.dp(),4.dp(),8.dp());accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};root.addView(note)
 val tray=FrameLayout(this);root.addView(tray,LinearLayout.LayoutParams(-1,140.dp()))
 val categories=linkedMapOf<String,LinearLayout>()
 fun category(name:String):LinearLayout {
  val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(4.dp(),8.dp(),4.dp(),8.dp())}
  categories[name]=content
  tray.addView(ScrollView(this).apply{visibility=View.GONE;addView(content)},FrameLayout.LayoutParams(-1,-1))
  return content
 }
 val adjustments=category("Adjust");val transforms=category("Transform");val assist=category("Assist")
 val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};adjustments.addView(panel)
 val groups=mutableListOf<LinearLayout>()
 fun adjust(name:String,max:Int,value:Int,update:(Int)->PhotoEdit){
  val group=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE};groups.add(group);panel.addView(group)
  val label=TextView(this).apply{text="$name   0";setTextColor(Color.BLACK);textSize=14f};group.addView(label)
  group.addView(SeekBar(this).apply{this.max=max;progress=value;contentDescription=name;sliders.add(this);progressTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);thumbTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){label.text="$name   ${v-value}"};override fun onStopTrackingTouch(s:SeekBar){tool(name.lowercase());change(update(s.progress))}})},LinearLayout.LayoutParams(-1,48.dp()))
 }
 adjust("Brightness",200,100){edit.copy(brightness=(it-100)*1.28f)};adjust("Contrast",200,100){edit.copy(contrast=it/100f)};adjust("Saturation",200,100){edit.copy(saturation=it/100f)};adjust("Exposure",400,200){edit.copy(exposure=(it-200)/100f)}
 val toolScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false};val rail=LinearLayout(this);toolScroll.addView(rail);adjustments.addView(toolScroll)
 val toolButtons=mutableListOf<Button>()
 listOf("Brightness","Contrast","Color","Exposure").forEachIndexed{i,name->
  val control=Button(this).apply{text=name;isAllCaps=false;textSize=12f;setTextColor(Color.DKGRAY);setBackgroundColor(Color.WHITE);setOnClickListener{activeTool=i;groups.forEachIndexed{index,view->view.visibility=if(index==i)View.VISIBLE else View.GONE};toolButtons.forEachIndexed{index,view->view.isSelected=index==i;view.setTextColor(if(index==i)Color.BLACK else Color.GRAY)}}};toolButtons.add(control);rail.addView(control,LinearLayout.LayoutParams(104.dp(),48.dp()))
 }
 val transformRow=row(transforms)
 button(transformRow,"Rotate"){tool("rotate");change(edit.copy(rotation=(edit.rotation+90)%360))}
 button(transformRow,"Crop center"){tool("crop");change(edit.copy(crop=if(edit.crop>.51f)edit.crop-.1f else 1f))}
 val finishRow=row(transforms)
 button(finishRow,"Monochrome"){tool("monochrome");change(edit.copy(saturation=0f))}
 button(finishRow,"Reset"){tool("reset");change(PhotoEdit())}
 button(row(assist),"AI scene enhance"){enhanceScene()}
 button(row(assist),"Cut out person (beta)"){cutOutPerson()}
 button(row(assist),"Read text"){readText()}
 val tabs=row();val tabButtons=mutableListOf<Button>()
 fun selectCategory(name:String){
  categories.forEach{(key,content)->(content.parent as View).visibility=if(key==name)View.VISIBLE else View.GONE}
  tabButtons.forEach{control->val selected=control.text.toString()==name;control.isSelected=selected;control.setTextColor(if(selected)Color.WHITE else Color.BLACK);control.background=GradientDrawable().apply{setColor(if(selected)Color.BLACK else Color.WHITE);cornerRadius=8.dp().toFloat()}}
 }
 categories.keys.forEach{name->tabButtons.add(button(tabs,name){selectCategory(name)})}
 selectCategory("Adjust");toolButtons.first().performClick()

 setContentView(root)
 NativeWorkspaceInsets.install(this,root)
 SafeBetaTelemetry.emit(this,"editor_opened",JSONObject().put("mediaType","IMAGE"));
 worker.execute{try{SafeBetaTelemetry.authorizeEditor(this);val low=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice;if(low)SafeBetaTelemetry.emit(this,"performance_sample",JSONObject().put("metric","PROXY_USE").put("durationMs",0));val loaded=decode(if(low)768 else 1600);runOnUiThread{if(!isDestroyed){
   bitmap=loaded;loadedScope=currentScope()
   restoredSession?.takeIf{it.scope==loadedScope}?.let{saved->edit=saved.edit;undo.addAll(saved.undo);redo.addAll(saved.redo);before=saved.compareOriginal;activeTool=saved.activeTool
    if(activeTool in toolButtons.indices)toolButtons[activeTool].performClick()}
   if(restoredSession!=null&&restoredSession?.scope!=loadedScope){exportEdit=null;pendingDestination=null;note.text="Previous edits belong to another session."}
   restoredSession=null;preview();resumePendingExport()
  }else loaded.recycle()}}catch(e:Exception){runOnUiThread{if(!isDestroyed)note.text="Unable to open this image. Check access and try again."}}catch(e:OutOfMemoryError){runOnUiThread{if(!isDestroyed)note.text="Not enough memory to open this photo. Try a smaller image."}}}
 }
 private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
 private fun change(next:PhotoEdit){undo.add(edit);if(undo.size>50)undo.removeAt(0);redo.clear();edit=next;before=false;preview()}
 private fun decode(limit:Int):Bitmap{val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,bounds)};require(bounds.outWidth>0){"Unsupported image"};val runtime=Runtime.getRuntime();val headroom=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory());require(headroom>8L*1024*1024){"Not enough memory. Close other tools and try again."};val low=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice;val maxPixels=(headroom/24).coerceAtMost(if(low)1_500_000L else 6_000_000L);val sample=PhotoDecodeBudget.sampleSize(bounds.outWidth,bounds.outHeight,limit,maxPixels);val options=BitmapFactory.Options().apply{inSampleSize=sample};val decoded=contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,options)}?:error("Unable to decode image");val orientation=runCatching{contentResolver.openInputStream(source).use{android.media.ExifInterface(requireNotNull(it)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1);val matrix=Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)};if(orientation==1)return decoded;val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true);if(oriented!==decoded)decoded.recycle();return oriented}
 private fun render(input:Bitmap,e:PhotoEdit):Bitmap=PhotoRasterRenderer.render(input,e,personMask)
 private fun preview() {
  listOf((edit.brightness/1.28f+100).toInt(),(edit.contrast*100).toInt(),(edit.saturation*100).toInt(),(edit.exposure*100+200).toInt()).forEachIndexed{i,v->sliders.getOrNull(i)?.progress=v}
  val input=bitmap?:return
  val state=if(before)PhotoEdit()else edit
  if(state.personCutout&&personMask==null){note.text="Restoring person cutout on this device…";cutOutPerson(true);return}
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
 private fun cutOutPerson(recover:Boolean=false) {
  val input=bitmap?:return
  if(cuttingOut||readingText||enhancing||(!recover&&exportEdit!=null)||isFinishing)return
  if(personMask!=null){if(recover){preview();resumePendingExport()}else{tool("person_cutout");change(edit.copy(personCutout=true))};return}
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
       if(mask==null){if(recover){exportEdit=null;pendingDestination=null};note.text=if(result.exceptionOrNull() is PersonMaskAlpha.NoClearPerson)"No clear person boundary found. Try a photo with a person." else "Person cutout unavailable. Try again.";return@start}
       personMask=mask
       if(recover){preview();resumePendingExport()}
       else if(request==revision){tool("person_cutout");change(edit.copy(personCutout=true))}
       else note.text="Cutout ready. Tap Cut out person to apply it to your current edit."
      }
     }.onFailure{owned.recycle();cuttingOut=false;if(recover){exportEdit=null;pendingDestination=null};note.text="Person cutout unavailable. Try again."}
    }
   }catch(error:Throwable){runOnUiThread{cuttingOut=false;if(recover){exportEdit=null;pendingDestination=null};if(!isDestroyed)note.text="Not enough resources for person cutout. Try a smaller photo."}}
  }
 }
 private fun enhanceScene() {
  val input=bitmap?:return
  if(enhancing||cuttingOut||exportEdit!=null||isFinishing)return
  val owned=runCatching{requireNotNull(input.copy(Bitmap.Config.ARGB_8888,false))}.getOrNull()?:run{note.text="Not enough memory for scene analysis.";return}
  val initial=edit;val request=revision
  enhancing=true;note.text="Analyzing this photo on your device…"
  val labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
  try{labeler.process(InputImage.fromBitmap(owned,0))
   .addOnSuccessListener { labels ->
    if(isDestroyed||isFinishing)return@addOnSuccessListener
    if(request!=revision){note.text="Photo changed during analysis. Run scene enhance again.";return@addOnSuccessListener}
    val scene=labels.filter{it.confidence>=.55f}.map{it.text.lowercase()}
    val isPerson=scene.any{it.contains("person")||it.contains("face")||it.contains("portrait")}
    val isOutdoor=scene.any{it.contains("landscape")||it.contains("beach")||it.contains("sky")||it.contains("nature")}
    val isNight=scene.any{it.contains("night")||it.contains("dark")}
    val light=sampleLight(owned)
    val exposureBoost=when {
     light>=.72f -> 0f
     light<.28f -> if(isNight).38f else .3f
     light<.46f -> .15f
     else -> .05f
    }
    val next=initial.copy(
     exposure=(initial.exposure+exposureBoost).coerceIn(-2f,2f),
     contrast=(initial.contrast*(if(isPerson)1.03f else 1.08f)).coerceIn(0f,2f),
     saturation=(initial.saturation*(if(isOutdoor)1.12f else if(isPerson)1.01f else 1.05f)).coerceIn(0f,2f)
    )
    tool("ai_scene_enhance");change(next)
    note.text="Scene-aware adjustments applied on device. Compare before saving a copy."
   }
   .addOnFailureListener { if(!isDestroyed)note.text="Scene analysis failed. Try manual adjustments." }
   .addOnCompleteListener { enhancing=false;labeler.close();owned.recycle() }
  }catch(e:Exception){enhancing=false;labeler.close();owned.recycle();note.text="Scene analysis failed. Try manual adjustments."}
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
  waitingForPicker=false
  if(exportEdit==null){pendingDestination=null;note.text="Save interrupted. Choose Save copy again.";return}
  if(resultCode!=RESULT_OK||data?.data==null){exportEdit=null;return}
  pendingDestination=requireNotNull(data.data)
  resumePendingExport()
 }
 private fun resumePendingExport(){
  val destination=pendingDestination?:return
  if(bitmap==null||exportInProgress||isDestroyed)return
  if(loadedScope!=null&&currentScope()!=loadedScope){pendingDestination=null;exportEdit=null;note.text="Account changed. Reopen this photo before saving.";return}
  val state=exportEdit?:run{pendingDestination=null;return}
  if(state.personCutout&&personMask==null){cutOutPerson(true);return}
  pendingDestination=null;exportInProgress=true
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
    runOnUiThread{exportInProgress=false;exportEdit=null;if(!isDestroyed)note.text="Copy saved · $dimensions px. Original preserved. Large photos are reduced for memory safety."}
   } catch(e:Exception) {
    SafeBetaTelemetry.emit(this,"client_error",JSONObject().put("errorCode","SAVE_FAILED"))
    runOnUiThread{exportInProgress=false;exportEdit=null;if(!isDestroyed)note.text="Export failed. Choose a local writable destination; check Files for a partial copy."}
   } catch(e:OutOfMemoryError) {
    runOnUiThread{exportInProgress=false;exportEdit=null;if(!isDestroyed)note.text="Not enough memory to save this photo. Close other tools and try again."}
   } finally {output?.recycle();input?.recycle()}
  }
 }
 override fun onSaveInstanceState(outState:Bundle){
  super.onSaveInstanceState(outState)
  runCatching {
   val scope=currentScope()?:return@runCatching
   val pending=restoredSession?.takeIf{it.scope==scope}
   if(restoredSession!=null&&pending==null)return@runCatching
   val snapshot=pending?.copy(pendingExport=if(waitingForPicker)exportEdit else null)?:PhotoSessionSnapshot(scope,edit,undo.toList(),redo.toList(),before,activeTool,
    if(waitingForPicker&&!exportInProgress&&pendingDestination==null)exportEdit else null)
   if(loadedScope==null||loadedScope==scope)outState.putByteArray("photoSession",PhotoSessionCodec.encode(snapshot))
  }
 }
 override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)SafeBetaTelemetry.emit(this,"performance_sample",JSONObject().put("metric","MEMORY_PRESSURE").put("durationMs",0))}
 override fun onDestroy(){SafeBetaTelemetry.emit(this,"editor_closed",JSONObject().put("durationMs",(System.currentTimeMillis()-openedAt).coerceIn(0,3600000)));revision++;cutoutJob?.close();cutoutJob=null;if(::image.isInitialized)image.setImageDrawable(null);rendered?.recycle();rendered=null;worker.execute{bitmap?.recycle();bitmap=null;personMask?.recycle();personMask=null};worker.shutdown();super.onDestroy()}
}
