package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

/** Actual private reset protocol with constructor-free recording boundary, not device lifecycle proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class DrawingResetLifecycleTest {
    private class RecordingWebView(context:Context):WebView(context){
        var calls=0;var reloads=0;var rejectEvaluation=false;var reply:ValueCallback<String>?=null
        override fun evaluateJavascript(value:String,callback:ValueCallback<String>?){if(rejectEvaluation)throw IllegalStateException("Synthetic WebView failure");calls++;reply=callback}
        override fun reload(){reloads++}
    }
    private val unsafe=JvmTestAllocator
    private fun field(a:HonorableDrawingActivity,name:String,value:Any){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun setup():Pair<HonorableDrawingActivity,RecordingWebView>{
        val a=unsafe.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity
        val w=unsafe.allocateInstance(RecordingWebView::class.java) as RecordingWebView
        field(a,"operationLock",Any());field(a,"web",w);field(a,"importing",AtomicBoolean(false));field(a,"exporting",AtomicBoolean(false));field(a,"printForeground",true)
        return a to w
    }
    private fun request(a:HonorableDrawingActivity)=a.javaClass.getDeclaredMethod("requestNewDrawing").apply{isAccessible=true}.invoke(a) as Boolean
    private fun begin(a:HonorableDrawingActivity):Any?=a.javaClass.getDeclaredMethod("beginResetReview").apply{isAccessible=true}.invoke(a)
    private fun approve(a:HonorableDrawingActivity,review:Any)=a.javaClass.getDeclaredMethod("approveResetReview",review.javaClass).apply{isAccessible=true}.invoke(a,review) as Boolean
    private fun invalidate(a:HonorableDrawingActivity){a.javaClass.getDeclaredMethod("invalidateResetReview").apply{isAccessible=true}.invoke(a)}
    private fun finish(a:HonorableDrawingActivity){a.javaClass.getDeclaredMethod("finishResetDispatch").apply{isAccessible=true}.invoke(a)}
    private fun claim(a:HonorableDrawingActivity,name:String)=a.javaClass.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a) as Boolean
    @Test fun pausedResetDoesNotReachWebView(){val(a,w)=setup();field(a,"printForeground",false);assertFalse(request(a));assertEquals(0,w.calls)}
    @Test fun closedResetDoesNotReachWebView(){val(a,w)=setup();field(a,"printClosed",true);assertFalse(request(a));assertEquals(0,w.calls)}
    @Test fun pausedSuccessCannotReload(){val(a,w)=setup();assertTrue(request(a));field(a,"printForeground",false);w.reply!!.onReceiveValue("true");assertEquals(0,w.reloads)}
    @Test fun successCallbackIsSingleUse(){val(a,w)=setup();assertTrue(request(a));val reply=w.reply!!;reply.onReceiveValue("true");reply.onReceiveValue("true");assertEquals(1,w.reloads)}
    @Test fun overlappingResetWaitsForItsCurrentReply(){val(a,w)=setup();assertTrue(request(a));assertFalse(request(a));assertEquals(1,w.calls)}
    @Test fun refusalDoesNotReloadAndAllowsRetry(){val(a,w)=setup();assertTrue(request(a));w.reply!!.onReceiveValue("false");assertEquals(0,w.reloads);assertTrue(request(a))}

    @Test fun pausedAndClosedReviewsDoNotOpen(){val(a,w)=setup();field(a,"printForeground",false);assertNull(begin(a));field(a,"printForeground",true);field(a,"printClosed",true);assertNull(begin(a));assertEquals(0,w.calls)}
    @Test fun reviewHasOneCurrentOwner(){val(a,w)=setup();val review=begin(a)!!;assertNull(begin(a));val(other,_)=setup();assertFalse(approve(other,review));assertEquals(0,w.calls);assertTrue(approve(a,review));assertFalse(approve(a,review));assertEquals(1,w.calls)}
    @Test fun pauseInvalidatesApprovalEvenAfterResume(){val(a,w)=setup();val old=begin(a)!!;field(a,"printForeground",false);invalidate(a);field(a,"printForeground",true);val current=begin(a)!!;assertFalse(approve(a,old));assertEquals(0,w.calls);assertTrue(approve(a,current));assertEquals(1,w.calls)}
    @Test fun operationsStartingDuringReviewKeepTheDrawing(){for(name in listOf("importing","exporting")){val(a,w)=setup();val review=begin(a)!!;field(a,name,AtomicBoolean(true));assertFalse(approve(a,review));assertEquals(0,w.calls)}}
    @Test fun pendingResetBlocksNewReviewAndImportExportClaims(){val(a,w)=setup();assertTrue(request(a));assertNull(begin(a));assertFalse(claim(a,"claimImport"));assertFalse(claim(a,"claimExport"));w.reply!!.onReceiveValue("false");assertTrue(claim(a,"claimImport"))}
    @Test fun staleReplyCannotReloadOrReleaseLaterReset(){val(a,w)=setup();assertTrue(request(a));val old=w.reply!!;old.onReceiveValue("false");assertTrue(request(a));val current=w.reply!!;old.onReceiveValue("true");assertEquals(0,w.reloads);assertFalse(request(a));current.onReceiveValue("true");assertEquals(1,w.reloads)}
    @Test fun approvedSuccessFinishesOnceAfterResume(){val(a,w)=setup();assertTrue(request(a));val reply=w.reply!!;field(a,"printForeground",false);invalidate(a);reply.onReceiveValue("true");assertEquals(0,w.reloads);field(a,"printForeground",true);finish(a);finish(a);reply.onReceiveValue("true");assertEquals(1,w.reloads);assertTrue(request(a))}
    @Test fun invalidatingReviewDoesNotReleaseAnUnrelatedImport(){val(a,w)=setup();assertNotNull(begin(a));field(a,"importing",AtomicBoolean(true));invalidate(a);assertFalse(request(a));assertEquals(0,w.calls)}

    @Test fun pausedRefusalRetainsOwnershipUntilForeground(){val(a,w)=setup();assertTrue(request(a));field(a,"printForeground",false);invalidate(a);w.reply!!.onReceiveValue("false");assertFalse(claim(a,"claimImport"));field(a,"printForeground",true);finish(a);assertEquals(0,w.reloads);assertTrue(claim(a,"claimImport"))}
    @Test fun pausedSuccessCannotBeChangedByDuplicateReply(){val(a,w)=setup();assertTrue(request(a));val reply=w.reply!!;field(a,"printForeground",false);invalidate(a);reply.onReceiveValue("true");reply.onReceiveValue("false");field(a,"printForeground",true);finish(a);assertEquals(1,w.reloads)}
    @Test fun resumeWaitsForAnApprovedResetReply(){val(a,w)=setup();assertTrue(request(a));val reply=w.reply!!;field(a,"printForeground",false);invalidate(a);field(a,"printForeground",true);finish(a);assertEquals(0,w.reloads);assertFalse(request(a));assertFalse(claim(a,"claimExport"));reply.onReceiveValue("true");assertEquals(1,w.reloads)}
    @Test fun closedCallbackCannotReloadOrDispatchNewWork(){val(a,w)=setup();assertTrue(request(a));field(a,"printClosed",true);w.reply!!.onReceiveValue("true");finish(a);assertEquals(0,w.reloads);assertFalse(request(a))}
    @Test fun failedWebViewDispatchReleasesOnlyItsLease(){val(a,w)=setup();w.rejectEvaluation=true;assertFalse(request(a));assertEquals(0,w.calls);assertTrue(claim(a,"claimImport"))}
}
