package com.honorablemobile

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.locks.ReentrantLock

/** Downloads public weights only. There is no media, transcript, account or query request body. */
internal object VideoSpeechModelStore {
    private val lease = ReentrantLock()
    fun model(directory: File) = File(directory, "wav2vec2-english-${VideoSpeechModel.REVISION}.onnx")
    fun obtain(directory: File, downloadConsent: Boolean, stop: VideoSpeechModel.Stop, progress: (Long) -> Unit = {},
        connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): File {
        stop.check()
        check(lease.tryLock()) { "Speech model setup is already running; try again when it finishes" }
        var temporary: File? = null
        try {
            val target = model(directory)
            if (!downloadConsent) { VideoSpeechModel.verify(target, stop); return target }
            check(directory.isDirectory || directory.mkdirs()) { "Speech model storage is unavailable" }
            require(directory.usableSpace >= VideoSpeechModel.BYTES + 8_388_608) { "Free at least 104 MB for speech model setup" }
            val pending = File.createTempFile("speech-", ".part", directory); temporary = pending
            val started = System.nanoTime()
            fun current() { stop.check();require(System.nanoTime()-started<600_000_000_000L){"Speech model download took too long"} }
            var address = URL(VideoSpeechModel.URL)
            var connection: HttpURLConnection? = null
            try {
                var redirects = 0
                while (true) {
                    current()
                    require(address.protocol == "https" && address.userInfo == null && address.port in listOf(-1,443)) { "Unsafe speech model download address" }
                    val host = address.host.lowercase(java.util.Locale.ROOT)
                    require(host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(".hf.co")) { "Unsupported speech model download host" }
                    val request = connect(address)
                    connection = request
                    request.requestMethod = "GET"; request.instanceFollowRedirects = false
                    request.connectTimeout = 15000; request.readTimeout = 15000
                    request.setRequestProperty("Accept-Encoding", "identity")
                    val code = request.responseCode
                    if (code in listOf(301,302,303,307,308)) {
                        require(redirects++ < 5) { "Too many speech model redirects" }
                        address = URL(address, requireNotNull(request.getHeaderField("Location")))
                        request.disconnect(); connection = null; continue
                    }
                    require(code == 200) { "Speech model download failed; try again later" }
                    require(request.contentLengthLong == -1L || request.contentLengthLong == VideoSpeechModel.BYTES) { "Unexpected speech model size" }
                    var total = 0L
                    request.inputStream.buffered().use { input ->
                        pending.outputStream().buffered().use { output ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                current()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= VideoSpeechModel.BYTES) { "Speech model exceeds download limits" }
                                output.write(buffer,0,count); progress(total)
                            }
                        }
                    }
                    break
                }
            } finally { connection?.disconnect() }
            VideoSpeechModel.verify(pending, stop)
            stop.check()
            check(pending.renameTo(target)) { "Speech model could not be installed; existing model was kept" }
            temporary = null
            return target
        } finally { temporary?.delete(); lease.unlock() }
    }
}
