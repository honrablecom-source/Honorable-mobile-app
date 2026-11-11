package com.honorablemobile

import android.os.CancellationSignal
import app.honorable.editor.EditorAccessMode
import app.honorable.search.MediaRecord
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.LifecycleState
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One owned request. Cancellation closes current IO and asks the provider to cancel opening. */
internal class LibraryDuplicateRequest(val id:String){
    init {require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))){"Invalid duplicate-check request"}}
    val signal=CancellationSignal()
    private val cancelled=AtomicBoolean(false)
    private val input=AtomicReference<InputStream?>()
    fun requireActive(){if(cancelled.get())throw CancellationException("Duplicate check cancelled")}
    fun bind(stream:InputStream){
        try{requireActive();check(input.compareAndSet(null,stream));requireActive()}
        catch(error:Throwable){input.compareAndSet(stream,null);runCatching{stream.close()};throw error}
    }
    fun detach(stream:InputStream){input.compareAndSet(stream,null)}
    fun cancel(){cancelled.set(true);runCatching{signal.cancel()};input.getAndSet(null)?.let{runCatching{it.close()}}}
}

internal object LibraryDuplicateBridge {
    fun scan(context:ReactApplicationContext,chosen:List<LibraryCollectionItem>,request:LibraryDuplicateRequest,records:()->List<MediaRecord>,minimumItems:Int=2):LibraryDuplicateHash.Report {
        request.requireActive()
        val owner=requireNotNull(VideoPrivateOwner.namespace(context,EditorAccessMode.ACCOUNT)){"Sign in to compare Library originals"}
        fun requireForeground(){
            request.requireActive()
            val activity=context.currentActivity
            check(activity!=null&&!activity.isFinishing&&!activity.isDestroyed&&context.lifecycleState==LifecycleState.RESUMED){"Open Library again before comparing originals"}
        }
        requireForeground()
        fun requireBindings(){
            requireForeground();check(VideoPrivateOwner.namespace(context,EditorAccessMode.ACCOUNT)==owner){"Account changed. Compare originals again"}
            val catalog=records().associateBy{it.id.toString()}
            require(chosen.all{entry->catalog[entry.id]?.let{it.uri==entry.uri&&it.kind.name==entry.kind}==true}){"A chosen original changed. Refresh Library and select it again"}
        }
        requireBindings()
        val report=LibraryDuplicateHash.inspect(chosen,open={item->
            requireForeground()
            val asset=context.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(item.uri),"r",request.signal)?:throw java.io.IOException("Original is unavailable")
            val stream=try{asset.createInputStream()}catch(error:Throwable){asset.close();throw error}
            try{request.bind(stream)}catch(error:Throwable){runCatching{asset.close()};throw error}
            LibraryDuplicateHash.Source(stream,asset.declaredLength.takeIf{it>=0}){try{asset.close()}finally{request.detach(stream)}}
        },requireCurrent=::requireForeground,minimumItems=minimumItems)
        requireBindings()
        return report
    }
    fun inspect(context:ReactApplicationContext,items:ReadableArray,request:LibraryDuplicateRequest,records:()->List<MediaRecord>):WritableMap {
        require(items.size() in 2..20){"Choose 2–20 originals"}
        val chosen=(0 until items.size()).map{i->val item=requireNotNull(items.getMap(i));LibraryCollectionItem(requireNotNull(item.getString("id")),requireNotNull(item.getString("uri")),requireNotNull(item.getString("kind")))}
        val report=scan(context,chosen,request,records)
        return Arguments.createMap().apply{
            putDouble("bytesRead",report.bytesRead.toDouble())
            putArray("checked",Arguments.createArray().apply{report.checked.forEach{entry->pushMap(Arguments.createMap().apply{putString("id",entry.id);putDouble("bytes",entry.bytes.toDouble());putString("sha256",entry.sha256)})}})
            putArray("skipped",Arguments.createArray().apply{report.skipped.forEach{entry->pushMap(Arguments.createMap().apply{putString("id",entry.id);putString("reason",entry.reason.name)})}})
            putArray("groups",Arguments.createArray().apply{report.groups.forEach{group->pushMap(Arguments.createMap().apply{putDouble("bytes",group.bytes.toDouble());putString("sha256",group.sha256);putArray("ids",Arguments.createArray().apply{group.ids.forEach{pushString(it)}})})}})
        }
    }
}
