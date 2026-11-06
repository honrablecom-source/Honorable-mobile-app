package app.honorable.editor

import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.junit.Assert.*
import org.junit.Test

class PhotoEditorBackTest {
    private class Dispatcher(val registrationFails:Boolean=false,val removalFails:Boolean=false):OnBackInvokedDispatcher {
        var callback:OnBackInvokedCallback?=null;var priority:Int?=null;var removals=0
        override fun registerOnBackInvokedCallback(priority:Int,callback:OnBackInvokedCallback){this.callback=callback;this.priority=priority;if(registrationFails)error("Synthetic register failure")}
        override fun unregisterOnBackInvokedCallback(callback:OnBackInvokedCallback){assertSame(this.callback,callback);removals++;if(removalFails)error("Synthetic unregister failure")}
    }
    @Test fun releaseIsIdempotentAndLateCallbacksCannotClose(){
        val dispatcher=Dispatcher();var calls=0;val release=PhotoEditorBack.Api33.attach(dispatcher){calls++}
        assertEquals(OnBackInvokedDispatcher.PRIORITY_DEFAULT,dispatcher.priority)
        dispatcher.callback!!.onBackInvoked();assertEquals(1,calls)
        release();release();dispatcher.callback!!.onBackInvoked();assertEquals(1,calls);assertEquals(1,dispatcher.removals)
    }
    @Test fun failedRegistrationDisablesAndAttemptsOwnedCleanup(){
        val dispatcher=Dispatcher(registrationFails=true);var calls=0
        assertTrue(runCatching{PhotoEditorBack.Api33.attach(dispatcher){calls++}}.isFailure)
        dispatcher.callback!!.onBackInvoked();assertEquals(0,calls);assertEquals(1,dispatcher.removals)
    }
    @Test fun unregisterFailureStillDisablesLateCallbacks(){
        val dispatcher=Dispatcher(removalFails=true);var calls=0;val release=PhotoEditorBack.Api33.attach(dispatcher){calls++}
        assertTrue(runCatching{release()}.isFailure);release();dispatcher.callback!!.onBackInvoked()
        assertEquals(0,calls);assertEquals(1,dispatcher.removals)
    }
}
