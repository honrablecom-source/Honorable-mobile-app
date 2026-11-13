package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

/** Exercise the real bridge's shared admission and stale-owner rules without constructing a WebView. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=android.app.Application::class)
class DrawingGifAdmissionTest {
    private fun field(a:HonorableDrawingActivity,name:String,value:Any){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun instance():HonorableDrawingActivity=(JvmTestAllocator.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity).also{
        field(it,"operationLock",Any());field(it,"exporting",AtomicBoolean(false));field(it,"importing",AtomicBoolean(false))
    }
    private fun exporting(a:HonorableDrawingActivity)=a.javaClass.getDeclaredField("exporting").apply{isAccessible=true}.get(a) as AtomicBoolean
    @Test fun pausedClosedAndImportingStatesRefuseModelAdmission(){
        val a=instance();val b=a.Bridge();assertFalse(b.beginGifShare(1,24));field(a,"printForeground",true);field(a,"printClosed",true);assertFalse(b.beginGifShare(1,24));field(a,"printClosed",false);field(a,"importing",AtomicBoolean(true));assertFalse(b.beginGifShare(1,24));assertFalse(exporting(a).get())
    }
    @Test fun gifModelSvgAndPrintCannotOverlapAndWrongIdsCannotReleaseOwner(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge();assertTrue(b.beginGifShare(3,24));assertFalse(b.beginModelShare(4,24));assertFalse(b.beginSvgShare(4,100));assertFalse(b.beginPrintImage(4,100));assertFalse(b.beginGifShare(4,24));b.releaseGifShare(4);assertTrue(exporting(a).get());assertFalse(b.appendGifShareChunk(4,"AAAA"));b.releaseGifShare(3);assertFalse(exporting(a).get());assertTrue(b.beginSvgShare(5,100));assertFalse(b.beginGifShare(6,24));b.releaseSvgShare(5);assertTrue(b.beginPrintImage(7,100));assertFalse(b.beginGifShare(8,24));b.releasePrintImage(7)
    }
    @Test fun malformedModelChunkReleasesOnlyItsOwnLease(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge();assertFalse(b.beginGifShare(0,24));assertFalse(exporting(a).get());assertTrue(b.beginGifShare(2,24));assertFalse(b.appendGifShareChunk(2,""));assertFalse(exporting(a).get());assertTrue(b.beginGifShare(3,24));b.releaseGifShare(2);assertTrue(exporting(a).get());b.releaseGifShare(3);assertFalse(exporting(a).get())
    }
    @Test fun preparingOrPausedModelRejectsLateChunks(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge();assertTrue(b.beginGifShare(3,24));val op=a.javaClass.getDeclaredField("gifOperation").apply{isAccessible=true}.get(a);op.javaClass.getDeclaredField("preparing").apply{isAccessible=true}.setBoolean(op,true);assertFalse(b.appendGifShareChunk(3,"AAAA"));field(a,"printForeground",false);assertFalse(b.appendGifShareChunk(3,"AAAA"));b.releaseGifShare(3);assertFalse(exporting(a).get())
    }
}
