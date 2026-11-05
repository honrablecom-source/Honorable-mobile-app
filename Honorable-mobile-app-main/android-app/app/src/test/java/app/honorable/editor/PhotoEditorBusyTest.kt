package app.honorable.editor

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test

/** Constructor-free admission checks. No bitmap access, worker, inference or Android UI execution. */
class PhotoEditorBusyTest {
 private val allocatorType=Class.forName("sun.misc.Unsafe")
 private val allocator=allocatorType.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
 private fun allocate(type:Class<*>)=allocatorType.getMethod("allocateInstance",Class::class.java).invoke(allocator,type)
 private fun set(activity:PhotoEditorActivity,name:String,value:Any?)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)
 private fun get(activity:PhotoEditorActivity,name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity)
 private fun invoke(activity:PhotoEditorActivity,name:String)=PhotoEditorActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(activity)
 private fun fixture()=(allocate(PhotoEditorActivity::class.java) as PhotoEditorActivity).also{
  set(it,"accessMode",EditorAccessMode.GUEST_BASIC)
  set(it,"bitmap",allocate(Bitmap::class.java))
 }
 @Test fun recognitionBlocksFinishUntilItEnds(){
  val activity=fixture()
  assertEquals(true,invoke(activity,"canFinish"))
  set(activity,"readingText",true)
  assertEquals(false,invoke(activity,"canFinish"))
  set(activity,"readingText",false)
  assertEquals(true,invoke(activity,"canFinish"))
 }
 @Test fun recognitionAdmissionRejectsOtherBusyOperationsBeforeTouchingUiOrWorker(){
  for(name in listOf("photoProjectBusy","enhancing","cuttingOut","activityClosed")){
   val activity=fixture();set(activity,name,true)
   invoke(activity,"readText")
   assertEquals("Must not start recognition during $name",false,get(activity,"readingText"))
  }
  val activity=fixture();set(activity,"exportEdit",PhotoEdit())
  invoke(activity,"readText");assertEquals(false,get(activity,"readingText"))
 }
 @Test fun repeatedRecognitionAdmissionKeepsRunningRequestUntouched(){
  val activity=fixture();set(activity,"readingText",true)
  invoke(activity,"readText");assertEquals(true,get(activity,"readingText"))
 }
 @Test fun curveAdmissionRejectsBusyOperationsBeforeReadingEditOrOpeningUi(){
  for(name in listOf("readingText","photoProjectBusy","enhancing","cuttingOut","activityClosed")){
   val activity=fixture();set(activity,name,true)
   invoke(activity,"showCurve")
   assertNull("Admission must not read or replace the absent fixture edit",get(activity,"edit"))
  }
  val activity=fixture();val pending=PhotoEdit();set(activity,"exportEdit",pending)
  invoke(activity,"showCurve");assertSame(pending,get(activity,"exportEdit"))
 }

 @Test fun personCutoutRejectsProjectWorkAndClosedEditorBeforeMaskPreparation(){
  val method=PhotoEditorActivity::class.java.getDeclaredMethod("cutOutPerson",java.lang.Boolean.TYPE,PhotoBackground::class.java).apply{isAccessible=true}
  for(name in listOf("photoProjectBusy","activityClosed","readingText","enhancing","cuttingOut")){
   val activity=fixture();set(activity,name,true)
   method.invoke(activity,false,null)
   assertEquals(name=="cuttingOut",get(activity,"cuttingOut"))
  }
 }

 @Test fun personBackgroundRejectsBusyAndClosedWorkBeforeOpeningDialog(){
  for(name in listOf("photoProjectBusy","activityClosed","readingText","enhancing","cuttingOut")){
   val activity=fixture();set(activity,name,true);invoke(activity,"chooseBackground")
   assertNull(get(activity,"edit"));assertNull(get(activity,"exportEdit"))
  }
 }

 @Test fun photoLookChooserRejectsBusyOrClosedSessionBeforeOpeningUi(){
  for(name in listOf("readingText","photoProjectBusy","enhancing","cuttingOut","activityClosed")){
   val activity=fixture();set(activity,name,true);invoke(activity,"chooseLook")
   assertNull("Must not prepare a look during $name",get(activity,"edit"))
   assertEquals(true,get(activity,name))
  }
  val pending=fixture();set(pending,"exportEdit",PhotoEdit());invoke(pending,"chooseLook")
  val loading=fixture();set(loading,"bitmap",null);invoke(loading,"chooseLook")
 }
 @Test fun sceneEnhancementRejectsClosedOrBusySessionBeforeCopyingBitmap(){
  val method=PhotoEditorActivity::class.java.getDeclaredMethod("enhanceScene",PhotoLook::class.java).apply{isAccessible=true}
  for(name in listOf("readingText","photoProjectBusy","enhancing","cuttingOut","activityClosed")){
   val activity=fixture();set(activity,name,true);method.invoke(activity,PhotoLook.AUTO)
   assertNull(get(activity,"edit"));assertEquals(name=="enhancing",get(activity,"enhancing"))
  }
  val pending=fixture();set(pending,"exportEdit",PhotoEdit());method.invoke(pending,PhotoLook.AUTO)
  val loading=fixture();set(loading,"bitmap",null);method.invoke(loading,PhotoLook.AUTO)
 }

}
