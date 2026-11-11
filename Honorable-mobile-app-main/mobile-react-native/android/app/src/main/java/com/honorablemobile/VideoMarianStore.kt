package com.honorablemobile

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONObject

/** Optional public weights only; never accepts caption text/account/query in a network request. */
internal object VideoMarianStore {
    private val lease=ReentrantLock()
    fun present(directory:File)=VideoMarianModel.artifacts.all{spec->File(directory,spec.name).let{it.isFile&&it.length()==spec.bytes}}
    fun obtain(directory:File,consent:Boolean,stop:VideoMarianModel.Stop,connect:(URL)->HttpURLConnection={it.openConnection()as HttpURLConnection}):File{
        stop.check();check(lease.tryLock()){"Translation model setup is already running"}
        try{
            for(spec in VideoMarianModel.artifacts){
                val target=File(directory,spec.name)
                val valid=try{VideoMarianModel.verify(target,spec,stop);true}catch(_:IllegalArgumentException){false}
                if(valid)continue
                require(consent){"Allow model setup before downloading missing or damaged weights"}
                check(directory.isDirectory||directory.mkdirs()){"Translation model storage is unavailable"}
                require(directory.usableSpace>=spec.bytes+8388608){"Free more storage for the optional translation model"}
                download(directory,spec,stop,connect)
            }
            return directory
        }finally{lease.unlock()}
    }
    fun vocabulary(directory:File,stop:VideoMarianModel.Stop):Map<String,Int>{
        val spec=VideoMarianModel.artifacts.single{it.name=="vocab.json"};val file=File(directory,spec.name);VideoMarianModel.verify(file,spec,stop)
        val json=JSONObject(file.readText(Charsets.UTF_8));require(json.length()==65001)
        val result=LinkedHashMap<String,Int>();val keys=json.keys()
        while(keys.hasNext()){stop.check();val key=keys.next();result[key]=json.getInt(key)}
        require(result.values.toSet().size==65001&&result.values.all{it in 0..65000});return result
    }
    private fun download(directory:File,artifact:VideoMarianModel.Artifact,stop:VideoMarianModel.Stop,connect:(URL)->HttpURLConnection):File{
        val target=File(directory,artifact.name);val pending=File.createTempFile("translation-",".part",directory)
        val started=System.nanoTime()
        fun current(){stop.check();require(System.nanoTime()-started<600000000000L){"Translation model download took too long"}}
        val progress:(Long)->Unit={}
        try{
            var address = URL(artifact.url)
            var connection: HttpURLConnection? = null
            try {
                var redirects = 0
                while (true) {
                    current()
                    require(address.protocol == "https" && address.userInfo == null && address.port in listOf(-1,443)) { "Unsafe translation model download address" }
                    val host = address.host.lowercase(java.util.Locale.ROOT)
                    require(host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(".hf.co")) { "Unsupported translation model download host" }
                    val request = connect(address)
                    connection = request
                    request.requestMethod = "GET"; request.instanceFollowRedirects = false
                    request.connectTimeout = 15000; request.readTimeout = 15000
                    request.setRequestProperty("Accept-Encoding", "identity")
                    val code = request.responseCode
                    if (code in listOf(301,302,303,307,308)) {
                        require(redirects++ < 5) { "Too many translation model redirects" }
                        address = URL(address, requireNotNull(request.getHeaderField("Location")))
                        request.disconnect(); connection = null; continue
                    }
                    require(code == 200) { "Translation model download failed; try again later" }
                    require(request.contentLengthLong == -1L || request.contentLengthLong == artifact.bytes) { "Unexpected translation model size" }
                    var total = 0L
                    request.inputStream.buffered().use { input ->
                        pending.outputStream().buffered().use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                current()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= artifact.bytes) { "Translation model exceeds download limits" }
                                output.write(buffer,0,count); progress(total)
                            }
                        }
                    }
                    break
                }
            } finally { connection?.disconnect() }
            VideoMarianModel.verify(pending, artifact, stop)
            stop.check()
            check(pending.renameTo(target)) { "Translation model could not be installed; existing model was kept" }
            return target
        }finally{pending.delete()}
    }
}
