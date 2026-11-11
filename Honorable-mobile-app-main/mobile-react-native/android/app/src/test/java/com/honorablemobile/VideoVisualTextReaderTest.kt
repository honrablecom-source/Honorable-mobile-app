package com.honorablemobile

import android.graphics.Bitmap
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoVisualTextReaderTest {
    private fun bitmap()=Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888)
    @Test fun actualCropPixelsAreIndependentAndOriginalSurvivesSdkRecycle(){
        val frame=Bitmap.createBitmap(87,53,Bitmap.Config.ARGB_8888)
        for(y in 0 until 53)for(x in 0 until 87)frame.setPixel(x,y,0xff000000.toInt() or (x shl 16) or (y shl 8) or (x xor y))
        val cropped=VideoVisualTextReader.cropCopy(frame,VideoVisualText.Crop(60,100))
        try{
            assertNotSame(frame,cropped);assertEquals(87,cropped.width);assertEquals(22,cropped.height)
            for(y in 0 until 22)for(x in 0 until 87)assertEquals(frame.getPixel(x,y+31),cropped.getPixel(x,y))
            cropped.recycle();assertFalse(frame.isRecycled);assertEquals(0xff000000.toInt(),frame.getPixel(0,0))
        }finally{cropped.recycle();frame.recycle()}
    }
    @Test fun largeCropBoundsPreserveAspectAndNeverRecycleTheSource(){
        val frame=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)
        val result=VideoVisualTextReader.cropCopy(frame,VideoVisualText.Crop(0,100))
        try{assertEquals(1280,result.width);assertEquals(720,result.height);assertFalse(frame.isRecycled)}finally{result.recycle();frame.recycle()}
    }
    @Test fun successfulActualGoogleTaskCopiesResultAndRecyclesOwnedBitmap(){
        val bitmap=bitmap();assertEquals("Actual task",VideoVisualTextReader.recognizeOwned(bitmap,{Tasks.forResult("Actual task")},{it},{ }))
        assertTrue(bitmap.isRecycled)
    }
    @Test fun failedAndPrecancelledRequestsDoNotLeakOwnedBitmap(){
        val bitmap=bitmap()
        try{VideoVisualTextReader.recognizeOwned(bitmap,{Tasks.forException<String>(IllegalArgumentException("Synthetic failure"))},{it},{ });fail()}catch(_:IllegalStateException){}
        assertTrue(bitmap.isRecycled)
        val second=bitmap();var called=false
        try{VideoVisualTextReader.recognizeOwned(second,{called=true;Tasks.forResult("Wrong")},{it},{throw CancellationException()});fail()}catch(_:CancellationException){}
        assertFalse(called);assertTrue(second.isRecycled)
    }
    @Test fun stopRetainsSdkBitmapAndWorkerUntilRealTaskSettles(){
        val bitmap=bitmap();val completion=TaskCompletionSource<String>();val launched=CountDownLatch(1);val stopped=AtomicBoolean(false);val executor=Executors.newSingleThreadExecutor()
        try{
            val future=executor.submit<Boolean>{try{VideoVisualTextReader.recognizeOwned(bitmap,{launched.countDown();completion.task},{it},{if(stopped.get())throw CancellationException()});false}catch(_:CancellationException){true}}
            assertTrue(launched.await(5,TimeUnit.SECONDS));stopped.set(true)
            assertFalse(bitmap.isRecycled);assertFalse(future.isDone)
            completion.setResult("Late text must not publish")
            assertTrue(future.get(5,TimeUnit.SECONDS));assertTrue(bitmap.isRecycled)
        }finally{if(!completion.task.isComplete)completion.setException(IllegalStateException("Fixture cleanup"));executor.shutdownNow()}
    }
    @Test fun timeoutAlsoWaitsForSdkSettlementBeforeBitmapCleanup(){
        val bitmap=bitmap();val completion=TaskCompletionSource<String>();val observedTimeout=CountDownLatch(1);val executor=Executors.newSingleThreadExecutor()
        try{
            var checks=0
            val future=executor.submit<Boolean>{try{VideoVisualTextReader.recognizeOwned(bitmap,{completion.task},{it},{if(++checks>=3)observedTimeout.countDown()},1);false}catch(_:IllegalStateException){true}}
            assertTrue(observedTimeout.await(5,TimeUnit.SECONDS));assertFalse(bitmap.isRecycled);assertFalse(future.isDone)
            completion.setResult("Late")
            assertTrue(future.get(5,TimeUnit.SECONDS));assertTrue(bitmap.isRecycled)
        }finally{if(!completion.task.isComplete)completion.setException(IllegalStateException("Fixture cleanup"));executor.shutdownNow()}
    }
}
