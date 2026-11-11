package app.honorable.editor

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Dedicated neural-upscale session. Never modifies or closes the shared search environment. */
class PhotoNeuralModel private constructor(private val session: OrtSession, private val cancellation: Cancellation) : PhotoNeuralUpscale.Network, AutoCloseable {
    class Cancellation {
        private val lock = Any()
        private var stopped = false
        private var active: OrtSession.RunOptions? = null
        fun stop() = synchronized(lock) {
            stopped = true
            active?.setTerminate(true)
            Unit
        }
        fun check() = synchronized(lock) { if (stopped || Thread.currentThread().isInterrupted) throw CancellationException("AI upscale stopped") }
        internal fun attach(options: OrtSession.RunOptions) = synchronized(lock) {
            check(); check(active == null) { "An AI inference is already running" }; active = options
        }
        internal fun detach(options: OrtSession.RunOptions) = synchronized(lock) {
            check(active === options) { "AI inference lease changed" }; active = null
        }
    }

    override fun infer(width: Int, height: Int, rgb: FloatArray, current: () -> Unit): FloatArray {
        cancellation.check(); current()
        require(width in PhotoNeuralUpscale.WINDOW..PhotoNeuralUpscale.MAX_TILE_EDGE && height in PhotoNeuralUpscale.WINDOW..PhotoNeuralUpscale.MAX_TILE_EDGE &&
            width % PhotoNeuralUpscale.WINDOW == 0 && height % PhotoNeuralUpscale.WINDOW == 0 && rgb.size.toLong() == width.toLong() * height * 3) { "Invalid AI tile" }
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(rgb), longArrayOf(1, 3, height.toLong(), width.toLong())).use { input ->
            OrtSession.RunOptions().use { options ->
                cancellation.attach(options)
                try {
                    cancellation.check(); current()
                    session.run(mapOf("pixel_values" to input), options).use { result ->
                        cancellation.check(); current()
                        val tensor = result[0] as? OnnxTensor ?: error("AI output is not an image tensor")
                        require(tensor.info.type == OnnxJavaType.FLOAT && tensor.info.shape.contentEquals(longArrayOf(1, 3, height * 2L, width * 2L))) { "Unexpected AI output dimensions" }
                        val buffer = tensor.floatBuffer
                        require(buffer.remaining().toLong() == width.toLong() * height * 12) { "Incomplete AI output" }
                        val values = FloatArray(buffer.remaining())
                        buffer.get(values)
                        cancellation.check(); current()
                        return values
                    }
                } finally {
                    // Stop and detach share a lock; no stop call can touch these options after close.
                    cancellation.detach(options)
                }
            }
        }
    }

    override fun close() { session.close() }

    companion object {
        const val ASSET = "neural-upscale/swin2sr-lightweight-x2.onnx"
        const val BYTES = 8_078_888
        const val SHA256 = "c2abbfe0cc8e685b5e11964970f8ebe3d24072e904fd5545a30ac31ec1e110db"

        fun open(context: Context, cancellation: Cancellation, current: () -> Unit): PhotoNeuralModel =
            open({ context.applicationContext.assets.open(ASSET) }, cancellation, current)

        /** Injectable byte source for exact production-class host inference and pin failure checks. */
        internal fun open(read: () -> InputStream, cancellation: Cancellation, current: () -> Unit): PhotoNeuralModel {
            fun checked() { cancellation.check(); current() }
            checked()
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = ByteArrayOutputStream(BYTES)
            read().use { stream ->
                val buffer = ByteArray(32768)
                while (true) {
                    checked(); val count = stream.read(buffer)
                    if (count < 0) break
                    require(count > 0 && bytes.size().toLong() + count <= BYTES) { "AI model is incomplete or too large" }
                    digest.update(buffer, 0, count); bytes.write(buffer, 0, count)
                }
            }
            checked()
            val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(bytes.size() == BYTES && sha == SHA256) { "AI model verification failed; your image was kept" }
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
                // Session construction itself has no RunOptions; retain ownership until it settles.
                val session = OrtEnvironment.getEnvironment().createSession(bytes.toByteArray(), options)
                try {
                    checked()
                    require(session.inputNames == setOf("pixel_values") && session.outputNames == setOf("reconstruction")) { "Unexpected AI model contract" }
                    val input = session.inputInfo.getValue("pixel_values").info as? TensorInfo ?: error("Unexpected AI input")
                    val output = session.outputInfo.getValue("reconstruction").info as? TensorInfo ?: error("Unexpected AI output")
                    require(input.type == OnnxJavaType.FLOAT && input.shape.size == 4 && output.type == OnnxJavaType.FLOAT && output.shape.size == 4) { "Unexpected AI image type" }
                    return PhotoNeuralModel(session, cancellation)
                } catch (failure: Throwable) {
                    session.close(); throw failure
                }
            }
        }
    }
}
