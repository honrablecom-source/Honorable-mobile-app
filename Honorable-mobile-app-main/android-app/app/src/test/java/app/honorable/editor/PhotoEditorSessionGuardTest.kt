package app.honorable.editor

import android.graphics.Bitmap
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

/** Host actual Activity guard + actual guest lifecycle scope; no UI, pixels or account keystore. */
class PhotoEditorSessionGuardTest {
 private val uuid="11111111-1111-1111-1111-111111111111"
 private val source="content://fixture/photo"
 private val original=PhotoEdit(exposure=.5f)
 private val scope=PhotoSessionCodec.scope("guest-activity:$uuid","local-only",source)
 private fun field(name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}
 private fun set(activity:PhotoEditorActivity,name:String,value:Any?)=field(name).set(activity,value)
 private fun fixture():PhotoEditorActivity {
  val type=Class.forName("sun.misc.Unsafe");val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
  fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
  val activity=allocate(PhotoEditorActivity::class.java) as PhotoEditorActivity
  for((name,value)in listOf("copyForeground" to true,"accessMode" to EditorAccessMode.GUEST_BASIC,"guestInstance" to uuid,"source" to Uri.parse(source),"loadedScope" to scope,"edit" to original,"bitmap" to allocate(Bitmap::class.java)))set(activity,name,value)
  return activity
 }
 private fun capture(activity:PhotoEditorActivity)=PhotoEditorActivity::class.java.getDeclaredMethod("photoControlScope").apply{isAccessible=true}.invoke(activity)
 private fun current(activity:PhotoEditorActivity)=PhotoEditorActivity::class.java.getDeclaredMethod("photoControlUnchanged",PhotoEdit::class.java,String::class.java).apply{isAccessible=true}.invoke(activity,original,scope) as Boolean
 @Test fun validGuestCaptureUsesActualCurrentSourceAndInstanceScope(){
  val activity=fixture();assertEquals(scope,capture(activity));assertTrue(current(activity))
 }
 @Test fun editChangesRejectOldControlsAndEquivalentSnapshotsRemainValid(){
  val activity=fixture();set(activity,"edit",original.copy(exposure=1f));assertFalse(current(activity))
  set(activity,"edit",original.copy());assertTrue(current(activity));assertEquals(original,field("edit").get(activity))
 }
 @Test fun changedGuestInstanceOrSourceCannotReuseLoadedOwnerSnapshot(){
  val ownerChanged=fixture();set(ownerChanged,"guestInstance","22222222-2222-2222-2222-222222222222");assertFalse(current(ownerChanged))
  val sourceChanged=fixture();set(sourceChanged,"source",Uri.parse("content://fixture/other"));assertFalse(current(sourceChanged))
 }
 @Test fun missingMismatchedOrMalformedSessionFailsClosed(){
  for(value in listOf(null,"other-scope")){val activity=fixture();set(activity,"loadedScope",value);assertFalse(current(activity))}
  val malformed=fixture();set(malformed,"guestInstance","invalid");assertFalse(current(malformed))
  val unavailable=fixture();set(unavailable,"accessMode",null);assertFalse(current(unavailable))
 }
 @Test fun busyClosedExportAndMissingPhotoRejectCaptureAndCallbackWithoutUi(){
  for(name in listOf("photoProjectBusy","readingText","enhancing","cuttingOut","activityClosed")){
   val activity=fixture();set(activity,name,true);assertNull(capture(activity));assertFalse(current(activity));set(activity,name,false);assertTrue(current(activity))
  }
  val exporting=fixture();set(exporting,"exportEdit",PhotoEdit());assertNull(capture(exporting));assertFalse(current(exporting))
  val empty=fixture();set(empty,"bitmap",null);assertNull(capture(empty));assertFalse(current(empty))
 }
 private fun loadCurrent(activity:PhotoEditorActivity)=PhotoEditorActivity::class.java.getDeclaredMethod("photoLoadCurrent",String::class.java).apply{isAccessible=true}.invoke(activity,scope) as Boolean
 @Test fun initialLoadGuardWorksBeforeBitmapAndLoadedScopeExist(){
  val activity=fixture();set(activity,"bitmap",null);set(activity,"loadedScope",null)
  assertTrue(loadCurrent(activity));assertEquals(original,field("edit").get(activity))
 }
 @Test fun initialLoadGuardRejectsChangedOwnerSourceClosedOrUnavailableSession(){
  val owner=fixture();set(owner,"guestInstance","22222222-2222-2222-2222-222222222222");assertFalse(loadCurrent(owner))
  val source=fixture();set(source,"source",Uri.parse("content://fixture/other"));assertFalse(loadCurrent(source))
  val closed=fixture();set(closed,"activityClosed",true);assertFalse(loadCurrent(closed))
  val missing=fixture();set(missing,"accessMode",null);assertFalse(loadCurrent(missing))
  val malformed=fixture();set(malformed,"guestInstance","invalid");assertFalse(loadCurrent(malformed))
 }

}
