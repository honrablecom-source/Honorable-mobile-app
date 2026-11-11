package app.honorable.editor

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test

/** Actual Activity admission without constructing Android UI or reading a bitmap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PhotoComparisonAdmissionTest {
 private val type=Class.forName("sun.misc.Unsafe")
 private val allocator=type.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null)
 private fun allocate(clazz:Class<*>)=type.getMethod("allocateInstance",Class::class.java).invoke(allocator,clazz)
 private fun set(activity:PhotoEditorActivity,name:String,value:Any?)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)
 private fun call(activity:PhotoEditorActivity,name:String)=PhotoEditorActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(activity)
 private fun fixture()=(uncreatedPhotoActivity() as PhotoEditorActivity).also{
  set(it,"accessMode",EditorAccessMode.GUEST_BASIC);set(it,"bitmap",allocate(Bitmap::class.java))
 }
 @Test fun pausedEditorRefusesComparisonBeforeReadingScopeOrOpeningDialog(){
  val activity=fixture();assertEquals(true,call(activity,"canFinish"))
  call(activity,"createComparison")
 }
 @Test fun foregroundBusyEditorStillRefusesComparisonBeforeAccessingUi(){
  for(flag in listOf("readingText","enhancing","cuttingOut","photoProjectBusy","activityClosed")){
   val activity=fixture();set(activity,"copyForeground",true);set(activity,flag,true)
   assertEquals(false,call(activity,"canFinish"));call(activity,"createComparison")
  }
 }
}
