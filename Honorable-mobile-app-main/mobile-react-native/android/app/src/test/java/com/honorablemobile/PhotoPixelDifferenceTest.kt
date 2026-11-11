package com.honorablemobile

import java.util.concurrent.CancellationException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PhotoPixelDifferenceTest {
    private val black=0xff000000.toInt()
    private fun compare(left:IntArray,right:IntArray,threshold:Int=0)=PhotoPixelDifference.compare(PhotoPixelDifference.Size(left.size,1),left,right,threshold)
    @Test fun identicalPixelsProduceAnOpaqueBlackMap(){
        val input=intArrayOf(0xff123456.toInt(),0x80402010.toInt(),0)
        val result=compare(input,input.copyOf())
        assertArrayEquals(IntArray(3){black},result.pixels);assertEquals(0,result.changedPixels);assertEquals(0,result.maximumDelta);assertEquals(0.0,result.meanDelta,0.0)
    }
    @Test fun RGBAndAlphaDifferencesMarkIndependentCells(){
        val result=compare(intArrayOf(black,black,black,black),intArrayOf(0xff010000.toInt(),0xff000200.toInt(),0xff000003.toInt(),0xfe000000.toInt()))
        assertArrayEquals(IntArray(4){-1},result.pixels);assertEquals(4,result.changedPixels);assertEquals(3,result.maximumDelta);assertEquals(1.75,result.meanDelta,0.0)
    }
    @Test fun thresholdIsExclusiveAndStatisticsRetainRawDeltas(){
        val result=compare(intArrayOf(black,black),intArrayOf(0xff030000.toInt(),0xff040000.toInt()),3)
        assertArrayEquals(intArrayOf(black,-1),result.pixels);assertEquals(1,result.changedPixels);assertEquals(4,result.maximumDelta);assertEquals(3.5,result.meanDelta,0.0)
        assertEquals(0,compare(intArrayOf(black),intArrayOf(-1),255).changedPixels)
    }
    @Test fun invisibleRGBIsIgnoredButTransparencyChangesRemainVisible(){
        assertEquals(0,compare(intArrayOf(0x00112233),intArrayOf(0x00abcdef)).changedPixels)
        assertEquals(255,compare(intArrayOf(0),intArrayOf(black)).maximumDelta)
        assertEquals(1,compare(intArrayOf(0x80ff0000.toInt()),intArrayOf(0x80fe0000.toInt())).maximumDelta)
    }
    @Test fun comparisonIsSymmetricAndNeverChangesSourceArrays(){
        val a=intArrayOf(0xff123456.toInt(),0x81234567.toInt());val b=intArrayOf(-1,0x00112233)
        val beforeA=a.copyOf();val beforeB=b.copyOf();val forward=compare(a,b);val reverse=compare(b,a)
        assertArrayEquals(forward.pixels,reverse.pixels);assertEquals(forward.meanDelta,reverse.meanDelta,0.0)
        assertArrayEquals(beforeA,a);assertArrayEquals(beforeB,b)
    }
    @Test fun rejectsInvalidThresholdArrayAndGridBoundsBeforeComparison(){
        for(t in listOf(-1,256))assertThrows(IllegalArgumentException::class.java){compare(intArrayOf(black),intArrayOf(black),t)}
        assertThrows(IllegalArgumentException::class.java){PhotoPixelDifference.compare(PhotoPixelDifference.Size(2,1),intArrayOf(black),intArrayOf(black,black),0)}
        for(s in listOf(0 to 1,1 to 0,1025 to 1,1 to 1025))assertThrows(IllegalArgumentException::class.java){PhotoPixelDifference.Size(s.first,s.second)}
    }
    @Test fun planCorrectsOrientationAndUsesACommonBoundedPowerOfTwoGrid(){
        val a=PhotoPixelDifference.Original(4033,3025,1);val b=PhotoPixelDifference.Original(3025,4033,6)
        val plan=PhotoPixelDifference.plan(a,b)
        assertEquals(4,plan.sample);assertEquals(PhotoPixelDifference.Size(1009,757),plan.size);assertTrue(plan.sampled)
        assertEquals(4033,plan.sourceWidth);assertEquals(3025,plan.sourceHeight)
        val tiny=PhotoPixelDifference.plan(PhotoPixelDifference.Original(3,2,1),PhotoPixelDifference.Original(3,2,1))
        assertEquals(1,tiny.sample);assertFalse(tiny.sampled)
    }
    @Test fun mismatchedOrInvalidOriginalDimensionsDoNotSilentlyAlignOrCrop(){
        assertThrows(IllegalArgumentException::class.java){PhotoPixelDifference.plan(PhotoPixelDifference.Original(100,50,1),PhotoPixelDifference.Original(50,100,1))}
        for(o in listOf(0,9))assertThrows(IllegalArgumentException::class.java){PhotoPixelDifference.Original(1,1,o)}
        assertThrows(IllegalArgumentException::class.java){PhotoPixelDifference.Original(32769,1,1)}
        assertThrows(IllegalArgumentException::class.java){PhotoPixelDifference.Original(20000,20000,1)}
    }
    @Test fun allEightExifOrientationsMatchIndependentTinyReferenceMatrices(){
        val raw=intArrayOf(1,2,3,4,5,6)
        val expected=listOf(intArrayOf(1,2,3,4,5,6),intArrayOf(2,1,4,3,6,5),intArrayOf(6,5,4,3,2,1),intArrayOf(5,6,3,4,1,2),intArrayOf(1,3,5,2,4,6),intArrayOf(5,3,1,6,4,2),intArrayOf(6,4,2,5,3,1),intArrayOf(2,4,6,1,3,5))
        for(o in 1..8){val target=if(o>=5)PhotoPixelDifference.Size(3,2)else PhotoPixelDifference.Size(2,3);assertArrayEquals("orientation $o",expected[o-1],PhotoPixelDifference.normalize(raw,2,3,o,target))}
        assertArrayEquals(intArrayOf(1,2,3,4,5,6),raw)
    }
    @Test fun nearestNeighborNormalizationHandlesDecoderRoundingWithoutHiddenPadding(){
        val raw=intArrayOf(1,2,3,4)
        assertArrayEquals(intArrayOf(1,1,2,1,1,2,3,3,4),PhotoPixelDifference.normalize(raw,2,2,1,PhotoPixelDifference.Size(3,3)))
        assertArrayEquals(intArrayOf(3),PhotoPixelDifference.normalize(raw,2,2,6,PhotoPixelDifference.Size(1,1)))
        assertThrows(IllegalArgumentException::class.java){PhotoPixelDifference.normalize(raw,3,2,1,PhotoPixelDifference.Size(1,1))}
    }
    @Test fun cancellationStopsBothNormalizationAndComparison(){
        assertThrows(CancellationException::class.java){PhotoPixelDifference.normalize(IntArray(64*64),64,64,1,PhotoPixelDifference.Size(64,64)){throw CancellationException()}}
        assertThrows(CancellationException::class.java){PhotoPixelDifference.compare(PhotoPixelDifference.Size(128,128),IntArray(128*128),IntArray(128*128),0){throw CancellationException()}}
        var calls=0
        assertThrows(CancellationException::class.java){PhotoPixelDifference.compare(PhotoPixelDifference.Size(128,128),IntArray(128*128),IntArray(128*128),0){calls++;if(calls==2)throw CancellationException()}}
    }
    @Test fun maximumGridCompletesWithBoundedIntegerStatistics(){
        val size=PhotoPixelDifference.Size(1024,1024);val n=1024*1024
        val result=PhotoPixelDifference.compare(size,IntArray(n){black},IntArray(n){-1},0)
        assertEquals(n,result.changedPixels);assertEquals(255,result.maximumDelta);assertEquals(255.0,result.meanDelta,0.0);assertEquals(n,result.pixels.size)
    }
    @Test fun randomizedPixelsAgreeWithIndependentPremultipliedReference(){
        val random=Random(116)
        repeat(40){
            val a=IntArray(128){random.nextInt()};val b=IntArray(128){random.nextInt()};val threshold=random.nextInt(256)
            val expected=a.indices.map{i->
                val ac=a[i].ushr(24);val bc=b[i].ushr(24)
                val channels=listOf(ac-bc,((a[i].ushr(16) and 255)*ac+127)/255-((b[i].ushr(16) and 255)*bc+127)/255,((a[i].ushr(8) and 255)*ac+127)/255-((b[i].ushr(8) and 255)*bc+127)/255,((a[i] and 255)*ac+127)/255-((b[i] and 255)*bc+127)/255)
                channels.maxOf{kotlin.math.abs(it)}
            }
            val result=compare(a,b,threshold)
            assertEquals(expected.count{it>threshold},result.changedPixels);assertEquals(expected.max(),result.maximumDelta);assertEquals(expected.sum().toDouble()/128,result.meanDelta,0.0)
            assertArrayEquals(expected.map{if(it>threshold)-1 else black}.toIntArray(),result.pixels)
        }
    }
    private data class Preview(val token:String)
    @Test fun stalePreviewTokensCannotConsumeOrDisposeASuccessor(){
        val disposed=mutableListOf<String>();val lease=PhotoDifferenceLease<Preview>({it.token},{disposed.add(it.token)})
        val old=Preview("old");val next=Preview("next");lease.offer(old);lease.offer(next)
        assertEquals(listOf("old"),disposed);assertNull(lease.take("old"));lease.release("old");assertSame(next,lease.peek("next"));assertEquals(listOf("old"),disposed)
    }
    @Test fun transferredPreviewIsSingleUseAndLaterCloseDoesNotDeleteIt(){
        val disposed=mutableListOf<String>();val lease=PhotoDifferenceLease<Preview>({it.token},{disposed.add(it.token)});val owned=Preview("one")
        lease.offer(owned);assertSame(owned,lease.take("one"));assertNull(lease.take("one"));lease.release("one");lease.clear();assertTrue(disposed.isEmpty())
    }
    @Test fun cancellationClearsOnlyTheCurrentlyOwnedPreviewOnce(){
        val disposed=mutableListOf<String>();val lease=PhotoDifferenceLease<Preview>({it.token},{disposed.add(it.token)});val owned=Preview("one")
        lease.offer(owned);lease.offer(owned);assertTrue(disposed.isEmpty());lease.clear();lease.clear();lease.release("one");assertEquals(listOf("one"),disposed)
    }
}

class PhotoDifferenceReadBudgetTest {
    @Test fun blockAndSingleByteReadsShareTheActualByteCounter(){
        val budget=PhotoDifferenceReadBudget(16,32);val stream=budget.wrap(ByteArrayInputStream(byteArrayOf(-1,0,1,2,3)))
        assertEquals(255,stream.read());assertEquals(0,stream.read())
        val buffer=ByteArray(8);assertEquals(3,stream.read(buffer));assertEquals(-1,stream.read());stream.finish();assertEquals(5L,budget.bytesRead)
    }
    @Test fun decoderReadRequestsAreClampedTo64KiB(){
        var largest=0;val source=object:ByteArrayInputStream(ByteArray(200000)){override fun read(bytes:ByteArray,offset:Int,length:Int):Int{largest=maxOf(largest,length);return super.read(bytes,offset,length)}}
        val budget=PhotoDifferenceReadBudget();val stream=budget.wrap(source);val buffer=ByteArray(500000)
        while(stream.read(buffer)>=0){};stream.finish();assertEquals(65536,largest);assertEquals(200000L,budget.bytesRead)
    }
    @Test fun skipCannotBypassPerStreamLimitsAndSwallowedFailureRemainsFatal(){
        val budget=PhotoDifferenceReadBudget(4,100);val source=ByteArrayInputStream(ByteArray(100));val stream=budget.wrap(source)
        assertThrows(IllegalStateException::class.java){stream.skip(10)}
        assertEquals(4L,budget.bytesRead);assertEquals(96,source.available())
        assertThrows(IllegalStateException::class.java){stream.finish()};assertThrows(IllegalStateException::class.java){budget.wrap(ByteArrayInputStream(byteArrayOf()))}
    }
    @Test fun exactLimitIsRefusedEvenIfDecoderReturnsBeforeAnotherRead(){
        val budget=PhotoDifferenceReadBudget(4,100);val stream=budget.wrap(ByteArrayInputStream(ByteArray(4)))
        assertEquals(4,stream.read(ByteArray(10)));assertThrows(IllegalStateException::class.java){stream.finish()}
    }
    @Test fun totalLimitIsSharedAcrossFreshHeaderExifAndDecodeStreams(){
        val budget=PhotoDifferenceReadBudget(10,8)
        repeat(2){val stream=budget.wrap(ByteArrayInputStream(ByteArray(3)));assertEquals(3,stream.read(ByteArray(20)));stream.finish()}
        val stream=budget.wrap(ByteArrayInputStream(ByteArray(10)));assertEquals(2,stream.read(ByteArray(20)))
        assertEquals(8L,budget.bytesRead);assertThrows(IllegalStateException::class.java){stream.finish()}
    }
    @Test fun swallowedProviderErrorsCannotYieldACompletedDecode(){
        val budget=PhotoDifferenceReadBudget();val stream=budget.wrap(object:InputStream(){override fun read():Int=throw IOException("synthetic provider")})
        assertThrows(IOException::class.java){stream.read()};assertThrows(IllegalStateException::class.java){stream.finish()}
        assertThrows(IllegalStateException::class.java){budget.wrap(ByteArrayInputStream(byteArrayOf()))}
    }
    @Test fun repeatedZeroReadsAndUnsupportedRewindCannotSilentlyContinue(){
        val budget=PhotoDifferenceReadBudget();var calls=0;val stream=budget.wrap(object:InputStream(){override fun read()=0;override fun read(bytes:ByteArray,offset:Int,length:Int):Int{calls++;return 0}})
        assertEquals(0,stream.read(ByteArray(4)));assertEquals(0,stream.read(ByteArray(4)));assertThrows(IllegalStateException::class.java){stream.read(ByteArray(4))};assertEquals(3,calls)
        val other=PhotoDifferenceReadBudget().wrap(ByteArrayInputStream(byteArrayOf(1)));assertFalse(other.markSupported());assertThrows(IOException::class.java){other.reset()};assertThrows(IllegalStateException::class.java){other.finish()}
    }
    @Test fun cancellationIsCheckedBeforeAndAfterProviderCalls(){
        var checks=0;var read=0;val budget=PhotoDifferenceReadBudget(current={checks++;if(checks>=3)throw CancellationException()})
        val stream=budget.wrap(object:ByteArrayInputStream(byteArrayOf(1,2,3)){override fun read(bytes:ByteArray,off:Int,length:Int):Int{read++;return super.read(bytes,off,length)}})
        assertThrows(CancellationException::class.java){stream.read(ByteArray(10))};assertEquals(1,read);assertEquals(3L,budget.bytesRead)
    }
    @Test fun availabilityIsConservativeAndBudgetsCannotExceedProductionLimits(){
        val budget=PhotoDifferenceReadBudget(4,100);val stream=budget.wrap(ByteArrayInputStream(ByteArray(20)))
        assertEquals(4,stream.available());assertEquals(2,stream.read(ByteArray(2)));assertEquals(2,stream.available())
        assertThrows(IllegalArgumentException::class.java){PhotoDifferenceReadBudget(0,1)}
        assertThrows(IllegalArgumentException::class.java){PhotoDifferenceReadBudget(64L*1024*1024+1,1)}
        assertThrows(IllegalArgumentException::class.java){PhotoDifferenceReadBudget(1,256L*1024*1024+1)}
    }
}
