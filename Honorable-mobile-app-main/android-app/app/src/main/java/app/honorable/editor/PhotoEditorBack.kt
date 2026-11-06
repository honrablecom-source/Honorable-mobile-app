package app.honorable.editor

import android.app.Activity
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

/** API33 classes stay nested; older devices use the Activity.onBackPressed override. */
internal object PhotoEditorBack {
    fun install(activity:Activity,action:()->Unit):()->Unit=
        if(Build.VERSION.SDK_INT>=33)Api33.install(activity,action)else({})
    internal object Api33 {
        fun install(activity:Activity,action:()->Unit):()->Unit=attach(activity.onBackInvokedDispatcher,action)
        fun attach(dispatcher:android.window.OnBackInvokedDispatcher,action:()->Unit):()->Unit {
            val active=AtomicBoolean(true)
            val callback=android.window.OnBackInvokedCallback{if(active.get())action()}
            try{dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)}
            catch(error:Throwable){active.set(false);runCatching{dispatcher.unregisterOnBackInvokedCallback(callback)};throw error}
            return{if(active.compareAndSet(true,false))dispatcher.unregisterOnBackInvokedCallback(callback)}
        }
    }
}
