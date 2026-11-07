package com.honorablemobile

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal object VideoSubtitleDocuments {
    private fun active(requireCurrent:()->Unit){
        if(Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException("Subtitle operation cancelled")
        requireCurrent()
    }
    fun readBounded(input:InputStream):ByteArray=readBounded(input,{})
    fun readBounded(input:InputStream,requireCurrent:()->Unit):ByteArray=ByteArrayOutputStream().use{output->
        val buffer=ByteArray(4096)
        while(true){
            active(requireCurrent);val count=input.read(buffer);active(requireCurrent)
            if(count<0)break
            require(count>0){"Cannot read subtitle data"}
            require(output.size()+count<=VideoCaptions.MAX_SRT_BYTES){"Choose an SRT file up to 128 KB"}
            output.write(buffer,0,count)
        }
        active(requireCurrent);output.toByteArray()
    }
    /** Cooperative boundaries; a blocking provider call may finish before cancellation is observed. */
    fun writeBounded(output:OutputStream,bytes:ByteArray,requireCurrent:()->Unit){
        active(requireCurrent);require(bytes.size in 1..VideoCaptions.MAX_SRT_BYTES)
        var offset=0
        while(offset<bytes.size){active(requireCurrent);val count=minOf(4096,bytes.size-offset);output.write(bytes,offset,count);offset+=count;active(requireCurrent)}
        active(requireCurrent);output.flush();active(requireCurrent)
    }
    fun cleanupIfOwned(separate:Boolean,requireCurrent:()->Unit,delete:()->Boolean):Boolean=separate&&runCatching{active(requireCurrent);delete()}.getOrDefault(false)
    fun writeCopy(context:Context,destination:Uri,bytes:ByteArray,originals:Set<Uri>)=writeCopy(context,destination,bytes,originals,{})
    /** Verify URI/inode separation before truncation; never clean up a destination after losing ownership. */
    fun writeCopy(context:Context,destination:Uri,bytes:ByteArray,originals:Set<Uri>,requireCurrent:()->Unit){
        active(requireCurrent);require(bytes.size in 1..VideoCaptions.MAX_SRT_BYTES&&originals.isNotEmpty())
        require(destination !in originals){"Subtitle copy cannot replace an imported original"}
        val resolver=context.contentResolver;var separate=false
        try{
            active(requireCurrent)
            resolver.openFileDescriptor(destination,"rw").use{output->
                requireNotNull(output);active(requireCurrent);val target=Os.fstat(output.fileDescriptor)
                for(original in originals){
                    active(requireCurrent);resolver.openFileDescriptor(original,"r").use{input->
                        requireNotNull(input){"An original is unavailable"};active(requireCurrent);val source=Os.fstat(input.fileDescriptor)
                        require(source.st_ino!=0L&&target.st_ino!=0L)
                        require(source.st_dev!=target.st_dev||source.st_ino!=target.st_ino){"Subtitle copy cannot replace an imported original"}
                    }
                }
                active(requireCurrent);separate=true;active(requireCurrent);Os.ftruncate(output.fileDescriptor,0)
                active(requireCurrent);ParcelFileDescriptor.AutoCloseOutputStream(output.dup()).use{writeBounded(it,bytes,requireCurrent)}
            }
            active(requireCurrent);val saved=resolver.openInputStream(destination)?.use{readBounded(it,requireCurrent)}?:error("Cannot verify subtitle copy")
            active(requireCurrent);require(saved.contentEquals(bytes)){"Saved subtitles differ from the exported copy"};active(requireCurrent)
        }catch(error:Throwable){
            val removed=cleanupIfOwned(separate,requireCurrent){DocumentsContract.deleteDocument(resolver,destination)}
            throw IllegalStateException("${error.message?:"Subtitle export failed"}${if(separate&&!removed)". Check Files for a partial copy."else""}",error)
        }
    }
}
