package app.honorable.editor
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoDepthControlsTest {
 private val fixtures=mutableListOf<Fixture>()
 private fun views(view:View):List<View> =listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
 private fun dialog():AlertDialog{shadowOf(Looper.getMainLooper()).idle();return ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing)}}
 private fun positive(d:AlertDialog){d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()}
 private fun checks(d:AlertDialog)=views(d.window!!.decorView).filterIsInstance<CheckBox>()
 private inner class Fixture(val account:Boolean=false,val launchFailure:Boolean=false){
  val controller=Robolectric.buildActivity(Activity::class.java).setup();val activity=controller.get()
  var current=true;var releases=0;var opens=0;var job:Runnable?=null;var handed:File?=null;var source:Bitmap?=null
  val control=PhotoDepthControls(activity,Executor{job=it},prepareInput={check->check();Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED);source=this}},
   isCurrent={current},requireWorkerCurrent={check(current)},accountOwned=account,openScene={if(launchFailure)error("Synthetic unavailable workspace");opens++;handed=it},message={},released={releases++},
   modelFile={File(activity.cacheDir,"synthetic-model-not-read")},infer={_,_,w,h,stop->stop.check();PhotoDepthMesh.create(FloatArray(196){(it%14).toFloat()},14,14,w,h)})
  init{fixtures.add(this)}
  fun start(){control.open();positive(dialog())}
  fun finish(){val pending=requireNotNull(job);job=null;pending.run();shadowOf(Looper.getMainLooper()).idle()}
 }
 @After fun cleanup(){for(f in fixtures){f.control.close();if(f.job!=null)f.finish();f.handed?.delete();f.controller.pause().stop().destroy()};shadowOf(Looper.getMainLooper()).idle()}
 @Test fun actualReviewShowsBothImagesAndRequiresFreshConfirmationBeforeOneHandoff(){
  val f=Fixture();f.start();f.finish();val review=dialog()
  val previews=views(review.window!!.decorView).filterIsInstance<ImageView>().filter{it.contentDescription in setOf("Working photo","Estimated relative depth")}
  assertEquals(2,previews.size);assertTrue(previews.all{it.drawable!=null});assertFalse(checks(review).single().isChecked)
  positive(review);assertEquals(0,f.opens);assertTrue(review.isShowing)
  checks(review).single().isChecked=true;positive(review)
  assertEquals(1,f.opens);assertEquals(1,f.releases);assertTrue(f.handed!!.isFile);assertTrue(f.source!!.isRecycled)
  val bytes=f.handed!!.readBytes();assertEquals(0x67,bytes[0].toInt() and 255);assertTrue(bytes.size<PhotoDepthMesh.MAX_PAYLOAD_BYTES)
  positive(review);f.control.close();assertEquals(1,f.opens)
 }
 @Test fun accountPhotoCannotEnterSharedGuestStorageWithoutSeparateUncheckedCopyConsent(){
  val f=Fixture(account=true);f.start();f.finish();val review=dialog();val confirmations=checks(review);assertEquals(2,confirmations.size);assertTrue(confirmations.none{it.isChecked})
  assertTrue(confirmations[1].text.contains("switching accounts"));confirmations[0].isChecked=true;positive(review)
  assertEquals(0,f.opens);assertNotNull(confirmations[1].error);confirmations[1].isChecked=true;positive(review);assertEquals(1,f.opens)
 }
 @Test fun stoppedWorkerRetainsLeaseUntilSettlementAndCannotPublishLate(){
  val f=Fixture();f.start();f.control.close();assertEquals(0,f.releases);f.finish();assertEquals(1,f.releases);assertEquals(0,f.opens);assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
 }
 @Test fun staleOwnerBeforeReviewConfirmationCannotTransferAndDeletesPreparedCopy(){
  val f=Fixture();f.start();f.finish();val review=dialog();checks(review).single().isChecked=true;f.current=false;positive(review)
  assertEquals(0,f.opens);assertEquals(1,f.releases);assertTrue(f.source!!.isRecycled)
  assertTrue(File(f.activity.cacheDir,"noble-depth-handoff").listFiles().orEmpty().isEmpty())
 }
 @Test fun failedWorkspaceLaunchDeletesTemporaryCopyAndReleasesOnlyOnce(){
  val f=Fixture(launchFailure=true);f.start();f.finish();val review=dialog();checks(review).single().isChecked=true;positive(review)
  assertEquals(0,f.opens);assertEquals(1,f.releases);assertTrue(File(f.activity.cacheDir,"noble-depth-handoff").listFiles().orEmpty().isEmpty())
 }
}
