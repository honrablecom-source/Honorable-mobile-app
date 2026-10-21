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

 private var renderedEdit:PhotoEdit?=null
 private var placingLight:PhotoRadialLight?=null
 private var placingOn:PhotoEdit?=null
 private var activeTool=-1
 private var restoredSession:PhotoSessionSnapshot?=null
 private var pendingDestination:Uri?=null
 private var exportEncoding=SafePhotoCopy.Encoding.PNG
 private var exportQuality=95
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
 if(restoredSession!=null){exportEncoding=SafePhotoCopy.Encoding.values().getOrNull(savedInstanceState?.getInt("photoEncoding",0)?:0)?:SafePhotoCopy.Encoding.PNG;exportQuality=(savedInstanceState?.getInt("photoQuality",95)?:95).coerceIn(1,100)}
 window.statusBarColor=Color.WHITE;window.navigationBarColor=if(android.os.Build.VERSION.SDK_INT>=26)Color.WHITE else Color.BLACK
 window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if(android.os.Build.VERSION.SDK_INT>=26)View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);setPadding(16.dp(),4.dp(),16.dp(),8.dp())}
 fun row(parent:LinearLayout=root)=LinearLayout(this).also{parent.addView(it)}
 fun button(parent:LinearLayout,label:String,action:()->Unit):Button {
  val control=Button(this).apply{text=label;contentDescription=label;EditorChrome.style(this);setOnClickListener{action()}}
  parent.addView(control,LinearLayout.LayoutParams(0,48.dp(),1f).apply{setMargins(2.dp(),2.dp(),2.dp(),2.dp())})
  return control
 }
 val top=row()
 button(top,"‹"){finish()}.apply{contentDescription="Close photo editor";layoutParams=LinearLayout.LayoutParams(48.dp(),48.dp())}
 top.addView(TextView(this).apply{text="Photo editor";textSize=16f;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.BLACK);gravity=android.view.Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,48.dp(),1f))
 button(top,"Tools"){openInspector()}.apply{layoutParams=LinearLayout.LayoutParams(64.dp(),48.dp())}
 val export=button(top,"Export"){chooseExport()}
 export.apply{contentDescription="Save copy of photo";layoutParams=LinearLayout.LayoutParams(88.dp(),48.dp());setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.BLACK);cornerRadius=8.dp().toFloat()}}
 image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(0xFFFAFAFA.toInt());setPadding(4.dp(),12.dp(),4.dp(),12.dp())}
 root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
 image.setOnTouchListener { _,event ->
  val light=placingLight
  if(light==null)false else {
   if(event.action==android.view.MotionEvent.ACTION_UP){
    val input=bitmap;val shown=rendered
    if(placingOn!=edit||before||exportEdit!=null){placingLight=null;placingOn=null;note.text="Photo changed. Open local light again."}
    else if(input!=null&&shown!=null&&renderedEdit==edit){
     val inverse=Matrix();val point=floatArrayOf(event.x-image.paddingLeft,event.y-image.paddingTop)
     if(image.imageMatrix.invert(inverse)){
      inverse.mapPoints(point)
      if(point[0] in 0f..shown.width.toFloat() && point[1] in 0f..shown.height.toFloat()){
       val center=PhotoToneGeometry(input.width,input.height,edit).normalizedSourcePoint(point[0],point[1])
       tool("local_light");change(edit.copy(radialLight=light.copy(centerX=center.x,centerY=center.y)))
      }else note.text="Tap inside the photo to place the light."
     }
    }else note.text="Wait for the edited preview, then tap again."
    image.performClick()
   }
   true
  }
 }
 val transport=row()
 button(transport,"Compare"){before=!before;preview()}
 button(transport,"Undo"){if(undo.isNotEmpty()){tool("undo");redo.add(edit);edit=undo.removeAt(undo.lastIndex);preview()}}
 button(transport,"Redo"){if(redo.isNotEmpty()){tool("redo");undo.add(edit);edit=redo.removeAt(redo.lastIndex);preview()}}
 note=TextView(this).apply{setTextColor(Color.DKGRAY);text="Loading original…";textSize=11f;maxLines=2;setPadding(4.dp(),8.dp(),4.dp(),8.dp());accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};root.addView(note)
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
  val control=Button(this).apply{text=name;EditorChrome.style(this,true);setTextColor(Color.DKGRAY);setOnClickListener{activeTool=i;groups.forEachIndexed{index,view->view.visibility=if(index==i)View.VISIBLE else View.GONE};toolButtons.forEachIndexed{index,view->view.isSelected=index==i;view.setTextColor(if(index==i)Color.BLACK else Color.GRAY)}}};toolButtons.add(control);rail.addView(control,LinearLayout.LayoutParams(92.dp(),72.dp()))
 }
 val transformRow=row(transforms)
 button(transformRow,"Rotate"){tool("rotate");change(edit.copy(rotation=(edit.rotation+90)%360))}
 button(transformRow,"Crop center"){tool("crop");change(edit.copy(crop=if(edit.crop>.51f)edit.crop-.1f else 1f))}
 val framing=row(transforms)
 button(framing,"Aspect ratio"){chooseRatio()}
 button(framing,"Mirror"){chooseMirror()}
 val finishRow=row(transforms)
 button(finishRow,"Monochrome"){tool("monochrome");change(edit.copy(saturation=0f))}
 button(finishRow,"Reset"){tool("reset");change(PhotoEdit())}
 val toneRow=row(adjustments)
 button(toneRow,"Tone curve"){showCurve()}
 button(toneRow,"Local light"){showLocalLight()}
 val finishTools=row(adjustments)
 button(finishTools,"White balance"){finishAdjust("White balance",0)}
 button(finishTools,"Shadows / highlights"){finishAdjust("Shadows / highlights",1)}
 val detailTools=row(adjustments)
 button(detailTools,"Vignette"){finishAdjust("Vignette",2)}
 button(detailTools,"Sharpen"){finishAdjust("Sharpen",3)}
 button(row(transforms),"Add text"){editCaption()}
 button(row(assist),"AI scene enhance"){enhanceScene()}
 button(row(assist),"AI photo looks"){chooseLook()}
 button(row(assist),"Cut out person (beta)"){cutOutPerson()}
 button(row(assist),"AI person background"){chooseBackground()}
 button(row(assist),"Read text"){readText()}
 val tabs=row();val tabButtons=mutableListOf<Button>()
 fun selectCategory(name:String){
  categories.forEach{(key,content)->(content.parent as View).visibility=if(key==name)View.VISIBLE else View.GONE}
  tabButtons.forEach{control->val selected=control.text.toString()==name;control.isSelected=selected;control.setTextColor(Color.BLACK);control.background=GradientDrawable().apply{setColor(if(selected)0xFFF1F1F1.toInt() else Color.WHITE);cornerRadius=14.dp().toFloat()}}
 }
 categories.keys.forEach{name->tabButtons.add(button(tabs,name){selectCategory(name)}.apply{EditorChrome.style(this,true);layoutParams=LinearLayout.LayoutParams(0,72.dp(),1f)})}
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
 private fun change(next:PhotoEdit){placingLight=null;placingOn=null;undo.add(edit);if(undo.size>50)undo.removeAt(0);redo.clear();edit=next;before=false;preview()}
 private fun decode(limit:Int):Bitmap{val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,bounds)};require(bounds.outWidth>0){"Unsupported image"};val runtime=Runtime.getRuntime();val headroom=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory());require(headroom>8L*1024*1024){"Not enough memory. Close other tools and try again."};val low=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice;val maxPixels=(headroom/24).coerceAtMost(if(low)1_500_000L else 6_000_000L);val sample=PhotoDecodeBudget.sampleSize(bounds.outWidth,bounds.outHeight,limit,maxPixels);val options=BitmapFactory.Options().apply{inSampleSize=sample};val decoded=contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,options)}?:error("Unable to decode image");val orientation=runCatching{contentResolver.openInputStream(source).use{android.media.ExifInterface(requireNotNull(it)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1);val matrix=Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)};if(orientation==1)return decoded;val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true);if(oriented!==decoded)decoded.recycle();return oriented}
 private fun render(input:Bitmap,e:PhotoEdit):Bitmap=PhotoRasterRenderer.render(input,e,personMask)
 private fun preview() {
  listOf((edit.brightness/1.28f+100).toInt(),(edit.contrast*100).toInt(),(edit.saturation*100).toInt(),(edit.exposure*100+200).toInt()).forEachIndexed{i,v->sliders.getOrNull(i)?.progress=v}
  val input=bitmap?:return
  val state=if(before)PhotoEdit()else edit
  if(state.needsPersonMask&&personMask==null){note.text="Restoring person cutout on this device…";cutOutPerson(true);return}
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
      image.setImageBitmap(result);renderedEdit=state;old?.recycle()
      if(!readingText&&!cuttingOut&&exportEdit==null)note.text=if(before)"Original preview" else if(state.background!=null)"Person background · check hair and edges before saving a copy" else if(state.personCutout)"Person cutout · check edges before saving a PNG copy" else "Edited preview · original preserved"
     }else result.recycle()
    }
   }catch(e:OutOfMemoryError){
    runOnUiThread{if(request==revision&&!isDestroyed)note.text="Not enough memory for this preview. Try again."}
   }
  }
 }
 private fun openInspector(){
  if(!canFinish())return
  fun t(label:String,run:()->Unit)=NativeInspectorPanel.Tool(label,run)
  NativeInspectorPanel.show(this,"Photo inspector","${bitmap?.width} × ${bitmap?.height} px · ${edit.rotation}° · edits save as a new copy",listOf(
   "Framing" to listOf(t("Aspect ratio"){chooseRatio()},t("Mirror"){chooseMirror()}),
   "Color & light" to listOf(t("White balance"){finishAdjust("White balance",0)},t("Shadows / highlights"){finishAdjust("Shadows / highlights",1)},t("Tone curve"){showCurve()},t("Local light"){showLocalLight()}),
   "Finish" to listOf(t("Vignette"){finishAdjust("Vignette",2)},t("Sharpen"){finishAdjust("Sharpen",3)},t("Text overlay"){editCaption()}),
   "On-device AI" to listOf(t("Photo looks"){chooseLook()},t("Person background"){chooseBackground()},t("Person cutout"){cutOutPerson()},t("Read text"){readText()}),
   "Output" to listOf(t("Export PNG / JPEG"){chooseExport()})
  ))
 }
 private fun canFinish()=bitmap!=null&&exportEdit==null&&!enhancing&&!cuttingOut&&!isFinishing
 private fun chooseRatio(){if(!canFinish())return;val base=edit
  android.app.AlertDialog.Builder(this).setTitle("Photo aspect ratio").setItems(arrayOf("Original","Square 1:1","Portrait 4:5","Wide 16:9","Vertical 9:16")){_,i->if(edit==base&&canFinish())change(edit.copy(finish=edit.finish.copy(ratio=listOf(0f,1f,.8f,16f/9,9f/16)[i])))}.setNegativeButton("Cancel",null).show()
 }
 private fun chooseMirror(){if(!canFinish())return;val base=edit
  android.app.AlertDialog.Builder(this).setTitle("Mirror photo").setItems(arrayOf("Flip horizontally","Flip vertically","Reset mirrors")){_,i->if(edit==base&&canFinish())change(edit.copy(finish=when(i){0->edit.finish.copy(mirrorX=!edit.finish.mirrorX);1->edit.finish.copy(mirrorY=!edit.finish.mirrorY);else->edit.finish.copy(mirrorX=false,mirrorY=false)}))}.setNegativeButton("Cancel",null).show()
 }
 private fun finishAdjust(title:String,kind:Int){if(!canFinish())return;val base=edit
  PhotoFinishControls.adjust(this,title,base.finish,kind){f->if(edit==base&&canFinish())change(edit.copy(finish=f))else note.text="Photo changed. Open the tool again."}
 }
 private fun editCaption(){if(!canFinish())return;val base=edit
  PhotoFinishControls.caption(this,base.finish.caption){caption->if(edit==base&&canFinish())change(edit.copy(finish=edit.finish.copy(caption=caption)))else note.text="Photo changed. Open text again."}
 }
 private fun chooseExport(){if(!canFinish())return
  android.app.AlertDialog.Builder(this).setTitle("Export photo").setItems(arrayOf("PNG · transparency","JPEG · compact (70%)","JPEG · balanced (85%)","JPEG · high quality (95%)")){_,i->
   if(!canFinish())return@setItems
   exportEncoding=if(i==0)SafePhotoCopy.Encoding.PNG else SafePhotoCopy.Encoding.JPEG;exportQuality=listOf(100,70,85,95)[i]
   exportEdit=edit;waitingForPicker=true
   try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=exportEncoding.mime;putExtra(Intent.EXTRA_TITLE,"Honorable-copy.${exportEncoding.extension}")},20)}catch(e:Exception){waitingForPicker=false;exportEdit=null;note.text="No app is available to save a copy."}
  }.setNegativeButton("Cancel",null).show()
 }
 private fun chooseBackground(){
  if(bitmap==null||exportEdit!=null||cuttingOut||enhancing||readingText)return
  val base=edit;val request=revision
  fun commit(background:PhotoBackground?){
   if(edit!=base||request!=revision||exportEdit!=null){note.text="Photo changed. Open person background again.";return}
   if(background==null){tool("person_background");change(edit.copy(personCutout=false,background=null))}else cutOutPerson(background=background)
  }
  val names=arrayOf("Blur background","White background","Black background","Gray background","Restore original background")
  android.app.AlertDialog.Builder(this).setTitle("AI person background · beta").setItems(names){_,which->
   when(which){
    0->{var strength=edit.background?.strength?:45
     val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20.dp(),12.dp(),20.dp(),12.dp())}
     val label=TextView(this).apply{text="Blur strength: $strength%";setTextColor(Color.BLACK)};panel.addView(label)
     panel.addView(TextView(this).apply{text="People only · processed on this device. Check hair and edges before exporting.";setTextColor(Color.DKGRAY)})
     panel.addView(SeekBar(this).apply{max=99;progress=strength-1;contentDescription="Background blur strength";progressTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);thumbTintList=android.content.res.ColorStateList.valueOf(Color.BLACK)
      setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){strength=v+1;label.text="Blur strength: $strength%"}})},LinearLayout.LayoutParams(-1,48.dp()))
     android.app.AlertDialog.Builder(this).setTitle("Blur person background").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->commit(PhotoBackground(strength=strength))}.show()
    }
    1->commit(PhotoBackground(PhotoBackgroundMode.SOLID,color=Color.WHITE))
    2->commit(PhotoBackground(PhotoBackgroundMode.SOLID,color=Color.BLACK))
    3->commit(PhotoBackground(PhotoBackgroundMode.SOLID,color=0xFF808080.toInt()))
    else->commit(null)
   }
  }.setNegativeButton("Cancel",null).show()
 }
 private fun cutOutPerson(recover:Boolean=false,background:PhotoBackground?=null) {
  val input=bitmap?:return
  if(cuttingOut||readingText||enhancing||(!recover&&exportEdit!=null)||isFinishing)return
  fun applyPersonEdit(){tool(if(background==null)"person_cutout" else "person_background");change(edit.copy(personCutout=background==null,background=background))}
  if(personMask!=null){if(recover){preview();resumePendingExport()}else applyPersonEdit();return}
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
       else if(request==revision)applyPersonEdit()
       else note.text="Person mask ready. Open the person tools to apply it to your current edit."
      }
     }.onFailure{owned.recycle();cuttingOut=false;if(recover){exportEdit=null;pendingDestination=null};note.text="Person cutout unavailable. Try again."}
    }
   }catch(error:Throwable){runOnUiThread{cuttingOut=false;if(recover){exportEdit=null;pendingDestination=null};if(!isDestroyed)note.text="Not enough resources for person cutout. Try a smaller photo."}}
  }
 }
 private fun showCurve(){
  if(bitmap==null||exportEdit!=null||enhancing||cuttingOut)return
  val base=edit;val request=revision
  PhotoToneControls.curve(this,base.curve){curve->if(edit==base&&request==revision){tool("tone_curve");change(edit.copy(curve=curve))}else note.text="Photo changed. Open the curve tool again."}
 }
 private fun showLocalLight(){
  if(bitmap==null||exportEdit!=null||enhancing||cuttingOut)return
  val base=edit;val request=revision
  fun unchanged()=edit==base&&request==revision&&exportEdit==null
  PhotoToneControls.light(this,base.radialLight,{light->if(unchanged()){tool("local_light");change(edit.copy(radialLight=light))}else note.text="Photo changed. Open local light again."},{light->
   if(unchanged()){before=false;preview();placingLight=light;placingOn=edit;note.text="Tap the edited photo to place the local light."}
  })
 }
 private fun chooseLook(){
  if(bitmap==null||enhancing||cuttingOut||exportEdit!=null)return
  val names=arrayOf("Auto scene look","Cinematic","Soft portrait","Vivid","Monochrome")
  android.app.AlertDialog.Builder(this).setTitle("AI-assisted photo look").setItems(names){_,which->enhanceScene(PhotoLook.values()[which])}.setNegativeButton("Cancel",null).show()
 }
 private fun enhanceScene(look:PhotoLook=PhotoLook.AUTO) {
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
    val pixels=IntArray(32*32){index->owned.getPixel(((index%32+.5f)*owned.width/32).toInt().coerceAtMost(owned.width-1),((index/32+.5f)*owned.height/32).toInt().coerceAtMost(owned.height-1))}
    val next=runCatching{PhotoAutoLook.apply(initial,PhotoLightStats.measure(pixels),scene.toSet(),look)}.getOrElse{note.text="No visible photo content to analyze. Try manual adjustments.";return@addOnSuccessListener}
    tool("ai_scene_enhance");change(next)
    note.text="Scene-aware tone and color look applied on device. Compare before saving a copy."
   }
   .addOnFailureListener { if(!isDestroyed)note.text="Scene analysis failed. Try manual adjustments." }
   .addOnCompleteListener { enhancing=false;labeler.close();owned.recycle() }
  }catch(e:Exception){enhancing=false;labeler.close();owned.recycle();note.text="Scene analysis failed. Try manual adjustments."}
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
  if(state.needsPersonMask&&personMask==null){cutOutPerson(true);return}
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
    SafePhotoCopy.save(this,source,destination,result,exportEncoding,exportQuality)
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
  outState.putInt("photoEncoding",exportEncoding.ordinal);outState.putInt("photoQuality",exportQuality)
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
