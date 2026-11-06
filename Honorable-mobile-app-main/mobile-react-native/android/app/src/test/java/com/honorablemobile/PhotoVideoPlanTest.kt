package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class PhotoVideoPlanTest {
    private val id="12345678-1234-1234-1234-123456789abc"
    private val photos=listOf("content://local/one","content://local/two")
    private fun plan(name:String="My story",default:Long=5000,selection:PhotoVideoSelection=PhotoVideoSelection.of(photos,mapOf(photos[1] to 1250))) =
        PhotoVideoPlan(id,name,PhotoVideoFormat.PORTRAIT,default,selection)
    private fun rejects(action:()->Unit) { assertTrue(runCatching(action).isFailure) }
    @Test fun exactOrderedPhotosTimingFormatAndUnicodeNameRoundTrip() {
        val original=plan("Story 🎬 café");val bytes=PhotoVideoPlanCodec.encode(original)
        val restored=PhotoVideoPlanCodec.decode(bytes,id)
        assertEquals(original.id,restored.id);assertEquals(original.name,restored.name)
        assertEquals(original.format,restored.format);assertEquals(5000L,restored.defaultMs)
        assertEquals(photos,restored.selection.items);assertEquals(mapOf(photos[1] to 1250L),restored.selection.holds)
        assertEquals(listOf(5000L,1250L),restored.selection.durations(restored.defaultMs))
        assertArrayEquals(bytes,PhotoVideoPlanCodec.encode(restored))
    }
    @Test fun allAllowedDefaultsAndFormatsRoundTrip() {
        for(default in PhotoVideoPlan.DEFAULT_HOLDS)for(format in PhotoVideoFormat.entries) {
            val restored=PhotoVideoPlanCodec.decode(PhotoVideoPlanCodec.encode(PhotoVideoPlan(id,"Story",format,default,PhotoVideoSelection.of(photos))),id)
            assertEquals(default,restored.defaultMs);assertEquals(format,restored.format)
        }
    }
    @Test fun namesDefaultsIdentityAndEmptyPlansAreStrict() {
        for(name in listOf("","  "," padded","padded ","a\nb","x".repeat(41),"\uD800"))rejects { plan(name) }
        for(default in listOf(0L,1000L,4999L,10001L))rejects { plan(default=default) }
        rejects { plan(selection=PhotoVideoSelection.of(emptyList())) }
        rejects { PhotoVideoPlan("../outside","Story",PhotoVideoFormat.SQUARE,3000,PhotoVideoSelection.of(photos)) }
    }
    @Test fun sourceModelKeepsBoundsDuplicatesAndHoldOwnershipStrict() {
        rejects { PhotoVideoSelection.of(listOf(photos[0],photos[0])) }
        rejects { PhotoVideoSelection.of(listOf("file:///private/photo")) }
        rejects { PhotoVideoSelection.of(photos,mapOf("content://other/source" to 3000L)) }
        rejects { PhotoVideoSelection.of(photos,mapOf(photos[0] to 999L)) }
        rejects { PhotoVideoSelection.of(List(21){"content://local/$it"}) }
        rejects { PhotoVideoSelection.of(listOf("content://local/"+"x".repeat(2048))) }
    }
    @Test fun largestSelectionAndHoldsRemainBoundedAndIndependent() {
        val mutable=MutableList(20){"content://local/$it"+"x".repeat(2000)}
        val selection=PhotoVideoSelection.of(mutable,mutable.associateWith{10000L});val bytes=PhotoVideoPlanCodec.encode(plan(selection=selection))
        mutable.clear();assertTrue(bytes.size<PhotoVideoPlanCodec.MAX_BYTES)
        val restored=PhotoVideoPlanCodec.decode(bytes,id);assertEquals(20,restored.selection.items.size)
        assertEquals(200000L,restored.selection.durations(3000).sum())
        rejects { (restored.selection.items as MutableList).clear() }
        rejects { (PhotoVideoPlan.DEFAULT_HOLDS as MutableList).clear() }
    }
    @Test fun corruptionTruncationWrongIdentityAndExtraDataNeverLoad() {
        val raw=PhotoVideoPlanCodec.encode(plan())
        for(position in listOf(0,10,raw.lastIndex)) { val bad=raw.clone();bad[position]=(bad[position].toInt() xor 1).toByte();rejects { PhotoVideoPlanCodec.decode(bad,id) } }
        for(length in listOf(0,20,raw.size-1))rejects { PhotoVideoPlanCodec.decode(raw.copyOf(length),id) }
        rejects { PhotoVideoPlanCodec.decode(raw,"12345678-1234-1234-1234-123456789abd") }
        rejects { PhotoVideoPlanCodec.decode(ByteArray(PhotoVideoPlanCodec.MAX_BYTES+1),id) }
        val body=raw.copyOf(raw.size-32)+byteArrayOf(0)
        rejects { PhotoVideoPlanCodec.decode(body+MessageDigest.getInstance("SHA-256").digest(body),id) }
    }
    @Test fun unknownVersionAndInvalidFormatAreRejectedEvenWithValidChecksum() {
        val raw=PhotoVideoPlanCodec.encode(plan())
        val version=raw.copyOf(raw.size-32);version[7]=2
        rejects { PhotoVideoPlanCodec.decode(version+MessageDigest.getInstance("SHA-256").digest(version),id) }
        val format=raw.copyOf(raw.size-32)
        // Header8 + ID UTF(2+36) + ASCII name UTF(2+8) => format starts56.
        format[59]=99
        rejects { PhotoVideoPlanCodec.decode(format+MessageDigest.getInstance("SHA-256").digest(format),id) }
    }
    @Test fun gateBlocksSameFrameWorkAndOldForegroundReviews() {
        val gate=PhotoVideoPlanGate();assertNull(gate.begin());assertNull(gate.review())
        gate.resume();val review=gate.review()!!;assertTrue(gate.canReview(review))
        val work=gate.begin()!!;assertTrue(gate.busy());assertNull(gate.begin());assertFalse(gate.canReview(review))
        gate.pause();assertFalse(gate.current(work));gate.resume();assertFalse(gate.canReview(review));assertNull(gate.begin())
        assertFalse(gate.finish(work));assertFalse(gate.busy());assertNotNull(gate.review())
    }
    @Test fun staleCompletionCannotReleaseNewWorkAndDestroyNeverResumes() {
        val gate=PhotoVideoPlanGate();gate.resume();val review=gate.review()!!;val first=gate.begin()!!;assertTrue(gate.finish(first));assertFalse(gate.canReview(review))
        val next=gate.begin()!!;assertFalse(gate.finish(first));assertTrue(gate.busy());assertTrue(gate.current(next))
        gate.destroy();assertFalse(gate.finish(next));gate.resume();assertNull(gate.begin());assertNull(gate.review())
    }

}
