package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class PhotoNeuralModelTest {
    @Test fun stoppedRequestNeverReadsOrCreatesModel() {
        val cancellation = PhotoNeuralModel.Cancellation(); cancellation.stop(); cancellation.stop()
        var reads = 0
        assertThrows(CancellationException::class.java) { PhotoNeuralModel.open({ reads++; ByteArrayInputStream(byteArrayOf()) }, cancellation) {} }
        assertEquals(0, reads)
    }

    @Test fun truncatedAndExtraBytesRejectedWithStreamClosed() {
        for (length in listOf(0, 7, PhotoNeuralModel.BYTES - 1, PhotoNeuralModel.BYTES + 1)) {
            var closed = false
            val stream = object : ByteArrayInputStream(ByteArray(length)) { override fun close() { closed = true; super.close() } }
            assertThrows(IllegalArgumentException::class.java) { PhotoNeuralModel.open({ stream }, PhotoNeuralModel.Cancellation()) {} }
            assertTrue(closed)
        }
    }

    @Test fun correctLengthWrongDigestRejectedBeforeNativeSession() {
        var closed = false
        val stream = object : ByteArrayInputStream(ByteArray(PhotoNeuralModel.BYTES)) { override fun close() { closed = true; super.close() } }
        val failure = assertThrows(IllegalArgumentException::class.java) { PhotoNeuralModel.open({ stream }, PhotoNeuralModel.Cancellation()) {} }
        assertTrue(failure.message!!.contains("verification")); assertTrue(closed)
    }

    @Test fun stoppedDuringReadClosesStreamAndDoesNotContinueLoading() {
        val cancellation = PhotoNeuralModel.Cancellation(); var closed = false; var reads = 0
        val stream = object : ByteArrayInputStream(ByteArray(65536)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int { reads++; return super.read(b, off, len).also { cancellation.stop() } }
            override fun close() { closed = true; super.close() }
        }
        assertThrows(CancellationException::class.java) { PhotoNeuralModel.open({ stream }, cancellation) {} }
        assertEquals(1, reads); assertTrue(closed)
    }

    @Test fun ownerInvalidatedDuringReadClosesStream() {
        var closed = false; var checks = 0
        val stream = object : ByteArrayInputStream(ByteArray(65536)) { override fun close() { closed = true; super.close() } }
        assertThrows(CancellationException::class.java) { PhotoNeuralModel.open({ stream }, PhotoNeuralModel.Cancellation()) { if (++checks == 3) throw CancellationException() } }
        assertTrue(closed)
    }
}
