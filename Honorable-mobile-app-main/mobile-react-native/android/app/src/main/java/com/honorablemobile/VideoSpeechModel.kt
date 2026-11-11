package com.honorablemobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Optional speech model; never replaces, reconfigures or closes the shared search environment. */
internal object VideoSpeechModel {
    const val REVISION = "a19f851b3d42865797e410752b4c570c871e4825"
    const val BYTES = 95_286_046L
    const val SHA256 = "cd5040c147381580ed73258143dd8e0c28e800a09e74ee42ee2b3e8cb4d760a3"
    const val URL = "https://huggingface.co/Xenova/wav2vec2-base-960h/resolve/$REVISION/onnx/model_quantized.onnx"

    /** A run retains its native resources until inference settles, even after Stop. */
    class Stop {
        private var stopped = false
        private var active: OrtSession.RunOptions? = null
        @Synchronized fun cancel() {
            stopped = true
            active?.setTerminate(true)
        }
        @Synchronized fun isCancelled() = stopped || Thread.currentThread().isInterrupted
        @Synchronized internal fun attach(options: OrtSession.RunOptions) {
            check(active == null)
            if (stopped) throw CancellationException("Speech captions stopped")
            active = options
        }
        @Synchronized internal fun detach(options: OrtSession.RunOptions) {
            check(active === options)
            active = null
        }
        fun check() { if (isCancelled()) throw CancellationException("Speech captions stopped") }
    }

    fun verify(model: File, stop: Stop) {
        stop.check()
        require(model.isFile && model.length() == BYTES) { "Download the complete optional speech model first" }
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                stop.check()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == SHA256) { "Speech model integrity check failed; download it again" }
        stop.check()
    }

    fun recognize(model: File, pcm: FloatArray, sourceStartMs: Long, stop: Stop): VideoSpeechCaptions.Draft {
        verify(model, stop)
        val normalized = VideoSpeechCaptions.normalize(pcm) { stop.isCancelled() }
        val environment = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            stop.check()
            environment.createSession(model.absolutePath, options).use { session ->
                require(session.inputNames == setOf("input_values") && session.outputNames == setOf("logits")) { "Unexpected speech model interface" }
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(normalized), longArrayOf(1, normalized.size.toLong())).use { tensor ->
                    OrtSession.RunOptions().use { run ->
                        stop.attach(run)
                        try {
                            session.run(mapOf("input_values" to tensor), run).use { output ->
                                stop.check()
                                val batch = output.get(0).value as? Array<*> ?: error("Invalid speech output batch")
                                require(batch.size == 1)
                                val rows = batch[0] as? Array<*> ?: error("Invalid speech output frames")
                                val logits = Array(rows.size) { i -> rows[i] as? FloatArray ?: error("Invalid speech output scores") }
                                return VideoSpeechCaptions.decode(logits, normalized.size, sourceStartMs) { stop.isCancelled() }
                            }
                        } finally { stop.detach(run) }
                    }
                }
            }
        }
    }
}
