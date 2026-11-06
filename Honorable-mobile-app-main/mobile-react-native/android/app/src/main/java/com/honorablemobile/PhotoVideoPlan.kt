package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** A guest-local preparation recipe, not media, an encoded project, or a URI permission. */
internal class PhotoVideoPlan(
    val id:String,
    val name:String,
    val format:PhotoVideoFormat,
    val defaultMs:Long,
    val selection:PhotoVideoSelection,
) {
    init {
        require(PhotoVideoProjectCodec.validId(id)) { "Invalid saved plan" }
        require(name==name.trim() && name.isNotEmpty() && name.length<=40 &&
            name.none { it.isISOControl() } && name.toByteArray(Charsets.UTF_8).size<=120 &&
            name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==name) { "Use a plan name of 1–40 characters" }
        require(defaultMs in DEFAULT_HOLDS) { "Choose an available default hold" }
        require(selection.items.size in 1..20) { "Choose photos before saving a plan" }
    }
    companion object { val DEFAULT_HOLDS:List<Long> = java.util.Collections.unmodifiableList(listOf(3000L,5000L,8000L,10000L)) }
}

internal object PhotoVideoPlanCodec {
    const val MAX_BYTES=65536
    private const val MAGIC=0x4E505650 // NPVP
    fun token(bytes:ByteArray):String=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun encode(plan:PhotoVideoPlan):ByteArray {
        val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC);out.writeInt(1);out.writeUTF(plan.id);out.writeUTF(plan.name)
            out.writeInt(plan.format.ordinal);out.writeLong(plan.defaultMs);out.writeInt(plan.selection.items.size)
            plan.selection.items.forEach { uri -> out.writeUTF(uri);out.writeLong(plan.selection.holds[uri]?:0L) }
        }
        val body=buffer.toByteArray()
        require(body.size+32<=MAX_BYTES) { "Saved plan exceeds the supported size" }
        return body+MessageDigest.getInstance("SHA-256").digest(body)
    }
    fun decode(bytes:ByteArray,expectedId:String):PhotoVideoPlan {
        require(bytes.size in 33..MAX_BYTES) { "Saved plan is incomplete or too large" }
        val body=bytes.copyOfRange(0,bytes.size-32)
        require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(body),bytes.copyOfRange(bytes.size-32,bytes.size))) { "Saved plan changed or is unreadable" }
        return DataInputStream(ByteArrayInputStream(body)).use { input ->
            require(input.readInt()==MAGIC && input.readInt()==1) { "Saved plan version is unavailable" }
            val id=input.readUTF();require(id==expectedId) { "Saved plan identity changed" }
            val name=input.readUTF();val format=input.readInt();require(format in PhotoVideoFormat.entries.indices)
            val default=input.readLong();val count=input.readInt();require(count in 1..20)
            val items=ArrayList<String>(count);val holds=LongArray(count)
            repeat(count) { items.add(input.readUTF());holds[it]=input.readLong() }
            require(input.available()==0) { "Saved plan has unexpected data" }
            PhotoVideoPlan(id,name,PhotoVideoFormat.entries[format],default,PhotoVideoSelection.restore(items,holds))
        }
    }
}

/** Owns slow metadata work and invalidates old review dialogs across foreground changes. */
internal class PhotoVideoPlanGate {
    class Lease internal constructor(internal val epoch:Long)
    private var epoch=0L
    private var active=false
    private var destroyed=false
    private var owned:Lease?=null
    @Synchronized fun resume() { if(!destroyed) { active=true;epoch++ } }
    @Synchronized fun pause() { active=false;epoch++ }
    @Synchronized fun destroy() { destroyed=true;active=false;epoch++;owned=null }
    @Synchronized fun busy():Boolean=owned!=null
    @Synchronized fun review():Long?=if(active&&!destroyed&&owned==null)epoch else null
    @Synchronized fun canReview(value:Long):Boolean=active&&!destroyed&&owned==null&&epoch==value
    @Synchronized fun begin():Lease? {
        if(!active||destroyed||owned!=null)return null
        epoch++ // A later completion must never revalidate a pre-operation dialog.
        return Lease(epoch).also { owned=it }
    }
    @Synchronized fun current(value:Lease):Boolean=active&&!destroyed&&owned===value&&epoch==value.epoch
    @Synchronized fun finish(value:Lease):Boolean {
        if(owned!==value)return false
        val accept=current(value);owned=null;return accept
    }
}
