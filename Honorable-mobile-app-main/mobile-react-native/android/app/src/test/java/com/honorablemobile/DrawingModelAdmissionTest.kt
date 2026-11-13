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
class DrawingModelAdmissionTest {
    private fun field(a:HonorableDrawingActivity,name:String,value:Any){a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
    private fun instance():HonorableDrawingActivity=(JvmTestAllocator.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity).also{
        field(it,"operationLock",Any());field(it,"exporting",AtomicBoolean(false));field(it,"importing",AtomicBoolean(false))
    }
    private fun exporting(a:HonorableDrawingActivity)=a.javaClass.getDeclaredField("exporting").apply{isAccessible=true}.get(a) as AtomicBoolean
    @Test fun pausedClosedAndImportingStatesRefuseModelAdmission(){
        val a=instance();val b=a.Bridge();assertFalse(b.beginModelShare(1,24));field(a,"printForeground",true);field(a,"printClosed",true);assertFalse(b.beginModelShare(1,24));field(a,"printClosed",false);field(a,"importing",AtomicBoolean(true));assertFalse(b.beginModelShare(1,24));assertFalse(exporting(a).get())
    }
    @Test fun modelSvgAndPrintCannotOverlapAndWrongIdsCannotReleaseOwner(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge();assertTrue(b.beginModelShare(3,24));assertFalse(b.beginSvgShare(4,100));assertFalse(b.beginPrintImage(4,100));assertFalse(b.beginModelShare(4,24));b.releaseModelShare(4);assertTrue(exporting(a).get());assertFalse(b.appendModelShareChunk(4,"AAAA"));b.releaseModelShare(3);assertFalse(exporting(a).get());assertTrue(b.beginSvgShare(5,100));assertFalse(b.beginModelShare(6,24));b.releaseSvgShare(5);assertTrue(b.beginPrintImage(7,100));assertFalse(b.beginModelShare(8,24));b.releasePrintImage(7)
    }
    @Test fun malformedModelChunkReleasesOnlyItsOwnLease(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge();assertFalse(b.beginModelShare(0,24));assertFalse(exporting(a).get());assertTrue(b.beginModelShare(2,24));assertFalse(b.appendModelShareChunk(2,""));assertFalse(exporting(a).get());assertTrue(b.beginModelShare(3,24));b.releaseModelShare(2);assertTrue(exporting(a).get());b.releaseModelShare(3);assertFalse(exporting(a).get())
    }
    @Test fun preparingOrPausedModelRejectsLateChunks(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge();assertTrue(b.beginModelShare(3,24));val op=a.javaClass.getDeclaredField("modelOperation").apply{isAccessible=true}.get(a);op.javaClass.getDeclaredField("preparing").apply{isAccessible=true}.setBoolean(op,true);assertFalse(b.appendModelShareChunk(3,"AAAA"));field(a,"printForeground",false);assertFalse(b.appendModelShareChunk(3,"AAAA"));b.releaseModelShare(3);assertFalse(exporting(a).get())
    }
    @Test fun boundedDepthReadAndReleaseNeverStealAnExportLease(){
        val a=instance();field(a,"printForeground",true);field(a,"depthModel","A".repeat(40000));val b=a.Bridge();assertTrue(b.beginSvgShare(9,100));assertEquals(40000,b.getDepthModelLength());assertNull(b.readDepthModelChunk(-1));assertNull(b.readDepthModelChunk(40000));assertEquals(32768,b.readDepthModelChunk(0)!!.length);assertEquals(7232,b.readDepthModelChunk(32768)!!.length);field(a,"printClosed",true);assertEquals(0,b.getDepthModelLength());assertNull(b.readDepthModelChunk(0));field(a,"printClosed",false);b.releaseDepthModel();assertEquals(0,b.getDepthModelLength());assertTrue(exporting(a).get());b.releaseSvgShare(9);assertFalse(exporting(a).get())
    }
}
