package com.honorablemobile

import android.app.Activity
import android.content.ContextWrapper
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/** Actual private Activity dispatch with synthetic picker metadata; no provider or Android UI. */
class DrawingProjectOperationTest {
    private class Queue:AbstractExecutorService(){
        val jobs=mutableListOf<Runnable>();var reject=false;var providerAccess=0
        override fun execute(job:Runnable){if(reject)throw java.util.concurrent.RejectedExecutionException();jobs.add(job)}
        override fun shutdown(){}
        override fun shutdownNow():MutableList<Runnable> = mutableListOf()
        override fun isShutdown()=false
        override fun isTerminated()=false
        override fun awaitTermination(timeout:Long,unit:TimeUnit)=false
    }
    private class ProbeContext(val queue:Queue):ContextWrapper(null){override fun getContentResolver():ContentResolver {queue.providerAccess++;error("Unexpected provider access in host fixture")}}
    private val unsafe=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null) as Unsafe
    private fun set(a:HonorableDrawingActivity,name:String,value:Any?){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun importing(a:HonorableDrawingActivity)=a.javaClass.getDeclaredField("importing").apply{isAccessible=true}.get(a) as AtomicBoolean
    private fun setup(saving:Boolean=true):Pair<HonorableDrawingActivity,Queue>{
        val a=unsafe.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity;val q=Queue()
        Activity::class.java.getDeclaredField("mUiThread").apply{isAccessible=true}.set(a,Thread.currentThread())
        ContextWrapper::class.java.getDeclaredField("mBase").apply{isAccessible=true}.set(a,ProbeContext(q))
        set(a,"operationLock",Any());set(a,"worker",q);set(a,"importing",AtomicBoolean(true));set(a,"exporting",AtomicBoolean(false));set(a,"printForeground",true)
        val type=a.javaClass.declaredClasses.single{it.simpleName=="ProjectOperation"}
        val operation=type.getDeclaredConstructor(Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,String::class.java).apply{isAccessible=true}.newInstance(1,saving,if(saving)"{}"else null)
        set(a,"projectOperation",operation);return a to q
    }
    private fun operation(a:HonorableDrawingActivity)=a.javaClass.getDeclaredField("projectOperation").apply{isAccessible=true}.get(a)!!
    private fun start(a:HonorableDrawingActivity,op:Any){a.javaClass.getDeclaredMethod("startProjectOperation",op.javaClass).apply{isAccessible=true}.invoke(a,op)}
    private fun cancel(a:HonorableDrawingActivity){a.javaClass.getDeclaredMethod("cancelProjectTransfer").apply{isAccessible=true}.invoke(a)}
    private fun release(a:HonorableDrawingActivity,op:Any,read:Any?=null)=a.javaClass.declaredMethods.single{it.name=="releaseProjectOperation"}.apply{isAccessible=true}.invoke(a,op,read) as Boolean
    private fun result(a:HonorableDrawingActivity,saving:Boolean=true,code:Int=Activity.RESULT_OK,data:Intent?=Intent().setData(Uri.parse("content://synthetic.invalid/noble.json"))){
        a.javaClass.getDeclaredMethod("projectResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply{isAccessible=true}.invoke(a,if(saving)DrawingProjectFiles.SAVE_REQUEST else DrawingProjectFiles.OPEN_REQUEST,code,data)
    }
    @Test fun duplicatePickerResultQueuesOnlyOneTransfer(){for(saving in listOf(true,false)){val(a,q)=setup(saving);result(a,saving);result(a,saving);assertEquals(1,q.jobs.size);assertTrue(importing(a).get())}}
    @Test fun lateCancellationCannotReleaseAnAcceptedTransfer(){for(saving in listOf(true,false)){val(a,q)=setup(saving);result(a,saving);result(a,saving,Activity.RESULT_CANCELED,null);assertTrue(importing(a).get());assertEquals(1,q.jobs.size)}}
    @Test fun pausedResultWaitsBeforeQueuingProviderWork(){for(saving in listOf(true,false)){val(a,q)=setup(saving);set(a,"printForeground",false);result(a,saving);assertEquals(0,q.jobs.size);assertTrue(importing(a).get())}}
    @Test fun differentRequestCannotConsumeTheCurrentPicker(){val(a,q)=setup();result(a,false);assertEquals(0,q.jobs.size);assertTrue(importing(a).get());result(a);assertEquals(1,q.jobs.size)}

    @Test fun foregroundResumeStartsDeferredMetadataOnlyOnce(){for(saving in listOf(true,false)){val(a,q)=setup(saving);val op=operation(a);set(a,"printForeground",false);result(a,saving);cancel(a);assertTrue(importing(a).get());assertEquals(0,q.jobs.size);set(a,"printForeground",true);start(a,op);start(a,op);result(a,saving);assertEquals(1,q.jobs.size);assertEquals(0,q.providerAccess)}}
    @Test fun expectedPickerPauseDoesNotCancelTheUnansweredPicker(){val(a,q)=setup();set(a,"printForeground",false);cancel(a);assertTrue(importing(a).get());assertNotNull(operation(a));assertEquals(0,q.jobs.size)}
    @Test fun cancellationStopsQueuedWorkerBeforeProviderAndKeepsLaterSvgOwned(){for(saving in listOf(true,false)){val(a,q)=setup(saving);result(a,saving);val old=q.jobs.single();set(a,"printForeground",false);cancel(a);assertFalse(importing(a).get());set(a,"printForeground",true);assertTrue(a.Bridge().beginSvgShare(3,100));old.run();assertEquals(0,q.providerAccess);val exporting=a.javaClass.getDeclaredField("exporting").apply{isAccessible=true}.get(a) as AtomicBoolean;assertTrue(exporting.get());a.Bridge().releaseSvgShare(3)}}
    @Test fun staleCompletionCannotReleaseLaterProject(){val(a,_)=setup();val old=operation(a);assertTrue(release(a,old));val(other,_)=setup();val current=operation(other);set(a,"projectOperation",current);importing(a).set(true);assertFalse(release(a,old));assertTrue(importing(a).get());assertTrue(release(a,current));assertFalse(importing(a).get())}
    @Test fun readTransferKeepsBusyUntilMatchingChunkConsumerReleases(){val(a,_)=setup(false);val op=operation(a);val type=a.javaClass.declaredClasses.single{it.simpleName=="ProjectRead"};val text="作品🙂";val read=type.getDeclaredConstructor(Int::class.javaPrimitiveType,String::class.java).apply{isAccessible=true}.newInstance(1,text);assertTrue(release(a,op,read));assertTrue(importing(a).get());assertEquals(text,a.Bridge().readProjectFileChunk(1,0));a.Bridge().releaseProjectFile(2);assertTrue(importing(a).get());a.Bridge().releaseProjectFile(1);assertFalse(importing(a).get())}
    @Test fun invalidDestinationSchemeReleasesWithoutProviderWork(){val(a,q)=setup();result(a,data=Intent().setData(Uri.parse("file:///synthetic/noble.json")));assertEquals(0,q.jobs.size);assertEquals(0,q.providerAccess);assertFalse(importing(a).get())}
    @Test fun rejectedExecutorReleasesOnlyTheOwnedTransfer(){val(a,q)=setup();q.reject=true;result(a);assertFalse(importing(a).get());assertEquals(0,q.jobs.size);assertEquals(0,q.providerAccess)}
    @Test fun normalPickerCancellationCannotLaterClearAnSvgOperation(){val(a,q)=setup();result(a,code=Activity.RESULT_CANCELED,data=null);assertFalse(importing(a).get());assertTrue(a.Bridge().beginSvgShare(3,100));result(a,code=Activity.RESULT_CANCELED,data=null);val exporting=a.javaClass.getDeclaredField("exporting").apply{isAccessible=true}.get(a) as AtomicBoolean;assertTrue(exporting.get());assertEquals(0,q.jobs.size);a.Bridge().releaseSvgShare(3)}
    @Test fun closedActivityCannotConsumePickerMetadata(){val(a,q)=setup();set(a,"printClosed",true);result(a);assertEquals(0,q.jobs.size);assertEquals(0,q.providerAccess)}
    @Test fun invalidSavedTextIsRejectedBeforeOpeningOrTruncatingProvider(){val(a,q)=setup();val type=operation(a).javaClass;val bad=type.getDeclaredConstructor(Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,String::class.java).apply{isAccessible=true}.newInstance(1,true,"\uD800");set(a,"projectOperation",bad);result(a);q.jobs.single().run();assertEquals(0,q.providerAccess);assertFalse(importing(a).get())}
    @Test fun uploadAdmissionAndCancellationHaveExactForegroundOwnership(){val(a,_)=setup();set(a,"projectOperation",null);importing(a).set(false);val b=a.Bridge();set(a,"printForeground",false);assertFalse(b.beginProjectFileSave(1,"Name",2));set(a,"printForeground",true);assertTrue(b.beginProjectFileSave(1,"Name",2));assertTrue(b.appendProjectFileChunk(1,"{"));val old=a.javaClass.getDeclaredField("projectUpload").apply{isAccessible=true}.get(a)!!;set(a,"printForeground",false);assertFalse(b.appendProjectFileChunk(1,"}"));cancel(a);assertFalse(importing(a).get());set(a,"printForeground",true);assertTrue(b.beginProjectFileSave(2,"Name",2));val released=a.javaClass.getDeclaredMethod("releaseProjectUpload",old.javaClass).apply{isAccessible=true}.invoke(a,old) as Boolean;assertFalse(released);assertTrue(importing(a).get());b.releaseProjectFile(2);assertFalse(importing(a).get())}
}
