package com.honorablemobile

import android.app.Activity
import android.content.ContextWrapper
import android.content.ContentResolver
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/** Actual Activity result entry with synthetic metadata; no device lifecycle/provider execution. */
class DrawingPhotoOperationTest {
    private class Queue:AbstractExecutorService(){
        val jobs=mutableListOf<Runnable>();var reject=false;var providerAccess=0
        override fun execute(job:Runnable){if(reject)throw java.util.concurrent.RejectedExecutionException();jobs.add(job)}
        override fun shutdown(){}
        override fun shutdownNow():MutableList<Runnable> = mutableListOf()
        override fun isShutdown()=false
        override fun isTerminated()=false
        override fun awaitTermination(timeout:Long,unit:TimeUnit)=false
    }
    private class ProbeContext(val queue:Queue):ContextWrapper(null){override fun getContentResolver():ContentResolver {queue.providerAccess++;error("Unexpected resolver access")}}
    private val unsafe=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null) as Unsafe
    private fun set(a:HonorableDrawingActivity,name:String,value:Any?){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun importing(a:HonorableDrawingActivity)=a.javaClass.getDeclaredField("importing").apply{isAccessible=true}.get(a) as AtomicBoolean
    private fun setup(leased:Boolean=true):Pair<HonorableDrawingActivity,Queue>{
        val a=unsafe.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity;val q=Queue()
        Activity::class.java.getDeclaredField("mUiThread").apply{isAccessible=true}.set(a,Thread.currentThread())
        set(a,"operationLock",Any());set(a,"worker",q);set(a,"importing",AtomicBoolean(true));set(a,"exporting",AtomicBoolean(false));set(a,"printForeground",true);set(a,"photoOperation",a.javaClass.declaredClasses.single{it.simpleName=="PhotoOperation"}.getDeclaredConstructor(Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply{isAccessible=true}.newInstance(1,leased))
        ContextWrapper::class.java.getDeclaredField("mBase").apply{isAccessible=true}.set(a,ProbeContext(q))
        return a to q
    }
    private fun operation(a:HonorableDrawingActivity)=a.javaClass.getDeclaredField("photoOperation").apply{isAccessible=true}.get(a)!!
    private fun start(a:HonorableDrawingActivity,op:Any){a.javaClass.getDeclaredMethod("startPhotoOperation",op.javaClass).apply{isAccessible=true}.invoke(a,op)}
    private fun cancel(a:HonorableDrawingActivity){a.javaClass.getDeclaredMethod("cancelPhotoTransfer").apply{isAccessible=true}.invoke(a)}
    private fun release(a:HonorableDrawingActivity,op:Any)=a.javaClass.getDeclaredMethod("releasePhotoOperation",op.javaClass).apply{isAccessible=true}.invoke(a,op) as Boolean
    private fun deliver(a:HonorableDrawingActivity,op:Any){a.javaClass.getDeclaredMethod("deliverPhotoCopy",op.javaClass,String::class.java).apply{isAccessible=true}.invoke(a,op,"Synthetic bounded copy; no decoder execution")}
    private fun begin(a:HonorableDrawingActivity,id:Int)=a.javaClass.getDeclaredMethod("beginPhotoOperation",Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,id,true)
    private fun result(a:HonorableDrawingActivity,code:Int=Activity.RESULT_OK,data:Intent?=Intent().setData(Uri.parse("content://synthetic.invalid/photo/1")),request:Int=DrawingPhotoPolicy.REQUEST_CODE){
        a.javaClass.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply{isAccessible=true}.invoke(a,request,code,data)
    }
    @Test fun pausedPhotoResultCannotQueueProviderWork(){val(a,q)=setup();set(a,"printForeground",false);result(a);assertEquals(0,q.jobs.size);assertTrue(importing(a).get())}
    @Test fun closedPhotoResultCannotQueueProviderWork(){val(a,q)=setup();set(a,"printClosed",true);result(a);assertEquals(0,q.jobs.size)}
    @Test fun repeatedPhotoResultQueuesOnlyOnce(){val(a,q)=setup();result(a);result(a);assertEquals(1,q.jobs.size);assertTrue(importing(a).get())}
    @Test fun normalPickerCancellationDoesNotQueueARead(){val(a,q)=setup();result(a,Activity.RESULT_CANCELED,null);assertEquals(0,q.jobs.size);assertFalse(importing(a).get())}

    @Test fun expectedPickerPauseKeepsUnansweredSelection(){val(a,q)=setup();set(a,"printForeground",false);cancel(a);assertTrue(importing(a).get());assertEquals(0,q.jobs.size)}
    @Test fun deferredResultStartsOnceAfterForeground(){val(a,q)=setup();val op=operation(a);set(a,"printForeground",false);result(a);cancel(a);assertEquals(0,q.jobs.size);assertTrue(importing(a).get());set(a,"printForeground",true);start(a,op);start(a,op);result(a);assertEquals(1,q.jobs.size);assertEquals(0,q.providerAccess)}
    @Test fun cancellationStopsQueuedCopyBeforeProviderAndKeepsLaterSvgOwned(){val(a,q)=setup();result(a);val old=q.jobs.single();set(a,"printForeground",false);cancel(a);assertFalse(importing(a).get());set(a,"printForeground",true);assertTrue(a.Bridge().beginSvgShare(3,100));old.run();assertEquals(0,q.providerAccess);val exporting=a.javaClass.getDeclaredField("exporting").apply{isAccessible=true}.get(a) as AtomicBoolean;assertTrue(exporting.get());a.Bridge().releaseSvgShare(3)}
    @Test fun staleReleaseCannotClearAReplacementPhoto(){val(a,_)=setup();val old=operation(a);assertTrue(release(a,old));val current=begin(a,2)!!;assertFalse(release(a,old));assertTrue(importing(a).get());assertTrue(release(a,current))}
    @Test fun leasedCopyRetainsBusyUntilMatchingClientRelease(){val(a,_)=setup();val op=operation(a);assertFalse(a.Bridge().isPhotoImportCurrent(1));deliver(a,op);assertTrue(importing(a).get());assertTrue(a.Bridge().isPhotoImportCurrent(1));assertFalse(a.Bridge().isPhotoImportCurrent(2));assertFalse(a.Bridge().releasePhotoImport(2));assertTrue(importing(a).get());assertTrue(a.Bridge().releasePhotoImport(1));assertFalse(importing(a).get());assertFalse(a.Bridge().releasePhotoImport(1))}
    @Test fun readyCopyCannotBecomeCurrentAgainAfterPause(){val(a,_)=setup();val op=operation(a);op.javaClass.getDeclaredField("started").apply{isAccessible=true}.setBoolean(op,true);deliver(a,op);set(a,"printForeground",false);assertFalse(a.Bridge().isPhotoImportCurrent(1));cancel(a);set(a,"printForeground",true);assertFalse(a.Bridge().isPhotoImportCurrent(1));assertTrue(a.Bridge().beginProjectFileSave(2,"Name",2));assertFalse(a.Bridge().releasePhotoImport(1));assertTrue(importing(a).get());a.Bridge().releaseProjectFile(2)}
    @Test fun legacyCopyReleasesBeforeCallbackWithoutRequiringNewClientAck(){val(a,_)=setup(false);deliver(a,operation(a));assertFalse(importing(a).get());assertFalse(a.Bridge().isPhotoImportCurrent(1));assertFalse(a.Bridge().releasePhotoImport(1))}
    @Test fun cancelledOldDeliveryCannotMarkNewOwnerReady(){val(a,_)=setup();val old=operation(a);assertTrue(release(a,old));val current=begin(a,2)!!;deliver(a,old);assertFalse(a.Bridge().isPhotoImportCurrent(2));assertTrue(importing(a).get());deliver(a,current);assertTrue(a.Bridge().isPhotoImportCurrent(2))}
    @Test fun invalidSourceDoesNotQueueOrAccessProvider(){val(a,q)=setup();result(a,data=Intent().setData(Uri.parse("file:///synthetic/photo.png")));assertEquals(0,q.jobs.size);assertEquals(0,q.providerAccess);assertFalse(importing(a).get())}
    @Test fun onlySingleClipDataUriIsAccepted(){for(single in listOf(true,false)){val(a,q)=setup();val data=ClipData.newRawUri("Synthetic",Uri.parse("content://synthetic.invalid/photo/1"));if(!single)data.addItem(ClipData.Item(Uri.parse("content://synthetic.invalid/photo/2")));result(a,data=Intent().apply{clipData=data});assertEquals(if(single)1 else 0,q.jobs.size);assertEquals(0,q.providerAccess)}}
    @Test fun rejectedExecutorReleasesItsOwnSelection(){val(a,q)=setup();q.reject=true;result(a);assertEquals(0,q.jobs.size);assertEquals(0,q.providerAccess);assertFalse(importing(a).get())}
    @Test fun invalidAndPausedOwnedAdmissionDoesNotReplaceTheCurrentImport(){val(a,q)=setup();assertFalse(a.Bridge().choosePhotoOwned(0));set(a,"printForeground",false);assertFalse(a.Bridge().choosePhotoOwned(2));assertTrue(importing(a).get());assertEquals(0,q.jobs.size);set(a,"printForeground",true);assertFalse(a.Bridge().choosePhotoOwned(2));assertTrue(importing(a).get())}
    @Test fun unrelatedResultCannotConsumePhotoSelection(){val(a,q)=setup();result(a,request=9999);assertEquals(0,q.jobs.size);assertTrue(importing(a).get());result(a);assertEquals(1,q.jobs.size)}
    @Test fun duplicateCancellationCannotReleaseLaterProjectUpload(){val(a,_)=setup();result(a,Activity.RESULT_CANCELED,null);assertTrue(a.Bridge().beginProjectFileSave(2,"Name",2));result(a,Activity.RESULT_CANCELED,null);assertTrue(importing(a).get());a.Bridge().releaseProjectFile(2)}
}
