package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class GuestVideoProjectTest {
    private val primary="content://test/original"
    private fun rich()=VideoDraft(5000,listOf(
        VideoSegment(0,5000,captions=listOf(VideoCaption(200,1200,"Hello")),captionSourceUri="content://test/subtitles"),
        VideoSegment(100,2100,sourceId="second")),0,200,0,5000,
        listOf(VideoSource("second","content://test/second",3000)),VideoSoundtrack("content://test/audio",6000))

    @Test fun guestEnvelopeRoundTripsFullProjectWithoutAccountStore() {
        val draft=rich();assertEquals(draft,GuestVideoProjectCodec.decode(GuestVideoProjectCodec.encode(primary,draft),primary,5000))
        assertTrue(GuestVideoProjectCodec.storageName(primary).matches(Regex("guest-video-project-[0-9a-f]{64}\\.bin")))
        assertFalse(GuestVideoProjectCodec.storageName(primary).contains("original"))
    }
    @Test fun differentRootCannotLoadAnotherGuestProjectOrUseAccountFilename() {
        val bytes=GuestVideoProjectCodec.encode(primary,rich())
        assertTrue(runCatching{GuestVideoProjectCodec.decode(bytes,"content://test/another",5000)}.isFailure)
        assertNotEquals(GuestVideoProjectCodec.storageName(primary),VideoDraftCodec.storageName("guest","local",primary))
        for(uri in listOf("file:///private", "content://test/\n", "content://"+"x".repeat(2048)))assertTrue(runCatching{GuestVideoProjectCodec.storageName(uri)}.isFailure)
    }
    @Test fun checksumFutureVersionAndChangedDurationFailClosed() {
        val bytes=GuestVideoProjectCodec.encode(primary,rich())
        assertTrue(runCatching{GuestVideoProjectCodec.decode(bytes,primary,5001)}.isFailure)
        val damaged=bytes.copyOf();damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
        assertTrue(runCatching{GuestVideoProjectCodec.decode(damaged,primary,5000)}.isFailure)
        val future=bytes.copyOf();future[7]=2
        assertTrue(runCatching{GuestVideoProjectCodec.decode(future,primary,5000)}.isFailure)
    }
    @Test fun truncationTrailingDataAndOversizedInputAreRejected() {
        val bytes=GuestVideoProjectCodec.encode(primary,rich())
        for(input in listOf(bytes.copyOf(70),bytes.copyOf(bytes.size-1),bytes+byteArrayOf(1),ByteArray(GuestVideoProjectCodec.MAX_BYTES+1)))assertTrue(runCatching{GuestVideoProjectCodec.decode(input,primary,5000)}.isFailure)
    }
    @Test fun restoredProjectIsOneUndoableEditAndKeepsProtectedOriginals() {
        val current=VideoEditProject(5000)
        current.setCaptions(0,listOf(VideoCaption(0,500,"Old")),"content://test/old-subtitles")
        val before=current.segments;val saved=rich()
        current.applyDraft(saved.segments,saved.sources.associate{it.id to it.durationMs},saved.soundtrack)
        assertEquals(saved.segments,current.segments);assertEquals(saved.soundtrack,current.soundtrack)
        assertTrue(current.captionSourceUris().containsAll(listOf("content://test/old-subtitles","content://test/subtitles")))
        assertTrue(current.undo());assertEquals(before,current.segments);assertNull(current.soundtrack)
        assertTrue(current.redo());assertEquals(saved.segments,current.segments)
    }
    @Test fun validationChecksPrimaryAdditionalAudioAndSubtitleSources() {
        val draft=rich();val videos=mutableSetOf<String>();val audio=mutableSetOf<String>();val subtitles=mutableSetOf<Pair<String,Long>>()
        GuestVideoProjectMedia.validate(primary,draft,{true},{videos.add(it);if(it==primary)5000 else 3000},{audio.add(it);6000}){uri,duration->subtitles.add(uri to duration)}
        assertEquals(setOf(primary,"content://test/second"),videos);assertEquals(setOf("content://test/audio"),audio)
        assertEquals(setOf("content://test/subtitles" to 5000L),subtitles)
    }
    @Test fun missingDurableGrantRejectsBeforeAnyMediaRead() {
        assertTrue(runCatching{GuestVideoProjectMedia.validate(primary,rich(),{it!="content://test/subtitles"},{error("Must not read")},{error("Must not read")}){_,_->error("Must not read")}}.isFailure)
    }
    @Test fun changedOrUnavailableMediaDoesNotApplySavedEdits() {
        val current=VideoEditProject(5000);val before=current.segments;val saved=rich()
        val result=runCatching{
            GuestVideoProjectMedia.validate(primary,saved,{true},{if(it==primary)5000 else 2999},{6000}){_,_->}
            current.applyDraft(saved.segments,saved.sources.associate{it.id to it.durationMs},saved.soundtrack)
        }
        assertTrue(result.isFailure);assertEquals(before,current.segments);assertFalse(current.canUndo)
    }
    @Test fun failedSaveRollsBackOnlyNewlyAcquiredGrants() {
        val owned=mutableSetOf("content://old");val released=mutableSetOf<String>()
        assertTrue(runCatching{GuestVideoProjectMedia.withDurableAccess(linkedSetOf("content://old","content://new"),owned::contains,{owned.add(it)},{released.add(it);owned.remove(it)},{}){error("Storage full")}}.isFailure)
        assertEquals(setOf("content://old"),owned);assertEquals(setOf("content://new"),released)
    }
    @Test fun successfulSaveKeepsGrantsAndReusesExistingOnes() {
        val owned=mutableSetOf("content://old");val taken=mutableListOf<String>()
        assertEquals("saved",GuestVideoProjectMedia.withDurableAccess(linkedSetOf("content://old","content://new"),owned::contains,{taken.add(it);owned.add(it)},{error("Must keep successful access")},{}){assertEquals(2,owned.size);"saved"})
        assertEquals(listOf("content://new"),taken)
    }
    @Test fun staleSessionNeverAcquiresOrCommitsProjectAccess() {
        var applied=false
        assertTrue(runCatching{GuestVideoProjectMedia.withDurableAccess(setOf(primary),{false},{error("Must not acquire")},{},{error("Editor destroyed")}){applied=true}}.isFailure)
        assertFalse(applied)
    }
    @Test fun providerWithoutDurableAccessDoesNotSaveAndReleasesAttempt() {
        var saved=false;var releases=0
        assertTrue(runCatching{GuestVideoProjectMedia.withDurableAccess(setOf(primary),{false},{},{releases++},{}){saved=true}}.isFailure)
        assertFalse(saved);assertEquals(1,releases)
    }

    @Test fun overlappingSavesCannotRevokeAnotherSuccessfulSaveGrant() {
        val owned=java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val inside=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val secondStarted=java.util.concurrent.CountDownLatch(1)
        val secondRead=java.util.concurrent.CountDownLatch(1)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val first=pool.submit<Boolean>{runCatching{GuestVideoProjectMedia.withDurableAccess(setOf(primary),owned::contains,{owned.add(it)},{owned.remove(it)},{}){inside.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS));error("Failed save")}}.isFailure}
            assertTrue(inside.await(5,java.util.concurrent.TimeUnit.SECONDS))
            val second=pool.submit<Boolean>{secondStarted.countDown();GuestVideoProjectMedia.withDurableAccess(setOf(primary),{secondRead.countDown();owned.contains(it)},{owned.add(it)},{owned.remove(it)},{}){true}}
            assertTrue(secondStarted.await(5,java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(secondRead.await(100,java.util.concurrent.TimeUnit.MILLISECONDS))
            release.countDown()
            assertTrue(first.get(5,java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(second.get(5,java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(owned.contains(primary))
        }finally{release.countDown();pool.shutdownNow()}
    }
}
