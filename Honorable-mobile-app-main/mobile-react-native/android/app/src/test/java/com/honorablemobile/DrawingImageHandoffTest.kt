package com.honorablemobile

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/** Actual bridge and private ownership protocol. No bitmap/provider/Activity construction or launch. */
class DrawingImageHandoffTest {
    private class Queue:AbstractExecutorService(){
        val jobs=mutableListOf<Runnable>();var reject=false
        override fun execute(job:Runnable){if(reject)throw java.util.concurrent.RejectedExecutionException();jobs.add(job)}
        override fun shutdown(){}
        override fun shutdownNow():MutableList<Runnable> = mutableListOf()
        override fun isShutdown()=false
        override fun isTerminated()=false
        override fun awaitTermination(timeout:Long,unit:TimeUnit)=false
    }
    private val unsafe=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null) as Unsafe
    private fun set(a:HonorableDrawingActivity,name:String,value:Any){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun flag(a:HonorableDrawingActivity,name:String)=a.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(a) as AtomicBoolean
    private fun setup():Pair<HonorableDrawingActivity,Queue>{
        val a=unsafe.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity;val queue=Queue()
        android.app.Activity::class.java.getDeclaredField("mUiThread").apply{isAccessible=true}.set(a,Thread.currentThread());set(a,"operationLock",Any());set(a,"importing",AtomicBoolean(false));set(a,"exporting",AtomicBoolean(false));set(a,"worker",queue);set(a,"printForeground",true)
        return a to queue
    }
    private fun send(a:HonorableDrawingActivity):Any?=a.Bridge().javaClass.getDeclaredMethod("usePng",String::class.java).invoke(a.Bridge(),"data:image/png;base64,AAAA")
    private fun begin(a:HonorableDrawingActivity):Any?=a.javaClass.getDeclaredMethod("beginImageOperation").apply{isAccessible=true}.invoke(a)
    private fun call(a:HonorableDrawingActivity,name:String,operation:Any)=a.javaClass.getDeclaredMethod(name,operation.javaClass).apply{isAccessible=true}.invoke(a,operation) as Boolean
    private fun abandon(a:HonorableDrawingActivity)=a.javaClass.getDeclaredMethod("abandonImageOperation").apply{isAccessible=true}.invoke(a) as Boolean
    @Test fun pausedBridgeCannotQueueImageOrClaimExport(){val(a,q)=setup();set(a,"printForeground",false);send(a);assertEquals(0,q.jobs.size);assertFalse(flag(a,"exporting").get())}
    @Test fun closedBridgeCannotQueueImageOrClaimExport(){val(a,q)=setup();set(a,"printClosed",true);send(a);assertEquals(0,q.jobs.size);assertFalse(flag(a,"exporting").get())}
    @Test fun busyImportRemainsOwnedAndNoImageIsQueued(){val(a,q)=setup();flag(a,"importing").set(true);send(a);assertEquals(0,q.jobs.size);assertTrue(flag(a,"importing").get());assertFalse(flag(a,"exporting").get())}
    @Test fun existingExportCannotBeReplacedByImage(){val(a,q)=setup();flag(a,"exporting").set(true);send(a);assertEquals(0,q.jobs.size);assertTrue(flag(a,"exporting").get())}

    @Test fun admittedBridgeReturnsBooleanAndQueuesOnlyOneWorker(){val(a,q)=setup();assertEquals(true,send(a));assertEquals(false,send(a));assertEquals(1,q.jobs.size);assertTrue(flag(a,"exporting").get());assertTrue(abandon(a));assertFalse(flag(a,"exporting").get())}
    @Test fun rejectedExecutorReleasesItsOwnAdmission(){val(a,q)=setup();q.reject=true;assertEquals(false,send(a));assertEquals(0,q.jobs.size);assertFalse(flag(a,"exporting").get());q.reject=false;assertEquals(true,send(a))}
    @Test fun pauseCancellationCannotReviveOldOperationAfterResume(){val(a,_)=setup();val old=begin(a)!!;assertTrue(call(a,"currentImageOperation",old));set(a,"printForeground",false);assertFalse(call(a,"currentImageOperation",old));assertTrue(abandon(a));set(a,"printForeground",true);assertFalse(call(a,"currentImageOperation",old));assertNotNull(begin(a))}
    @Test fun staleReleaseCannotClearLaterImageOrItsBusyFlag(){val(a,_)=setup();val old=begin(a)!!;assertTrue(abandon(a));val current=begin(a)!!;assertFalse(call(a,"releaseImageOperation",old));assertTrue(flag(a,"exporting").get());assertTrue(call(a,"currentImageOperation",current));assertTrue(call(a,"releaseImageOperation",current));assertFalse(flag(a,"exporting").get())}
    @Test fun staleReleaseCannotClearPrintOrSvgOperation(){for(print in listOf(true,false)){val(a,_)=setup();val old=begin(a)!!;assertTrue(abandon(a));val bridge=a.Bridge();assertTrue(if(print)bridge.beginPrintImage(2,100)else bridge.beginSvgShare(2,100));assertFalse(call(a,"releaseImageOperation",old));assertTrue(flag(a,"exporting").get());if(print)bridge.releasePrintImage(2)else bridge.releaseSvgShare(2);assertFalse(flag(a,"exporting").get())}}
    @Test fun pausedOperationCannotEnterLaunchPhase(){val(a,_)=setup();val op=begin(a)!!;set(a,"printForeground",false);assertFalse(call(a,"launchImageOperation",op));assertTrue(abandon(a));assertFalse(call(a,"launchImageOperation",op))}
    @Test fun committedLaunchIsSingleUseAndIsNotCancelledAsPreparation(){val(a,_)=setup();val op=begin(a)!!;assertTrue(call(a,"launchImageOperation",op));assertFalse(call(a,"launchImageOperation",op));set(a,"printForeground",false);assertFalse(abandon(a));assertTrue(flag(a,"exporting").get());assertTrue(call(a,"releaseImageOperation",op));assertFalse(flag(a,"exporting").get())}
    @Test fun foreignOwnerCannotReleaseOrLaunchTheCurrentImage(){val(a,_)=setup();val own=begin(a)!!;val(other,_)=setup();val foreign=begin(other)!!;assertFalse(call(a,"releaseImageOperation",foreign));assertFalse(call(a,"launchImageOperation",foreign));assertTrue(call(a,"currentImageOperation",own));assertTrue(flag(a,"exporting").get())}
    @Test fun cancellingWithoutAnImageKeepsUnrelatedExportOwned(){val(a,_)=setup();flag(a,"exporting").set(true);assertFalse(abandon(a));assertTrue(flag(a,"exporting").get())}

    @Test fun cancelledQueuedWorkerCannotReleaseLaterImage(){val(a,q)=setup();assertEquals(true,send(a));val old=q.jobs.single();assertTrue(abandon(a));val current=begin(a)!!;old.run();assertTrue(call(a,"currentImageOperation",current));assertTrue(flag(a,"exporting").get())}
    @Test fun cancelledQueuedWorkerCannotReleaseLaterSvg(){val(a,q)=setup();assertEquals(true,send(a));val old=q.jobs.single();assertTrue(abandon(a));assertTrue(a.Bridge().beginSvgShare(3,100));old.run();assertTrue(flag(a,"exporting").get());a.Bridge().releaseSvgShare(3);assertFalse(flag(a,"exporting").get())}
    @Test fun invalidImageWorkerReleasesItsOwnLeaseAndAllowsRetry(){val(a,q)=setup();assertEquals(true,send(a));q.jobs.single().run();assertFalse(flag(a,"exporting").get());assertEquals(true,send(a));assertEquals(2,q.jobs.size)}
}
