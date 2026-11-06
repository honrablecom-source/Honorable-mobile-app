package com.honorablemobile

import android.content.Context
import android.util.Base64
import app.honorable.auth.SecureAccountStore
import org.json.JSONObject

internal interface VideoVersionRecord {
    fun read():ByteArray?
    fun write(bytes:ByteArray)
}

/** Both guest and account version metadata reuse the existing private keystore/AtomicFile envelope. */
internal class EncryptedVideoVersionRecord(context:Context,scope:VideoVersionScope):VideoVersionRecord {
    private val store=SecureAccountStore(context,scope.storageName)
    override fun read():ByteArray?=VideoVersionRecordCodec.decode(store.readPreserving())
    override fun write(bytes:ByteArray){store.write(VideoVersionRecordCodec.encode(bytes))}
}

internal object VideoVersionRecordCodec {
    fun decode(record:JSONObject?):ByteArray? {
        if(record==null)return null
        val value=record.opt("versions")
        require(value is String&&value.length in 1..720_000){"Saved versions are damaged or too large"}
        return Base64.decode(value,Base64.NO_WRAP).also {
            require(it.size in 1..VideoProjectVersionCodec.MAX_BYTES)
            require(Base64.encodeToString(it,Base64.NO_WRAP)==value){"Saved version encoding is damaged"}
        }
    }
    fun encode(bytes:ByteArray):JSONObject {
        require(bytes.size in 1..VideoProjectVersionCodec.MAX_BYTES)
        return JSONObject().put("versions",Base64.encodeToString(bytes,Base64.NO_WRAP))
    }
}

internal class VideoProjectVersionStore(private val scope:VideoVersionScope,private val duration:Long,private val record:VideoVersionRecord) {
    class Deletion internal constructor(internal val scope:String,internal val before:VideoProjectVersions,internal val after:VideoProjectVersions)
    fun load():VideoProjectVersions=synchronized(lock){read()}
    fun save(value:VideoProjectVersion,requireCurrent:()->Unit)=synchronized(lock){requireCurrent();write(read().add(value),requireCurrent)}
    fun requireEntry(value:VideoProjectVersion):VideoProjectVersion=synchronized(lock){
        requireNotNull(read().entries.singleOrNull{it.id==value.id&&it==value}){"Saved versions changed. Reopen the shelf."}
    }
    fun delete(value:VideoProjectVersion,requireCurrent:()->Unit):Deletion=synchronized(lock){
        requireCurrent();val before=read();val after=before.remove(value);write(after,requireCurrent)
        Deletion(scope.storageName,before,after)
    }
    fun undo(value:Deletion,requireCurrent:()->Unit)=synchronized(lock){
        requireCurrent();require(value.scope==scope.storageName&&read()==value.after){"Saved versions changed. Deletion Undo is no longer available."}
        write(value.before,requireCurrent)
    }
    private fun read():VideoProjectVersions=record.read()?.let{VideoProjectVersionCodec.decode(scope,duration,it)}?:VideoProjectVersions(duration)
    private fun write(catalog:VideoProjectVersions,requireCurrent:()->Unit) {
        val bytes=VideoProjectVersionCodec.encode(scope,catalog)
        requireCurrent();record.write(bytes)
        check(record.read()?.contentEquals(bytes)==true){"Version save could not be verified. Reopen saved versions before retrying."}
    }
    companion object { private val lock=Any() }
}

/** Existing broad/selected native video capability is only a precheck; every original is opened below. */
internal object VideoVersionReadPolicy {
    fun existingAccess(value:String,persistedOrOwned:Boolean,videoCapability:Boolean):Boolean {
        if(persistedOrOwned)return true
        val uri=android.net.Uri.parse(value);val parts=uri.pathSegments
        return videoCapability&&uri.scheme=="content"&&uri.authority=="media"&&parts.size==4&&
            parts[0].isNotBlank()&&parts[1]=="video"&&parts[2]=="media"&&(parts[3].toLongOrNull()?:0)>0
    }
}
