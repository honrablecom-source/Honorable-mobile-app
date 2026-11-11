package com.honorablemobile

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import app.honorable.editor.PhotoPresetNamespace
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class VideoProjectVersionsTest {
    private val scope=VideoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/primary")
    private fun draft():VideoDraft=VideoDraft(8000,listOf(
        VideoSegment(0,8000,speed=2f,volume=.5f,title="Keep text",grade=VideoGrade(exposure=.3f),layout=VideoLayout(rotation=90),titleStyle=VideoTitleStyle(0),captions=listOf(VideoCaption(0,1000,"Words")),captionSourceUri="content://synthetic/subtitles"),
        VideoSegment(0,4000,sourceId="extra",title="Second")),1,1000,500,3500,
        listOf(VideoSource("extra","content://synthetic/extra",4000)),VideoSoundtrack("content://synthetic/music",8000,loop=false,startMs=1000,endMs=7000))
    private fun reject(block:()->Unit){try{block();fail("Expected refusal")}catch(_:Exception){}}
    @Test fun catalogRoundTripsWholeEditableDraftAndScopeWithoutMediaBytes() {
        val saved=VideoProjectVersion.create("First 😀",draft());val catalog=VideoProjectVersions(8000,listOf(saved))
        val decoded=VideoProjectVersionCodec.decode(scope,8000,VideoProjectVersionCodec.encode(scope,catalog))
        assertEquals(catalog,decoded);assertEquals(draft(),decoded.entries.single().draft())
        assertEquals(VideoProjectVersions(8000),VideoProjectVersionCodec.decode(scope,8000,VideoProjectVersionCodec.encode(scope,VideoProjectVersions(8000))))
    }
    @Test fun savedEntriesAndFrozenDraftsDetachMutableInputsAndEncodedOutputs() {
        val cues=mutableListOf(VideoCaption(0,1000,"Keep"));val clips=mutableListOf(VideoSegment(0,8000,captions=cues))
        val original=VideoDraft(8000,clips,0,0,0,8000);val frozen=VideoProjectVersion.snapshot(original)
        val saved=VideoProjectVersion.create("Frozen",frozen);val expected=saved.draft()
        cues.clear();clips.clear();saved.encoded().fill(0)
        assertEquals(expected,saved.draft());assertEquals(expected,frozen)
        val entries=mutableListOf(saved);val catalog=VideoProjectVersions(8000,entries);entries.clear();assertEquals(listOf(saved),catalog.entries)
        try{(catalog.entries as MutableList).clear();fail("Mutable catalog")}catch(_:UnsupportedOperationException){}
        try{(frozen.segments[0].captions as MutableList).clear();fail("Mutable frozen cues")}catch(_:UnsupportedOperationException){}
    }
    @Test fun namedSnapshotKeepsOnlyOriginalsUsedByItsTimelineWithoutChangingLiveUndoCatalog() {
        val value=draft();val unused=VideoSource("unused","content://synthetic/unused",4000)
        val live=value.copy(sources=value.sources+unused)
        assertEquals(value,VideoProjectVersion.snapshot(live))
        assertEquals(value,VideoProjectVersion.create("Used originals",live).draft())
        assertEquals(2,live.sources.size);assertEquals(unused,live.sources.last())
    }
    @Test fun namesIdentityUniquenessAndDurationAreStrict() {
        VideoVersionName.requireValid("😀".repeat(40))
        for(name in listOf(""," a","a\n","x".repeat(41),"\uD800"))reject{VideoVersionName.requireValid(name)}
        val a=VideoProjectVersion.create("Title",draft())
        reject{VideoProjectVersions(8000,listOf(a,VideoProjectVersion.create("ＴＩＴＬＥ",draft())))}
        reject{VideoProjectVersions(8000,listOf(a,VideoProjectVersion.create("Other",draft(),a.id)))}
        reject{VideoProjectVersion.create("ID",draft(),"bad")}
        reject{VideoProjectVersions(8001,listOf(a))}
        reject{VideoProjectVersions(8000,listOf(a)).remove(VideoProjectVersion.create("Changed",draft(),a.id))}
    }
    @Test fun capacityBoundsEntriesAndCombinedDraftBytes() {
        val small=VideoDraft(8000,listOf(VideoSegment(0,8000)),0,0,0,8000)
        val twenty=List(20){VideoProjectVersion.create("Version $it",small)}
        assertEquals(20,VideoProjectVersions(8000,twenty).entries.size)
        reject{VideoProjectVersions(8000,twenty+VideoProjectVersion.create("Extra",small))}
        val large=VideoDraft(8000,List(600){VideoSegment(0,8000,title="x".repeat(120))},0,0,0,8000)
        val first=VideoProjectVersion.create("Large",large)
        assertTrue(first.byteCount*20L>512_000)
        reject{VideoProjectVersions(8000,List(20){VideoProjectVersion.decode(UUID.randomUUID().toString(),"Large $it",8000,first.encoded())})}
    }
    private fun body(bytes:ByteArray,change:(ByteArray)->ByteArray):ByteArray {
        val payload=change(bytes.copyOfRange(40,bytes.size))
        return ByteBuffer.allocate(40+payload.size).putInt(0x4e565653).putInt(payload.size).put(MessageDigest.getInstance("SHA-256").digest(payload)).put(payload).array()
    }
    @Test fun corruptedFutureMalformedTrailingAndWrongScopePayloadsFailClosed() {
        val bytes=VideoProjectVersionCodec.encode(scope,VideoProjectVersions(8000,listOf(VideoProjectVersion.create("V",draft()))))
        for(index in bytes.indices)reject{VideoProjectVersionCodec.decode(scope,8000,bytes.copyOf().also{it[index]=(it[index].toInt() xor 128).toByte()})}
        for(length in 0 until bytes.size)reject{VideoProjectVersionCodec.decode(scope,8000,bytes.copyOf(length))}
        reject{VideoProjectVersionCodec.decode(scope,8000,body(bytes){it.also{data->data[0]=2}})}
        reject{VideoProjectVersionCodec.decode(scope,8000,body(bytes){it.also{data->data[41]=21}})}
        reject{VideoProjectVersionCodec.decode(scope,8000,body(bytes){it+0.toByte()})}
        reject{VideoProjectVersionCodec.decode(scope,8000,body(bytes){it.also{data->data[60]=0xff.toByte()}})}
        reject{VideoProjectVersionCodec.decode(scope,8000,body(bytes){it.also{data->ByteBuffer.wrap(data).putInt(61,Int.MAX_VALUE)}})}
        reject{VideoProjectVersionCodec.decode(VideoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/other"),8000,bytes)}
        reject{VideoProjectVersionCodec.decode(VideoVersionScope(PhotoPresetNamespace.account("other","https://example.invalid"),"content://synthetic/primary"),8000,bytes)}
        reject{VideoProjectVersionCodec.decode(scope,8001,bytes)}
    }
    @Test fun openingVersionIsOneRealProjectUndoAndPreservesSavedSnapshot() {
        val saved=VideoProjectVersion.create("Saved",draft());val value=saved.draft()
        val project=VideoEditProject(8000);project.split(0,4000);val before=project.segments.toList()
        project.applyDraft(value.segments,value.sources.associate{it.id to it.durationMs},value.soundtrack)
        assertEquals(value.segments,project.segments);assertEquals(value.soundtrack,project.soundtrack)
        assertTrue(project.undo());assertEquals(before,project.segments);assertNull(project.soundtrack)
        assertTrue(project.redo());assertEquals(value.segments,project.segments);assertEquals(draft(),saved.draft())
    }
    @Test fun existingMediaAccessNeverTreatsAudioImagesOrArbitraryProvidersAsVideo() {
        for(uri in listOf("content://media/external/video/media/12","content://media/external_primary/video/media/42?requireOriginal=1")) {
            assertTrue(VideoVersionReadPolicy.existingAccess(uri,false,true));assertFalse(VideoVersionReadPolicy.existingAccess(uri,false,false))
        }
        for(uri in listOf("content://media/external/images/media/1","content://media/external/audio/media/1","content://media/external/video/media/0","content://media/external/video/media/x","content://media/external/video/media/1/extra","content://another/external/video/media/1","file://media/external/video/media/1"))assertFalse(VideoVersionReadPolicy.existingAccess(uri,false,true))
        assertTrue(VideoVersionReadPolicy.existingAccess("content://documents/original",true,false))
    }
    @Test fun reopenedSourceAliasesReuseCurrentIdsWithoutChangingSavedBytes() {
        val version=VideoProjectVersion.create("Alias",draft());val original=version.encoded()
        val live=VideoSource("live","content://synthetic/extra",4000);val catalog=mapOf("live" to live)
        val proposal=VideoVersionSources.reconcile("content://synthetic/primary",catalog,version.draft())
        assertEquals("live",proposal.segments[1].sourceId);assertEquals(listOf(live),proposal.sources)
        assertArrayEquals(original,version.encoded());assertEquals("extra",version.draft().segments[1].sourceId)
        assertEquals(mapOf("live" to live),catalog)
    }
    @Test fun sourceIdCollisionGetsIndependentIdAndRealProjectUndoKeepsPriorOriginals() {
        val currentSource=VideoSource("extra","content://synthetic/other",5000);val catalog=mapOf("extra" to currentSource)
        val proposal=VideoVersionSources.reconcile("content://synthetic/primary",catalog,draft()){"fresh-source"}
        assertEquals("fresh-source",proposal.segments[1].sourceId);assertEquals("content://synthetic/extra",proposal.sources.single().uri)
        val project=VideoEditProject(8000);project.appendSource("extra",5000);val before=project.segments.toList()
        project.applyDraft(proposal.segments,(catalog.values+proposal.sources).associate{it.id to it.durationMs},proposal.soundtrack)
        assertEquals(proposal.segments,project.segments);assertTrue(project.undo());assertEquals(before,project.segments)
        assertEquals(currentSource,catalog["extra"])
    }
    @Test fun changedSourceDurationOverCapacityAndUnallocatableIdRefuseWithoutMutation() {
        val changed=mapOf("live" to VideoSource("live","content://synthetic/extra",5000))
        reject{VideoVersionSources.reconcile("content://synthetic/primary",changed,draft())}
        val full=(0 until 31).associate{i->"old$i" to VideoSource("old$i","content://synthetic/old/$i",4000)}
        reject{VideoVersionSources.reconcile("content://synthetic/primary",full,draft())}
        val collision=mapOf("extra" to VideoSource("extra","content://synthetic/other",4000))
        reject{VideoVersionSources.reconcile("content://synthetic/primary",collision,draft()){"primary"}}
        assertEquals(31,full.size);assertEquals(5000L,changed.getValue("live").durationMs)
    }
}
