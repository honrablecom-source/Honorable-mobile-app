package com.honorablemobile

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class VideoColorLooksTest {
    private val look = NamedVideoColorLook("Warm",VideoGrade(.5f,1.2f,.8f,.3f,-.2f))
    @Test fun colorRecipeAppliesOnlyGradeAndUsesExistingUndoAndRecovery() {
        val clip=VideoSegment(1000,3000,speed=2f,volume=.5f,title="Title",fadeEdges=true,captionSourceUri="content://subtitle/original",captions=listOf(VideoCaption(1200,1800,"Words")),layout=VideoLayout(rotation=90))
        val changed=look.applyTo(clip)
        assertEquals(look.grade,changed.grade);assertEquals(clip,changed.copy(grade=clip.grade))
        val project=VideoEditProject(4000);project.restore(listOf(clip));project.applyDraft(listOf(changed))
        assertTrue(project.undo());assertEquals(clip,project.segments.single());assertTrue(project.redo());assertEquals(changed,project.segments.single())
        val draft=VideoDraft(4000,listOf(changed),0,0,1000,3000)
        assertEquals(draft,VideoDraftCodec.decode(VideoDraftCodec.encode(draft),4000))
    }
    @Test fun catalogIsImmutableAndRefusesDuplicateOrStaleDeletes() {
        val source=mutableListOf(look);val catalog=VideoColorLookCatalog(source);source.clear()
        assertEquals(listOf(look),catalog.looks)
        assertTrue(runCatching{(catalog.looks as MutableList<*>).clear()}.isFailure)
        assertTrue(runCatching{catalog.add(look.copy(name="WARM"))}.isFailure)
        assertTrue(runCatching{catalog.remove(look.copy(grade=VideoGrade()))}.isFailure)
        assertTrue(catalog.remove(look).looks.isEmpty());assertEquals(1,catalog.looks.size)
    }
    @Test fun maximumCatalogFitsBoundedCodecAndPreservesExactColorSettings() {
        val catalog=VideoColorLookCatalog((0 until 20).map{look.copy(name=it.toString().padStart(2,'0')+"界".repeat(38))})
        val bytes=VideoColorLookCodec.encode(catalog)
        assertTrue(bytes.size<=VideoColorLookCodec.MAX_BYTES);assertEquals(catalog,VideoColorLookCodec.decode(bytes))
        assertEquals(VideoColorLookCatalog(),VideoColorLookCodec.decode(VideoColorLookCodec.encode(VideoColorLookCatalog())))
        assertTrue(runCatching{catalog.add(look)}.isFailure)
    }
    @Test fun invalidNamesAreRefusedWithoutMutatingShelf() {
        for(name in listOf(""," "," Leading","Trailing ","x".repeat(41),"bad\nname","\uD800")) assertTrue(runCatching{look.copy(name=name)}.isFailure)
        assertTrue(runCatching{VideoColorLookCatalog(listOf(look,look))}.isFailure)
    }
    private fun envelope(payload:ByteArray):ByteArray=ByteArrayOutputStream().also{buffer->DataOutputStream(buffer).use{out->out.writeInt(0x4e564c4b);out.writeInt(payload.size);out.write(MessageDigest.getInstance("SHA-256").digest(payload));out.write(payload)}}.toByteArray()
    @Test fun damagedFutureTruncatedAndTrailingShelvesFailClosed() {
        val bytes=VideoColorLookCodec.encode(VideoColorLookCatalog(listOf(look)))
        for(invalid in listOf(bytes.copyOf(bytes.size-1),bytes+byteArrayOf(0),bytes.copyOf().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()},ByteArray(8193))) assertTrue(runCatching{VideoColorLookCodec.decode(invalid)}.isFailure)
        val payload=bytes.copyOfRange(40,bytes.size)
        assertTrue(runCatching{VideoColorLookCodec.decode(envelope(payload.copyOf().apply{this[0]=2}))}.isFailure)
        assertTrue(runCatching{VideoColorLookCodec.decode(envelope(payload+byteArrayOf(0)))}.isFailure)
    }
    @Test fun malformedColorValuesCannotEnterThroughAValidDigest() {
        val bytes=VideoColorLookCodec.encode(VideoColorLookCatalog(listOf(look)))
        val payload=bytes.copyOfRange(40,bytes.size)
        val colorStart=2+1+look.name.toByteArray(Charsets.UTF_8).size
        for(i in 0..3) payload[colorStart+i]=byteArrayOf(0x7f,0xc0.toByte(),0,0)[i]
        assertTrue(runCatching{VideoColorLookCodec.decode(envelope(payload))}.isFailure)
    }
}
