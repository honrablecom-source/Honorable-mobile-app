package app.honorable.editor

import android.graphics.Bitmap
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

/** Actual production Activity guards; no Android dialogs, rendering, storage or lifecycle execution. */
private object RestoreFixture {
 private val type=Class.forName("sun.misc.Unsafe")
 private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
 private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
 fun set(activity:PhotoEditorActivity,name:String,value:Any?)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)
 fun get(activity:PhotoEditorActivity,name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)
 fun call(activity:PhotoEditorActivity,name:String,vararg args:Any?):Any? {
  val method=PhotoEditorActivity::class.java.declaredMethods.single{it.name==name&&it.parameterCount==args.size}.apply{isAccessible=true}
  return method.invoke(activity,*args)
 }
 fun activity()=(allocate(PhotoEditorActivity::class.java)as PhotoEditorActivity).also{
  set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"bitmap",allocate(Bitmap::class.java))
 }
 fun ready():PhotoEditorActivity=activity().also{
  set(it,"copyForeground",true);set(it,"copyGeneration",7L);set(it,"edit",PhotoEdit(brightness=.2f))
  set(it,"source",Uri.parse("content://synthetic/original"));set(it,"protectedOriginals",emptyList<Uri>())
  set(it,"guestInstance","00000000-0000-0000-0000-000000000001")
  set(it,"loadedScope",call(it,"currentScope"));set(it,"undo",mutableListOf<PhotoEdit>());set(it,"redo",mutableListOf<PhotoEdit>())
 }
 fun approve(activity:PhotoEditorActivity,token:Any,base:PhotoEdit=PhotoEdit(brightness=.2f),original:Uri=Uri.parse("content://synthetic/original"),scope:String=get(activity,"loadedScope")as String,namespace:String=PhotoPresetNamespace.GUEST,generation:Long=7L,projectId:String?=null,origins:List<String> = emptyList(),mode:EditorAccessMode=EditorAccessMode.GUEST_BASIC)=
  call(activity,"canApprovePhotoRestore",token,base,original,scope,namespace,generation,projectId,origins,mode)as Boolean
}

class PhotoRestoreForegroundTest {
 @Test fun pausedOriginalAndCanvasRestoreRefuseBeforeScopeStorageOrUi(){
  for(command in listOf("photoProjectOperation","restoreCanvasProject")){
   val activity=RestoreFixture.activity()
   assertEquals(true,RestoreFixture.call(activity,"canFinish"))
   if(command=="photoProjectOperation")RestoreFixture.call(activity,command,"restore")else RestoreFixture.call(activity,command)
  }
 }
 @Test fun foregroundBusyOriginalAndCanvasRestoreStillRefuse(){
  for(flag in listOf("readingText","enhancing","cuttingOut","photoProjectBusy","activityClosed")){
   val activity=RestoreFixture.activity();RestoreFixture.set(activity,"copyForeground",true);RestoreFixture.set(activity,flag,true)
   RestoreFixture.call(activity,"photoProjectOperation","restore");RestoreFixture.call(activity,"restoreCanvasProject")
  }
 }
}

class PhotoRestoreReviewTest {
 @Test fun requestAdmissionRefusesPausedBusyUnloadedOrClosedEditorBeforeUi(){
  val paused=RestoreFixture.activity();RestoreFixture.call(paused,"requestPhotoRestore")
  val unloaded=RestoreFixture.activity();RestoreFixture.set(unloaded,"copyForeground",true);RestoreFixture.call(unloaded,"requestPhotoRestore")
  for(flag in listOf("readingText","enhancing","cuttingOut","photoProjectBusy","activityClosed")){
   val activity=RestoreFixture.activity();RestoreFixture.set(activity,"copyForeground",true);RestoreFixture.set(activity,flag,true)
   assertEquals(false,RestoreFixture.call(activity,"canRequestPhotoRestore"));RestoreFixture.call(activity,"requestPhotoRestore")
  }
 }
 @Test fun reviewRequiresCurrentDialogAndEveryCapturedProjectSessionField(){
  val activity=RestoreFixture.ready();val token=Any();RestoreFixture.set(activity,"photoRestoreReviewToken",token)
  assertTrue(RestoreFixture.approve(activity,token));assertFalse(RestoreFixture.approve(activity,Any()))
  assertFalse(RestoreFixture.approve(activity,token,base=PhotoEdit()))
  assertFalse(RestoreFixture.approve(activity,token,original=Uri.parse("content://synthetic/other")))
  assertFalse(RestoreFixture.approve(activity,token,scope="other"))
  assertFalse(RestoreFixture.approve(activity,token,namespace="other"))
  assertFalse(RestoreFixture.approve(activity,token,generation=6L))
  assertFalse(RestoreFixture.approve(activity,token,projectId="other"))
  assertFalse(RestoreFixture.approve(activity,token,origins=listOf("content://synthetic/other")))
  assertFalse(RestoreFixture.approve(activity,token,mode=EditorAccessMode.ACCOUNT))
  RestoreFixture.set(activity,"guestInstance","00000000-0000-0000-0000-000000000002")
  assertFalse(RestoreFixture.approve(activity,token))
 }
 @Test fun pauseGenerationBusyAndReplacedOrClosedReviewCannotApprove(){
  for(flag in listOf("readingText","enhancing","cuttingOut","photoProjectBusy","activityClosed")){
   val activity=RestoreFixture.ready();val token=Any();RestoreFixture.set(activity,"photoRestoreReviewToken",token);RestoreFixture.set(activity,flag,true)
   assertFalse(RestoreFixture.approve(activity,token))
  }
  val activity=RestoreFixture.ready();val token=Any();RestoreFixture.set(activity,"photoRestoreReviewToken",token)
  RestoreFixture.set(activity,"copyForeground",false);assertFalse(RestoreFixture.approve(activity,token))
  RestoreFixture.set(activity,"copyForeground",true);RestoreFixture.set(activity,"copyGeneration",9L);assertFalse(RestoreFixture.approve(activity,token))
  RestoreFixture.set(activity,"copyGeneration",7L);val replacement=Any();RestoreFixture.set(activity,"photoRestoreReviewToken",replacement)
  assertFalse(RestoreFixture.approve(activity,token));assertTrue(RestoreFixture.approve(activity,replacement))
  RestoreFixture.call(activity,"closePhotoRestoreReview");assertNull(RestoreFixture.get(activity,"photoRestoreReviewToken"));assertFalse(RestoreFixture.approve(activity,replacement))
 }
 @Test fun untouchedOriginalDirectButEditsHistoryAndSavedCanvasNeedReview(){
  val activity=RestoreFixture.ready();assertEquals(true,RestoreFixture.call(activity,"photoRestoreNeedsReview"))
  RestoreFixture.set(activity,"edit",PhotoEdit());assertEquals(false,RestoreFixture.call(activity,"photoRestoreNeedsReview"))
  for(history in listOf("undo","redo")){
   RestoreFixture.set(activity,history,mutableListOf(PhotoEdit()));assertEquals(true,RestoreFixture.call(activity,"photoRestoreNeedsReview"));RestoreFixture.set(activity,history,mutableListOf<PhotoEdit>())
  }
  RestoreFixture.set(activity,"canvasProjectId","saved");assertEquals(true,RestoreFixture.call(activity,"photoRestoreNeedsReview"))
 }
}
