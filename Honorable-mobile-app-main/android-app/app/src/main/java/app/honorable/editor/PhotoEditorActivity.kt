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
private typealias PhotoCopyTransform = (Bitmap, () -> Unit) -> Bitmap

class PhotoEditorActivity:Activity(){
 companion object {
  const val START_LOOK_EXTRA="app.honorable.editor.START_LOOK"
  const val CANVAS_PROJECT_EXTRA="app.honorable.editor.CANVAS_PROJECT"
  const val QUICK_START_EXTRA="app.honorable.editor.QUICK_START"
 }
 private var accessMode:EditorAccessMode?=null
 private var startLook:PhotoLook?=null
 private var quickStart:PhotoQuickStart?=null
 private var protectedOriginals:List<Uri> = emptyList()
 private var guestInstance:String?=null
 private var canvasProjectId:String?=null
 private var canvasProjectName="Untitled image"
 private var renderedEdit:PhotoEdit?=null
 private var placingLight:PhotoRadialLight?=null
 private var placingOn:PhotoEdit?=null
 private var placingScope:String?=null
 private var activeTool=-1
 private var restoredSession:PhotoSessionSnapshot?=null
 private var photoRecovery:PhotoSessionRecovery?=null
 private var recoveryExportInvalidated=false
 @Volatile private var photoLoading=false
 private var photoRecoveryPanel:LinearLayout?=null
 private var photoRecoveryRetry:Button?=null
 private var photoRecoveryStart:Button?=null
 private var photoRecoveryDiscard:android.app.AlertDialog?=null
 private var photoRecoveryDiscardToken:Any?=null
 private var recoveryToolSelection:((Int)->Unit)?=null
 private var photoRestoreReviewDialog:android.app.AlertDialog?=null
 private var photoRestoreReviewToken:Any?=null
 private var photoCloseDialog:android.app.AlertDialog?=null
 private var photoCloseToken:Any?=null
 private var photoSettingsDialog:android.app.AlertDialog?=null
 private var photoSettingsToken:Any?=null
 private var photoVersions:PhotoVersionShelf?=null
 private var photoObjectArrange:PhotoObjectArrangeControls?=null
 private var photoTextReplace:PhotoTextReplaceControls?=null
 private var photoDestructiveDialog:android.app.AlertDialog?=null
 private var photoDestructiveToken:Any?=null
 private var releasePhotoBack:(()->Unit)?=null
 private var pendingDestination:Uri?=null
 private var exportEncoding=SafePhotoCopy.Encoding.PNG
 private var exportQuality=95
 @Volatile private var activityClosed=false
 @Volatile private var photoProjectBusy=false
 private var exportInProgress=false
 @Volatile private var photoExport:PhotoExportOperation?=null
 private var photoExportNotice:Pair<PhotoExportOperation,String>?=null
 private var waitingForPicker=false
 private var loadedScope:String?=null
 private fun currentScope():String?=runCatching {
  accessMode?.lifecycleScope(source.toString(),guestInstance,readAccountScope=readScope@{
   val session=app.honorable.auth.SecureAccountStore(this).read()?:return@readScope null
   if(session.optLong("expiresAt")<=System.currentTimeMillis())return@readScope null
   val account=session.optJSONObject("cached")?.optJSONObject("account")?.optString("accountId").orEmpty()
   val api=session.optString("apiUrl")
   if(account.isBlank()||!api.startsWith("https://"))return@readScope null
   PhotoSessionCodec.scope(account,api,source.toString())
  })
 }.getOrNull()
 private val textReadGate=PhotoTextReadGate()
 private var readingText=false;private var enhancing=false;private var cuttingOut=false;private var exportEdit:PhotoEdit?=null
 @Volatile private var personMask:Bitmap?=null
 private var cutoutJob:AutoCloseable?=null
 private val openedAt=System.currentTimeMillis()
 private fun emit(type:String,metadata:JSONObject=JSONObject()){if(accessMode==EditorAccessMode.ACCOUNT)SafeBetaTelemetry.emit(this,type,metadata)}
 private fun tool(name:String){emit("edit_tool_used",JSONObject().put("tool",name))}
 private fun authorizeEditor(){requireNotNull(accessMode).authorizeAccount{SafeBetaTelemetry.authorizeEditor(this)}}
 private fun allowOnDeviceAi():Boolean {
  if(photoRecovery!=null){photoRecoveryNotice();return false}
  if(accessMode?.allowsOnDeviceAi==true)return true
  note.text="On-device editing tools are unavailable in this editor session."
  return false
 }
 private val sliders=mutableListOf<SeekBar>();private var edit=PhotoEdit();private var undo=mutableListOf<PhotoEdit>();private var redo=mutableListOf<PhotoEdit>();private var before=false
 private lateinit var image:ImageView;private lateinit var note:TextView;private lateinit var source:Uri
 @Volatile private var copyForeground=false
 @Volatile private var copyGeneration=0L
 private var bitmap:Bitmap?=null;private var rendered:Bitmap?=null;private val worker=Executors.newSingleThreadExecutor();@Volatile private var revision=0
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
 accessMode=runCatching{EditorAccessMode.fromExtra(intent.getStringExtra(EditorAccessMode.EXTRA))}.getOrElse{finish();return}
 if(accessMode==EditorAccessMode.GUEST_BASIC)guestInstance=savedInstanceState?.getString("guestEditorInstance")?.takeIf{it.matches(Regex("[0-9a-f-]{36}"))}?:java.util.UUID.randomUUID().toString()
 val recoveredCanvasSource=if(accessMode==EditorAccessMode.GUEST_BASIC&&savedInstanceState?.getString("canvasProjectId")!=null)savedInstanceState?.getString("canvasSourceUri")else null
 source=Uri.parse(recoveredCanvasSource?:intent.getStringExtra("uri")?:run{finish();return})
 canvasProjectId=(savedInstanceState?.getString("canvasProjectId")?:intent.getStringExtra(CANVAS_PROJECT_EXTRA))?.also{if(!CanvasProjectCodec.validId(it)||accessMode!=EditorAccessMode.GUEST_BASIC){finish();return}}
 canvasProjectName=savedInstanceState?.getString("canvasProjectName")?:"Untitled image"
 startLook=runCatching{(if(savedInstanceState?.containsKey("pendingStartLook")==true)savedInstanceState.getString("pendingStartLook") else intent.getStringExtra(START_LOOK_EXTRA))?.let{PhotoLook.valueOf(it)}}.getOrNull()
 quickStart=runCatching{(if(savedInstanceState?.containsKey("pendingQuickStart")==true)savedInstanceState.getString("pendingQuickStart") else intent.getStringExtra(QUICK_START_EXTRA))?.let{PhotoQuickStart.valueOf(it)}}.getOrNull()
 protectedOriginals=runCatching{ImportedPhotoOriginals.validate(((intent.getStringArrayListExtra(ImportedPhotoOriginals.EXTRA)?.toList()?:emptyList())+(savedInstanceState?.getStringArrayList("protectedPhotoOriginals")?.toList()?:emptyList())).distinct()).map(Uri::parse)}.getOrElse{finish();return}
 restorePhotoRecovery(savedInstanceState?.getByteArray("photoSession"),savedInstanceState?.getBoolean("photoRecoveryExportInvalidated",false)?:false)
 if(restoredSession!=null){exportEncoding=SafePhotoCopy.Encoding.values().getOrNull(savedInstanceState?.getInt("photoEncoding",0)?:0)?:SafePhotoCopy.Encoding.PNG;exportQuality=(savedInstanceState?.getInt("photoQuality",95)?:95).coerceIn(1,100)}
 window.statusBarColor=Color.WHITE;window.navigationBarColor=if(android.os.Build.VERSION.SDK_INT>=26)Color.WHITE else Color.BLACK
 window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if(android.os.Build.VERSION.SDK_INT>=26)View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);setPadding(16.dp(),4.dp(),16.dp(),8.dp())}
 fun row(parent:LinearLayout=root)=LinearLayout(this).also{parent.addView(it)}
 fun button(parent:LinearLayout,label:String,action:()->Unit):Button {
  val control=Button(this).apply{text=label;contentDescription=label;EditorChrome.style(this);setOnClickListener{if(photoRecovery!=null)photoRecoveryNotice()else if(!photoProjectBusy)action()}}
  parent.addView(control,LinearLayout.LayoutParams(0,48.dp(),1f).apply{setMargins(2.dp(),2.dp(),2.dp(),2.dp())})
  return control
 }
 val top=row()
 button(top,"‹"){requestPhotoClose()}.apply{contentDescription="Close photo editor";layoutParams=LinearLayout.LayoutParams(48.dp(),48.dp());setOnClickListener{requestPhotoClose()}}
 top.addView(TextView(this).apply{text="Photo editor";textSize=16f;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.BLACK);gravity=android.view.Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(0,48.dp(),1f))
 button(top,"Tools"){openInspector()}.apply{layoutParams=LinearLayout.LayoutParams(64.dp(),48.dp())}
 val export=button(top,"Export"){chooseExport()}
 export.apply{contentDescription="Save copy of photo";layoutParams=LinearLayout.LayoutParams(88.dp(),48.dp());setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.BLACK);cornerRadius=8.dp().toFloat()}}
 image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(0xFFFAFAFA.toInt());setPadding(4.dp(),12.dp(),4.dp(),12.dp())}
 root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
 image.setOnTouchListener { _,event ->
  val light=placingLight
  if(light==null&&pickingObjectOn==null){placementTap?.cancel();false} else {
   if(event.actionMasked==android.view.MotionEvent.ACTION_CANCEL){clearObjectPick();placingLight=null;placingOn=null;placingScope=null}
   val released=placementTapReleased(event)
   if(light==null){if(released)pickObjectTouch(event);true} else {
   if(released){
    val input=bitmap;val shown=rendered
    if(!canFinish()||isDestroyed||placingOn!=edit||before||placingScope==null||loadedScope!=placingScope||!runCatching{currentScope()==placingScope}.getOrDefault(false)){placingLight=null;placingOn=null;placingScope=null;note.text="Photo changed. Open local light again."}
    else if(input!=null&&shown!=null&&renderedEdit==edit){
     val inverse=Matrix();val point=floatArrayOf(event.x-image.paddingLeft,event.y-image.paddingTop)
     if(image.imageMatrix.invert(inverse)){
      inverse.mapPoints(point)
      val bounds=image.drawable?.bounds
      val mapped=bounds?.takeIf{it.width()>0&&it.height()>0}?.let{PhotoPreviewCoordinates.bitmapPoint(shown.width,shown.height,it.width(),it.height(),point[0]-it.left,point[1]-it.top)}
      if(mapped!=null){
       val center=PhotoLightPlacement.normalizedSourcePoint(input.width,input.height,edit,mapped.x,mapped.y)
       if(center==null)note.text="Tap the photo content, outside added frames and margins."
       else {tool("local_light");change(edit.copy(radialLight=light.copy(centerX=center.x,centerY=center.y)))}
      }else note.text="Tap inside the photo to place the light."
     }
    }else note.text="Wait for the edited preview, then tap again."
    image.performClick()
   }
   true
   }
  }
 }
 val transport=row()
 button(transport,"Compare"){clearObjectPick();placingLight=null;placingOn=null;placingScope=null;before=!before;preview()}
 button(transport,"Undo"){if(undo.isNotEmpty()){tool("undo");navigateHistory(false)}}
 button(transport,"Redo"){if(redo.isNotEmpty()){tool("redo");navigateHistory(true)}}
 note=TextView(this).apply{setTextColor(Color.DKGRAY);text="Loading original…";textSize=11f;maxLines=2;setPadding(4.dp(),8.dp(),4.dp(),8.dp());accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};root.addView(note)
 photoRecoveryPanel=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL;setPadding(4.dp(),4.dp(),4.dp(),4.dp())
  addView(TextView(this@PhotoEditorActivity).apply{text="Previous photo edits have not recovered yet. Editing and saving are paused. Retry, or discard recovery to start from the original. Saved projects and original media are kept.";setTextColor(Color.BLACK);textSize=12f})
  val actions=LinearLayout(this@PhotoEditorActivity);addView(actions)
  photoRecoveryRetry=Button(this@PhotoEditorActivity).apply{text="Retry recovery";contentDescription="Retry previous photo session recovery";EditorChrome.style(this);setOnClickListener{retryPhotoRecovery()}}
  photoRecoveryStart=Button(this@PhotoEditorActivity).apply{text="Start original";contentDescription="Review discarding unrecovered photo edits and start from original";EditorChrome.style(this);setOnClickListener{requestPhotoRecoveryDiscard()}}
  actions.addView(photoRecoveryRetry,LinearLayout.LayoutParams(0,48.dp(),1f));actions.addView(photoRecoveryStart,LinearLayout.LayoutParams(0,48.dp(),1f))
 };root.addView(photoRecoveryPanel)
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
  val label=TextView(this).apply{text="$name: ${adjustmentValue(name,value)}";setTextColor(Color.BLACK);textSize=14f};group.addView(label)
  group.addView(SeekBar(this).apply{this.max=max;progress=value;contentDescription=name;sliders.add(this);progressTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);thumbTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);if(android.os.Build.VERSION.SDK_INT>=30)stateDescription=adjustmentValue(name,value);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){label.text="$name: ${adjustmentValue(name,v)}";if(android.os.Build.VERSION.SDK_INT>=30)s.stateDescription=adjustmentValue(name,v)};override fun onStopTrackingTouch(s:SeekBar){tool(name.lowercase());change(update(s.progress))}})},LinearLayout.LayoutParams(-1,48.dp()))
 }
 adjust("Brightness",200,100){edit.copy(brightness=(it-100)*1.28f)};adjust("Contrast",200,100){edit.copy(contrast=it/100f)};adjust("Saturation",200,100){edit.copy(saturation=it/100f)};adjust("Exposure",400,200){edit.copy(exposure=(it-200)/100f)}
 val toolScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false};val rail=LinearLayout(this);toolScroll.addView(rail);adjustments.addView(toolScroll)
 val toolButtons=mutableListOf<Button>()
 listOf("Brightness","Contrast","Color","Exposure").forEachIndexed{i,name->
  val control=Button(this).apply{text=name;EditorChrome.style(this,true);setTextColor(Color.DKGRAY);setOnClickListener{activeTool=i;groups.forEachIndexed{index,view->view.visibility=if(index==i)View.VISIBLE else View.GONE};toolButtons.forEachIndexed{index,view->view.isSelected=index==i;view.setTextColor(if(index==i)Color.BLACK else Color.GRAY)}}};toolButtons.add(control);rail.addView(control,LinearLayout.LayoutParams(92.dp(),72.dp()))
 }
 val transformRow=row(transforms)
 button(transformRow,"Rotate"){tool("rotate");change(edit.copy(rotation=(edit.rotation+90)%360))}
 button(row(transforms),"Trim transparency"){createTrimmedCopy()}
 button(row(transforms),"Make outlined sticker"){createStickerCopy()}
 button(transformRow,"Crop center"){tool("crop");change(edit.copy(crop=if(edit.crop>.51f)edit.crop-.1f else 1f))}
 val framing=row(transforms)
 button(framing,"Aspect ratio"){chooseRatio()}
 button(framing,"Mirror"){chooseMirror()}
 val finishRow=row(transforms)
 button(finishRow,"Monochrome"){tool("monochrome");change(edit.copy(saturation=0f))}
 button(finishRow,"Reset"){confirmPhotoReset()}
 val toneRow=row(adjustments)
 button(toneRow,"Tone curve"){showCurve()}
 button(toneRow,"Local light"){showLocalLight()}
 val finishTools=row(adjustments)
 button(finishTools,"White balance"){finishAdjust("White balance",0)}
 button(finishTools,"Shadows / highlights"){finishAdjust("Shadows / highlights",1)}
 val detailTools=row(adjustments)
 button(detailTools,"Vignette"){finishAdjust("Vignette",2)}
 button(detailTools,"Sharpen"){finishAdjust("Sharpen",3)}
 button(row(adjustments),"Saved looks"){showSavedLooks()}
 button(row(adjustments),"Creative color"){chooseCreative()}
 button(row(adjustments),"Black-and-white mix"){manageMonochrome()}
 button(row(adjustments),"Photo effects"){chooseEffects()}
 button(row(adjustments),"Create & compose"){chooseComposition()}
 button(row(transforms),"Add text"){editCaption()}
 button(row(assist),"AI scene enhance"){enhanceScene()}.isEnabled=accessMode?.allowsOnDeviceAi==true
 button(row(assist),"AI photo looks"){chooseLook()}.isEnabled=accessMode?.allowsOnDeviceAi==true
 button(row(assist),"Cut out person (beta)"){cutOutPerson()}.isEnabled=accessMode?.allowsOnDeviceAi==true
 button(row(assist),"AI person background"){chooseBackground()}.isEnabled=accessMode?.allowsOnDeviceAi==true
 button(row(assist),"AI upscale copy"){openNeuralUpscaleCopy()}.isEnabled=accessMode?.allowsOnDeviceAi==true
 button(row(assist),"AI subject asset pack"){openSubjectAssetPack()}.isEnabled=accessMode?.allowsOnDeviceAi==true
 button(row(assist),"Read text"){readText()}.isEnabled=accessMode?.allowsOnDeviceAi==true
 val tabs=row();val tabButtons=mutableListOf<Button>()
 fun selectCategory(name:String){
  categories.forEach{(key,content)->(content.parent as View).visibility=if(key==name)View.VISIBLE else View.GONE}
  tabButtons.forEach{control->val selected=control.text.toString()==name;control.isSelected=selected;control.setTextColor(Color.BLACK);control.background=GradientDrawable().apply{setColor(if(selected)0xFFF1F1F1.toInt() else Color.WHITE);cornerRadius=14.dp().toFloat()}}
 }
 val directTools=listOf<Pair<String,()->Unit>>(
  "Light" to {selectCategory("Adjust");toolButtons.first().performClick()},
  "Crop" to {chooseRatio()}, "Rotate" to {tool("rotate");change(edit.copy(rotation=(edit.rotation+90)%360))},
  "Mirror" to {chooseMirror()}, "Color" to {finishAdjust("White balance",0)},
  "Curves" to {showCurve()}, "Saved looks" to {showSavedLooks()}, "People" to {chooseBackground()}, "Text" to {editCaption()},
  "Framing" to {selectCategory("Transform")}, "AI" to {selectCategory("Assist")}, "More" to {openInspector()})
 directTools.forEach{(name,action)->val b=Button(this).apply{text=name;contentDescription="$name photo tool";EditorChrome.style(this,true);isEnabled=accessMode?.allowsOnDeviceAi==true||name !in setOf("AI","People");setOnClickListener{if(photoRecovery!=null)photoRecoveryNotice()else if(!photoProjectBusy)action()}};tabs.addView(b,LinearLayout.LayoutParams(72.dp(),72.dp()))}
 root.removeView(tabs)
 root.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(tabs)})
 selectCategory("Adjust");toolButtons.first().performClick()

 recoveryToolSelection={index->toolButtons.getOrNull(index)?.performClick()}
 setContentView(root)
 refreshPhotoRecoveryPanel()
 runCatching{PhotoEditorBack.install(this){requestPhotoClose()}}.onSuccess{releasePhotoBack=it}.onFailure{note.text="System Back protection is unavailable. Use the editor Close button to review edits before leaving."}
 NativeWorkspaceInsets.install(this,root)
 emit("editor_opened",JSONObject().put("mediaType","IMAGE"));
 val openingOriginals=protectedOriginals.map{it.toString()}
 val openingScope=currentScope()?:run{note.text="Session changed. Reopen this photo.";return}
 photoLoading=true;refreshPhotoRecoveryPanel()
 worker.execute{try{authorizeEditor();check(photoLoadCurrent(openingScope)){"Photo session changed"};val canvasSaved=canvasProjectId?.let{id->
   val store=CanvasProjectStore(java.io.File(filesDir,"canvas-projects"))
   require(accessMode==EditorAccessMode.GUEST_BASIC&&source==androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",store.sourceFile(id))){"Saved project source is unavailable"}
   store.load(id){check(photoLoadCurrent(openingScope)){"Photo session changed"}}
  };val loadedOriginals=ImportedPhotoOriginals.validate((openingOriginals+(canvasSaved?.protectedOriginals?:emptyList())).distinct());check(photoLoadCurrent(openingScope)){"Photo session changed"};val low=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice;if(low)emit("performance_sample",JSONObject().put("metric","PROXY_USE").put("durationMs",0));val loaded=decode(if(low)768 else 1600);runOnUiThread{photoLoading=false;if(photoLoadCurrent(openingScope)){
   protectedOriginals=loadedOriginals.map(Uri::parse)
   bitmap=loaded;loadedScope=openingScope
   canvasSaved?.let{edit=it.edit;canvasProjectName=it.name}
   photoRecovery?.let{pending->runCatching{applyPhotoRecovery(pending,openingScope)}.onFailure{photoRecoveryNotice()}}
   finishPhotoOpening()
  }else{loaded.recycle();if(!activityClosed&&!isFinishing&&!isDestroyed){refreshPhotoRecoveryPanel();note.text="Session changed. Reopen this photo."}}}}catch(e:Exception){runOnUiThread{photoLoading=false;if(!activityClosed&&!isDestroyed&&!isFinishing){refreshPhotoRecoveryPanel();note.text="Unable to open this image. Check access and try again."}}}catch(e:OutOfMemoryError){runOnUiThread{photoLoading=false;if(!activityClosed&&!isDestroyed&&!isFinishing){refreshPhotoRecoveryPanel();note.text="Not enough memory to open this photo. Try a smaller image."}}}}
 }
 private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
 private fun commitPhotoChange(next:PhotoEdit):Boolean {
  if(photoRecovery!=null){photoRecoveryNotice();return false}
  if(photoProjectBusy){note.text="Wait for the photo project operation to finish.";return false}
  clearObjectPick();placingLight=null;placingOn=null;placingScope=null
  if(next!=edit){undo.add(edit);if(undo.size>50)undo.removeAt(0);redo.clear();edit=next}
  before=false;return true
 }
 private fun change(next:PhotoEdit){if(commitPhotoChange(next))preview()}
 private fun adjustmentValue(name:String,progress:Int):String=when(name){
  "Exposure"->String.format(java.util.Locale.ROOT,"%+.2f EV",(progress-200)/100.0)
  "Contrast","Saturation"->"$progress%"
  else->String.format(java.util.Locale.ROOT,"%+d",progress-100)
 }
 private fun canRequestPhotoDestructive()=copyForeground&&canFinish()&&!isDestroyed&&!waitingForPicker&&pendingDestination==null&&::source.isInitialized
 private fun canApprovePhotoDestructive(token:Any,captured:PhotoCloseState,scope:String,mode:EditorAccessMode)=photoDestructiveToken===token&&canRequestPhotoDestructive()&&accessMode==mode&&currentScope()==scope&&captured.stillMatches(photoCloseState())
 private fun closePhotoDestructiveReview(){photoDestructiveToken=null;val dialog=photoDestructiveDialog;photoDestructiveDialog=null;runCatching{dialog?.dismiss()}}
 private fun applyPhotoReset(token:Any,captured:PhotoCloseState,scope:String,mode:EditorAccessMode):Boolean {
  if(!canApprovePhotoDestructive(token,captured,scope,mode))return false
  return commitPhotoChange(PhotoEdit())
 }
 private fun confirmPhotoReset(){
  if(!canRequestPhotoDestructive())return
  if(edit==PhotoEdit()){note.text="This photo already has no edits to reset.";return}
  showPhotoDestructiveReview(false)
 }
 private fun showPhotoDestructiveReview(delete:Boolean){
  if(!canRequestPhotoDestructive())return
  val scope=photoControlScope()?:return;val mode=accessMode?:return;val captured=photoCloseState()
  closePhotoDestructiveReview();val token=Any()
  val dialog=android.app.AlertDialog.Builder(this).setTitle(if(delete)"Delete saved photo edits?"else"Reset photo edits?")
   .setMessage(if(delete)"Deletes the saved editable project for this original in the current local guest or account shelf. The original photo and current edits are kept. Named photo versions are kept."
    else"Removes adjustments, crop, rotation, mirroring, cutout, transparency fade, composition, captions and added objects from this edit. Returns to the source image for this workspace. Original media and saved projects are kept. Undo restores the previous edit.")
   .setNegativeButton("Keep current"){_,_->if(photoDestructiveToken===token)closePhotoDestructiveReview()}
   .setPositiveButton(if(delete)"Delete saved edits"else"Reset edits"){_,_->
    if(photoDestructiveToken!==token)return@setPositiveButton
    val approved=canApprovePhotoDestructive(token,captured,scope,mode)
    val reset=if(approved&&!delete)applyPhotoReset(token,captured,scope,mode)else false
    closePhotoDestructiveReview()
    if(!approved){if(copyForeground&&!isDestroyed&&!isFinishing)note.text="Photo or session changed. Current edits and saved data were kept. Open the control again.";return@setPositiveButton}
    if(delete)photoProjectOperation("delete")else if(reset){tool("reset");val ready=runCatching{preview()}.isSuccess;note.text=if(ready)"Photo edits reset. Undo restores the previous edit."else"Photo edits reset, but preview is unavailable. Undo restores the previous edit."}
   }.create()
  photoDestructiveToken=token;photoDestructiveDialog=dialog
  dialog.setOnCancelListener{if(photoDestructiveToken===token)closePhotoDestructiveReview()}
  dialog.setOnDismissListener{if(photoDestructiveToken===token){photoDestructiveToken=null;photoDestructiveDialog=null}}
  dialog.show()
 }
 private fun navigateHistory(forward:Boolean){
  if(photoRecovery!=null){photoRecoveryNotice();return}
  if(photoProjectBusy)return
  val from=if(forward)redo else undo
  if(from.isEmpty())return
  clearObjectPick();placingLight=null;placingOn=null;placingScope=null;before=false
  val to=if(forward)undo else redo
  to.add(edit);if(to.size>50)to.removeAt(0)
  edit=from.removeAt(from.lastIndex);preview()
 }
 private fun decode(limit:Int):Bitmap=PhotoSourceDecoder.decode(this,source,limit)
 private fun render(input:Bitmap,e:PhotoEdit):Bitmap=PhotoRasterRenderer.render(input,e,personMask)
 private fun preview() {
  listOf((edit.brightness/1.28f+100).toInt(),(edit.contrast*100).toInt(),(edit.saturation*100).toInt(),(edit.exposure*100+200).toInt()).forEachIndexed{i,v->sliders.getOrNull(i)?.progress=v}
  val input=bitmap?:return
  val state=if(before||photoRecovery!=null)PhotoEdit()else edit
  if(state.needsPersonMask&&personMask==null){note.text="Restoring person cutout on this device…";cutOutPerson(true);return}
  image.background=if(state.personCutout||state.alphaGradient.enabled||input.hasAlpha())TransparencyGridDrawable(12.dp())else null
  val request=++revision
  val started=System.currentTimeMillis()
  worker.execute {
   if(request!=revision)return@execute
   try {
    val result=render(input,state)
    runOnUiThread {
     if(request==revision&&!isDestroyed){
      val old=rendered;rendered=result
      if(accessMode==EditorAccessMode.ACCOUNT)SafeBetaTelemetry.duration(this,"EDITOR_RESPONSE",started)
      image.setImageBitmap(result);renderedEdit=state;old?.recycle()
      if(photoRecovery!=null)photoRecoveryNotice()else if(!photoProjectBusy&&!readingText&&!cuttingOut&&exportEdit==null)note.text=if(placingLight!=null&&placingOn==state)"Tap the edited photo to place the local light." else if(pickingObjectOn==state)"Tap a visible shape or text box. Front object wins; text uses its box." else if(before)"Original preview" else if(state.background!=null)"Person background · check hair and edges before saving a copy" else if(state.personCutout)"Person cutout · check edges before saving a PNG copy" else "Edited preview · original preserved"
     }else result.recycle()
    }
   }catch(e:OutOfMemoryError){
    runOnUiThread{if(request==revision&&!isDestroyed)note.text="Not enough memory for this preview. Try again."}
   }
  }
 }
 private var placementTap:PhotoTapTracker?=null
 private fun placementTapReleased(event:android.view.MotionEvent):Boolean {
  val tracker=placementTap?:PhotoTapTracker(android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()).also{placementTap=it}
  if(event.pointerCount!=1){tracker.cancel();return false}
  val pointer=event.getPointerId(0)
  if(event.actionMasked!=android.view.MotionEvent.ACTION_DOWN)for(index in 0 until event.historySize)tracker.move(event.getHistoricalX(0,index),event.getHistoricalY(0,index),pointer)
  return when(event.actionMasked){
   android.view.MotionEvent.ACTION_DOWN->{tracker.down(event.x,event.y,pointer);false}
   android.view.MotionEvent.ACTION_MOVE->{tracker.move(event.x,event.y,pointer);false}
   android.view.MotionEvent.ACTION_UP->tracker.up(event.x,event.y,pointer)
   else->{tracker.cancel();false}
  }
 }
 private var pickingObjectOn:PhotoEdit?=null
 private var pickingObjectScope:String?=null
 private fun clearObjectPick(){pickingObjectOn=null;pickingObjectScope=null;placementTap?.cancel()}
 private fun armObjectPick(){
  if(!canFinish())return
  if(edit.layers.objects.isEmpty()){note.text="Add a text or shape object first.";return}
  val scope=loadedScope?.takeIf{runCatching{currentScope()==it}.getOrDefault(false)}?:run{note.text="Session changed. Reopen this photo.";return}
  placementTap?.cancel();placingLight=null;placingOn=null;placingScope=null;before=false;preview()
  pickingObjectOn=edit;pickingObjectScope=scope
  note.text="Tap a visible shape or text box. The front object wins; locked objects can be unlocked."
 }
 private fun pickObjectTouch(event:android.view.MotionEvent):Boolean{
  if(event.action==android.view.MotionEvent.ACTION_CANCEL){clearObjectPick();return true}
  if(event.action!=android.view.MotionEvent.ACTION_UP)return true
  val state=pickingObjectOn;val scope=pickingObjectScope
  fun current()=state!=null&&scope!=null&&canFinish()&&!isDestroyed&&!before&&edit==state&&loadedScope==scope&&runCatching{currentScope()==scope}.getOrDefault(false)
  if(!current()){clearObjectPick();if(!activityClosed&&!isDestroyed&&!isFinishing)note.text="Photo or session changed. Open Pick object again.";return true}
  val shown=rendered
  if(shown==null||renderedEdit!=state){note.text="Wait for the edited preview, then tap again.";return true}
  val inverse=Matrix();val point=floatArrayOf(event.x-image.paddingLeft,event.y-image.paddingTop)
  if(!image.imageMatrix.invert(inverse)){note.text="Preview is not ready. Tap again.";return true}
  inverse.mapPoints(point)
  val bounds=image.drawable?.bounds
  if(bounds==null||bounds.width()<=0||bounds.height()<=0){note.text="Preview is not ready. Tap again.";return true}
  val picked=PhotoObjectHitTest.pickDrawable(requireNotNull(state).layers,shown.width,shown.height,bounds.width(),bounds.height(),point[0]-bounds.left,point[1]-bounds.top)
  if(picked==null){note.text="No object here. Tap a visible shape or text box, or use the object list.";image.performClick();return true}
  clearObjectPick();image.performClick();note.text="Object selected. Review its controls."
  PhotoLayerControls.showObject(this,state.layers,picked.id){next->
   require(current()){"Photo or account changed. Pick the object again."}
   if(next!=state.layers){tool("composition_objects");change(state.copy(layers=next))}
  }
  return true
 }
 private var previewAnalysisRunning=false
 private fun showHistogram()=inspectPreview({preview->
  val stats=PhotoHistogramAccumulator();val row=IntArray(preview.width)
  for(y in 0 until preview.height){preview.getPixels(row,0,preview.width,0,y,preview.width,1);stats.add(row)}
  stats.snapshot()
 }){width,height,result,_->PhotoHistogramControls.show(this,width,height,result)}
 private fun showPalette()=inspectPreview({preview->
  val colors=PhotoPaletteAccumulator();val row=IntArray(preview.width)
  for(y in 0 until preview.height){preview.getPixels(row,0,preview.width,0,y,preview.width,1);colors.add(row)}
  colors.snapshot()
 }){width,height,result,canCopy->
  PhotoPaletteControls.show(this,width,height,result,canCopy){colors->paletteShelfControls(canCopy)?.save(colors)}
 }
 private var colorPaletteControls:PhotoColorPaletteControls?=null
 @Volatile private var neuralUpscaleControls:PhotoNeuralUpscaleControls?=null
 @Volatile private var subjectAssetControls:PhotoSubjectAssetControls?=null
 private fun paletteShelfControls(extraGuard:()->Boolean={true}):PhotoColorPaletteControls? {
  if(!copyForeground)return null
  val scope=photoControlScope()?:return null
  val namespace=currentPresetNamespace()?:run{note.text="Session changed. Reopen the photo.";return null}
  val state=edit;val original=source;val requestGeneration=copyGeneration
  colorPaletteControls?.close()
  val store=runCatching{PhotoColorPaletteStore(java.io.File(noBackupFilesDir,"photo-palettes"),namespace)}.getOrElse{note.text="Palette storage is unavailable. Close and try again.";return null}
  return PhotoColorPaletteControls(this,store,worker){
   copyForeground&&copyGeneration==requestGeneration&&photoControlUnchanged(state,scope)&&source==original&&currentPresetNamespace()==namespace&&extraGuard()
  }.also{colorPaletteControls=it}
 }
 private fun showSavedPalettes(){paletteShelfControls()?.shelf()}
 private fun <T> inspectPreview(measure:(Bitmap)->T,present:(Int,Int,T,()->Boolean)->Unit){
  if(!canFinish()||previewAnalysisRunning)return
  val state=edit
  if(before||renderedEdit!=state){note.text="Wait for the edited preview, then open this tool again.";return}
  val preview=rendered?:return
  val scope=loadedScope?.takeIf{runCatching{currentScope()==it}.getOrDefault(false)}?:run{note.text="Session changed. Reopen this photo.";return}
  val owned=runCatching{requireNotNull(preview.copy(Bitmap.Config.ARGB_8888,false))}.getOrNull()?:run{note.text="Not enough memory to measure the preview.";return}
  previewAnalysisRunning=true
  note.text="Measuring edited preview on this device…"
  fun current()=!activityClosed&&!isFinishing&&!isDestroyed&&edit==state&&!before&&loadedScope==scope&&runCatching{currentScope()==scope}.getOrDefault(false)
  try{worker.execute{
   try{
    val width=owned.width;val height=owned.height
    val result=measure(owned)
    runOnUiThread{previewAnalysisRunning=false;if(current()&&canFinish()){present(width,height,result){current()&&canFinish()};note.text="Preview measured locally. Original preserved."}}
   }catch(failure:Exception){runOnUiThread{previewAnalysisRunning=false;if(current()&&canFinish())note.text="Unable to measure this preview. Try again."}}
   catch(failure:OutOfMemoryError){runOnUiThread{previewAnalysisRunning=false;if(current()&&canFinish())note.text="Not enough memory to measure this preview."}}
   finally{owned.recycle()}
  }}catch(failure:Exception){previewAnalysisRunning=false;owned.recycle();if(current())note.text="Unable to measure this preview. Try again."}
 }
 private fun createTrimmedCopy()=createDerivedPhoto(
  "Trim transparent border?",
  "Removes only fully transparent outer rows and columns from the edited preview. Faint edges stay. Creates a flattened PNG at preview resolution, up to 2024 pixels per source edge, and opens a separate local guest photo workspace. Current editable work and originals stay here. An opaque background cannot be trimmed. Save an image project or export PNG in the new workspace to keep the result; its starting copy is temporary.",
  "Create trimmed copy"
 ){_ -> { {pixels,guard -> PhotoAlphaTrim.render(pixels,guard)} }}
 private fun createStickerCopy()=createDerivedPhoto(
  "Create outlined sticker?",
  "Use a transparent PNG or make a person cutout first. Adds a rounded border and transparent padding around visible artwork. Original visible pixels keep their colors and alpha. Creates a separate flattened PNG at edited preview resolution, up to 2024 pixels per source edge. It does not keep upstream layers or border controls in the new image. Current editable work and originals stay here. Save an image project or export PNG in the new workspace to keep it; its starting copy is temporary.",
  "Create sticker"
 ){body ->
  var radius=16;var color=Color.WHITE
  val label=TextView(this).apply{text="Border width: 16 preview pixels";setTextColor(Color.BLACK)};body.addView(label)
  body.addView(SeekBar(this).apply{max=30;progress=14;contentDescription="Sticker border width";progressTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);thumbTintList=android.content.res.ColorStateList.valueOf(Color.BLACK)
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,value:Int,user:Boolean){radius=value+2;label.text="Border width: $radius preview pixels"}})
  },LinearLayout.LayoutParams(-1,48.dp()))
  val choices=RadioGroup(this)
  for((name,tint)in listOf("White border" to Color.WHITE,"Black border" to Color.BLACK)){
   choices.addView(RadioButton(this).apply{text=name;setTextColor(Color.BLACK);buttonTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);isChecked=tint==color;setOnCheckedChangeListener{_,checked->if(checked)color=tint}},LinearLayout.LayoutParams(-1,48.dp()))
  }
  body.addView(choices)
  val snapshot:()->PhotoCopyTransform={val width=radius;val tint=color;{pixels,guard->PhotoSticker.render(pixels,width,tint,guard)}}
  snapshot
 }
 private fun createDerivedPhoto(title:String,description:String,button:String,configure:(LinearLayout)->(() -> PhotoCopyTransform)){
  if(!copyForeground)return
  val scope=photoControlScope()?:return
  val requestGeneration=copyGeneration
  val input=bitmap?:return;val state=edit;val original=source
  if(state.needsPersonMask&&personMask==null){before=false;preview();note.text="Wait for the person preview, then create the copy again.";return}
  val inherited=protectedOriginals.map{it.toString()}
  val origins=runCatching{PhotoComparisonFiles.originals(original.toString(),inherited)}.getOrElse{note.text="This copy cannot safely retain its original sources. Current edits were kept.";return}
  fun current()=copyForeground&&copyGeneration==requestGeneration&&!activityClosed&&!isDestroyed&&!isFinishing&&bitmap===input&&edit==state&&source==original&&loadedScope==scope&&protectedOriginals.map{it.toString()}==inherited&&runCatching{currentScope()==scope}.getOrDefault(false)
  val controls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);setPadding(16.dp(),8.dp(),16.dp(),8.dp())}
  val snapshot=configure(controls)
  android.app.AlertDialog.Builder(this).setTitle(title).setMessage(description).setView(controls)
   .setNegativeButton("Cancel",null).setPositiveButton(button){_,_->
    if(!current()||!canFinish()){note.text="Photo or session changed. Open this tool again.";return@setPositiveButton}
    val operation=snapshot()
    clearObjectPick();placingLight=null;placingOn=null;placingScope=null
    photoProjectBusy=true;note.text="Creating a local image copy…"
    try{worker.execute{
     val prepared=runCatching{
      fun requireCurrent(){check(current()){"Photo session changed"}}
      requireCurrent()
      var edited:Bitmap?=null;var derived:Bitmap?=null
      try{
       val rendered=render(input,state);edited=rendered;requireCurrent()
       val result=operation(rendered,::requireCurrent);derived=result
       PhotoComparisonFiles.write(cacheDir,::requireCurrent){file->java.io.FileOutputStream(file).use{stream->check(result.compress(Bitmap.CompressFormat.PNG,100,stream)){"PNG encoding failed"};stream.fd.sync()}}
      }finally{if(derived!==edited)derived?.recycle();edited?.recycle()}
     }
     runOnUiThread{
      val unchanged=current();photoProjectBusy=false
      prepared.onSuccess{file->
       if(!unchanged||!canFinish()){file.delete();if(!activityClosed&&!isDestroyed&&!isFinishing)note.text="Copy cancelled. Current edits were kept.";return@onSuccess}
       runCatching{
        val uri=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
        startActivity(Intent(this,PhotoEditorActivity::class.java).putExtra("uri",uri.toString()).putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name).putStringArrayListExtra(ImportedPhotoOriginals.EXTRA,ArrayList(origins)))
       }.onSuccess{note.text="Image copy opened separately. Current edits and originals were kept."}.onFailure{file.delete();note.text="Could not open the image copy. Current edits were kept."}
      }.onFailure{if(!activityClosed&&!isDestroyed&&!isFinishing)note.text=if(!unchanged)"Copy cancelled. Current edits were kept." else when(val message=it.message){
       "This image is fully transparent. Add visible artwork first.","No fully transparent outer border was found. The current image was kept.","Image exceeds the trimming limit",
       "Add visible artwork before making a sticker.","Use a transparent PNG or a person cutout before making a sticker.","The sticker is too large. Use a smaller image or border.","Sticker source exceeds the image limit"->message
       else->"Could not create the image copy. Try a smaller image or free some storage; current edits were kept."
      }}
     }
    }}catch(failure:Exception){photoProjectBusy=false;if(current())note.text="Could not start creating the copy. Current edits were kept."}
   }.show()
 }
 private fun openNeuralUpscaleCopy(){
  if(!canFinish()||!copyForeground||isDestroyed||neuralUpscaleControls!=null||accessMode?.allowsOnDeviceAi!=true)return
  val scope=photoControlScope()?:return
  val input=bitmap?:return;val state=edit;val original=source;val mask=personMask;val mode=accessMode
  if(before||renderedEdit!=state){note.text="Wait for the edited preview, then open AI upscale again.";return}
  if(state.needsPersonMask&&mask==null){note.text="Wait for the current person-mask preview, then try again.";return}
  val generation=copyGeneration;val inherited=protectedOriginals.map{it.toString()}
  lateinit var controls:PhotoNeuralUpscaleControls
  fun sameFields()=neuralUpscaleControls===controls&&photoProjectBusy&&copyForeground&&copyGeneration==generation&&!activityClosed&&!isDestroyed&&!isFinishing&&bitmap===input&&edit==state&&source==original&&personMask===mask&&accessMode==mode&&loadedScope==scope&&protectedOriginals.map{it.toString()}==inherited
  fun same()=sameFields()&&runCatching{currentScope()==scope}.getOrDefault(false)
  controls=PhotoNeuralUpscaleControls(this,worker,
   prepareInput={current->
    current();val preview=PhotoRasterRenderer.render(input,state,mask);var fitted:Bitmap?=null;var delivered=false
    try{
     val scale=minOf(1f,PhotoNeuralUpscale.MAX_EDGE.toFloat()/maxOf(preview.width,preview.height))
     fitted=if(scale<1f)Bitmap.createScaledBitmap(preview,(preview.width*scale).toInt().coerceAtLeast(1),(preview.height*scale).toInt().coerceAtLeast(1),true)else preview
     current();val result=requireNotNull(fitted);delivered=true;result
    }finally{if(fitted!==preview)preview.recycle();if(!delivered)fitted?.recycle()}
   },isCurrent={same()},requireWorkerCurrent={check(sameFields()&&currentScope()==scope){"Photo or session changed"}},
   share={file->
    check(same()){"Photo or session changed"}
    val uri=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
    val send=Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=android.content.ClipData.newUri(contentResolver,"Reviewed AI upscale copy",uri)}
    startActivity(Intent.createChooser(send,"Save or share AI upscale copy").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
   },message={value->if(neuralUpscaleControls===controls&&copyForeground&&!activityClosed&&!isDestroyed&&!isFinishing)note.text=value},
   released={if(neuralUpscaleControls===controls){neuralUpscaleControls=null;photoProjectBusy=false}})
  neuralUpscaleControls=controls;photoProjectBusy=true
  runCatching{controls.open()}.onFailure{controls.close();if(copyForeground&&!activityClosed&&!isDestroyed&&!isFinishing)note.text="AI upscale controls could not open. Your photo and edits were kept."}
 }
 private fun openSubjectAssetPack(){
  if(!canFinish()||!copyForeground||isDestroyed||subjectAssetControls!=null||accessMode?.allowsOnDeviceAi!=true)return
  val scope=photoControlScope()?:return
  val input=bitmap?:return;val state=edit;val original=source;val mask=personMask;val mode=accessMode
  if(before||renderedEdit!=state){note.text="Wait for the edited preview, then open AI subject assets again.";return}
  if(state.needsPersonMask&&mask==null){note.text="Wait for the current person-mask preview, then try again.";return}
  val generation=copyGeneration;val inherited=protectedOriginals.map{it.toString()}
  lateinit var controls:PhotoSubjectAssetControls
  fun sameFields()=subjectAssetControls===controls&&photoProjectBusy&&copyForeground&&copyGeneration==generation&&!activityClosed&&!isDestroyed&&!isFinishing&&bitmap===input&&edit==state&&source==original&&personMask===mask&&accessMode==mode&&loadedScope==scope&&protectedOriginals.map{it.toString()}==inherited
  fun same()=sameFields()&&runCatching{currentScope()==scope}.getOrDefault(false)
  controls=PhotoSubjectAssetControls(this,worker,
   prepareInput={current->
    current();val preview=PhotoRasterRenderer.render(input,state,mask);var fitted:Bitmap?=null;var delivered=false
    try{
     val scale=minOf(1f,PhotoSubjectAssets.MAX_EDGE.toFloat()/maxOf(preview.width,preview.height))
     fitted=if(scale<1f)Bitmap.createScaledBitmap(preview,(preview.width*scale).toInt().coerceAtLeast(1),(preview.height*scale).toInt().coerceAtLeast(1),true)else preview
     current();val result=requireNotNull(fitted);delivered=true;result
    }finally{if(fitted!==preview)preview.recycle();if(!delivered)fitted?.recycle()}
   },isCurrent={same()},requireWorkerCurrent={check(sameFields()&&currentScope()==scope){"Photo or session changed"}},
   share={file->
    check(same()){"Photo or session changed"}
    val uri=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
    val send=Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=android.content.ClipData.newUri(contentResolver,"Reviewed AI subject asset pack",uri)}
    startActivity(Intent.createChooser(send,"Save or share selected AI cutouts").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
   },message={value->if(subjectAssetControls===controls&&copyForeground&&!activityClosed&&!isDestroyed&&!isFinishing)note.text=value},
   released={if(subjectAssetControls===controls){subjectAssetControls=null;photoProjectBusy=false}})
  subjectAssetControls=controls;photoProjectBusy=true
  runCatching{controls.open()}.onFailure{controls.close();if(copyForeground&&!activityClosed&&!isDestroyed&&!isFinishing)note.text="AI subject controls could not open. Your photo and edits were kept."}
 }
 private fun createComparison(){
  if(!copyForeground)return
  val scope=photoControlScope()?:return
  val requestGeneration=copyGeneration
  val input=bitmap?:return;val state=edit;val original=source
  if(state.needsPersonMask&&personMask==null){before=false;preview();note.text="Wait for the person preview, then create the comparison again.";return}
  val inherited=protectedOriginals.map{it.toString()}
  val origins=runCatching{PhotoComparisonFiles.originals(original.toString(),inherited)}.getOrElse{note.text="This comparison cannot safely carry all original sources. Existing edits were kept.";return}
  fun current()=copyForeground&&copyGeneration==requestGeneration&&!activityClosed&&!isDestroyed&&!isFinishing&&bitmap===input&&edit==state&&source==original&&loadedScope==scope&&protectedOriginals.map{it.toString()}==inherited&&runCatching{currentScope()==scope}.getOrDefault(false)
  android.app.AlertDialog.Builder(this).setTitle("Create before/after image?")
   .setMessage("Creates a labelled PNG from the original and edited views at editor preview resolution, up to 2024 pixels per edge. Both views fit without cropping; small images may be enlarged. Transparency uses white. Cropped or composed edits may show a different framing. The two panels become one flattened image. Opens a separate local guest photo workspace with no account draft or privileges transferred. Save an image project or export a copy there to keep it; its starting PNG is temporary. Your original photo, current edits and Undo history are kept.")
   .setNegativeButton("Cancel",null).setPositiveButton("Choose layout"){_,_->
    if(!current()||!canFinish()){note.text="Photo or session changed. Open comparison again.";return@setPositiveButton}
    android.app.AlertDialog.Builder(this).setTitle("Comparison layout").setNegativeButton("Cancel",null).setItems(arrayOf("Side by side","Stacked")){_,which->
    if(!current()||!canFinish()){note.text="Photo or session changed. Open comparison again.";return@setItems}
    val layout=PhotoComparisonLayout.entries[which]
    clearObjectPick();placingLight=null;placingOn=null;placingScope=null
    photoProjectBusy=true;note.text="Creating a local comparison image…"
    worker.execute{
     val prepared=runCatching{
      fun requireCurrent(){check(current()){"Photo session changed"}}
      requireCurrent()
      var edited:Bitmap?=null;var proof:Bitmap?=null
      try{
       val rendered=render(input,state);edited=rendered;requireCurrent()
       val comparison=PhotoComparisonRenderer.render(input,rendered,layout);proof=comparison;requireCurrent()
       PhotoComparisonFiles.write(cacheDir,::requireCurrent){file->java.io.FileOutputStream(file).use{stream->check(comparison.compress(Bitmap.CompressFormat.PNG,100,stream)){"Comparison encoding failed"};stream.fd.sync()}}
      }finally{proof?.recycle();edited?.recycle()}
     }
     runOnUiThread{
      val unchanged=current();photoProjectBusy=false
      prepared.onSuccess{file->
       if(!unchanged){file.delete();if(!activityClosed&&!isDestroyed&&!isFinishing)note.text="Comparison cancelled. Current edits were kept.";return@onSuccess}
       runCatching{
        val uri=androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",file)
        startActivity(Intent(this,PhotoEditorActivity::class.java).putExtra("uri",uri.toString()).putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name).putStringArrayListExtra(ImportedPhotoOriginals.EXTRA,ArrayList(origins)))
       }.onSuccess{note.text="Comparison opened separately. Current edits and originals were kept."}.onFailure{file.delete();note.text="Could not open the comparison. Current edits were kept."}
      }.onFailure{if(!activityClosed&&!isDestroyed&&!isFinishing)note.text="Could not create the comparison. Try a smaller photo or free some storage; current edits were kept."}
     }
    }
    }.show()
   }.show()
 }
 private fun openInspector(){
  if(!canFinish())return
  fun t(label:String,run:()->Unit)=NativeInspectorPanel.Tool(label,run)
  NativeInspectorPanel.show(this,"Photo inspector","${bitmap?.width} × ${bitmap?.height} px · ${edit.rotation}° · edits save as a new copy",listOf(
   "Framing" to listOf(t("Aspect ratio"){chooseRatio()},t("Mirror"){chooseMirror()}),
   "Color & light" to listOf(t("Preview tonal histogram"){showHistogram()},t("Extract preview palette"){showPalette()},t("Saved color palettes"){showSavedPalettes()},t("White balance"){finishAdjust("White balance",0)},t("Selective color range"){manageSelectiveColor()},t("Black-and-white mix"){manageMonochrome()},t("Shadows / highlights"){finishAdjust("Shadows / highlights",1)},t("Tone curve"){showCurve()},t("Local light"){showLocalLight()}),
   "Finish" to listOf(t("Fade to transparency"){manageAlphaGradient()},t("Vignette"){finishAdjust("Vignette",2)},t("Sharpen"){finishAdjust("Sharpen",3)},t("Text overlay"){editCaption()}),
   "Creative color" to PhotoCreativeTool.values().map{kind->t(kind.title){creativeAdjust(kind)}},
   "Photo effects" to PhotoEffectTool.values().map{kind->t(kind.title){effectAdjust(kind)}},
   "Composition objects" to listOf(t("Text and shape objects"){manageObjects()},t("Pick object on photo"){armObjectPick()},t("Local effect regions"){manageRegions()},t("Local region recipes"){manageRegionRecipes()},t("Find / replace photo text"){findReplacePhotoText()},t("Arrange selected objects"){arrangePhotoObjects()}),
   "Create & compose" to PhotoCompositionTool.values().map{kind->t(kind.title){compositionAdjust(kind)}},
   "Editable project" to (listOf(t("Save editable photo edits"){photoProjectOperation("save")},t("Restore editable photo edits"){requestPhotoRestore()},t("Delete saved photo edits"){deletePhotoProject()},t("Copy editable photo settings"){copyPhotoSettings()},t("Paste editable photo settings"){pastePhotoSettings()})+if(!isCreatedPhotoSource())listOf(t("Saved photo versions"){savedPhotoVersions()})else emptyList()),
   "Saved looks" to listOf(t("Save current adjustments"){saveCurrentLook()},t("Apply saved look"){chooseSavedLook()},t("Delete saved look"){deleteSavedLook()}),
    "On-device AI" to (if(accessMode?.allowsOnDeviceAi==true)listOf(t("Photo looks"){chooseLook()},t("Person background"){chooseBackground()},t("Person cutout"){cutOutPerson()},t("Subject asset pack"){openSubjectAssetPack()},t("AI upscale copy"){openNeuralUpscaleCopy()},t("Read text"){readText()})else emptyList()),
   "Output" to listOf(t("Export PNG / JPEG"){chooseExport()},t("Create before/after image"){createComparison()})
   ).filter{it.second.isNotEmpty()})
 }
 private fun deletePhotoProject(){
  if(!canRequestPhotoDestructive())return
  showPhotoDestructiveReview(true)
 }
 private fun canApprovePhotoObjectArrange(captured:PhotoCloseState,scope:String,mode:EditorAccessMode)=canApprovePhotoMetadataReview(captured,scope,mode)
 private fun applyPhotoObjectArrange(owner:PhotoObjectArrangeControls,captured:PhotoCloseState,scope:String,mode:EditorAccessMode,plan:PhotoObjectArrangement):Boolean {
  if(photoObjectArrange!==owner||!canApprovePhotoObjectArrange(captured,scope,mode)||plan.base!==edit||!plan.changed)return false
  return commitPhotoChange(plan.edit)
 }
 private fun arrangePhotoObjects(){
  if(!canRequestPhotoMetadataReview())return
  val scope=photoControlScope()?:return;val mode=accessMode?:return;val captured=photoCloseState();val base=edit
  photoObjectArrange?.close()
  lateinit var controls:PhotoObjectArrangeControls
  controls=PhotoObjectArrangeControls(this,base,isCurrent={photoObjectArrange===controls&&canApprovePhotoObjectArrange(captured,scope,mode)},apply={plan->
   val applied=applyPhotoObjectArrange(controls,captured,scope,mode,plan)
   if(applied){val ready=runCatching{preview()}.isSuccess;note.text=if(ready)"Arranged ${plan.changes.size} object centers. Undo restores positions. Check overlap and clipping."else"Object centers arranged, but preview is unavailable. Undo restores positions."}
   applied
  },message={note.text=it})
  photoObjectArrange=controls;controls.open()
 }
 private fun canRequestPhotoMetadataReview()=copyForeground&&canFinish()&&!isDestroyed&&!waitingForPicker&&pendingDestination==null&&::source.isInitialized
 private fun canRequestPhotoTextReplace()=canRequestPhotoMetadataReview()
 private fun canApprovePhotoMetadataReview(captured:PhotoCloseState,scope:String,mode:EditorAccessMode)=canRequestPhotoMetadataReview()&&accessMode==mode&&currentScope()==scope&&captured.stillMatches(photoCloseState())
 private fun canApprovePhotoTextReplace(captured:PhotoCloseState,scope:String,mode:EditorAccessMode)=canApprovePhotoMetadataReview(captured,scope,mode)
 private fun applyPhotoTextReplace(owner:PhotoTextReplaceControls,captured:PhotoCloseState,scope:String,mode:EditorAccessMode,plan:PhotoTextReplacement):Boolean {
  if(photoTextReplace!==owner||!canApprovePhotoTextReplace(captured,scope,mode)||plan.base!==edit||!plan.changed)return false
  return commitPhotoChange(plan.edit)
 }
 private fun findReplacePhotoText(){
  if(!canRequestPhotoTextReplace())return
  val scope=photoControlScope()?:return;val mode=accessMode?:return;val captured=photoCloseState();val base=edit
  photoTextReplace?.close()
  lateinit var controls:PhotoTextReplaceControls
  controls=PhotoTextReplaceControls(this,base,isCurrent={photoTextReplace===controls&&canApprovePhotoTextReplace(captured,scope,mode)},apply={plan->
   val applied=applyPhotoTextReplace(controls,captured,scope,mode,plan)
   if(applied){val ready=runCatching{preview()}.isSuccess;note.text=if(ready)"Replaced ${plan.matches} text matches. Undo restores previous text. Check wrapping and placement."else"Photo text replaced, but preview is unavailable. Undo restores previous text."}
   applied
  })
  photoTextReplace=controls;controls.open()
 }
 private fun hasPhotoProjectRead(original:Uri)=PhotoProjectReadAccess.hasExistingRead(original,contentResolver.persistedUriPermissions.any{it.uri==original&&it.isReadPermission}){app.honorable.search.MediaCapabilityManager(this).current().canReadImages}
 private fun isCreatedPhotoSource()=::source.isInitialized&&source.authority?.endsWith(".creation")==true&&source.authority==packageName+".creation"
 private fun canOpenPhotoVersions()=copyForeground&&canFinish()&&!isDestroyed&&::source.isInitialized&&!isCreatedPhotoSource()
 private fun canApplyPhotoVersion(captured:PhotoCloseState,scope:String,mode:EditorAccessMode)=canOpenPhotoVersions()&&accessMode==mode&&currentScope()==scope&&captured.stillMatches(photoCloseState())
 private fun applyPhotoVersion(captured:PhotoCloseState,scope:String,mode:EditorAccessMode,entry:PhotoSavedVersion,settings:PhotoEdit):Boolean {
  if(!canApplyPhotoVersion(captured,scope,mode))return false
  val namespace=currentPresetNamespace()?:return false
  if(entry.scopeKey!=PhotoVersionScope(namespace,source.toString()).storageName)return false
  val nextOriginals=ImportedPhotoOriginals.validate((protectedOriginals.map{it.toString()}+entry.originals).distinct()).map(Uri::parse)
  if(!commitPhotoChange(settings))return false
  protectedOriginals=nextOriginals
  return true
 }
 private fun savedPhotoVersions(){
  if(!canOpenPhotoVersions())return
  val scope=photoControlScope()?:return;val mode=accessMode?:return;val captured=photoCloseState();val original=source;val generation=copyGeneration
  val initial=edit;val origins=protectedOriginals.map{it.toString()};photoVersions?.close()
  lateinit var shelf:PhotoVersionShelf
  shelf=PhotoVersionShelf(this,original.toString(),initial,origins,namespace={currentPresetNamespace()},
   assertSource={check(copyForeground&&copyGeneration==generation&&!activityClosed&&!isDestroyed&&!isFinishing&&source==original&&accessMode==mode&&loadedScope==scope&&currentScope()==scope){"Photo or session changed"}},
   isCurrent={canApplyPhotoVersion(captured,scope,mode)},busy={value->if(photoVersions===shelf)photoProjectBusy=value},message={note.text=it},
   withOriginal={guard,action->
    PhotoProjectAccess.withDurableRead({hasPhotoProjectRead(original)},{contentResolver.takePersistableUriPermission(original,Intent.FLAG_GRANT_READ_URI_PERMISSION)},{contentResolver.releasePersistableUriPermission(original,Intent.FLAG_GRANT_READ_URI_PERMISSION)},guard){
     val fingerprint=contentResolver.openInputStream(original).use{PhotoProjectCodec.fingerprint(requireNotNull(it){"Original photo is unavailable"},guard)}
     guard();action(fingerprint)
    }
   },apply={entry,settings->
    val changed=settings!=edit
    runCatching{applyPhotoVersion(captured,scope,mode,entry,settings)}.onSuccess{opened->
     if(opened){val ready=runCatching{preview()}.isSuccess;note.text=if(!changed)"Saved version matches current photo settings. Original protection was kept."else if(ready)"Saved photo version opened. Undo restores previous settings."else"Version opened, but preview is unavailable. Undo restores previous settings."}
     else note.text="Photo or operation changed. Existing edits were kept. Reopen versions."
    }.onFailure{note.text="Version could not be applied safely with the current original sources. Existing edits were kept."}
   })
  photoVersions=shelf;shelf.open()
 }
 private fun canRequestPhotoSettings()=copyForeground&&canFinish()&&!isDestroyed
 private fun canApprovePhotoSettings(token:Any,captured:PhotoCloseState,scope:String,mode:EditorAccessMode)=photoSettingsToken===token&&canRequestPhotoSettings()&&accessMode==mode&&currentScope()==scope&&captured.stillMatches(photoCloseState())
 private fun applyPhotoSettings(token:Any,captured:PhotoCloseState,scope:String,mode:EditorAccessMode,settings:PhotoEdit):Boolean {
  if(!canApprovePhotoSettings(token,captured,scope,mode)||settings==edit)return false
  return commitPhotoChange(settings)
 }
 private fun closePhotoSettingsReview(){photoSettingsToken=null;val dialog=photoSettingsDialog;photoSettingsDialog=null;runCatching{dialog?.dismiss()}}
 private fun reviewPhotoSettings(title:String,message:String,positive:String,scope:String,mode:EditorAccessMode,captured:PhotoCloseState,action:(Any)->Unit){
  closePhotoSettingsReview();val token=Any()
  val dialog=android.app.AlertDialog.Builder(this).setTitle(title).setMessage(message)
   .setNegativeButton("Keep current"){_,_->if(photoSettingsToken===token)closePhotoSettingsReview()}
   .setPositiveButton(positive){_,_->
    if(photoSettingsToken!==token)return@setPositiveButton
    if(!canApprovePhotoSettings(token,captured,scope,mode))note.text="Photo or session changed. Open photo settings again."
    else runCatching{action(token)}.onFailure{note.text="Photo settings transfer could not finish. Check the current photo and clipboard before retrying."}
    closePhotoSettingsReview()
   }.create()
  photoSettingsToken=token;photoSettingsDialog=dialog
  dialog.setOnCancelListener{if(photoSettingsToken===token)closePhotoSettingsReview()}
  dialog.setOnDismissListener{if(photoSettingsToken===token){photoSettingsToken=null;photoSettingsDialog=null}}
  dialog.show()
 }
 private fun copyPhotoSettings(){
  if(!canRequestPhotoSettings())return
  val scope=photoControlScope()?:return;val mode=accessMode?:return;val captured=photoCloseState();val settings=edit
  reviewPhotoSettings("Copy editable photo settings?","Copies all adjustments, crop, captions, objects and local regions, including hidden object text, to the clipboard. Photo pixels, original locations, account details and Undo history are excluded. Copy only settings you intend to reuse.","Copy settings",scope,mode,captured){token->
   val clip=PhotoEditClipboard.create(settings)
   check(canApprovePhotoSettings(token,captured,scope,mode))
   (getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager).setPrimaryClip(clip)
   note.text="Editable settings copied. Open another photo and choose Paste editable photo settings."
  }
 }
 private fun pastePhotoSettings(){
  if(!canRequestPhotoSettings())return
  val scope=photoControlScope()?:return;val mode=accessMode?:return;val captured=photoCloseState()
  val settings=runCatching{PhotoEditClipboard.read((getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager).primaryClip)}.getOrElse{
   note.text="Clipboard does not contain compatible Noble photo settings. Copy editable settings in Noble first.";return
  }
  if(!canRequestPhotoSettings()||currentScope()!=scope||!captured.stillMatches(photoCloseState())){note.text="Photo or session changed. Open Paste again.";return}
  reviewPhotoSettings("Replace photo settings?","Uses the copied adjustments, crop, captions, objects and regions on this photo. Replaces current settings; original pixels and saved projects are kept. Undo restores current settings in this editor session. Person effects are recomputed on this device when needed.","Apply settings",scope,mode,captured){token->
   if(applyPhotoSettings(token,captured,scope,mode,settings)){val ready=runCatching{preview()}.isSuccess;note.text=if(ready)"Editable settings applied. Undo restores your previous photo settings."else"Settings applied, but preview is unavailable. Undo restores your previous settings."}
   else note.text="These settings already match this photo, or the editor changed. Current photo was kept."
  }
 }
 private fun canRequestPhotoRestore()=copyForeground&&canFinish()&&!isDestroyed
 private fun photoRestoreNeedsReview()=edit!=PhotoEdit()||undo.isNotEmpty()||redo.isNotEmpty()||canvasProjectId!=null
 private fun canApprovePhotoRestore(token:Any,base:PhotoEdit,original:Uri,scope:String,namespace:String,generation:Long,projectId:String?,origins:List<String>,mode:EditorAccessMode):Boolean=
  photoRestoreReviewToken===token&&canRequestPhotoRestore()&&copyGeneration==generation&&edit==base&&source==original&&loadedScope==scope&&canvasProjectId==projectId&&protectedOriginals.map{it.toString()}==origins&&accessMode==mode&&currentPresetNamespace()==namespace&&currentScope()==scope
 private fun closePhotoRestoreReview(){
  photoRestoreReviewToken=null;val dialog=photoRestoreReviewDialog;photoRestoreReviewDialog=null;runCatching{dialog?.dismiss()}
 }
 private fun requestPhotoRestore(){
  if(!canRequestPhotoRestore())return
  val scope=loadedScope?:return
  val namespace=currentPresetNamespace()?:run{note.text="Session changed. Reopen the photo.";return}
  if(currentScope()!=scope){note.text="Session changed. Reopen the photo.";return}
  closePhotoRestoreReview()
  if(!photoRestoreNeedsReview()){photoProjectOperation("restore");return}
  val token=Any();val base=edit;val original=source;val generation=copyGeneration;val projectId=canvasProjectId;val origins=protectedOriginals.map{it.toString()};val mode=accessMode?:return
  val dialog=android.app.AlertDialog.Builder(this).setTitle("Restore saved photo edits?")
   .setMessage("Replaces the current adjustments, crop, captions and added objects. Export a photo copy first to keep the current rendered result. Saving editable edits can replace the existing save. Undo returns to this edit only while this editor session stays open.")
   .setNegativeButton("Keep current"){_,_->if(photoRestoreReviewToken===token){closePhotoRestoreReview();note.text="Current photo edits kept."}}
   .setPositiveButton("Restore saved"){_,_->
    if(photoRestoreReviewToken!==token)return@setPositiveButton
    val approved=canApprovePhotoRestore(token,base,original,scope,namespace,generation,projectId,origins,mode)
    closePhotoRestoreReview()
    if(approved)photoProjectOperation("restore")else note.text="Photo or session changed. Current edits were kept. Open Restore again."
   }.create()
  photoRestoreReviewToken=token;photoRestoreReviewDialog=dialog
  dialog.setOnCancelListener{if(photoRestoreReviewToken===token)closePhotoRestoreReview()}
  dialog.setOnDismissListener{if(photoRestoreReviewToken===token){photoRestoreReviewToken=null;photoRestoreReviewDialog=null}}
  dialog.show()
 }
 private fun photoProjectForegroundMatches(action:String,generation:Long)=action=="save"||(copyForeground&&copyGeneration==generation)
 private fun photoProjectOperation(action:String){
  if(!canFinish()||(action!="save"&&!copyForeground))return
  val namespace=currentPresetNamespace()?:run{note.text="Session changed. Reopen the photo.";return}
  if(currentScope()!=loadedScope){note.text="Session changed. Reopen the photo.";return}
  if(source.authority==packageName+".creation"){
   when(action){"save"->saveCanvasProject();"restore"->restoreCanvasProject();else->note.text="Close this editor, then use Saved image projects to delete a canvas project and its source copy."}
   return
  }
  val operationGeneration=copyGeneration
  val base=edit;val original=source;val store=PhotoProjectStore(java.io.File(noBackupFilesDir,"photo-projects"),namespace,original.toString())
  photoProjectBusy=true;note.text=when(action){"save"->"Saving editable photo edits…";"restore"->"Checking original and restoring saved edits…";else->"Deleting saved photo edits…"}
  fun current()=photoProjectForegroundMatches(action,operationGeneration)&&!activityClosed&&!isDestroyed&&!isFinishing&&photoProjectBusy&&edit==base&&source==original&&currentPresetNamespace()==namespace&&currentScope()==loadedScope
  fun hasDurableRead():Boolean=hasPhotoProjectRead(original)
  fun requireCurrent(){check(current()){"Photo or session changed. Reopen the project controls."}}
  worker.execute{
   val result=runCatching{
    requireCurrent()
    if(action=="delete"){store.delete(::requireCurrent);null}else{
     PhotoProjectAccess.withDurableRead(
      hasRead=::hasDurableRead,
      takeRead={runCatching{contentResolver.takePersistableUriPermission(original,Intent.FLAG_GRANT_READ_URI_PERMISSION)}.getOrElse{error("This original needs durable Files access. Reopen it through Files and try again.")}},
      releaseRead={contentResolver.releasePersistableUriPermission(original,Intent.FLAG_GRANT_READ_URI_PERMISSION)},
      requireCurrent=::requireCurrent
     ){
      val fingerprint=contentResolver.openInputStream(original).use{PhotoProjectCodec.fingerprint(requireNotNull(it){"The original photo cannot be read"},::requireCurrent)}
      requireCurrent()
      if(action=="save"){store.save(fingerprint,base,::requireCurrent);null}else store.load(fingerprint)
     }
    }
   }
   runOnUiThread{
    val unchanged=current();photoProjectBusy=false
    if(isDestroyed||isFinishing)return@runOnUiThread
    if(!unchanged){note.text="Photo or session changed. Current edits were kept.";return@runOnUiThread}
    result.onSuccess{saved->
     if(action=="restore"&&saved!=null){change(saved);note.text="Saved editable photo restored. Undo returns to the previous edit."}
     else note.text=when(action){"save"->"Editable edits saved privately. Reopen this same original, then choose Restore editable photo edits. The original must remain available.";"restore"->"No saved editable project exists for this original.";else->"Saved edits deleted. Original photo and current edit kept."}
    }.onFailure{note.text=it.message?:"Photo project operation failed. Current edits were kept."}
   }
  }
 }
 private fun saveCanvasProject(){
  if(!canFinish()||accessMode!=EditorAccessMode.GUEST_BASIC)return
  val base=edit;val original=source;val origins=protectedOriginals.map{it.toString()}
  val field=EditText(this).apply{setText(canvasProjectName);contentDescription="Image project name";isSingleLine=true;filters=arrayOf(android.text.InputFilter.LengthFilter(40));setTextColor(Color.BLACK)}
  val dialog=android.app.AlertDialog.Builder(this).setTitle("Save editable image project").setMessage("Keeps a private source copy and editable object/adjustment state on this device. Export a PNG separately to share a finished image.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save project",null).create()
  dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   if(!canFinish()||edit!=base||source!=original||protectedOriginals.map{it.toString()}!=origins){field.error="Editor changed. Reopen Save project.";return@setOnClickListener}
   val name=field.text.toString().trim()
   if(name.isEmpty()||name.length>40||name.any{it.isISOControl()}){field.error="Use a name of 1–40 characters";return@setOnClickListener}
   dialog.dismiss();photoProjectBusy=true;note.text="Saving image project and its private source…"
   fun valid()=!activityClosed&&photoProjectBusy&&!isDestroyed&&!isFinishing&&accessMode==EditorAccessMode.GUEST_BASIC&&edit==base&&source==original&&protectedOriginals.map{it.toString()}==origins
   fun requireCurrent(){check(valid()){"Editor changed. Current edits were kept."}}
   worker.execute{val store=CanvasProjectStore(java.io.File(filesDir,"canvas-projects"));val saved=runCatching{
    val id=canvasProjectId
    if(id==null)contentResolver.openInputStream(original).use{store.create(name,requireNotNull(it){"Created image source is unavailable"},base,origins,::requireCurrent)}else store.update(id,name,base,origins,::requireCurrent)
   }
    runOnUiThread{val unchanged=valid();photoProjectBusy=false;if(isDestroyed||isFinishing)return@runOnUiThread
     if(!unchanged){note.text="Editor changed. Current edits were kept.";return@runOnUiThread}
     saved.onSuccess{project->
      val durable=runCatching{androidx.core.content.FileProvider.getUriForFile(this,packageName+".creation",store.sourceFile(project.id))}.getOrElse{note.text="Project data was saved, but its private source could not reopen. Current edits were kept.";return@onSuccess}
      canvasProjectId=project.id;canvasProjectName=project.name;protectedOriginals=project.protectedOriginals.map(Uri::parse)
      source=durable
      intent.putExtra("uri",source.toString());intent.putExtra(CANVAS_PROJECT_EXTRA,project.id);intent.putStringArrayListExtra(ImportedPhotoOriginals.EXTRA,ArrayList(project.protectedOriginals));loadedScope=currentScope()
      note.text="Editable image project saved. Reopen it from Saved image projects after leaving the editor."
     }.onFailure{note.text=it.message?:"Image project could not be saved. Current edits were kept."}
    }
   }
  }};dialog.show()
 }
 private fun restoreCanvasProject(){
  if(!canRequestPhotoRestore()||accessMode!=EditorAccessMode.GUEST_BASIC)return
  val id=canvasProjectId?:run{note.text="Save this image project first, or reopen one from Saved image projects.";return}
  val base=edit;val original=source;val scope=loadedScope;val generation=copyGeneration;val origins=protectedOriginals.map{it.toString()};photoProjectBusy=true;note.text="Checking saved image project…"
  fun current()=copyForeground&&copyGeneration==generation&&!activityClosed&&!isDestroyed&&!isFinishing&&photoProjectBusy&&edit==base&&source==original&&loadedScope==scope&&scope!=null&&currentScope()==scope&&canvasProjectId==id&&accessMode==EditorAccessMode.GUEST_BASIC&&protectedOriginals.map{it.toString()}==origins
  worker.execute{val saved=runCatching{CanvasProjectStore(java.io.File(filesDir,"canvas-projects")).load(id){check(current()){"Photo or session changed. Current edits were kept."}}.let{it to ImportedPhotoOriginals.validate((origins+it.protectedOriginals).distinct())}}
   runOnUiThread{val unchanged=current();photoProjectBusy=false;if(isDestroyed||isFinishing)return@runOnUiThread
    if(!unchanged){note.text="Editor changed. Current edits were kept.";return@runOnUiThread}
    saved.onSuccess{(project,restoredOriginals)->canvasProjectName=project.name;protectedOriginals=restoredOriginals.map(Uri::parse);intent.putStringArrayListExtra(ImportedPhotoOriginals.EXTRA,ArrayList(restoredOriginals));change(project.edit);note.text="Saved image project restored. Undo returns to the previous edit."}.onFailure{note.text=it.message?:"Saved project could not be restored. Current edits were kept."}
   }
  }
 }
 private fun manageAlphaGradient(){
  val scope=photoControlScope()?:return;val snapshot=edit
  val captured=photoCloseState()
  PhotoAlphaGradientControls.show(this,snapshot.alphaGradient){value->
   require(photoControlUnchanged(snapshot,scope,captured)){"Photo or session changed. Open transparency fade again."}
   if(value!=snapshot.alphaGradient){tool("alpha_gradient");changePhotoControl(snapshot,scope,captured,snapshot.copy(alphaGradient=value))}
  }
 }
 private fun manageMonochrome(){
  if(!canRequestPhotoMetadataReview())return
  val snapshot=edit
  val scope=loadedScope?.takeIf{runCatching{currentScope()==it}.getOrDefault(false)}?:run{note.text="Session changed. Reopen this photo.";return}
  val captured=photoCloseState()
  PhotoMonochromeControls.show(this,snapshot.monochrome){value->
   require(photoControlUnchanged(snapshot,scope,captured)){"Photo or account changed. Open black-and-white mix again."}
   if(value!=snapshot.monochrome){tool("monochrome_channel_mix");changePhotoControl(snapshot,scope,captured,snapshot.copy(monochrome=value))}
  }
 }
 private fun manageSelectiveColor(){
  if(!canRequestPhotoMetadataReview())return
  val snapshot=edit
  val scope=loadedScope?.takeIf{currentScope()==it}?:run{note.text="Session changed. Reopen this photo.";return}
  val captured=photoCloseState()
  PhotoSelectiveColorControls.show(this,snapshot.selectiveColor){value->
   require(photoControlUnchanged(snapshot,scope,captured)){"Photo or account changed. Open selective color again."}
   tool("selective_color_range");changePhotoControl(snapshot,scope,captured,snapshot.copy(selectiveColor=value))
  }
 }
 private fun manageRegionRecipes(){
  if(!canRequestPhotoMetadataReview())return
  val snapshot=edit
  val scope=loadedScope?.takeIf{currentScope()==it}?:run{note.text="Session changed. Reopen this photo.";return}
  val captured=photoCloseState()
  val namespace=currentPresetNamespace()?:return
  fun requireCurrent(){require(photoControlUnchanged(snapshot,scope,captured)&&currentPresetNamespace()==namespace){"Photo or account changed. Open region recipes again."}}
  val store=PhotoRegionPresetStore(java.io.File(noBackupFilesDir,"photo-region-recipes"),namespace)
  PhotoRegionPresetControls.show(this,snapshot,store,::requireCurrent){next->requireCurrent();tool("apply_region_recipe");changePhotoControl(snapshot,scope,captured,next)}
 }
 private fun manageRegions(){
  if(!canRequestPhotoMetadataReview())return
  val snapshot=edit
  val scope=loadedScope?.takeIf{currentScope()==it}?:run{note.text="Session changed. Reopen this photo.";return}
  val captured=photoCloseState()
  PhotoRegionControls.show(this,snapshot.regions){next->
   require(photoControlUnchanged(snapshot,scope,captured)){"Editor or account changed. Reopen local effect regions."}
   tool("local_effect_regions");changePhotoControl(snapshot,scope,captured,snapshot.copy(regions=next))
  }
 }
 private fun manageObjects(){
  if(!canRequestPhotoMetadataReview())return
  val snapshot=edit
  val scope=loadedScope?.takeIf{currentScope()==it}?:run{note.text="Session changed. Reopen this photo.";return}
  val captured=photoCloseState()
  PhotoLayerControls.show(this,snapshot.layers){next->
   require(photoControlUnchanged(snapshot,scope,captured)){"Editor or account changed. Reopen the object controls."}
   tool("composition_objects");changePhotoControl(snapshot,scope,captured,snapshot.copy(layers=next))
  }
 }
 private fun photoLoadCurrent(scope:String)=!activityClosed&&!isFinishing&&!isDestroyed&&runCatching{currentScope()==scope}.getOrDefault(false)
 private fun photoControlScope():String? {
  if(!canRequestPhotoMetadataReview())return null
  return loadedScope?.takeIf{runCatching{currentScope()==it}.getOrDefault(false)}?:run{note.text="Session changed. Reopen this photo.";null}
 }
 private fun photoControlUnchanged(base:PhotoEdit,scope:String)=canFinish()&&!isDestroyed&&edit==base&&loadedScope==scope&&runCatching{currentScope()==scope}.getOrDefault(false)
 private fun photoControlUnchanged(base:PhotoEdit,scope:String,captured:PhotoCloseState)=canRequestPhotoMetadataReview()&&photoControlUnchanged(base,scope)&&captured.stillMatches(photoCloseState())
 private fun commitPhotoControl(base:PhotoEdit,scope:String,captured:PhotoCloseState,next:PhotoEdit):Boolean {
  if(!photoControlUnchanged(base,scope,captured))return false
  return commitPhotoChange(next)
 }
 private fun changePhotoControl(base:PhotoEdit,scope:String,captured:PhotoCloseState,next:PhotoEdit){if(commitPhotoControl(base,scope,captured,next))preview()}
 private fun canFinish()=photoRecovery==null&&!activityClosed&&!photoProjectBusy&&!exportInProgress&&bitmap!=null&&exportEdit==null&&!readingText&&!enhancing&&!cuttingOut&&!isFinishing
 private fun chooseCreative(){if(!canFinish())return
  val tools=PhotoCreativeTool.values()
  android.app.AlertDialog.Builder(this).setTitle("Creative color").setItems(tools.map{it.title}.toTypedArray()){_,index->creativeAdjust(tools[index])}.setNegativeButton("Cancel",null).show()
 }
 private fun chooseComposition(){if(!canFinish())return
  val tools=PhotoCompositionTool.values()
  android.app.AlertDialog.Builder(this).setTitle("Create & compose").setItems(tools.map{it.title}.toTypedArray()){_,index->compositionAdjust(tools[index])}.setNegativeButton("Cancel",null).show()
 }
 private fun compositionAdjust(kind:PhotoCompositionTool){val scope=photoControlScope()?:return;val base=edit;val captured=photoCloseState()
  PhotoCompositionControls.show(this,kind,base.composition){value->
   if(photoControlUnchanged(base,scope,captured)){if(value!=base.composition){tool("composition_${kind.name.lowercase()}");changePhotoControl(base,scope,captured,base.copy(composition=value))}}
   else note.text="Photo or session changed. Open the composition tool again."
  }
 }
 private fun chooseEffects(){if(!canFinish())return
  val tools=PhotoEffectTool.values()
  android.app.AlertDialog.Builder(this).setTitle("Photo effects").setItems(tools.map{it.title}.toTypedArray()){_,index->effectAdjust(tools[index])}.setNegativeButton("Cancel",null).show()
 }
 private fun effectAdjust(kind:PhotoEffectTool){val scope=photoControlScope()?:return;val base=edit;val captured=photoCloseState()
  PhotoEffectsControls.show(this,kind,base.effects){value->
   if(photoControlUnchanged(base,scope,captured)){if(value!=base.effects){tool("effect_${kind.name.lowercase()}");changePhotoControl(base,scope,captured,base.copy(effects=value))}}
   else note.text="Photo or session changed. Open the effect again."
  }
 }
 private fun creativeAdjust(kind:PhotoCreativeTool){val scope=photoControlScope()?:return;val base=edit;val captured=photoCloseState()
  PhotoCreativeControls.show(this,kind,base.creative){value->
   if(photoControlUnchanged(base,scope,captured)){if(value!=base.creative){tool("creative_${kind.name.lowercase()}");changePhotoControl(base,scope,captured,base.copy(creative=value))}}
   else note.text="Photo or session changed. Open the tool again."
  }
 }
 private fun currentPresetNamespace():String?=runCatching {
  if(accessMode==EditorAccessMode.GUEST_BASIC)return@runCatching PhotoPresetNamespace.GUEST
  if(accessMode!=EditorAccessMode.ACCOUNT)return@runCatching null
  val session=app.honorable.auth.SecureAccountStore(this).read()?:return@runCatching null
  if(session.optLong("expiresAt")<=System.currentTimeMillis())return@runCatching null
  val account=session.optJSONObject("cached")?.optJSONObject("account")?.optString("accountId").orEmpty()
  val api=session.optString("apiUrl")
  if(account.isBlank()||!api.startsWith("https://"))return@runCatching null
  PhotoPresetNamespace.account(account,api)
 }.getOrNull()
 private fun presetShelf():Pair<String,PhotoPresetStore>? {
  if(!canFinish())return null
  val namespace=currentPresetNamespace()
  if(namespace==null||(loadedScope!=null&&currentScope()!=loadedScope)){note.text="Session changed. Reopen this photo before using saved looks.";return null}
  return namespace to PhotoPresetStore(java.io.File(noBackupFilesDir,"photo-presets"),namespace)
 }
 private fun presetAccessUnchanged(namespace:String)=canFinish()&&currentPresetNamespace()==namespace&&(loadedScope==null||currentScope()==loadedScope)
 private fun showSavedLooks(){
  if(!canFinish())return
  android.app.AlertDialog.Builder(this).setTitle("Saved photo looks")
   .setItems(arrayOf("Apply saved look","Save current adjustments","Delete saved look")){_,which->when(which){0->chooseSavedLook();1->saveCurrentLook();else->deleteSavedLook()}}
   .setNegativeButton("Cancel",null).show()
 }
 private fun saveCurrentLook(){
  val(namespace,store)=presetShelf()?:return
  val base=edit;val adjustments=PhotoAdjustmentPreset.capture(base)
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20.dp(),12.dp(),20.dp(),12.dp())}
  panel.addView(TextView(this).apply{text="Save light, color, selective color, curves and finishing for another photo. Framing, local light, effect regions, people and text are excluded. Looks stay in this local guest or account shelf.";setTextColor(Color.DKGRAY)})
  val name=EditText(this).apply{hint="Look name";contentDescription="Saved look name";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(40))};panel.addView(name)
  val dialog=android.app.AlertDialog.Builder(this).setTitle("Save photo look").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
  dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   if(edit!=base||!presetAccessUnchanged(namespace)){note.text="Photo or session changed. Open saved looks again.";dialog.dismiss();return@setOnClickListener}
   val preset=runCatching{NamedPhotoPreset(name.text.toString().trim(),adjustments)}.getOrElse{name.error="Use 1–40 characters without control characters.";return@setOnClickListener}
   val catalog=runCatching{store.load()}.getOrElse{name.error="Saved looks could not be read. Existing data was left unchanged.";return@setOnClickListener}
   if(catalog.find(preset.name)!=null){name.error="This name already exists. Choose another name or delete the old look.";return@setOnClickListener}
   if(catalog.entries.size>=PhotoPresetCatalog.MAX_ENTRIES){name.error="You can save 20 looks. Delete one before saving another.";return@setOnClickListener}
   runCatching{store.add(preset)}.onSuccess{tool("save_photo_look");note.text="Photo look saved locally. Open another photo to reuse it.";dialog.dismiss()}
    .onFailure{name.error="Unable to save this look. Existing looks were left unchanged."}
  }};dialog.show()
 }
 private fun chooseSavedLook(){
  val(namespace,store)=presetShelf()?:return
  val catalog=runCatching{store.load()}.getOrElse{note.text="Saved looks could not be read. Existing data was left unchanged.";return}
  if(catalog.entries.isEmpty()){android.app.AlertDialog.Builder(this).setTitle("No saved photo looks").setMessage("Save current adjustments, then reuse that look on another photo.").setPositiveButton("OK",null).show();return}
  val base=edit
  android.app.AlertDialog.Builder(this).setTitle("Apply saved photo look").setItems(catalog.entries.map{it.name}.toTypedArray()){_,which->
   if(edit!=base||!presetAccessUnchanged(namespace)){note.text="Photo or session changed. Open saved looks again.";return@setItems}
   tool("apply_saved_look");change(catalog.entries[which].adjustments.applyTo(edit))
  }.setNegativeButton("Cancel",null).show()
 }
 private fun deleteSavedLook(){
  val(namespace,store)=presetShelf()?:return
  val catalog=runCatching{store.load()}.getOrElse{note.text="Saved looks could not be read. Existing data was left unchanged.";return}
  if(catalog.entries.isEmpty()){note.text="No saved photo looks to delete.";return}
  android.app.AlertDialog.Builder(this).setTitle("Delete saved photo look").setItems(catalog.entries.map{it.name}.toTypedArray()){_,which->
   val name=catalog.entries[which].name
   android.app.AlertDialog.Builder(this).setTitle("Delete this look?").setMessage(name).setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->
    if(!presetAccessUnchanged(namespace)){note.text="Session changed. Open saved looks again.";return@setPositiveButton}
    runCatching{store.remove(name)}.onSuccess{note.text="Saved look deleted. Current photo edits are unchanged."}
     .onFailure{note.text="Unable to delete this look. Existing looks were left unchanged."}
   }.show()
  }.setNegativeButton("Cancel",null).show()
 }
 private fun chooseRatio(){val scope=photoControlScope()?:return;val base=edit;val captured=photoCloseState()
  android.app.AlertDialog.Builder(this).setTitle("Photo aspect ratio").setItems(arrayOf("Original","Square 1:1","Portrait 4:5","Wide 16:9","Vertical 9:16")){_,i->if(photoControlUnchanged(base,scope,captured))changePhotoControl(base,scope,captured,base.copy(finish=base.finish.copy(ratio=listOf(0f,1f,.8f,16f/9,9f/16)[i])))}.setNegativeButton("Cancel",null).show()
 }
 private fun chooseMirror(){val scope=photoControlScope()?:return;val base=edit;val captured=photoCloseState()
  android.app.AlertDialog.Builder(this).setTitle("Mirror photo").setItems(arrayOf("Flip horizontally","Flip vertically","Reset mirrors")){_,i->if(photoControlUnchanged(base,scope,captured))changePhotoControl(base,scope,captured,base.copy(finish=when(i){0->base.finish.copy(mirrorX=!base.finish.mirrorX);1->base.finish.copy(mirrorY=!base.finish.mirrorY);else->base.finish.copy(mirrorX=false,mirrorY=false)}))}.setNegativeButton("Cancel",null).show()
 }
 private fun finishAdjust(title:String,kind:Int){val scope=photoControlScope()?:return;val base=edit;val captured=photoCloseState()
  PhotoFinishControls.adjust(this,title,base.finish,kind){f->if(photoControlUnchanged(base,scope,captured))changePhotoControl(base,scope,captured,base.copy(finish=f))else note.text="Photo or session changed. Open the tool again."}
 }
 private fun editCaption(){val scope=photoControlScope()?:return;val base=edit;val captured=photoCloseState()
  PhotoFinishControls.caption(this,base.finish.caption){caption->if(photoControlUnchanged(base,scope,captured))changePhotoControl(base,scope,captured,base.copy(finish=base.finish.copy(caption=caption)))else note.text="Photo or session changed. Open text again."}
 }
 private fun chooseExport(){val scope=photoControlScope()?:return;val base=edit;val captured=photoCloseState()
  android.app.AlertDialog.Builder(this).setTitle("Export photo").setItems(arrayOf("PNG · transparency","JPEG · compact (70%)","JPEG · balanced (85%)","JPEG · high quality (95%)")){_,i->
   if(!photoControlUnchanged(base,scope,captured)){note.text="Photo or session changed. Open export again.";return@setItems}
   exportEncoding=if(i==0)SafePhotoCopy.Encoding.PNG else SafePhotoCopy.Encoding.JPEG;exportQuality=listOf(100,70,85,95)[i]
   exportEdit=base;waitingForPicker=true
   try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=exportEncoding.mime;putExtra(Intent.EXTRA_TITLE,"Noble-copy.${exportEncoding.extension}")},20)}catch(e:Exception){waitingForPicker=false;exportEdit=null;note.text="No app is available to save a copy."}
  }.setNegativeButton("Cancel",null).show()
 }
 private fun chooseBackground(){
   if(!allowOnDeviceAi())return
  val scope=photoControlScope()?:return
  val base=edit;val captured=photoCloseState();val request=revision
  fun commit(background:PhotoBackground?){
   if(!photoControlUnchanged(base,scope,captured)||request!=revision){note.text="Photo or session changed. Open person background again.";return}
   if(background==null){tool("person_background");changePhotoControl(base,scope,captured,base.copy(personCutout=false,background=null))}else cutOutPerson(background=background)
  }
  val names=arrayOf("Blur background","White background","Black background","Gray background","Restore original background")
  android.app.AlertDialog.Builder(this).setTitle("AI person background · beta").setItems(names){_,which->
   if(!photoControlUnchanged(base,scope,captured)||request!=revision){note.text="Photo or session changed. Open person background again.";return@setItems}
   when(which){
    0->{var strength=base.background?.strength?:45
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
   if(!allowOnDeviceAi())return
  val input=bitmap?:return
  if(photoProjectBusy||activityClosed||cuttingOut||readingText||enhancing||(!recover&&exportEdit!=null)||isFinishing||isDestroyed)return
  val scope=loadedScope?.takeIf{runCatching{currentScope()==it}.getOrDefault(false)}?:run{note.text="Session changed. Reopen this photo.";return}
  val initial=edit;val pending=exportEdit
  fun sourceUnchanged()=!activityClosed&&!isDestroyed&&!isFinishing&&bitmap===input&&loadedScope==scope&&runCatching{currentScope()==scope}.getOrDefault(false)
  fun cancelRecovery(){if(recover&&exportEdit==pending){exportEdit=null;pendingDestination=null}}
  fun discardStale(){cancelRecovery();if(!activityClosed&&!isDestroyed&&!isFinishing&&bitmap===input&&loadedScope==scope)note.text="Session changed. Reopen this photo."}
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
     if(!sourceUnchanged()){owned.recycle();cuttingOut=false;discardStale();return@runOnUiThread}
     runCatching {
      cutoutJob=PersonCutoutProcessor.start(owned){result->
       cutoutJob=null;cuttingOut=false
       val mask=result.getOrNull()
       if(!sourceUnchanged()){mask?.recycle();discardStale();return@start}
       if(mask==null){cancelRecovery();note.text=if(result.exceptionOrNull() is PersonMaskAlpha.NoClearPerson)"No clear person boundary found. Try a photo with a person." else "Person cutout unavailable. Try again.";return@start}
       val oldMask=personMask;personMask=mask;if(oldMask!==mask)oldMask?.recycle()
       if(recover){preview();resumePendingExport()}
       else if(edit==initial&&request==revision)applyPersonEdit()
       else note.text="Person mask ready. Open the person tools to apply it to your current edit."
      }
     }.onFailure{owned.recycle();cuttingOut=false;cancelRecovery();if(sourceUnchanged())note.text="Person cutout unavailable. Try again." else discardStale()}
    }
   }catch(error:Throwable){runOnUiThread{cuttingOut=false;cancelRecovery();if(sourceUnchanged())note.text="Not enough resources for person cutout. Try a smaller photo." else discardStale()}}
  }
 }
 private fun showCurve(){
  if(!canFinish()||isDestroyed)return
  val base=edit;val request=revision
  val scope=loadedScope?.takeIf{runCatching{currentScope()==it}.getOrDefault(false)}?:run{note.text="Session changed. Reopen this photo.";return}
  fun unchanged()=canFinish()&&!isDestroyed&&edit==base&&request==revision&&loadedScope==scope&&runCatching{currentScope()==scope}.getOrDefault(false)
  PhotoToneControls.curve(this,base.curve){curve->if(unchanged()){tool("tone_curve");change(base.copy(curve=curve))}else note.text="Photo or session changed. Open the curve tool again."}
 }
 private fun showLocalLight(){
  if(!canFinish())return
  val base=edit;val request=revision
  val scope=loadedScope?.takeIf{runCatching{currentScope()==it}.getOrDefault(false)}?:run{note.text="Session changed. Reopen this photo.";return}
  fun unchanged()=canFinish()&&!isDestroyed&&edit==base&&request==revision&&loadedScope==scope&&runCatching{currentScope()==scope}.getOrDefault(false)
  PhotoToneControls.light(this,base.radialLight,{light->if(unchanged()){tool("local_light");change(edit.copy(radialLight=light))}else note.text="Photo changed. Open local light again."},{light->
   if(unchanged()){clearObjectPick();before=false;preview();placingLight=light;placingOn=edit;placingScope=scope;note.text="Tap the edited photo to place the local light."}
  })
 }
 private fun chooseLook(){
  if(!canFinish()||isDestroyed)return
  if(!allowOnDeviceAi())return
  val names=arrayOf("Auto scene look","Cinematic","Soft portrait","Vivid","Monochrome")
  android.app.AlertDialog.Builder(this).setTitle("AI-assisted photo look").setItems(names){_,which->enhanceScene(PhotoLook.values()[which])}.setNegativeButton("Cancel",null).show()
 }
 private fun enhanceScene(look:PhotoLook=PhotoLook.AUTO) {
  if(!canFinish()||isDestroyed)return
  if(!allowOnDeviceAi())return
  val input=bitmap?:return
  if(photoProjectBusy||readingText||enhancing||cuttingOut||exportEdit!=null||isFinishing)return
  val initial=edit;val request=revision
  val scope=loadedScope?.takeIf{runCatching{currentScope()==it}.getOrDefault(false)}?:run{note.text="Session changed. Reopen this photo.";return}
  fun sourceUnchanged()=!activityClosed&&!isDestroyed&&!isFinishing&&bitmap===input&&loadedScope==scope&&runCatching{currentScope()==scope}.getOrDefault(false)
  fun unchanged()=!photoProjectBusy&&exportEdit==null&&edit==initial&&request==revision&&sourceUnchanged()
  val owned=runCatching{requireNotNull(input.copy(Bitmap.Config.ARGB_8888,false))}.getOrNull()?:run{note.text="Not enough memory for scene analysis.";return}
  if(!unchanged()){owned.recycle();return}
  enhancing=true;note.text="Analyzing this photo on your device…"
  val labeler=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
  try{labeler.process(InputImage.fromBitmap(owned,0))
   .addOnSuccessListener { labels ->
    if(isDestroyed||isFinishing)return@addOnSuccessListener
    if(!unchanged()){if(sourceUnchanged()&&!photoProjectBusy&&exportEdit==null)note.text="Photo changed during analysis. Run scene enhance again.";return@addOnSuccessListener}
    val scene=labels.filter{it.confidence>=.55f}.map{it.text.lowercase()}
    val pixels=IntArray(32*32){index->owned.getPixel(((index%32+.5f)*owned.width/32).toInt().coerceAtMost(owned.width-1),((index/32+.5f)*owned.height/32).toInt().coerceAtMost(owned.height-1))}
    val next=runCatching{PhotoAutoLook.apply(initial,PhotoLightStats.measure(pixels),scene.toSet(),look)}.getOrElse{note.text="No visible photo content to analyze. Try manual adjustments.";return@addOnSuccessListener}
    if(!unchanged()){if(sourceUnchanged()&&!photoProjectBusy&&exportEdit==null)note.text="Photo changed during analysis. Run scene enhance again.";return@addOnSuccessListener}
    tool("ai_scene_enhance");change(next)
    note.text="Scene-aware tone and color look applied on device. Compare before saving a copy."
   }
   .addOnFailureListener { if(unchanged())note.text="Scene analysis failed. Try manual adjustments." }
   .addOnCompleteListener { enhancing=false;labeler.close();owned.recycle() }
  }catch(e:Exception){enhancing=false;labeler.close();owned.recycle();if(unchanged())note.text="Scene analysis failed. Try manual adjustments."}
 }
 private fun finishTextRead(request:PhotoTextReadGate.Request,message:String?,current:()->Boolean) {
  textReadGate.settle(request)
  readingText=textReadGate.busy
  if(message!=null&&current())note.text=message
 }
 private fun readText() {
  if(!copyForeground||!allowOnDeviceAi())return
  val input=bitmap?:return
  val scope=photoControlScope()?:return
  val state=edit
  val generation=copyGeneration
  val request=textReadGate.begin(generation,copyForeground)?:return
  fun current()=textReadGate.isCurrent(request,copyGeneration,copyForeground)&&!activityClosed&&!isDestroyed&&!isFinishing&&!photoProjectBusy&&exportEdit==null&&bitmap===input&&edit==state&&loadedScope==scope&&runCatching{currentScope()==scope}.getOrDefault(false)
  readingText=textReadGate.busy
  note.text="Reading text on this device…"
  try { worker.execute {
   try {
    if(!current()){runOnUiThread{finishTextRead(request,null,::current)};return@execute}
    val snapshot=render(input,state)
    runOnUiThread {
     if(!current()){snapshot.recycle();finishTextRead(request,null,::current);return@runOnUiThread}
     PhotoTextReader.read(this,snapshot,::current){message->finishTextRead(request,message,::current)}
    }
   } catch(e:Exception) {
    runOnUiThread{finishTextRead(request,"Unable to read this photo. Try again.",::current)}
   } catch(e:OutOfMemoryError) {
    runOnUiThread{finishTextRead(request,"Not enough memory to read this photo. Try a smaller image.",::current)}
   }
  } } catch(e:java.util.concurrent.RejectedExecutionException) {
   finishTextRead(request,"Unable to start text recognition. Your photo was kept.",::current)
  }
 }
 @Deprecated("Platform result API")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=20)return
  markPhotoRecoveryPickerResult()
  waitingForPicker=false
  if(exportEdit==null){pendingDestination=null;note.text="Save interrupted. Choose Save copy again.";return}
  if(resultCode!=RESULT_OK||data?.data==null){exportEdit=null;return}
  pendingDestination=requireNotNull(data.data)
  resumePendingExport()
 }
 private fun capturePhotoExport(destination:Uri,state:PhotoEdit):PhotoExportOperation? {
  if(!copyForeground||photoRecovery!=null||activityClosed||isDestroyed||isFinishing||exportInProgress||bitmap==null||exportEdit!==state||!::source.isInitialized)return null
  val mode=accessMode?:return null;val scope=loadedScope?.takeIf{currentScope()==it}?:return null
  return runCatching{PhotoExportOperation(state,source.toString(),destination.toString(),scope,mode,copyGeneration,exportEncoding,exportQuality,protectedOriginals.map{it.toString()})}.getOrNull()
 }
 private fun photoExportCurrent(operation:PhotoExportOperation):Boolean {
  if(photoExport!==operation||operation.isCancelled||!exportInProgress||!copyForeground||photoRecovery!=null||activityClosed||isDestroyed||isFinishing||bitmap==null||!::source.isInitialized)return false
  return operation.matches(exportEdit,source.toString(),loadedScope,currentScope(),accessMode,copyGeneration,protectedOriginals.map{it.toString()})
 }
 private fun publishPhotoExportNotice(){
  val notice=photoExportNotice?:return;val operation=notice.first
  if(!copyForeground||activityClosed||isDestroyed||isFinishing||photoExport!=null||photoRecovery!=null||!::note.isInitialized)return
  if(!::source.isInitialized||source.toString()!=operation.source||loadedScope!=operation.scope||currentScope()!=operation.scope||accessMode!=operation.mode){photoExportNotice=null;return}
  note.text=notice.second;photoExportNotice=null
 }
 private fun completePhotoExport(operation:PhotoExportOperation,dimensions:String?,failure:Throwable?){
  if(photoExport!==operation)return
  val current=runCatching{photoExportCurrent(operation)}.getOrDefault(false)
  photoExport=null;exportInProgress=false;if(exportEdit===operation.edit)exportEdit=null
  val message=when{
   !current||failure is PhotoExportOperation.Cancelled->"Export stopped when the editor changed. Check Files for an empty, partial or completed copy before retrying."
   failure is OutOfMemoryError->"Not enough memory to save this photo. Check Files for a partial copy, then choose Export again."
   failure!=null->"Export failed. Choose a local writable destination; check Files for a partial copy."
   else->"Copy saved · $dimensions px. Original preserved. Large photos are reduced for memory safety."
  }
  photoExportNotice=operation to message;publishPhotoExportNotice()
 }
 private fun resumePendingExport(){
  if(photoRecovery!=null||!copyForeground||activityClosed||isFinishing||isDestroyed)return
  val destination=pendingDestination?:return
  if(bitmap==null||exportInProgress)return
  val state=exportEdit?:run{pendingDestination=null;return}
  val operation=capturePhotoExport(destination,state)?:run{pendingDestination=null;exportEdit=null;note.text="Photo or session changed. Reopen this photo before saving.";return}
  if(state.needsPersonMask&&personMask==null){cutOutPerson(true);return}
  val mask=personMask
  pendingDestination=null;exportInProgress=true;photoExport=operation;photoExportNotice=null
  note.text="Exporting copy…"
  try{worker.execute {
   var input:Bitmap?=null;var output:Bitmap?=null;var dimensions:String?=null;var failure:Throwable?=null
   fun requireCurrent(){operation.requireCurrent{photoExportCurrent(operation)}}
   try {
    requireCurrent();authorizeEditor();requireCurrent()
    val decoded=PhotoSourceDecoder.decode(this,Uri.parse(operation.source),8192);input=decoded;requireCurrent()
    val result=PhotoRasterRenderer.render(decoded,operation.edit,mask);output=result;requireCurrent()
    SafePhotoCopy.save(this,Uri.parse(operation.source),Uri.parse(operation.destination),result,operation.encoding,operation.quality,operation.originals.map(Uri::parse),::requireCurrent)
    requireCurrent();dimensions="${result.width} × ${result.height}"
    runCatching{emit("edit_saved",JSONObject().put("mediaType","IMAGE"))}
   }catch(error:Throwable){failure=error;if(error !is PhotoExportOperation.Cancelled&&error !is OutOfMemoryError&&runCatching{photoExportCurrent(operation)}.getOrDefault(false))runCatching{emit("client_error",JSONObject().put("errorCode","SAVE_FAILED"))}}
   finally{if(output!==input)output?.recycle();input?.recycle()}
   runOnUiThread{completePhotoExport(operation,dimensions,failure)}
  }}catch(error:java.util.concurrent.RejectedExecutionException){completePhotoExport(operation,null,error)}
 }
 override fun onSaveInstanceState(outState:Bundle){
  super.onSaveInstanceState(outState)
  outState.putString("canvasProjectId",canvasProjectId);outState.putString("canvasProjectName",canvasProjectName)
  if(canvasProjectId!=null&&::source.isInitialized)outState.putString("canvasSourceUri",source.toString())
  outState.putStringArrayList("protectedPhotoOriginals",ArrayList(protectedOriginals.map{it.toString()}))
  outState.putString("pendingStartLook",startLook?.name);outState.putString("pendingQuickStart",quickStart?.name)
   if(accessMode==EditorAccessMode.GUEST_BASIC)guestInstance?.let{outState.putString("guestEditorInstance",it)}
  outState.putInt("photoEncoding",exportEncoding.ordinal);outState.putInt("photoQuality",exportQuality)
  if(savePhotoRecoveryState(outState))return
  outState.remove("photoRecoveryExportInvalidated")
  runCatching {
   val scope=currentScope()?:return@runCatching
   val pending=restoredSession?.takeIf{it.scope==scope}
   if(restoredSession!=null&&pending==null)return@runCatching
   val snapshot=pending?.copy(pendingExport=if(waitingForPicker)exportEdit else null)?:PhotoSessionSnapshot(scope,edit,undo.toList(),redo.toList(),before,activeTool,
    if(waitingForPicker&&!exportInProgress&&pendingDestination==null)exportEdit else null)
   if(loadedScope==null||loadedScope==scope)outState.putByteArray("photoSession",PhotoSessionCodec.encodeForRecovery(snapshot))
  }
 }
 private fun restorePhotoRecovery(bytes:ByteArray?,exportInvalidated:Boolean){
  photoRecovery=bytes?.let(::PhotoSessionRecovery);recoveryExportInvalidated=exportInvalidated
  restoredSession=photoRecovery?.decode(currentScope())?.getOrNull()
  exportEdit=if(recoveryExportInvalidated)null else restoredSession?.pendingExport;waitingForPicker=exportEdit!=null
 }
 private fun markPhotoRecoveryPickerResult(){if(photoRecovery!=null)recoveryExportInvalidated=true}
 private fun photoRecoveryNotice(){if(::note.isInitialized)note.text="Previous photo edits are still kept for recovery. Retry recovery or review Start original before editing or saving."}
 private fun canRequestPhotoRecovery()=photoRecovery!=null&&copyForeground&&!activityClosed&&!isDestroyed&&!isFinishing&&!photoLoading&&!photoProjectBusy&&!readingText&&!enhancing&&!cuttingOut&&!exportInProgress
 private fun refreshPhotoRecoveryPanel(){
  photoRecoveryPanel?.visibility=if(photoRecovery==null)View.GONE else View.VISIBLE
  val ready=canRequestPhotoRecovery();photoRecoveryRetry?.isEnabled=ready;photoRecoveryStart?.isEnabled=ready
  sliders.forEach{it.isEnabled=photoRecovery==null}
 }
 private fun savePhotoRecoveryState(outState:Bundle):Boolean {
  val pending=photoRecovery?:return false
  outState.putByteArray("photoSession",pending.bytesForState())
  outState.putBoolean("photoRecoveryExportInvalidated",recoveryExportInvalidated||!waitingForPicker)
  return true
 }
 private fun photoRecoveryCurrent(pending:PhotoSessionRecovery,scope:String)=photoRecovery===pending&&!activityClosed&&!isDestroyed&&!isFinishing&&bitmap!=null&&loadedScope==scope&&currentScope()==scope
 private fun applyPhotoRecovery(pending:PhotoSessionRecovery,scope:String):Int {
  check(photoRecoveryCurrent(pending,scope)){"Photo session changed"}
  val saved=pending.decode(scope).getOrThrow();val previous=saved.undo.toMutableList();val future=saved.redo.toMutableList()
  check(photoRecoveryCurrent(pending,scope)){"Photo session changed"}
  edit=saved.edit;undo=previous;redo=future;before=saved.compareOriginal;activeTool=saved.activeTool
  // Picker results may have arrived or been cancelled while the original loaded. Keep that live state.
  restoredSession=null;photoRecovery=null
  return activeTool
 }
 private fun finishPhotoOpening(){
  refreshPhotoRecoveryPanel()
  if(photoRecovery!=null){preview();photoRecoveryNotice();return}
  recoveryToolSelection?.invoke(activeTool);preview();resumePendingExport()
  startLook?.let{look->startLook=null;enhanceScene(look)}
  quickStart?.let{preset->quickStart=null;tool("quick_${preset.name.lowercase()}");change(PhotoQuickStart.apply(edit,preset))}
 }
 private fun retryPhotoRecovery(){
  if(!canRequestPhotoRecovery())return
  val pending=photoRecovery?:return
  if(bitmap==null){recreate();return}
  val scope=loadedScope?:run{photoRecoveryNotice();return}
  runCatching{applyPhotoRecovery(pending,scope)}.onSuccess{finishPhotoOpening()}.onFailure{photoRecoveryNotice();refreshPhotoRecoveryPanel()}
 }
 private fun canApprovePhotoRecoveryDiscard(token:Any,pending:PhotoSessionRecovery,captured:PhotoCloseState)=photoRecoveryDiscardToken===token&&canRequestPhotoRecovery()&&photoRecovery===pending&&captured.stillMatches(photoCloseState())
 private fun discardPhotoRecovery(token:Any,pending:PhotoSessionRecovery,captured:PhotoCloseState):Boolean {
  if(!canApprovePhotoRecoveryDiscard(token,pending,captured))return false
  edit=PhotoEdit();undo=mutableListOf();redo=mutableListOf();before=false;activeTool=0
  restoredSession=null;photoRecovery=null;exportEdit=null;pendingDestination=null;waitingForPicker=false;recoveryExportInvalidated=true;startLook=null;quickStart=null
  return true
 }
 private fun closePhotoRecoveryDiscard(){photoRecoveryDiscardToken=null;val dialog=photoRecoveryDiscard;photoRecoveryDiscard=null;runCatching{dialog?.dismiss()}}
 private fun requestPhotoRecoveryDiscard(){
  if(!canRequestPhotoRecovery()||photoRecoveryDiscardToken!=null)return
  val pending=photoRecovery?:return;val captured=photoCloseState();val token=Any()
  val dialog=android.app.AlertDialog.Builder(this).setTitle("Discard unrecovered photo session?")
   .setMessage("Discards the retained session edits and Undo history so you can start from the original image. This cannot be undone here. Existing saved projects, exported copies and original media are kept.")
   .setNegativeButton("Keep recovery"){_,_->if(photoRecoveryDiscardToken===token)closePhotoRecoveryDiscard()}
   .setPositiveButton("Start original"){_,_->
    if(photoRecoveryDiscardToken!==token)return@setPositiveButton
    val discarded=discardPhotoRecovery(token,pending,captured);closePhotoRecoveryDiscard()
    if(discarded){finishPhotoOpening();note.text=if(bitmap==null)"Recovery discarded. Reopen the original to load it again."else "Started from the original. Saved projects and original media were kept."}else photoRecoveryNotice()
   }.create()
  photoRecoveryDiscardToken=token;photoRecoveryDiscard=dialog
  dialog.setOnCancelListener{if(photoRecoveryDiscardToken===token)closePhotoRecoveryDiscard()}
  dialog.setOnDismissListener{if(photoRecoveryDiscardToken===token){photoRecoveryDiscardToken=null;photoRecoveryDiscard=null}}
  dialog.show()
 }
 private fun canRequestPhotoClose()=copyForeground&&!activityClosed&&!isDestroyed&&!isFinishing
 private fun photoCloseState():PhotoCloseState {
  val operations=(if(photoProjectBusy)1 else 0)or(if(readingText)2 else 0)or(if(enhancing)4 else 0)or(if(cuttingOut)8 else 0)or
   (if(exportInProgress)16 else 0)or(if(waitingForPicker)32 else 0)or(if(exportEdit!=null)64 else 0)or(if(pendingDestination!=null)128 else 0)
  val original=if(::source.isInitialized)source else null
  val unsaved=canvasProjectId==null&&original?.authority?.endsWith(".creation")==true&&original.authority==packageName+".creation"
  return PhotoCloseState.capture(edit,undo,redo,operations,photoRecovery?:restoredSession,original?.toString(),loadedScope,accessMode,canvasProjectId,unsaved,
   protectedOriginals.map{it.toString()},copyGeneration,revision,bitmap,exportEdit,pendingDestination?.toString())
 }
 private fun canApprovePhotoClose(token:Any,captured:PhotoCloseState)=photoCloseToken===token&&canRequestPhotoClose()&&captured.stillMatches(photoCloseState())
 private fun closePhotoCloseReview(){photoCloseToken=null;val dialog=photoCloseDialog;photoCloseDialog=null;runCatching{dialog?.dismiss()}}
 private fun requestPhotoClose(){
  if(!canRequestPhotoClose()||photoCloseToken!=null)return
  val captured=photoCloseState()
  if(!captured.needsConfirmation()){finish();return}
  val message=when{
   captured.recoveryPending->"Photo session recovery is still pending. Leaving discards this in-memory recovery. Saved projects, exported copies and original media are kept."
   captured.working->"An operation is in progress. Leaving may interrupt it; check Files for an empty or partial copy. Saved projects and original media are kept."
   captured.unsavedCreatedSource->"This image workspace may not be saved. Keep editing to save an editable image project or export a copy first. Existing saved projects and original media are kept."
   else->"Current photo edits and Undo history may be lost when you leave. Keep editing to save editable edits or export a copy first. Existing saved projects, exported copies and original media are kept."
  }
  val token=Any()
  val dialog=android.app.AlertDialog.Builder(this).setTitle("Leave photo editor?").setMessage(message)
   .setNegativeButton("Keep editing"){_,_->if(photoCloseToken===token)closePhotoCloseReview()}
   .setPositiveButton("Leave editor"){_,_->
    if(photoCloseToken!==token)return@setPositiveButton
    val approved=canApprovePhotoClose(token,captured);closePhotoCloseReview()
    if(approved)finish()else if(::note.isInitialized)note.text="Photo or operation changed. Review it before closing again."
   }.create()
  photoCloseToken=token;photoCloseDialog=dialog
  dialog.setOnCancelListener{if(photoCloseToken===token)closePhotoCloseReview()}
  dialog.setOnDismissListener{if(photoCloseToken===token){photoCloseToken=null;photoCloseDialog=null}}
  dialog.show()
 }
 @Deprecated("Legacy Back path before the platform callback API")
 override fun onBackPressed(){requestPhotoClose()}
 override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)emit("performance_sample",JSONObject().put("metric","MEMORY_PRESSURE").put("durationMs",0))}
 override fun onResume(){super.onResume();copyGeneration++;copyForeground=true;refreshPhotoRecoveryPanel();if(photoExport?.isCancelled==true&&::note.isInitialized)note.text="Stopping the previous export. Check Files before retrying.";publishPhotoExportNotice();resumePendingExport()}
 override fun onPause(){neuralUpscaleControls?.close();subjectAssetControls?.close();subjectAssetControls=null;photoObjectArrange?.close();photoObjectArrange=null;photoTextReplace?.close();photoTextReplace=null;closePhotoDestructiveReview();photoVersions?.close();photoVersions=null;closePhotoSettingsReview();photoExport?.cancel();closePhotoRecoveryDiscard();closePhotoCloseReview();closePhotoRestoreReview();copyForeground=false;copyGeneration++;colorPaletteControls?.close();colorPaletteControls=null;super.onPause()}
 override fun onDestroy(){neuralUpscaleControls?.close();subjectAssetControls?.close();subjectAssetControls=null;photoObjectArrange?.close();photoObjectArrange=null;photoTextReplace?.close();photoTextReplace=null;closePhotoDestructiveReview();photoVersions?.close();photoVersions=null;closePhotoSettingsReview();photoExport?.cancel();closePhotoRecoveryDiscard();recoveryToolSelection=null;closePhotoCloseReview();runCatching{releasePhotoBack?.invoke()};releasePhotoBack=null;closePhotoRestoreReview();activityClosed=true;emit("editor_closed",JSONObject().put("durationMs",(System.currentTimeMillis()-openedAt).coerceIn(0,3600000)));revision++;cutoutJob?.close();cutoutJob=null;if(::image.isInitialized)image.setImageDrawable(null);rendered?.recycle();rendered=null;worker.execute{bitmap?.recycle();bitmap=null;personMask?.recycle();personMask=null};worker.shutdown();super.onDestroy()}
}
