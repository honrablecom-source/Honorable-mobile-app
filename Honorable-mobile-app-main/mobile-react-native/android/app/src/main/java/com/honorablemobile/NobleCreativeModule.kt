package com.honorablemobile

import android.content.Intent
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Independent creative capability; does not impersonate the native search/account module. */
class NobleCreativeModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    private var verified: Map<String, Boolean>? = null
    override fun getName() = "NobleCreative"
    private fun capabilities(): Map<String, Boolean> = verified ?: listOf("image", "video").associateWith { kind ->
        CreativeEditorPolicy.verified(kind) { reactApplicationContext.assets.open(it) }
    }.also { verified = it }
    private fun submit(promise: Promise, task: () -> Unit) {
        try { worker.execute(task) } catch (_: RejectedExecutionException) {
            promise.reject("EDITOR_CLOSED", "Creative tools are unavailable.")
        }
    }
    @ReactMethod fun getCapabilities(promise: Promise) {
        if(closed){promise.reject("EDITOR_CLOSED","Creative tools are unavailable.");return}
        submit(promise) {
            val caps=capabilities()
            if(!closed)promise.resolve(Arguments.createMap().apply { putBoolean("image",caps["image"]==true);putBoolean("video",caps["video"]==true) })
        }
    }
    @ReactMethod fun openEditor(value: String, promise: Promise) {
        val kind=CreativeEditorPolicy.kind(value)
        if(kind==null||closed){promise.reject("EDITOR_UNAVAILABLE","Choose an available creative editor.");return}
        submit(promise) {
            if(closed)return@submit
            if(capabilities()[kind]!=true){promise.reject("EDITOR_UNAVAILABLE","The editor bundle is unavailable in this build.");return@submit}
            val activity=reactApplicationContext.currentActivity
            if(activity==null){promise.reject("EDITOR_INACTIVE","Open Noble before using this editor.");return@submit}
            activity.runOnUiThread {
                if(closed||activity.isDestroyed||activity.isFinishing||reactApplicationContext.currentActivity!==activity){promise.reject("EDITOR_INACTIVE","The workspace changed before opening.");return@runOnUiThread}
                runCatching{activity.startActivity(Intent(activity,NobleCreativeEditorActivity::class.java).putExtra(NobleCreativeEditorActivity.EXTRA_KIND,kind));promise.resolve(true)}
                    .onFailure{promise.reject("EDITOR_OPEN_FAILED","The creative editor could not open. Existing projects are kept.")}
            }
        }
    }
    override fun invalidate(){closed=true;worker.shutdownNow();super.invalidate()}
}
