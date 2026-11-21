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
        launch(kind, null, promise)
    }
    @ReactMethod fun openImageAi(value: String, promise: Promise) {
        val tool = CreativeEditorPolicy.imageAiTool(value)
        if (tool == null || closed) {
            promise.reject("EDITOR_UNAVAILABLE", "Choose an available AI photo tool.")
            return
        }
        launch("image", tool, promise)
    }
    private fun launch(kind: String, tool: String?, promise: Promise) {
        submit(promise) {
            if(closed)return@submit
            if(capabilities()[kind]!=true){promise.reject("EDITOR_UNAVAILABLE","The editor bundle is unavailable in this build.");return@submit}
            val activity=reactApplicationContext.currentActivity
            if(activity==null){promise.reject("EDITOR_INACTIVE","Open Noble before using this editor.");return@submit}
            activity.runOnUiThread {
                if(closed||activity.isDestroyed||activity.isFinishing||reactApplicationContext.currentActivity!==activity){promise.reject("EDITOR_INACTIVE","The workspace changed before opening.");return@runOnUiThread}
                runCatching {
                    val intent = Intent(activity, NobleCreativeEditorActivity::class.java)
                        .putExtra(NobleCreativeEditorActivity.EXTRA_KIND, kind)
                        .putExtra(NobleCreativeEditorActivity.EXTRA_IMAGE_AI, tool)
                    activity.startActivity(intent)
                    promise.resolve(true)
                }
                    .onFailure{promise.reject("EDITOR_OPEN_FAILED","The creative editor could not open. Existing projects are kept.")}
            }
        }
    }
    override fun invalidate(){closed=true;worker.shutdownNow();super.invalidate()}
}
