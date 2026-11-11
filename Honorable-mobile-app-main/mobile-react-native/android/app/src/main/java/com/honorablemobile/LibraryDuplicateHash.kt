package com.honorablemobile

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections

/** Selected-file byte comparison only. No decoding, semantic search, persistence or deletion. */
internal object LibraryDuplicateHash {
    const val FILE_LIMIT=256L*1024*1024
    const val TOTAL_LIMIT=512L*1024*1024
    const val TIMEOUT_NS=60_000_000_000L
    data class Limits(val fileBytes:Long=FILE_LIMIT,val totalBytes:Long=TOTAL_LIMIT,val timeoutNs:Long=TIMEOUT_NS) {
        init {require(fileBytes in 1..FILE_LIMIT&&totalBytes in 1..TOTAL_LIMIT&&timeoutNs in 1..TIMEOUT_NS)}
    }
    class Source(val stream:InputStream,val expectedLength:Long?=null,private val cleanup:()->Unit={}):Closeable {
        init {require(expectedLength==null||expectedLength>=0)}
        override fun close(){try{stream.close()}finally{cleanup()}}
    }
    data class Fingerprint(val id:String,val bytes:Long,val sha256:String)
    enum class Reason { FILE_LIMIT, TOTAL_LIMIT, UNAVAILABLE, LENGTH_CHANGED }
    data class Skipped(val id:String,val reason:Reason)
    class Group(val bytes:Long,val sha256:String,ids:List<String>){val ids:List<String> = Collections.unmodifiableList(ids.toList())}
    class Report(checked:List<Fingerprint>,skipped:List<Skipped>,val bytesRead:Long){
        val checked:List<Fingerprint> = Collections.unmodifiableList(checked.toList())
        val skipped:List<Skipped> = Collections.unmodifiableList(skipped.toList())
        val groups:List<Group> = Collections.unmodifiableList(checked.groupBy{it.bytes to it.sha256}.values.filter{it.size>1}.map{values->Group(values[0].bytes,values[0].sha256,values.map{it.id})})
    }
    private class Limit(val reason:Reason):IOException()

    fun inspect(items:List<LibraryCollectionItem>,open:(LibraryCollectionItem)->Source,requireCurrent:()->Unit={},clock:()->Long=System::nanoTime,limits:Limits=Limits(),minimumItems:Int=2):Report {
        require(minimumItems in 1..2)
        require(items.size in minimumItems..20&&items.map{it.id}.distinct().size==items.size&&items.map{it.uri}.distinct().size==items.size){"Choose $minimumItems–20 different Library originals"}
        val originals=items.toList();val checked=mutableListOf<Fingerprint>();val skipped=mutableListOf<Skipped>()
        val started=clock();var total=0L
        fun current(){requireCurrent();check(clock()-started<limits.timeoutNs){"Duplicate check timed out. Try fewer or smaller originals"}}
        val buffer=ByteArray(64*1024)
        for(item in originals){
            current()
            try {
                if(total>=limits.totalBytes)throw Limit(Reason.TOTAL_LIMIT)
                val value=open(item).use{source->
                    if(source.expectedLength?.let{it>=limits.fileBytes}==true)throw Limit(Reason.FILE_LIMIT)
                    val digest=MessageDigest.getInstance("SHA-256");var count=0L;var emptyReads=0
                    while(true){
                        current()
                        val remaining=minOf(limits.fileBytes-count,limits.totalBytes-total)
                        if(remaining<=0)throw Limit(if(count>=limits.fileBytes)Reason.FILE_LIMIT else Reason.TOTAL_LIMIT)
                        val read=source.stream.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt())
                        current()
                        if(read<0)break
                        if(read==0){emptyReads++;if(emptyReads>=3)throw IOException("No input progress");continue}
                        emptyReads=0;count+=read;total+=read;digest.update(buffer,0,read)
                    }
                    if(source.expectedLength!=null&&source.expectedLength!=count)throw Limit(Reason.LENGTH_CHANGED)
                    Fingerprint(item.id,count,digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)})
                }
                current();checked.add(value)
            } catch(error:Limit){current();skipped.add(Skipped(item.id,error.reason))}
              catch(_:IOException){current();skipped.add(Skipped(item.id,Reason.UNAVAILABLE))}
              catch(_:SecurityException){current();skipped.add(Skipped(item.id,Reason.UNAVAILABLE))}
        }
        current();return Report(checked,skipped,total)
    }
}
