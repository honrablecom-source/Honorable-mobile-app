package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
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
class PhotoEraseControlsTest{
 private fun views(v:View):List<View> =listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
 private fun dialog():AlertDialog{shadowOf(Looper.getMainLooper()).idle();return ShadowAlertDialog.getLatestAlertDialog().also{assertTrue(it.isShowing)}}
 private fun positive(d:AlertDialog){d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()}
 private fun checks(d:AlertDialog)=views(d.window!!.decorView).filterIsInstance<CheckBox>()
 private val fixtures=mutableListOf<Fixture>()
 private inner class Fixture(val account:Boolean=false,val launchFailure:Boolean=false){
  val owner=Robolectric.buildActivity(Activity::class.java).setup();val activity=owner.get()
  var duringInference:(()->Unit)?=null
  var current=true;var releases=0;var opens=0;var calls=0;var job:Runnable?=null;var handed:File?=null;var source:Bitmap?=null
  val control=PhotoEraseControls(activity,Executor{check(job==null);job=it},prepareInput={check->check();Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED);source=this}},
   isCurrent={current},requireWorkerCurrent={check(current)},accountOwned=account,openCopy={if(launchFailure)error("Synthetic unavailable editor");opens++;handed=it},message={},released={releases++},
   modelFile={File(activity.cacheDir,"synthetic-model-not-read")},infer={_,pixels,_,_,mask,stop->stop.check();calls++;duringInference?.invoke();stop.check();IntArray(pixels.size){if(mask[it])Color.BLUE else pixels[it]}})
  init{fixtures.add(this)}
  fun finish(){val task=requireNotNull(job);job=null;task.run();shadowOf(Looper.getMainLooper()).idle()}
  fun select(){control.open();finish()}
  fun selected(){select();views(dialog().window!!.decorView).filterIsInstance<PhotoEraseMaskView>().single().rectangle(25,25,75,75)}
  fun review(){selected();positive(dialog());finish()}
 }
 @After fun cleanup(){for(f in fixtures){f.control.close();if(f.job!=null)f.finish();f.handed?.delete();f.owner.pause().stop().destroy()};shadowOf(Looper.getMainLooper()).idle()}
 @Test fun fullBeforeAfterReviewFreshApprovalAndExactOutsidePixels(){
  val f=Fixture();f.review();val review=dialog();val previews=views(review.window!!.decorView).filterIsInstance<ImageView>().filter{it.contentDescription in setOf("Before object removal","After AI object removal")}
  assertEquals(2,previews.size);assertTrue(previews.all{it.drawable!=null});assertEquals(1,f.calls);assertFalse(checks(review).single().isChecked)
  positive(review);assertEquals(0,f.opens);assertTrue(review.isShowing)
  checks(review).single().isChecked=true;positive(review);assertEquals(1,f.opens);assertEquals(1,f.releases)
  val image=PixelPngFixture.read(f.handed!!);assertEquals(32,image.width);assertEquals(Color.RED,image.getRGB(0,0,1,1,null,0,1)[0]);assertEquals(Color.BLUE,image.getRGB(16,16,1,1,null,0,1)[0]);assertTrue(f.source!!.isRecycled)
  positive(review);assertEquals(1,f.opens)
 }
 @Test fun accountCopyRequiresIndependentUncheckedScopeConsent(){
  val f=Fixture(account=true);f.review();val d=dialog();val boxes=checks(d);assertEquals(2,boxes.size);assertTrue(boxes.none{it.isChecked})
  boxes.first().isChecked=true;positive(d);assertEquals(0,f.opens);assertTrue(d.isShowing)
  boxes.last().isChecked=true;positive(d);assertEquals(1,f.opens)
 }
 @Test fun emptyAndTooLargeMasksNeverStartInference(){
  val f=Fixture();f.select();val d=dialog();val mask=views(d.window!!.decorView).filterIsInstance<PhotoEraseMaskView>().single()
  positive(d);assertNull(f.job);assertEquals(0,f.calls);assertTrue(d.isShowing)
  mask.rectangle(0,0,100,100);positive(d);assertNull(f.job);assertEquals(0,f.calls)
  mask.rectangle(20,20,40,40);positive(d);assertNotNull(f.job);f.finish();assertEquals(1,f.calls)
 }
 @Test fun stopRetainsInputUntilWorkerSettlesAndRejectsStaleReview(){
  val f=Fixture();f.selected();positive(dialog());f.control.close();assertFalse(f.source!!.isRecycled);assertEquals(0,f.releases)
  f.finish();assertTrue(f.source!!.isRecycled);assertEquals(1,f.releases);assertEquals(0,f.opens);assertEquals(0,f.calls)
  val stale=Fixture();stale.review();val d=dialog();checks(d).single().isChecked=true;stale.current=false;positive(d);assertEquals(0,stale.opens);assertEquals(1,stale.releases)
 }
 @Test fun failedLaunchDeletesResultAndKeepsOneUseApproval(){
  val f=Fixture(launchFailure=true);f.review();val folder=File(f.activity.cacheDir,"created-images");val before=folder.listFiles()!!.toSet();assertTrue(before.isNotEmpty())
  val d=dialog();checks(d).single().isChecked=true;positive(d);assertEquals(0,f.opens);assertEquals(1,f.releases);assertTrue(before.none{it.exists()})
 }
 @Test fun touchMaskMapsLetterboxingAndDefensiveSelectionWithoutChangingPhoto(){
  val owner=Robolectric.buildActivity(Activity::class.java).setup();try{
   val bitmap=Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.GREEN)};val v=PhotoEraseMaskView(owner.get());v.setPhoto(bitmap);v.radius=2f;v.layout(0,0,320,320)
   fun touch(action:Int,x:Float,y:Float):Boolean{val e=MotionEvent.obtain(0,0,action,x,y,0);try{return v.onTouchEvent(e)}finally{e.recycle()}}
   assertFalse(touch(MotionEvent.ACTION_DOWN,20f,20f));assertTrue(touch(MotionEvent.ACTION_DOWN,160f,160f));assertTrue(touch(MotionEvent.ACTION_UP,160f,160f));assertTrue(v.mask()[8*32+16]);val copy=v.mask();copy.fill(false);assertTrue(v.mask().any{it})
   assertEquals(Color.GREEN,bitmap.getPixel(16,8));v.clear();assertFalse(v.mask().any{it});v.release();assertFalse(bitmap.isRecycled);bitmap.recycle()
  }finally{owner.pause().stop().destroy()}
 }

 @Test fun cancellationDuringInferenceKeepsBitmapsOwnedUntilReturn(){
  val f=Fixture();f.selected();positive(dialog());f.duringInference={
   assertFalse(f.source!!.isRecycled);f.control.close();assertFalse(f.source!!.isRecycled);assertEquals(0,f.releases)
  };f.finish();assertEquals(1,f.calls);assertEquals(1,f.releases);assertTrue(f.source!!.isRecycled);assertEquals(0,f.opens)
 }
 @Test fun changedPreparedPngIsNeverTransferred(){
  val f=Fixture();f.review();val folder=File(f.activity.cacheDir,"created-images");val pending=folder.listFiles()!!.single()
  pending.appendBytes(byteArrayOf(1));val d=dialog();checks(d).single().isChecked=true;positive(d)
  assertEquals(0,f.opens);assertFalse(pending.exists());assertEquals(1,f.releases)
 }
 @Test fun actualPhotoEntryUsesOwnedEditedSnapshotAndRejectsOwnerChanges(){
  fun field(name:String)=PhotoEditorActivity::class.java.getDeclaredField(name).apply{isAccessible=true}
  for(flag in listOf("generation","mode","source","originals","scope")){
   val root=Robolectric.buildActivity(PhotoEditorActivity::class.java).get()
   val input=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.GREEN)}
   val executor=field("worker").get(root) as java.util.concurrent.ExecutorService
   try{
    val source=android.net.Uri.parse("content://generated/object-erase");val guest="00000000-0000-4000-8000-000000000001";val state=PhotoEdit()
    val scope=requireNotNull(EditorAccessMode.GUEST_BASIC.lifecycleScope(source.toString(),guest){null})
    field("bitmap").set(root,input);field("source").set(root,source);field("guestInstance").set(root,guest)
    field("edit").set(root,state);field("renderedEdit").set(root,state);field("loadedScope").set(root,scope)
    field("accessMode").set(root,EditorAccessMode.GUEST_BASIC);field("copyForeground").setBoolean(root,true);field("note").set(root,android.widget.TextView(root))
    PhotoEditorActivity::class.java.getDeclaredMethod("openErasePhoto").apply{isAccessible=true}.invoke(root)
    executor.submit{}.get(10,java.util.concurrent.TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()
    val control=field("eraseControls").get(root) as? PhotoEraseControls;assertNotNull("Actual guest entry must open",control)
    val d=dialog();views(d.window!!.decorView).filterIsInstance<PhotoEraseMaskView>().single().rectangle(20,20,40,40)
    when(flag){
     "generation"->field("copyGeneration").setLong(root,1L)
     "mode"->field("accessMode").set(root,EditorAccessMode.ACCOUNT)
     "source"->field("source").set(root,android.net.Uri.parse("content://generated/changed"))
     "originals"->field("protectedOriginals").set(root,listOf(android.net.Uri.parse("content://generated/protected")))
     "scope"->field("loadedScope").set(root,"0".repeat(64))
    }
    positive(d);assertNull(field("eraseControls").get(root));assertFalse(field("photoProjectBusy").getBoolean(root))
    assertFalse(input.isRecycled);assertEquals(Color.GREEN,input.getPixel(16,16));assertSame(state,field("edit").get(root))
   }finally{(field("eraseControls").get(root) as? PhotoEraseControls)?.close();executor.shutdownNow();input.recycle()}
  }
 }

}
