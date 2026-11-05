package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class VideoTitleStylesTest {
    private fun reject(block: () -> Unit) { try { block(); fail("Expected refusal") } catch (_: IllegalArgumentException) {} }
    @Test fun unicodeNamesAndCatalogLimits() {
        NamedVideoTitleStyle("😀".repeat(40), VideoTitleStyle())
        for (name in listOf("", " a", "a\n", "a".repeat(41), "\uD800")) reject { NamedVideoTitleStyle(name, VideoTitleStyle()) }
        val a = NamedVideoTitleStyle("Title", VideoTitleStyle())
        reject { VideoTitleStyleCatalog(listOf(a, a.copy(name="ＴＩＴＬＥ"))) }
        reject { VideoTitleStyleCatalog(List(21) { a.copy(name="Style $it") }) }
        val mutable = mutableListOf(a)
        val catalog = VideoTitleStyleCatalog(mutable)
        mutable.clear()
        assertEquals(listOf(a), catalog.styles)
        try { (catalog.styles as MutableList).clear(); fail("Mutable catalog") } catch (_: UnsupportedOperationException) {}
        reject { catalog.remove(a.copy(style=VideoTitleStyle(0))) }
    }
    @Test fun roundTripAllStyleFieldsAndBounds() {
        val values = (0..2).flatMap { position -> listOf(.025f,.12f).flatMap { size -> listOf(false,true).map { light ->
            NamedVideoTitleStyle("$position $size $light", VideoTitleStyle(position,size,light,!light))
        } } }
        val catalog = VideoTitleStyleCatalog(values)
        assertEquals(catalog, VideoTitleStyleCodec.decode(VideoTitleStyleCodec.encode(catalog)))
        assertEquals(VideoTitleStyleCatalog(), VideoTitleStyleCodec.decode(VideoTitleStyleCodec.encode(VideoTitleStyleCatalog())))
    }
    private fun mutatePayload(bytes: ByteArray, block:(ByteArray)->Unit):ByteArray {
        val result=bytes.copyOf(); val payload=result.copyOfRange(40,result.size); block(payload)
        payload.copyInto(result,40); MessageDigest.getInstance("SHA-256").digest(payload).copyInto(result,8); return result
    }
    @Test fun corruptVersionFlagsLengthsAndTrailingDataFailClosed() {
        val bytes=VideoTitleStyleCodec.encode(VideoTitleStyleCatalog(listOf(NamedVideoTitleStyle("A",VideoTitleStyle()))))
        for(index in bytes.indices) reject { VideoTitleStyleCodec.decode(bytes.copyOf().also { it[index]=(it[index].toInt() xor 128).toByte() }) }
        for(length in 0 until bytes.size) reject { VideoTitleStyleCodec.decode(bytes.copyOf(length)) }
        reject { VideoTitleStyleCodec.decode(bytes+0.toByte()) }
        reject { VideoTitleStyleCodec.decode(mutatePayload(bytes) { it[0]=2 }) }
        reject { VideoTitleStyleCodec.decode(mutatePayload(bytes) { it[it.lastIndex]=2 }) }
        reject { VideoTitleStyleCodec.decode(mutatePayload(bytes) { it[4]=0xFF.toByte() }) }
    }
    @Test fun applyChangesOnlyAppearanceAndProjectUndo() {
        val project=VideoEditProject(8000)
        val original=project.segments.single().copy(title="My actual words",volume=.4f,speed=1.5f,fadeEdges=true)
        project.applyDraft(listOf(original))
        val saved=NamedVideoTitleStyle("White top",VideoTitleStyle(0,.08f,true,false))
        val applied=saved.applyTo(original)
        assertEquals(original,applied.copy(titleStyle=original.titleStyle))
        project.applyDraft(listOf(applied))
        assertEquals(applied,project.segments.single())
        project.undo(); assertEquals(original,project.segments.single())
        project.redo(); assertEquals(applied,project.segments.single())
    }
}
