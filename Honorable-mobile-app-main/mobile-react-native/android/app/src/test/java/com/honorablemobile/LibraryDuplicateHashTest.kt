package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LibraryDuplicateHashTest {
    private fun item(i:Int)=LibraryCollectionItem(i.toString(),"content://synthetic/$i",if(i%2==0)"VIDEO"else"IMAGE")
    private fun items(n:Int=2)=(1..n).map(::item)
    private fun source(text:String)=LibraryDuplicateHash.Source(ByteArrayInputStream(text.toByteArray()))
    private fun limits(file:Long=100,total:Long=1000,time:Long=1000)=LibraryDuplicateHash.Limits(file,total,time)
    @Test fun knownDigestGroupsOnlyCompleteEqualBytesAndLengths(){
        val report=LibraryDuplicateHash.inspect(items(3),{source(if(it.id=="3")"abcd"else"abc")})
        assertEquals(3,report.checked.size);assertEquals(10L,report.bytesRead)
        assertEquals(listOf("1","2"),report.groups.single().ids)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",report.groups.single().sha256)
        assertEquals(3L,report.groups.single().bytes)
    }
    @Test fun equalSizesWithDifferentBytesDoNotMatch(){
        val report=LibraryDuplicateHash.inspect(items(),{source(if(it.id=="1")"abc"else"abd")})
        assertEquals(2,report.checked.size);assertTrue(report.groups.isEmpty())
    }
    @Test fun emptyFilesAreByteMatchesWithoutClaimingMediaDecoding(){
        val report=LibraryDuplicateHash.inspect(items(),{source("")})
        assertEquals(0L,report.bytesRead);assertEquals(0L,report.groups.single().bytes)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",report.groups.single().sha256)
    }
    @Test fun unavailableOrDeniedFilesNeverBecomeEmptyFingerprints(){
        val report=LibraryDuplicateHash.inspect(items(3),{if(it.id=="1")throw IOException("private provider detail");if(it.id=="2")throw SecurityException("private path");source("abc")})
        assertEquals(listOf("3"),report.checked.map{it.id});assertEquals(2,report.skipped.size)
        assertTrue(report.skipped.all{it.reason==LibraryDuplicateHash.Reason.UNAVAILABLE});assertTrue(report.groups.isEmpty())
    }
    @Test fun aPartialReadFailureNeverMatchesACompleteFile(){
        var closed=false
        val report=LibraryDuplicateHash.inspect(items(3),{item->
            if(item.id!="1")source("abc")else LibraryDuplicateHash.Source(object:InputStream(){
                var read=false
                override fun read():Int=throw IOException()
                override fun read(bytes:ByteArray,offset:Int,length:Int):Int {if(read)throw IOException();read=true;bytes[offset]='a'.code.toByte();bytes[offset+1]='b'.code.toByte();return 2}
                override fun close(){closed=true}
            })
        })
        assertTrue(closed);assertEquals(8L,report.bytesRead);assertEquals(listOf("2","3"),report.groups.single().ids)
        assertEquals(listOf("1"),report.skipped.map{it.id})
    }
    @Test fun perFileReadLimitIsStrictAndIncompleteDigestsAreDiscarded(){
        val report=LibraryDuplicateHash.inspect(items(),{source(if(it.id=="1")"abcd"else"abc")},clock={0},limits=limits(file=4))
        assertEquals(7L,report.bytesRead);assertEquals(listOf("2"),report.checked.map{it.id})
        assertEquals(LibraryDuplicateHash.Reason.FILE_LIMIT,report.skipped.single().reason);assertTrue(report.groups.isEmpty())
    }
    @Test fun declaredOversizeClosesWithoutReadingTheFile(){
        var reads=0;var closes=0
        val report=LibraryDuplicateHash.inspect(items(),{LibraryDuplicateHash.Source(object:InputStream(){override fun read():Int{reads++;return 1};override fun close(){closes++}},4)},clock={0},limits=limits(file=4))
        assertEquals(0,reads);assertEquals(2,closes);assertEquals(0L,report.bytesRead);assertEquals(2,report.skipped.size)
    }
    @Test fun totalReadLimitStopsFurtherOpeningAndNeverGroupsPartialWork(){
        var opens=0
        val report=LibraryDuplicateHash.inspect(items(4),{opens++;source("abc")},clock={0},limits=limits(total=8))
        assertEquals(8L,report.bytesRead);assertEquals(3,opens)
        assertEquals(listOf("1","2"),report.groups.single().ids)
        assertEquals(listOf("3","4"),report.skipped.map{it.id});assertTrue(report.skipped.all{it.reason==LibraryDuplicateHash.Reason.TOTAL_LIMIT})
    }
    @Test fun mismatchedProviderLengthNeverProducesACheckedFingerprint(){
        val report=LibraryDuplicateHash.inspect(items(),{LibraryDuplicateHash.Source(ByteArrayInputStream("abc".toByteArray()),if(it.id=="1")4 else 3)})
        assertEquals(listOf("2"),report.checked.map{it.id});assertEquals(LibraryDuplicateHash.Reason.LENGTH_CHANGED,report.skipped.single().reason)
    }
    @Test fun failedCloseDiscardsTheFingerprintAndStillRunsCleanup(){
        var cleanup=0
        val report=LibraryDuplicateHash.inspect(items(),{LibraryDuplicateHash.Source(object:ByteArrayInputStream("abc".toByteArray()){override fun close(){throw IOException()}},cleanup={cleanup++})})
        assertEquals(2,cleanup);assertTrue(report.checked.isEmpty());assertEquals(2,report.skipped.size)
    }
    @Test fun aCloseFailureDoesNotDuplicateAnExistingLengthMismatchSkip(){
        val report=LibraryDuplicateHash.inspect(items(),{LibraryDuplicateHash.Source(object:ByteArrayInputStream("abc".toByteArray()){override fun close(){throw IOException()}},expectedLength=4)})
        assertEquals(2,report.skipped.size);assertEquals(2,report.skipped.map{it.id}.distinct().size)
        assertTrue(report.skipped.all{it.reason==LibraryDuplicateHash.Reason.LENGTH_CHANGED})
    }
    @Test fun repeatedZeroProgressCannotSpinOrHashAnEmptyFile(){
        var reads=0;var closes=0
        val report=LibraryDuplicateHash.inspect(items(),{LibraryDuplicateHash.Source(object:InputStream(){override fun read()=0;override fun read(buffer:ByteArray,off:Int,len:Int):Int{reads++;return 0};override fun close(){closes++}})})
        assertEquals(6,reads);assertEquals(2,closes);assertTrue(report.checked.isEmpty());assertEquals(2,report.skipped.size)
    }
    @Test fun cancellationClosesTheStreamAndDoesNotOpenRemainingSources(){
        var checks=0;var opens=0;var closed=false
        assertThrows(CancellationException::class.java){LibraryDuplicateHash.inspect(items(),{opens++;LibraryDuplicateHash.Source(object:ByteArrayInputStream("abc".toByteArray()){override fun close(){closed=true}})},requireCurrent={checks++;if(checks==4)throw CancellationException()},clock={0})}
        assertTrue(closed);assertEquals(1,opens)
    }
    @Test fun timeoutClosesTheStreamAndReturnsNoPartialReport(){
        var now=0L;var closed=false
        assertThrows(IllegalStateException::class.java){LibraryDuplicateHash.inspect(items(),{LibraryDuplicateHash.Source(object:ByteArrayInputStream("abc".toByteArray()){override fun close(){closed=true}})},clock={++now},limits=limits(time=4))}
        assertTrue(closed)
    }
    @Test fun invalidOrRepeatedSelectionsAreRefusedBeforeOpening(){
        var opens=0
        for(selection in listOf(emptyList(),items(1),items(21),listOf(item(1),item(1)),listOf(item(1),LibraryCollectionItem("2",item(1).uri,"IMAGE")))){
            assertThrows(IllegalArgumentException::class.java){LibraryDuplicateHash.inspect(selection,{opens++;source("abc")})}
        }
        assertEquals(0,opens)
    }
    @Test fun ownsTheSelectionSnapshotAndImmutableReports(){
        val chosen=items().toMutableList()
        val report=LibraryDuplicateHash.inspect(chosen,{chosen.clear();source("abc")})
        assertEquals(2,report.checked.size)
        assertThrows(UnsupportedOperationException::class.java){(report.checked as MutableList).clear()}
        assertThrows(UnsupportedOperationException::class.java){(report.skipped as MutableList).clear()}
        assertThrows(UnsupportedOperationException::class.java){(report.groups as MutableList).clear()}
        assertThrows(UnsupportedOperationException::class.java){(report.groups.single().ids as MutableList).clear()}
    }
    @Test fun limitOverridesCanOnlyReduceProductionBounds(){
        for(values in listOf(Triple(0L,1L,1L),Triple(1L,0L,1L),Triple(1L,1L,0L),Triple(LibraryDuplicateHash.FILE_LIMIT+1,1L,1L),Triple(1L,LibraryDuplicateHash.TOTAL_LIMIT+1,1L),Triple(1L,1L,LibraryDuplicateHash.TIMEOUT_NS+1))){
            assertThrows(IllegalArgumentException::class.java){LibraryDuplicateHash.Limits(values.first,values.second,values.third)}
        }
    }
    @Test fun largeSourcesAreStreamedInBoundedChunks(){
        val bytes=ByteArray(1_000_000){(it%251).toByte()};var maxRequested=0
        val report=LibraryDuplicateHash.inspect(items(),{LibraryDuplicateHash.Source(object:ByteArrayInputStream(bytes){override fun read(buffer:ByteArray,offset:Int,length:Int):Int{maxRequested=maxOf(maxRequested,length);return super.read(buffer,offset,length)}},bytes.size.toLong())})
        assertEquals(2_000_000L,report.bytesRead);assertEquals(64*1024,maxRequested)
        assertEquals(1,report.groups.size);assertEquals(64,report.groups.single().sha256.length)
    }
}
