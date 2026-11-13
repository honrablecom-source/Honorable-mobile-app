package com.honorablemobile

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.locks.ReentrantLock

/** Fetches pinned public model/labels only. No media, queries, audio or account payloads. */
internal object VideoSoundStore {
    data class Artifact(val name:String,val bytes:Long,val sha256:String)
    val artifacts=listOf(Artifact("yamnet.onnx",VideoSoundModel.BYTES,VideoSoundModel.SHA256),Artifact("yamnet_class_map.csv",VideoSoundModel.LABEL_BYTES,VideoSoundModel.LABEL_SHA256))
    private val lease=ReentrantLock()
    fun present(directory:File)=artifacts.all{File(directory,it.name).let{file->file.isFile&&file.length()==it.bytes}}
    fun obtain(directory:File,consent:Boolean,stop:VideoSoundModel.Stop,connect:(URL)->HttpURLConnection={it.openConnection()as HttpURLConnection}):File{
        stop.check();check(lease.tryLock()){"Sound model setup is already running"}
        try{
            for(spec in artifacts){
                val file=File(directory,spec.name)
                val valid=try{VideoSoundModel.verify(file,spec.bytes,spec.sha256,stop);true}catch(_:IllegalArgumentException){false}
                if(valid)continue
                require(consent){"Allow public sound-model setup before downloading missing or damaged files"}
                check(directory.isDirectory||directory.mkdirs()){"Sound-model storage is unavailable"}
                require(directory.usableSpace>=spec.bytes+8388608){"Free storage for the optional sound model"}
                download(directory,spec,stop,connect)
            }
            return directory
        }finally{lease.unlock()}
    }
    private fun download(directory:File,spec:Artifact,stop:VideoSoundModel.Stop,connect:(URL)->HttpURLConnection){
        val pending=File.createTempFile("sound-",".part",directory);val began=System.nanoTime()
        fun current(){stop.check();require(System.nanoTime()-began<300000000000L){"Sound-model download took too long"}}
        try{
            var url=URL(VideoSoundModel.BASE_URL+spec.name);var redirects=0
            while(true){
                current();val host=url.host.lowercase(java.util.Locale.ROOT)
                require(url.protocol=="https"&&url.userInfo==null&&url.port in listOf(-1,443)&&(host=="huggingface.co"||host.endsWith(".huggingface.co")||host.endsWith(".hf.co"))){"Unsafe sound-model download address"}
                val connection=connect(url)
                try{
                    connection.requestMethod="GET";connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=15000;connection.setRequestProperty("Accept-Encoding","identity")
                    val code=connection.responseCode
                    if(code in listOf(301,302,303,307,308)){
                        require(redirects++<5){"Too many sound-model redirects"};url=URL(url,requireNotNull(connection.getHeaderField("Location")));continue
                    }
                    require(code==200){"Sound-model download failed"}
                    require(connection.contentLengthLong in listOf(-1L,spec.bytes)){"Unexpected sound-model size"}
                    connection.inputStream.buffered().use{input->pending.outputStream().buffered().use{output->
                        var total=0L;val buffer=ByteArray(65536)
                        while(true){current();val size=input.read(buffer);if(size<0)break;total+=size;require(total<=spec.bytes){"Sound model exceeds download limit"};output.write(buffer,0,size)}
                    }}
                    break
                }finally{connection.disconnect()}
            }
            VideoSoundModel.verify(pending,spec.bytes,spec.sha256,stop);stop.check()
            check(pending.renameTo(File(directory,spec.name))){"Sound model could not be installed; previous file was kept"}
        }finally{pending.delete()}
    }
}
