package app.honorable.editor

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class PhotoExportOperationTest {
    private val edit=PhotoEdit(exposure=.25f)
    private fun operation(originals:List<String> = listOf("content://synthetic/original"))=PhotoExportOperation(edit,"content://synthetic/source","content://synthetic/copy","scope",EditorAccessMode.GUEST_BASIC,4,SafePhotoCopy.Encoding.JPEG,83,originals)
    @Test fun chosenPixelsFormatAndOriginalsAreIndependentOfLaterSettingsOrListMutation(){
        val originals=mutableListOf("content://synthetic/original");val op=operation(originals);originals.clear()
        assertSame(edit,op.edit);assertEquals(SafePhotoCopy.Encoding.JPEG,op.encoding);assertEquals(83,op.quality);assertEquals("content://synthetic/copy",op.destination)
        assertEquals(listOf("content://synthetic/original"),op.originals)
        assertTrue(op.matches(edit,op.source,op.scope,op.scope,op.mode,4,listOf("content://synthetic/original")))
        try{(op.originals as MutableList).clear();fail("Mutable original set")}catch(_:UnsupportedOperationException){}
    }
    @Test fun everyCapturedOwnershipFieldMustMatch(){
        val op=operation()
        fun matches(state:PhotoEdit?=edit,source:String?=op.source,loaded:String?=op.scope,current:String?=op.scope,mode:EditorAccessMode?=op.mode,generation:Long=4,origins:List<String> = op.originals)=op.matches(state,source,loaded,current,mode,generation,origins)
        assertTrue(matches());assertFalse(matches(state=edit.copy()));assertFalse(matches(source="other"));assertFalse(matches(loaded="other"));assertFalse(matches(current=null))
        assertFalse(matches(mode=EditorAccessMode.ACCOUNT));assertFalse(matches(generation=5));assertFalse(matches(origins=emptyList()))
    }
    @Test fun cancelIsIdempotentAndStopsFurtherOwnerReads(){
        val op=operation();op.requireCurrent{true};op.cancel();op.cancel();var checks=0
        assertTrue(runCatching{op.requireCurrent{checks++;true}}.exceptionOrNull()is PhotoExportOperation.Cancelled)
        assertEquals(0,checks);assertFalse(op.matches(edit,op.source,op.scope,op.scope,op.mode,4,op.originals))
        assertTrue(runCatching{operation().requireCurrent{false}}.exceptionOrNull()is PhotoExportOperation.Cancelled)
    }
    @Test fun cancellationPublishedAcrossWorkerBoundaryStopsTheNextPhase(){
        val op=operation();val entered=CountDownLatch(1);val release=CountDownLatch(1);val worker=Executors.newSingleThreadExecutor()
        try{
            val result=worker.submit<Boolean>{op.requireCurrent{true};entered.countDown();release.await();runCatching{op.requireCurrent{true}}.exceptionOrNull()is PhotoExportOperation.Cancelled}
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));op.cancel();release.countDown();assertTrue(result.get(5,java.util.concurrent.TimeUnit.SECONDS))
        }finally{release.countDown();worker.shutdownNow()}
    }
    @Test fun invalidExportArgumentsNeverBecomeAnOperation(){
        for(quality in listOf(0,101))assertTrue(runCatching{PhotoExportOperation(edit,"source","copy","scope",EditorAccessMode.GUEST_BASIC,1,SafePhotoCopy.Encoding.PNG,quality,emptyList())}.isFailure)
        assertTrue(runCatching{PhotoExportOperation(edit,"","copy","scope",EditorAccessMode.GUEST_BASIC,1,SafePhotoCopy.Encoding.PNG,95,emptyList())}.isFailure)
    }
}
