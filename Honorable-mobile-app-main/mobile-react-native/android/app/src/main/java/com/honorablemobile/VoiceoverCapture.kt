package com.honorablemobile

import java.io.File
import java.util.UUID

/** File/lifecycle policy is independent of Android microphone APIs for focused verification. */
internal class VoiceoverFiles(private val root:File,val namespace:String) {
    init { require(namespace=="guest-basic"||namespace.matches(Regex("account-[0-9a-f]{64}"))) }
    private fun directory():File {
        check(root.isDirectory||root.mkdirs()) { "Voiceover storage is unavailable" }
        val dir=File(root,namespace)
        check(dir.canonicalFile==File(root.canonicalFile,namespace)) { "Invalid voiceover owner directory" }
        check(dir.isDirectory||dir.mkdirs()) { "Voiceover storage is unavailable" }
        return dir
    }
    private fun file(name:String):File {
        val dir=directory();val result=File(dir,name)
        check(result.canonicalFile.parentFile==dir.canonicalFile&&result.canonicalFile.name==name) { "Invalid voiceover path" }
        return result
    }
    fun saved():List<File> = directory().listFiles().orEmpty().filter{it.name.endsWith(".m4a")&&validId(it.name.removeSuffix(".m4a"))}.map{file(it.name)}.filter{it.isFile}.sortedByDescending{it.lastModified()}
    fun savedFile(id:String):File { require(validId(id));return file("$id.m4a") }
    fun createPending():File {
        require(saved().size<MAX_SAVED) { "Delete an unused saved voiceover first. Limit: 20." }
        val result=file(".pending-${UUID.randomUUID()}.m4a");check(result.createNewFile());return result
    }
    private fun pendingId(value:File):String {
        require(value.name.startsWith(".pending-")&&value.name.endsWith(".m4a"))
        val id=value.name.removePrefix(".pending-").removeSuffix(".m4a");require(validId(id));require(value.canonicalFile==file(value.name).canonicalFile);return id
    }
    fun discardPending(value:File) { pendingId(value);check(!value.exists()||value.delete()) { "Unfinished voiceover could not be removed" } }
    fun promote(value:File):File {
        val id=pendingId(value);require(value.isFile&&value.length() in 1..MAX_BYTES)
        require(saved().size<MAX_SAVED) { "Saved voiceover limit reached" }
        val target=savedFile(id);check(!target.exists()&&value.renameTo(target)) { "Voiceover could not be saved" };return target
    }
    fun readName(id:String):String? = runCatching {
        require(savedFile(id).isFile)
        val label=file("$id.label");require(label.isFile&&label.length() in 1..320)
        val bytes=label.readBytes();val text=bytes.toString(Charsets.UTF_8)
        require(text.toByteArray(Charsets.UTF_8).contentEquals(bytes));checkedName(text)
    }.getOrNull()
    fun renameSaved(id:String,name:String,requireCurrent:()->Unit) {
        val label=checkedName(name);requireCurrent();require(savedFile(id).isFile)
        val target=file("$id.label")
        val pending=File.createTempFile(".voiceover-label-",".pending",directory())
        try{
            pending.outputStream().use{it.write(label.toByteArray(Charsets.UTF_8));it.fd.sync()}
            requireCurrent();require(savedFile(id).isFile)
            check(pending.renameTo(target)){"Voiceover name could not be saved"}
        }finally{pending.delete()}
    }
    fun deleteSaved(id:String) {
        val value=savedFile(id);check(value.isFile&&value.delete()) { "Saved voiceover could not be deleted" }
        runCatching{file("$id.label").delete()} // A stale label must not turn successful audio deletion into failure.
    }
    fun cleanAbandoned(live:Set<String>) {
        directory().listFiles().orEmpty().filter{it.name.matches(Regex("\\.pending-[0-9a-f-]{36}\\.m4a"))}.forEach{if(it.canonicalPath !in live)discardPending(it)}
    }
    companion object {
        const val MAX_SAVED=20
        const val MAX_BYTES=8L*1024*1024
        const val MAX_RECORD_MS=180000
        const val MAX_ACCEPTED_MS=182000L
        fun checkedName(value:String):String {
            val clean=value.trim()
            require(clean.isNotEmpty()&&clean.codePointCount(0,clean.length)<=80&&clean.none{it.isISOControl()}){"Use a name of 1–80 characters on one line"}
            require(clean.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==clean){"Use valid text for the name"}
            return clean
        }
        fun validId(value:String)=value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    }
}

internal interface VoiceoverBackend { fun start(file:File);fun stop();fun release() }
internal class VoiceoverCapture(private val files:VoiceoverFiles,private val backend:VoiceoverBackend,private val requireOwner:()->Unit) {
    enum class Phase { IDLE, RECORDING, REVIEW }
    var phase=Phase.IDLE;private set
    var pending:File?=null;private set
    @Synchronized fun start(permission:Boolean,active:Boolean) {
        require(permission&&active) { "Microphone permission and an active recording screen are required" }
        check(phase==Phase.IDLE);requireOwner()
        synchronized(lock){check(recording==null) { "Another voiceover is already recording" };recording=this}
        try {
            val file=files.createPending();pending=file;synchronized(lock){live.add(file.canonicalPath)}
            requireOwner();backend.start(file);phase=Phase.RECORDING
        } catch(failure:Throwable){discard();throw failure}
    }
    private fun releaseBackend() {
        try{backend.release()}catch(_:Throwable){}
        synchronized(lock){if(recording===this)recording=null}
    }
    @Synchronized fun stop(active:Boolean) {
        check(phase==Phase.RECORDING)
        try {
            require(active);backend.stop();releaseBackend();requireOwner()
            val file=requireNotNull(pending);require(file.isFile&&file.length() in 1..VoiceoverFiles.MAX_BYTES) { "Record a longer take before stopping" }
            phase=Phase.REVIEW
        } catch(failure:Throwable){discard();throw failure}
    }
    @Synchronized fun accept(duration:(File)->Long):File {
        check(phase==Phase.REVIEW);requireOwner();val file=requireNotNull(pending)
        require(duration(file) in 1..VoiceoverFiles.MAX_ACCEPTED_MS) { "Choose a voiceover no longer than three minutes" };requireOwner()
        val saved=files.promote(file);synchronized(lock){live.remove(file.canonicalPath)};pending=null;phase=Phase.IDLE;return saved
    }
    @Synchronized fun discard() {
        releaseBackend();val file=pending;pending=null;phase=Phase.IDLE
        if(file!=null)try{files.discardPending(file)}finally{synchronized(lock){live.remove(file.canonicalPath)}}
    }
    companion object {
        private val lock=Any();private var recording:VoiceoverCapture?=null;private val live=mutableSetOf<String>()
        fun livePaths():Set<String> = synchronized(lock){live.toSet()}
    }
}
