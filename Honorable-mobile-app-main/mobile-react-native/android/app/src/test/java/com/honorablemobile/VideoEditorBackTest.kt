package com.honorablemobile

import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.junit.Assert.*
import org.junit.Test

class VideoEditorBackTest {
    private class Dispatcher(val fail:Boolean=false):OnBackInvokedDispatcher {
        var callback:OnBackInvokedCallback?=null;var priority:Int?=null;var removals=0
        override fun registerOnBackInvokedCallback(priority:Int,callback:OnBackInvokedCallback){this.priority=priority;this.callback=callback;if(fail)error("Synthetic registration failure")}
        override fun unregisterOnBackInvokedCallback(callback:OnBackInvokedCallback){assertSame(this.callback,callback);removals++}
    }
    @Test fun callbackRoutesOnceAndReleaseDisablesLateCallbacksIdempotently() {
        val dispatcher=Dispatcher();var calls=0
        val release=VideoEditorBack.Api33.attach(dispatcher){calls++}
        assertEquals(0,calls);assertEquals(OnBackInvokedDispatcher.PRIORITY_DEFAULT,dispatcher.priority)
        dispatcher.callback!!.onBackInvoked();assertEquals(1,calls)
        release();release();assertEquals(1,dispatcher.removals)
        dispatcher.callback!!.onBackInvoked();assertEquals(1,calls)
    }
    @Test fun failedRegistrationRemovesAndDisablesPartiallyRegisteredCallback() {
        val dispatcher=Dispatcher(true);var calls=0
        try{VideoEditorBack.Api33.attach(dispatcher){calls++};fail("Registration unexpectedly worked")}catch(_:IllegalStateException){}
        assertEquals(1,dispatcher.removals);dispatcher.callback!!.onBackInvoked();assertEquals(0,calls)
    }
}
