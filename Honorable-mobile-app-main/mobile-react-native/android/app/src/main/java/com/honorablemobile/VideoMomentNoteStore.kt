package com.honorablemobile

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

/** Private no-backup owner/source identities, with no raw URI in the filename or payload. */
internal class VideoMomentNoteStore(directory:File,namespace:String,source:String,private val durationMs:Long) {
    init { require(durationMs>0) }
    private val file=AtomicFile(File(directory,VideoMomentCodec.storageKey(namespace,source)+".bin"))
    fun load():VideoMomentNotes=synchronized(lock){read()}
    fun add(note:VideoMomentNote,requireCurrent:()->Unit)=synchronized(lock){write(read().add(note),requireCurrent)}
    fun addAll(notes:List<VideoMomentNote>,requireCurrent:()->Unit)=synchronized(lock){write(VideoMomentNoteText.merge(read(),notes.toList()),requireCurrent)}
    fun rename(before:VideoMomentNote,label:String,requireCurrent:()->Unit)=synchronized(lock){write(read().rename(before,label),requireCurrent)}
    fun remove(note:VideoMomentNote,requireCurrent:()->Unit)=synchronized(lock){write(read().remove(note),requireCurrent)}
    private fun read():VideoMomentNotes {
        val stream=try{file.openRead()}catch(missing:FileNotFoundException){if(file.baseFile.exists()||File(file.baseFile.path+".bak").exists())throw missing;return VideoMomentNotes(durationMs)}
        stream.use{input->
            val result=ByteArrayOutputStream();val buffer=ByteArray(1024)
            while(true){val remaining=VideoMomentCodec.MAX_BYTES+1-result.size();require(remaining>0);val count=input.read(buffer,0,minOf(buffer.size,remaining));if(count<0)break;result.write(buffer,0,count);require(result.size()<=VideoMomentCodec.MAX_BYTES)}
            return VideoMomentCodec.decode(result.toByteArray()).also{require(it.durationMs==durationMs){"Source video duration changed. Saved notes were kept."}}
        }
    }
    private fun write(catalog:VideoMomentNotes,requireCurrent:()->Unit) {
        val bytes=VideoMomentCodec.encode(catalog);requireCurrent();val directory=requireNotNull(file.baseFile.parentFile)
        check(directory.isDirectory||directory.mkdirs()) { "Moment-note storage is unavailable" }
        val output=file.startWrite()
        try{output.write(bytes);requireCurrent();file.finishWrite(output);check(read()==catalog){"Saved note could not be verified"}}
        catch(failure:Throwable){file.failWrite(output);throw failure}
    }
    companion object{private val lock=Any()}
}
