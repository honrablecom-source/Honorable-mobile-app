package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Private app directory only. Save-new writes a synced verified temporary file before rename.
 * Serializes this process; does not promise directory fsync, cross-process locking or power-loss recovery.
 */
internal class PhotoVideoPlanStore(directory:File) {
    private val root=directory.canonicalFile
    data class Summary(val id:String,val name:String,val readable:Boolean,val token:String?)
    data class Record(val plan:PhotoVideoPlan,val token:String)
    private fun file(id:String):File {
        require(PhotoVideoProjectCodec.validId(id)) { "Invalid saved plan" }
        return File(root,"$id.bin").also { require(it.canonicalFile==it.absoluteFile) { "Saved plan location changed" } }
    }
    private fun bytes(id:String):ByteArray {
        val target=file(id);require(target.isFile) { "Saved plan is unavailable" }
        FileInputStream(target).use { input ->
            val output=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true) {
                val count=input.read(buffer,0,minOf(buffer.size,PhotoVideoPlanCodec.MAX_BYTES+1-output.size()))
                if(count<0)break
                output.write(buffer,0,count)
                require(output.size()<=PhotoVideoPlanCodec.MAX_BYTES) { "Saved plan exceeds the supported size" }
            }
            return output.toByteArray()
        }
    }
    fun list(requireCurrent:()->Unit = {}):List<Summary> = synchronized(lock) {
        requireCurrent()
        if(!root.exists())return@synchronized emptyList()
        check(root.isDirectory) { "Saved plan storage is unavailable" }
        val files=checkNotNull(root.listFiles()) { "Saved plans could not be listed" }
        val summaries=files.filter { it.name.endsWith(".bin") && PhotoVideoProjectCodec.validId(it.name.removeSuffix(".bin")) }.map { target ->
            requireCurrent();val id=target.name.removeSuffix(".bin")
            val raw=runCatching { bytes(id) }.getOrNull()
            val decoded=raw?.let { runCatching { PhotoVideoPlanCodec.decode(it,id) }.getOrNull() }
            Summary(id,decoded?.name?:"Unreadable preparation plan",decoded!=null,raw?.let(PhotoVideoPlanCodec::token))
        }
        summaries.sortedWith(compareBy<Summary> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
    }
    fun load(id:String,requireCurrent:()->Unit = {}):Record = synchronized(lock) {
        requireCurrent();val raw=bytes(id);val plan=PhotoVideoPlanCodec.decode(raw,id);requireCurrent()
        Record(plan,PhotoVideoPlanCodec.token(raw))
    }
    fun save(plan:PhotoVideoPlan,requireCurrent:()->Unit = {}):Record = synchronized(lock) {
        val raw=PhotoVideoPlanCodec.encode(plan);requireCurrent()
        check(root.isDirectory || root.mkdirs()) { "Saved plan storage is unavailable" }
        val saved=list(requireCurrent)
        require(saved.size<20) { "Keep up to 20 preparation plans. Delete an older plan first." }
        require(saved.none { it.readable && it.name.equals(plan.name,true) }) { "Use an unused plan name; existing plans are never overwritten" }
        val target=file(plan.id);require(!target.exists()) { "Saved plan identity already exists" }
        val pending=File(root,".pending-${plan.id}")
        check(pending.createNewFile()) { "Preparation plan save is unavailable" }
        try {
            FileOutputStream(pending).use { output -> output.write(raw);output.fd.sync() }
            check(pending.readBytes().contentEquals(raw)) { "Preparation plan write could not be verified" }
            requireCurrent();check(!target.exists()) { "Saved plan identity changed during save" }
            check(pending.renameTo(target)) { "Preparation plan could not be committed" }
            val result=load(plan.id)
            check(result.token==PhotoVideoPlanCodec.token(raw)) { "Saved plan could not be verified" }
            result
        } finally { pending.delete() }
    }
    /** Exact reviewed metadata only; never opens/deletes an original or releases shared URI grants. */
    fun delete(id:String,expectedToken:String,requireCurrent:()->Unit = {}) = synchronized(lock) {
        require(expectedToken.matches(Regex("[0-9a-f]{64}")))
        requireCurrent();require(PhotoVideoPlanCodec.token(bytes(id))==expectedToken) { "Saved plan changed. Open the shelf again." }
        requireCurrent();check(file(id).delete()) { "Saved plan could not be deleted" }
    }
    companion object { private val lock=Any() }
}
