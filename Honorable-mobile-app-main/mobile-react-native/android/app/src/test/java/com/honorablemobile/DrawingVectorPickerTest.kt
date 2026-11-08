package com.honorablemobile
import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
/** Actual private metadata-entry/ownership methods with no Android provider/UI execution. */
class DrawingVectorPickerTest {
    private class Queue:AbstractExecutorService(){val jobs=mutableListOf<Runnable>();var reject=false;override fun execute(job:Runnable){if(reject)throw java.util.concurrent.RejectedExecutionException();jobs.add(job)};override fun shutdown(){};override fun shutdownNow():MutableList<Runnable> = mutableListOf();override fun isShutdown()=false;override fun isTerminated()=false;override fun awaitTermination(t:Long,u:TimeUnit)=false}
    private val unsafe=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}.get(null) as Unsafe
    private fun set(a:HonorableDrawingActivity,name:String,value:Any?){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun get(a:HonorableDrawingActivity,name:String)=a.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(a)
    private fun setup(id:Int=1,code:Int=DrawingVectorFiles.FIRST_CODE):Pair<HonorableDrawingActivity,Queue>{val a=unsafe.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity;val q=Queue();Activity::class.java.getDeclaredField("mUiThread").apply{isAccessible=true}.set(a,Thread.currentThread());set(a,"operationLock",Any());set(a,"worker",q);set(a,"importing",AtomicBoolean(true));set(a,"exporting",AtomicBoolean(false));set(a,"printForeground",true);set(a,"lastVectorCode",code);val type=a.javaClass.declaredClasses.single{it.simpleName=="VectorOperation"};set(a,"vectorOperation",type.getDeclaredConstructor(Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply{isAccessible=true}.newInstance(id,code));return a to q}
    private fun op(a:HonorableDrawingActivity)=get(a,"vectorOperation")!!
    private fun field(op:Any,name:String,value:Any?){op.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(op,value)}
    private fun result(a:HonorableDrawingActivity,code:Int=DrawingVectorFiles.FIRST_CODE,status:Int=Activity.RESULT_OK,data:Intent?=Intent().setData(Uri.parse("content://synthetic.invalid/vector"))){a.javaClass.getDeclaredMethod("vectorResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply{isAccessible=true}.invoke(a,code,status,data)}
    private fun start(a:HonorableDrawingActivity,op:Any){a.javaClass.getDeclaredMethod("startVectorRead",op.javaClass).apply{isAccessible=true}.invoke(a,op)}
    private fun busy(a:HonorableDrawingActivity)=(get(a,"importing") as AtomicBoolean).get()
    @Test fun pausedResultQueuesNothingUntilForeground(){val(a,q)=setup();set(a,"printForeground",false);result(a);assertEquals(0,q.jobs.size);assertTrue(busy(a));set(a,"printForeground",true);start(a,op(a));assertEquals(1,q.jobs.size);start(a,op(a));assertEquals(1,q.jobs.size)}
    @Test fun duplicateMetadataQueuesOneReadOnly(){val(a,q)=setup();result(a);result(a);assertEquals(1,q.jobs.size);assertTrue(busy(a))}
    @Test fun oldRequestCodeCannotConsumeCurrentSelection(){val(a,q)=setup(code=DrawingVectorFiles.FIRST_CODE+1);result(a);assertEquals(0,q.jobs.size);assertTrue(busy(a));result(a,code=DrawingVectorFiles.FIRST_CODE+1);assertEquals(1,q.jobs.size)}
    @Test fun cancelledPickerReleasesItsOwnState(){val(a,q)=setup();result(a,status=Activity.RESULT_CANCELED,data=null);assertFalse(busy(a));assertNull(get(a,"vectorOperation"));assertEquals(0,q.jobs.size)}
    @Test fun nonContentSourcesAreNeverQueued(){val(a,q)=setup();result(a,data=Intent().setData(Uri.parse("file:///private/vector.svg")));assertEquals(0,q.jobs.size);assertFalse(busy(a))}
    @Test fun closedActivityRefusesMetadata(){val(a,q)=setup();set(a,"printClosed",true);result(a);assertEquals(0,q.jobs.size)}
    @Test fun rejectedExecutorReleasesTheMatchingOperation(){val(a,q)=setup();q.reject=true;result(a);assertFalse(busy(a));assertNull(get(a,"vectorOperation"))}
    @Test fun chunksRequireReadyForegroundAndMatchingId(){val(a,_)=setup();field(op(a),"text","<svg/>");assertEquals("<svg/>",a.Bridge().readVectorFileChunk(1,0));assertNull(a.Bridge().readVectorFileChunk(2,0));set(a,"printForeground",false);assertNull(a.Bridge().readVectorFileChunk(1,0));set(a,"printForeground",true);assertFalse(a.Bridge().releaseVectorFile(2));assertTrue(busy(a));assertTrue(a.Bridge().releaseVectorFile(1));assertFalse(busy(a));assertFalse(a.Bridge().releaseVectorFile(1))}
    @Test fun staleReleaseCannotClearAReplacement(){val(a,_)=setup(id=2,code=DrawingVectorFiles.FIRST_CODE+1);assertFalse(a.Bridge().releaseVectorFile(1));assertTrue(busy(a));assertTrue(a.Bridge().releaseVectorFile(2));assertFalse(busy(a))}
    @Test fun hiddenReadIsCancelledAndDoesNotRevive(){val(a,q)=setup();result(a);set(a,"printForeground",false);a.javaClass.getDeclaredMethod("cancelVectorRead").apply{isAccessible=true}.invoke(a);assertNull(get(a,"vectorOperation"));assertFalse(busy(a));set(a,"printForeground",true);assertNull(a.Bridge().readVectorFileChunk(1,0));assertEquals(1,q.jobs.size)}
    @Test fun invalidPausedAndBusyAdmissionCannotReplaceExistingOwner(){val(a,_)=setup();assertFalse(a.Bridge().chooseVectorFile(0));assertFalse(a.Bridge().chooseVectorFile(2));set(a,"printForeground",false);assertFalse(a.Bridge().chooseVectorFile(2));assertTrue(busy(a))}
    @Test fun pickerIntentHasLocalReadOnlyOpenableContract(){val(a,_)=setup();val intent=a.javaClass.getDeclaredMethod("vectorPickerIntent").apply{isAccessible=true}.invoke(a) as Intent;assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.action);assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE));assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY,false));assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0);assertEquals(0,intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION);assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("image/svg+xml"));assertNull(intent.data)}
}
