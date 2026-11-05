package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoMomentNotesTest {
    @Test fun notesAreSortedImmutableAndVisibleInTheCurrentSourceRange() {
        val source=mutableListOf(VideoMomentNote(2500,"End"),VideoMomentNote(1000,"Start"))
        val notes=VideoMomentNotes(5000,source);source.clear()
        assertEquals(listOf(1000L,2500L),notes.notes.map{it.sourceMs})
        assertTrue(runCatching{(notes.notes as MutableList<*>).clear()}.isFailure)
        assertEquals(listOf(notes.notes[0]),notes.visible(1000,2500))
        assertTrue(notes.visible(3000,5000).isEmpty());assertEquals(2,notes.notes.size)
    }
    @Test fun addRenameDeleteRemainIndependentAndRejectStaleOrDuplicateOperations() {
        val first=VideoMomentNote(1000,"Idea");val initial=VideoMomentNotes(5000).add(first)
        val renamed=initial.rename(first,"Better idea")
        assertEquals("Idea",initial.notes.single().label);assertEquals("Better idea",renamed.notes.single().label)
        assertTrue(runCatching{renamed.remove(first)}.isFailure)
        assertTrue(runCatching{initial.add(first.copy(label="Duplicate time"))}.isFailure)
        assertTrue(renamed.remove(renamed.notes.single()).notes.isEmpty())
    }
    @Test fun sourceOwnerKeysArePrivateStableAndSeparated() {
        val uri="content://fixture/video"
        val key=VideoMomentCodec.storageKey("guest-basic",uri)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")));assertEquals(key,VideoMomentCodec.storageKey("guest-basic",uri))
        assertNotEquals(key,VideoMomentCodec.storageKey("account-"+"a".repeat(64),uri))
        assertNotEquals(key,VideoMomentCodec.storageKey("guest-basic",uri+"2"))
        assertTrue(runCatching{VideoMomentCodec.storageKey("../guest",uri)}.isFailure)
        assertTrue(runCatching{VideoMomentCodec.storageKey("guest-basic","https://remote.invalid/video")}.isFailure)
    }
    @Test fun codecPreservesMaximumCatalogAndRefusesDamageAndFutureVersions() {
        val notes=VideoMomentNotes(100000,(0 until 100).map{VideoMomentNote(it*1000L,"界".repeat(80))})
        val bytes=VideoMomentCodec.encode(notes);assertTrue(bytes.size<=32768);assertEquals(notes,VideoMomentCodec.decode(bytes))
        assertEquals(VideoMomentNotes(1),VideoMomentCodec.decode(VideoMomentCodec.encode(VideoMomentNotes(1))))
        for(bad in listOf(bytes.copyOf(bytes.size-1),bytes+byteArrayOf(0),bytes.copyOf().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()},ByteArray(32769)))assertTrue(runCatching{VideoMomentCodec.decode(bad)}.isFailure)
        val payload=bytes.copyOfRange(40,bytes.size).apply{this[0]=2}
        val output=java.io.ByteArrayOutputStream();java.io.DataOutputStream(output).use{it.writeInt(0x4e564d4e);it.writeInt(payload.size);it.write(java.security.MessageDigest.getInstance("SHA-256").digest(payload));it.write(payload)}
        assertTrue(runCatching{VideoMomentCodec.decode(output.toByteArray())}.isFailure)
    }
    @Test fun invalidLabelsTimesAndCatalogLimitsFailWithoutLosingNotes() {
        for(label in listOf(""," "," Leading","x".repeat(81),"bad\nlabel","\uD800"))assertTrue(runCatching{VideoMomentNote(0,label)}.isFailure)
        assertTrue(runCatching{VideoMomentNotes(1000,listOf(VideoMomentNote(1000,"Outside")))}.isFailure)
        assertTrue(runCatching{VideoMomentNote(-1,"Before")}.isFailure)
        val full=VideoMomentNotes(1000,(0 until 100).map{VideoMomentNote(it.toLong(),"Note")})
        assertTrue(runCatching{full.add(VideoMomentNote(100,"Extra"))}.isFailure);assertEquals(100,full.notes.size)
    }
    @Test fun noteJumpUsesExistingSourceSpeedMappingWithoutChangingTheProject() {
        val clips=listOf(VideoSegment(0,1000),VideoSegment(1000,4000,speed=1.5f))
        val note=VideoMomentNote(2000,"Review this moment")
        val output=VideoFilmstripScale.outputTime(clips,1,note.sourceMs)
        val position=VideoTimeline.locate(clips,output)
        assertEquals(1,position.clipIndex);assertTrue(kotlin.math.abs(position.sourceTimeMs-note.sourceMs)<=1)
        assertEquals(1000L,clips[1].startMs);assertEquals(4000L,clips[1].endMs);assertTrue(clips[1].captions.isEmpty())
    }
}
