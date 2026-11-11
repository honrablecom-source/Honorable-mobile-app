package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.app.AlertDialog
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoTitleShelfStagesTest {
 @Test fun nextDialogOwnsStageAndLateOldCallbacksCannotBegin(){val s=VideoTitleShelfStages();val old=Any();val next=Any();assertTrue(s.open(old));assertTrue(s.open(next));assertFalse(s.owns(old));assertNull(s.begin(old));assertTrue(s.owns(next))}
 @Test fun approvalConsumesStageAndCreatesOnlyOneReceipt(){val s=VideoTitleShelfStages();val d=Any();s.open(d);val receipt=requireNotNull(s.begin(d));assertFalse(s.owns(d));assertFalse(s.canBegin(d));assertNull(s.begin(d));assertTrue(s.permits(receipt));assertFalse(s.open(Any()))}
 @Test fun initialOpenCannotSupersedeStageOrPendingJob(){val s=VideoTitleShelfStages();val first=requireNotNull(s.begin(null));assertFalse(s.canBegin(null));assertNull(s.begin(null));assertTrue(s.permits(first));assertTrue(s.finish(first));val d=Any();s.open(d);assertFalse(s.canBegin(null));assertNull(s.begin(null));assertTrue(s.owns(d))}
 @Test fun foreignCompletionCannotReleaseCurrentBusyReceipt(){val s=VideoTitleShelfStages();val first=requireNotNull(s.begin(null));assertFalse(s.finish(Any()));assertTrue(s.permits(first));assertFalse(s.open(Any()));assertTrue(s.finish(first))}
 @Test fun completionIsOneUseAndAllowsSuccessorStage(){val s=VideoTitleShelfStages();val first=requireNotNull(s.begin(null));assertTrue(s.finish(first));assertFalse(s.permits(first));val d=Any();assertTrue(s.open(d));val next=requireNotNull(s.begin(d));assertFalse(s.finish(first));assertTrue(s.permits(next));assertTrue(s.finish(next))}
 @Test fun lifecycleCloseRevokesStageReceiptAndFutureAdmission(){val s=VideoTitleShelfStages();val receipt=requireNotNull(s.begin(null));s.close();assertFalse(s.permits(receipt));assertFalse(s.finish(receipt));assertNull(s.begin(null));assertFalse(s.open(Any()));s.close()}
 @Test fun lateDismissalDoesNotCloseNewStageOrPendingWork(){val s=VideoTitleShelfStages();val old=Any();val next=Any();s.open(old);s.open(next);if(s.owns(old))s.close();assertTrue(s.owns(next));val receipt=requireNotNull(s.begin(next));if(s.owns(next))s.close();assertTrue(s.permits(receipt));assertTrue(s.finish(receipt))}
 @Test fun invalidNameValidationLeavesTheStageEditable(){val s=VideoTitleShelfStages();val d=Any();s.open(d);assertTrue(runCatching{NamedVideoTitleStyle("",VideoTitleStyle())}.isFailure);assertTrue(s.owns(d));NamedVideoTitleStyle("Corrected",VideoTitleStyle());assertNotNull(s.begin(d))}
 @Test fun concurrentDuplicateApprovalsProduceOneWorkerReceipt(){val s=VideoTitleShelfStages();val d=Any();s.open(d);val start=CountDownLatch(1);val winners=AtomicInteger();val threads=List(16){Thread{start.await();if(s.begin(d)!=null)winners.incrementAndGet()}};threads.forEach{it.start()};start.countDown();threads.forEach{it.join(5000);assertFalse(it.isAlive)};assertEquals(1,winners.get())}
 @Test fun cooperativeWorkerGuardStopsAtTheNextCheckAfterClose(){val s=VideoTitleShelfStages();val receipt=requireNotNull(s.begin(null));var writes=0;fun guard(){check(s.permits(receipt))};guard();writes++;s.close();assertTrue(runCatching{guard();writes++}.isFailure);assertEquals(1,writes);assertFalse(s.finish(receipt))}
 private val unsafe=JvmTestAllocator
 private fun set(a:Any,n:String,v:Any?)=a.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(a,v)
 private fun call(a:Any,n:String,vararg args:Any?)=a.javaClass.declaredMethods.single{it.name==n&&it.parameterCount==args.size}.apply{isAccessible=true}.invoke(a,*args)
 private fun showing(d:AlertDialog,value:Boolean){android.app.Dialog::class.java.getDeclaredField("mDecor").apply{isAccessible=true}.set(d,if(value)unsafe.allocateInstance(android.view.View::class.java)else null)}
 private fun dialog()=(unsafe.allocateInstance(AlertDialog::class.java)as AlertDialog).also{showing(it,true)}
 private fun rejectsOwnerRead(shelf:VideoTitleStyleShelf){try{call(shelf,"requireOwner");fail("reached private owner read")}catch(e:java.lang.reflect.InvocationTargetException){assertTrue(e.cause is IllegalStateException);assertEquals("Title style operation cancelled",e.cause?.message)}}
 private fun fixture():Triple<VideoTitleStyleShelf,AlertDialog,VideoTitleShelfStages>{val shelf=unsafe.allocateInstance(VideoTitleStyleShelf::class.java)as VideoTitleStyleShelf;val d=dialog();val s=VideoTitleShelfStages();s.open(d);set(shelf,"dialog",d);set(shelf,"stages",s);set(shelf,"closed",AtomicBoolean(false));set(shelf,"isCurrent",{true});return Triple(shelf,d,s)}
 @Test fun actualControllerAdmissionRequiresExactVisibleCurrentStage(){val(shelf,d,s)=fixture();assertEquals(true,call(shelf,"active",d));rejectsOwnerRead(shelf);assertEquals(false,call(shelf,"active",dialog()));showing(d,false);assertEquals(false,call(shelf,"active",d));showing(d,true);set(shelf,"isCurrent",{false});assertEquals(false,call(shelf,"active",d));set(shelf,"isCurrent",{true});val receipt=requireNotNull(s.begin(d));assertEquals(false,call(shelf,"active",d));assertTrue(s.permits(receipt));set(shelf,"workerReceipt",receipt);s.close();rejectsOwnerRead(shelf)}
 @Test fun actualControllerRejectsOldJobsBeforeBusyOrWorkerDispatch(){val(shelf,d,s)=fixture();val next=dialog();s.open(next);set(shelf,"dialog",next);var effects=0;set(shelf,"busy",{_:Boolean->effects++});val action={effects++};val done={_:Int->effects++};call(shelf,"job",d,action,done);assertEquals(0,effects);assertTrue(s.owns(next));val receipt=requireNotNull(s.begin(next));set(shelf,"dialog",null);call(shelf,"job",null,action,done);assertEquals(0,effects);assertTrue(s.permits(receipt))}
}
