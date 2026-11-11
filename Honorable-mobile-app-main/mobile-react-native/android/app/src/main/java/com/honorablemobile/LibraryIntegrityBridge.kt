package com.honorablemobile

import android.util.Base64
import app.honorable.auth.SecureAccountStore
import app.honorable.editor.EditorAccessMode
import app.honorable.search.MediaRecord
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.LifecycleState
import java.security.MessageDigest
import org.json.JSONObject

internal class EncryptedLibraryIntegrityRecord(context:ReactApplicationContext,owner:String):LibraryIntegrityRecord {
    private val digest=MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
    private val store=SecureAccountStore(context,"noble-library-integrity-$digest")
    override fun read():ByteArray? {
        val saved=store.readPreserving()?:return null
        val value=saved.opt("baselines")
        require(value is String&&value.length in 1..533_336){"Integrity data is damaged or too large"}
        return Base64.decode(value,Base64.NO_WRAP).also{require(it.size in 1..LibraryIntegrityCodec.MAX_BYTES&&Base64.encodeToString(it,Base64.NO_WRAP)==value){"Integrity encoding is damaged"}}
    }
    override fun write(bytes:ByteArray){require(bytes.size in 1..LibraryIntegrityCodec.MAX_BYTES);store.write(JSONObject().put("baselines",Base64.encodeToString(bytes,Base64.NO_WRAP)))}
}

internal object LibraryIntegrityBridge {
    fun run(context:ReactApplicationContext,action:String,expectedOwner:String,id:String,name:String,items:ReadableArray,request:LibraryDuplicateRequest,records:()->List<MediaRecord>):WritableMap {
        require(action in setOf("list","create","verify","delete")){"Choose an integrity action"}
        request.requireActive()
        val owner=requireNotNull(VideoPrivateOwner.namespace(context,EditorAccessMode.ACCOUNT)){"Sign in to use integrity baselines"}
        fun guard(){
            request.requireActive();val activity=context.currentActivity
            check(activity!=null&&!activity.isFinishing&&!activity.isDestroyed&&context.lifecycleState==LifecycleState.RESUMED){"Open Library again before checking integrity"}
            check(VideoPrivateOwner.namespace(context,EditorAccessMode.ACCOUNT)==owner&&(action=="list"||owner==expectedOwner)){"Account changed. Reopen integrity baselines"}
        }
        guard()
        val store=LibraryIntegrityStore(owner,EncryptedLibraryIntegrityRecord(context,owner))
        var catalog=store.load(::guard)
        var verification:List<LibraryIntegrityVerification.Item>?=null
        if(action=="create"){
            require(items.size() in 1..20){"Select 1–20 originals before keeping a baseline"}
            val chosen=(0 until items.size()).map{i->val item=requireNotNull(items.getMap(i));LibraryCollectionItem(requireNotNull(item.getString("id")),requireNotNull(item.getString("uri")),requireNotNull(item.getString("kind")))}
            catalog.requireNew(name,chosen)
            val report=LibraryDuplicateBridge.scan(context,chosen,request,records,minimumItems=1)
            guard()
            val originals=records().associateBy{it.id.toString()}
            fun bindings(){guard();val current=records().associateBy{it.id.toString()};check(chosen.all{entry->current[entry.id]?.let{it.uri==entry.uri&&it.kind.name==entry.kind}==true}){"Originals changed. Select them again"}}
            val labels=chosen.associate{item->item.id to originals[item.id]?.displayName.orEmpty().replace(Regex("[\\p{Cntrl}\\s]+")," ").trim().take(120).toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)}
            val baseline=LibraryIntegrityBaseline.create(name,chosen,report,System.currentTimeMillis(),labels=labels)
            catalog=store.save(baseline,::bindings)
        } else if(action=="delete")catalog=store.remove(id,::guard)
        else if(action=="verify"){
            val baseline=catalog.find(id)
            fun missing():Set<String>{guard();val current=records().associateBy{it.id.toString()};return baseline.entries.filter{e->current[e.original.id]?.let{it.uri==e.original.uri&&it.kind.name==e.original.kind}!=true}.map{it.original.id}.toSet()}
            val absent=missing();val chosen=baseline.entries.filter{it.original.id !in absent}.map{it.original}
            val report=if(chosen.isEmpty())LibraryDuplicateHash.Report(emptyList(),emptyList(),0)else LibraryDuplicateBridge.scan(context,chosen,request,records,minimumItems=1)
            guard();check(missing()==absent){"Library availability changed. Verify again"}
            catalog=store.load(::guard)
            val latest=catalog.find(id);check(latest.entries==baseline.entries&&latest.name==baseline.name&&latest.recordedAt==baseline.recordedAt){"Baseline changed. Reopen the shelf"}
            verification=LibraryIntegrityVerification.compare(baseline,report,absent)
        }
        guard()
        return Arguments.createMap().apply{
            putString("owner",owner)
            putArray("baselines",Arguments.createArray().apply{catalog.baselines.forEach{b->pushMap(Arguments.createMap().apply{putString("id",b.id);putString("name",b.name);putDouble("recordedAt",b.recordedAt.toDouble());putInt("count",b.entries.size)})}})
            verification?.let{values->putMap("verification",Arguments.createMap().apply{
                putString("baselineId",id);putDouble("checkedAt",System.currentTimeMillis().toDouble())
                putArray("items",Arguments.createArray().apply{values.forEach{value->pushMap(Arguments.createMap().apply{putString("id",value.id);putString("label",value.label);putString("status",value.status.name);putDouble("previousBytes",value.previousBytes.toDouble());value.currentBytes?.let{putDouble("currentBytes",it.toDouble())}})}})
            })}
        }
    }
}
