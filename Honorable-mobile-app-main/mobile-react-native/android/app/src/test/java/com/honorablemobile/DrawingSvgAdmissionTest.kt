package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.junit.Assert.*
import org.junit.Test

import java.util.concurrent.atomic.AtomicBoolean

/** Actual bridge admission and ownership, without Activity/WebView/provider/chooser construction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class DrawingSvgAdmissionTest {
    private fun field(activity:HonorableDrawingActivity,name:String,value:Any){activity.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)}
    private fun instance():HonorableDrawingActivity {
        val unsafe=JvmTestAllocator
        return (unsafe.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity).also{field(it,"operationLock",Any());field(it,"exporting",AtomicBoolean(false));field(it,"importing",AtomicBoolean(false))}
    }
    private fun exporting(a:HonorableDrawingActivity)=a.javaClass.getDeclaredField("exporting").apply{isAccessible=true}.get(a) as AtomicBoolean
    @Test fun pausedClosedAndBusyContextsRefuseBeforeNativeWork(){
        val a=instance();val b=a.Bridge();assertFalse(b.beginSvgShare(1,100));field(a,"printForeground",true);field(a,"printClosed",true);assertFalse(b.beginSvgShare(1,100));field(a,"printClosed",false)
        (a.javaClass.getDeclaredField("importing").apply{isAccessible=true}.get(a) as AtomicBoolean).set(true);assertFalse(b.beginSvgShare(1,100))
    }
    @Test fun exactSvgRequestOwnsReleaseAndSharesTheNativeExportGate(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge()
        assertTrue(b.beginSvgShare(3,100));assertFalse(b.beginSvgShare(4,100));assertFalse(b.beginPrintImage(4,100));assertFalse(b.appendSvgShareChunk(4,"abc"));b.releaseSvgShare(4);assertTrue(exporting(a).get());assertTrue(b.appendSvgShareChunk(3,"abc"));b.releaseSvgShare(3);assertFalse(exporting(a).get())
        assertTrue(b.beginPrintImage(4,100));assertFalse(b.beginSvgShare(5,100));b.releasePrintImage(4);assertFalse(exporting(a).get())
    }
    @Test fun invalidInputAndChunksReleaseOnlyTheirOwnAdmission(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge();assertFalse(b.beginSvgShare(0,100));assertFalse(exporting(a).get());assertTrue(b.beginSvgShare(2,100));assertFalse(b.appendSvgShareChunk(2,""));assertFalse(exporting(a).get());assertTrue(b.beginSvgShare(3,100));b.releaseSvgShare(2);assertTrue(exporting(a).get());b.releaseSvgShare(3)
    }
    @Test fun preparingAndPausedSvgTransfersCannotBeChangedByRetainedChunks(){
        val a=instance();field(a,"printForeground",true);val b=a.Bridge();assertTrue(b.beginSvgShare(3,100))
        val op=a.javaClass.getDeclaredField("svgOperation").apply{isAccessible=true}.get(a)
        op.javaClass.getDeclaredField("preparing").apply{isAccessible=true}.setBoolean(op,true);assertFalse(b.appendSvgShareChunk(3,"abc"));field(a,"printForeground",false);assertFalse(b.appendSvgShareChunk(3,"abc"));b.releaseSvgShare(3);assertFalse(exporting(a).get())
    }
}
