package com.honorablemobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections

internal data class VideoMomentNote(val sourceMs: Long, val label: String) {
    init {
        require(sourceMs >= 0) { "Choose a valid source moment" }
        val bytes=label.toByteArray(Charsets.UTF_8)
        require(label==label.trim() && label.isNotEmpty() && label.length<=80 && label.none{it.isISOControl()} && bytes.size<=240 && bytes.toString(Charsets.UTF_8)==label) { "Use a note label of 1–80 characters" }
    }
}

/** Source-anchored notes remain independent of clip trims, speed and project render state. */
internal class VideoMomentNotes(val durationMs: Long, values: List<VideoMomentNote> = emptyList()) {
    val notes: List<VideoMomentNote> = Collections.unmodifiableList(values.sortedBy{it.sourceMs})
    init {
        require(durationMs>0 && notes.size<=100) { "Use up to 100 notes per source video" }
        require(notes.all{it.sourceMs<durationMs}) { "Note exceeds this source video" }
        require(notes.map{it.sourceMs}.distinct().size==notes.size) { "This moment already has a note. Edit its label instead." }
    }
    fun add(note:VideoMomentNote)=VideoMomentNotes(durationMs,notes+note)
    fun remove(note:VideoMomentNote):VideoMomentNotes {
        require(notes.contains(note)) { "Notes changed. Open moment notes again." }
        return VideoMomentNotes(durationMs,notes-note)
    }
    fun rename(before:VideoMomentNote,label:String):VideoMomentNotes {
        require(notes.contains(before)) { "Notes changed. Open moment notes again." }
        return VideoMomentNotes(durationMs,notes.map{if(it==before)it.copy(label=label)else it})
    }
    fun visible(startMs:Long,endMs:Long):List<VideoMomentNote> {
        require(startMs>=0 && endMs>startMs && endMs<=durationMs)
        return notes.filter{it.sourceMs>=startMs && it.sourceMs<endMs}
    }
    override fun equals(other:Any?)=other is VideoMomentNotes && durationMs==other.durationMs && notes==other.notes
    override fun hashCode()=31*durationMs.hashCode()+notes.hashCode()
}

internal object VideoMomentCodec {
    const val MAX_BYTES=32768
    private const val MAGIC=0x4e564d4e
    fun storageKey(namespace:String,source:String):String {
        require(namespace=="guest-basic" || namespace.matches(Regex("account-[0-9a-f]{64}")))
        require(source.startsWith("content://") && source.length<=4096 && source.none{it.isISOControl()} && source.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==source)
        return MessageDigest.getInstance("SHA-256").digest((namespace+"\u0000"+source).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    fun encode(catalog:VideoMomentNotes):ByteArray {
        val payload=ByteArrayOutputStream().also{buffer->DataOutputStream(buffer).use{out->
            out.writeByte(1);out.writeLong(catalog.durationMs);out.writeByte(catalog.notes.size)
            for(note in catalog.notes){val text=note.label.toByteArray(Charsets.UTF_8);out.writeLong(note.sourceMs);out.writeShort(text.size);out.write(text)}
        }}.toByteArray()
        return ByteArrayOutputStream().also{buffer->DataOutputStream(buffer).use{out->out.writeInt(MAGIC);out.writeInt(payload.size);out.write(MessageDigest.getInstance("SHA-256").digest(payload));out.write(payload)}}.toByteArray().also{require(it.size<=MAX_BYTES)}
    }
    fun decode(bytes:ByteArray):VideoMomentNotes {
        require(bytes.size in 50..MAX_BYTES) { "Invalid moment-note shelf size" }
        val input=DataInputStream(ByteArrayInputStream(bytes));require(input.readInt()==MAGIC)
        val size=input.readInt();require(size in 10..MAX_BYTES && input.available()==size+32)
        val digest=ByteArray(32).also{input.readFully(it)};val payload=ByteArray(size).also{input.readFully(it)}
        require(MessageDigest.isEqual(digest,MessageDigest.getInstance("SHA-256").digest(payload))) { "Moment notes are damaged" }
        val data=DataInputStream(ByteArrayInputStream(payload));require(data.readUnsignedByte()==1) { "Unsupported moment-note version" }
        val duration=data.readLong();val count=data.readUnsignedByte();require(count<=100)
        val notes=List(count){val at=data.readLong();val sizeText=data.readUnsignedShort();require(sizeText in 1..240);val raw=ByteArray(sizeText).also{data.readFully(it)};val label=raw.toString(Charsets.UTF_8);require(label.toByteArray(Charsets.UTF_8).contentEquals(raw));VideoMomentNote(at,label)}
        require(data.available()==0)
        return VideoMomentNotes(duration,notes)
    }
}
