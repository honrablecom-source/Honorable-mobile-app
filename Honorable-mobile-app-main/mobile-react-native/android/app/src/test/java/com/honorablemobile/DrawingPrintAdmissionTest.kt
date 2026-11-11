package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.junit.Assert.*
import org.junit.Test

import java.util.concurrent.atomic.AtomicBoolean

/** Actual bridge admission/chunk ownership only; no Android constructor, WebView, bitmap or print service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class DrawingPrintAdmissionTest {
    private fun field(activity:HonorableDrawingActivity,name:String,value:Any){HonorableDrawingActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)}
    private fun instance():HonorableDrawingActivity {
        val unsafe=JvmTestAllocator
        return (unsafe.allocateInstance(HonorableDrawingActivity::class.java) as HonorableDrawingActivity).also{
            field(it,"operationLock",Any());field(it,"exporting",AtomicBoolean(false));field(it,"importing",AtomicBoolean(false))
        }
    }
    private fun flag(activity:HonorableDrawingActivity,name:String) = HonorableDrawingActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity) as AtomicBoolean
    @Test fun bridgeDeniesInactiveClosedAndBusyRequestsBeforeNativeWork(){
        val activity=instance();val bridge=activity.Bridge()
        assertFalse(bridge.beginPrintImage(1,100));field(activity,"printForeground",true);field(activity,"printClosed",true);assertFalse(bridge.beginPrintImage(1,100));field(activity,"printClosed",false)
        flag(activity,"importing").set(true);assertFalse(bridge.beginPrintImage(1,100));flag(activity,"importing").set(false)
        flag(activity,"exporting").set(true);assertFalse(bridge.beginPrintImage(1,100));assertTrue(flag(activity,"exporting").get())
    }
    @Test fun exactRequestOwnsChunksAndReleaseCannotUnlockAnotherTransfer(){
        val activity=instance();field(activity,"printForeground",true);val bridge=activity.Bridge();val value=DrawingPolicy.PNG_PREFIX+"AAAA"
        assertTrue(bridge.beginPrintImage(7,value.length));assertFalse(bridge.beginPrintImage(8,value.length));assertFalse(bridge.appendPrintImageChunk(8,value))
        bridge.releasePrintImage(8);assertTrue(flag(activity,"exporting").get());assertTrue(bridge.appendPrintImageChunk(7,value))
        bridge.releasePrintImage(7);assertFalse(flag(activity,"exporting").get());assertTrue(bridge.beginPrintImage(8,value.length));bridge.releasePrintImage(7);assertTrue(flag(activity,"exporting").get());bridge.releasePrintImage(8)
    }
    @Test fun invalidRequestAndChunkFailuresReleaseOnlyTheirAdmittedOperation(){
        val activity=instance();field(activity,"printForeground",true);val bridge=activity.Bridge()
        assertFalse(bridge.beginPrintImage(0,100));assertFalse(flag(activity,"exporting").get())
        assertTrue(bridge.beginPrintImage(1,100));assertFalse(bridge.appendPrintImageChunk(1,""));assertFalse(flag(activity,"exporting").get())
        assertTrue(bridge.beginPrintImage(2,100));field(activity,"printForeground",false);assertFalse(bridge.appendPrintImageChunk(2,"AAAA"));assertTrue(flag(activity,"exporting").get());bridge.releasePrintImage(2);assertFalse(flag(activity,"exporting").get())
    }
    @Test fun preparedTransferCannotBeMutatedOrReleasedByAnotherRequest(){
        val activity=instance();field(activity,"printForeground",true);val bridge=activity.Bridge()
        assertTrue(bridge.beginPrintImage(7,100))
        val upload=HonorableDrawingActivity::class.java.getDeclaredField("printUpload").apply{isAccessible=true}.get(activity)
        field(activity,"printPreparing",upload)
        assertFalse(bridge.appendPrintImageChunk(7,"AAAA"));bridge.releasePrintImage(8);assertTrue(flag(activity,"exporting").get())
        bridge.releasePrintImage(7);assertFalse(flag(activity,"exporting").get())
        assertNull(HonorableDrawingActivity::class.java.getDeclaredField("printPreparing").apply{isAccessible=true}.get(activity))
    }

}
