package app.honorable.editor

import android.graphics.Bitmap
import android.os.Looper
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoTextLayoutReaderTest {
    private fun lines() = listOf(PhotoTextLayout.RawLine("Recognized lettering", PhotoTextLayout.Box(3, 4, 90, 25)))
    private fun image() = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
    private fun worker(action: () -> PhotoTextLayout.Batch): Result<PhotoTextLayout.Batch> {
        val task = FutureTask { runCatching(action) }; val thread = Thread(task, "text-layout-reader-test")
        thread.start()
        try { return task.get(10, TimeUnit.SECONDS) } finally { if (thread.isAlive) thread.interrupt(); thread.join(2000) }
    }

    @Test fun completedRealGoogleTaskCopiesEveryLineBeforeClosingOwnedInput() {
        val bitmap = image(); val close = AtomicInteger(); val settle = AtomicInteger(); val task = Tasks.forResult(lines())
        val result = worker { PhotoTextLayoutReader.read(bitmap, {}, { settle.incrementAndGet() }, { owned ->
            assertSame(bitmap, owned)
            PhotoTextLayoutReader.Pending(task, { close.incrementAndGet() }) { assertFalse(bitmap.isRecycled); task.result }
        }) }.getOrThrow()
        assertEquals("Recognized lettering", result.lines.single().text); assertEquals(100, result.width); assertEquals(60, result.height)
        assertTrue(bitmap.isRecycled); assertEquals(1, close.get()); assertEquals(1, settle.get())
    }

    @Test fun stopKeepsSdkInputAndParentSettlementLeaseUntilTaskActuallyCompletes() {
        for (fail in listOf(false, true)) {
            val bitmap = image(); val close = AtomicInteger(); val settle = AtomicInteger(); val checks = AtomicInteger(); val pending = TaskCompletionSource<List<PhotoTextLayout.RawLine>>()
            val result = worker { PhotoTextLayoutReader.read(bitmap, { if (checks.incrementAndGet() == 2) throw CancellationException() }, { settle.incrementAndGet() }, {
                PhotoTextLayoutReader.Pending(pending.task, { close.incrementAndGet() }) { error("Stopped result must not be decoded") }
            }) }
            assertTrue(result.exceptionOrNull() is CancellationException); assertFalse(bitmap.isRecycled); assertEquals(0, close.get()); assertEquals(0, settle.get())
            if (fail) pending.setException(IllegalStateException("Synthetic SDK failure")) else pending.setResult(lines())
            assertTrue(bitmap.isRecycled); assertEquals(1, close.get()); assertEquals(1, settle.get())
        }
    }

    @Test fun timeoutRetainsInputUntilLateSdkCompletionWithoutDecodingIt() {
        val bitmap = image(); val close = AtomicInteger(); val settle = AtomicInteger(); val reads = AtomicInteger(); val pending = TaskCompletionSource<List<PhotoTextLayout.RawLine>>()
        val result = worker { PhotoTextLayoutReader.read(bitmap, {}, { settle.incrementAndGet() }, {
            PhotoTextLayoutReader.Pending(pending.task, { close.incrementAndGet() }) { error("Timed-out result must not be decoded") }
        }, { if (reads.incrementAndGet() < 3) 0L else 90_000_000_001L }) }
        assertTrue(result.exceptionOrNull()?.message?.contains("timed out") == true); assertFalse(bitmap.isRecycled); assertEquals(0, settle.get())
        pending.setResult(lines()); assertTrue(bitmap.isRecycled); assertEquals(1, close.get()); assertEquals(1, settle.get())
    }

    @Test fun sdkFailureAndCancellationCloseWithoutPublishingOrDecoding() {
        val cancellation = CancellationTokenSource(); val canceled = TaskCompletionSource<List<PhotoTextLayout.RawLine>>(cancellation.token); cancellation.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(canceled.task.isComplete && canceled.task.isCanceled)
        val tasks: List<Task<*>> = listOf(Tasks.forException<List<PhotoTextLayout.RawLine>>(IllegalStateException("Synthetic SDK failure")), canceled.task)
        for (task in tasks) {
            val bitmap = image(); val settle = AtomicInteger(); val close = AtomicInteger()
            val result = worker { PhotoTextLayoutReader.read(bitmap, {}, { settle.incrementAndGet() }, {
                PhotoTextLayoutReader.Pending(task, { close.incrementAndGet() }) { error("Failed task must not be decoded") }
            }) }
            assertTrue(result.isFailure); assertTrue(bitmap.isRecycled); assertEquals(1, close.get()); assertEquals(1, settle.get())
        }
    }

    @Test fun malformedGeometryAfterSuccessFailsWholeBatchAndClosesOnce() {
        val bitmap = image(); var closed = 0; var settled = 0
        val result = worker { PhotoTextLayoutReader.read(bitmap, {}, { settled++ }, {
            PhotoTextLayoutReader.Pending(Tasks.forResult(Unit), { closed++ }) { listOf(PhotoTextLayout.RawLine("Outside", PhotoTextLayout.Box(101, 0, 110, 10))) }
        }) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException); assertTrue(bitmap.isRecycled); assertEquals(1, closed); assertEquals(1, settled)
    }

    @Test fun failedStartOrInvalidOwnerClosesInputAndSignalsSettlement() {
        for (beforeStart in listOf(false, true)) {
            val bitmap = image(); var starts = 0; var settled = 0
            val result = worker { PhotoTextLayoutReader.read(bitmap, { if (beforeStart) throw CancellationException() }, { settled++ }, {
                starts++; throw IllegalStateException("Synthetic start failure")
            }) }
            assertTrue(result.isFailure); assertTrue(bitmap.isRecycled); assertEquals(1, settled); assertEquals(if (beforeStart) 0 else 1, starts)
        }
    }

    @Test fun awaitingSdkKeepsInputAliveAndOnlyDecodesAfterActualCompletion() {
        val bitmap = image(); val began = CountDownLatch(1); val pending = TaskCompletionSource<List<PhotoTextLayout.RawLine>>(); val settled = AtomicInteger(); val decoded = AtomicInteger()
        val future = FutureTask { PhotoTextLayoutReader.read(bitmap, {}, { settled.incrementAndGet() }, {
            began.countDown()
            PhotoTextLayoutReader.Pending(pending.task, {}) { assertTrue(pending.task.isComplete); decoded.incrementAndGet(); pending.task.result }
        }) }
        val thread = Thread(future); thread.start()
        try {
            assertTrue(began.await(3, TimeUnit.SECONDS)); assertFalse(bitmap.isRecycled); assertEquals(0, decoded.get()); assertEquals(0, settled.get())
            pending.setResult(lines()); assertEquals("Recognized lettering", future.get(10, TimeUnit.SECONDS).lines.single().text)
            assertTrue(bitmap.isRecycled); assertEquals(1, decoded.get()); assertEquals(1, settled.get())
        } finally { if (thread.isAlive) thread.interrupt(); thread.join(2000) }
    }

    @Test fun clientCloseFailureCannotLeakBitmapOrSkipSettlement() {
        val bitmap = image(); var settled = 0
        val result = worker { PhotoTextLayoutReader.read(bitmap, {}, { settled++ }, {
            PhotoTextLayoutReader.Pending(Tasks.forResult(Unit), { throw IllegalStateException("Synthetic close failure") }) { lines() }
        }) }
        assertTrue(result.isSuccess); assertTrue(bitmap.isRecycled); assertEquals(1, settled)
    }
}
