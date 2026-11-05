package app.honorable.editor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Local editable state only. No original media bytes, export destinations, account tokens or raw URI. */
object PhotoProjectCodec {
    private const val MAGIC=0x4E50524A
    const val MAX_BYTES=65_664
    const val MAX_SOURCE_BYTES=128L*1024*1024
    private fun scope(namespace:String,source:String):String {
        require(PhotoPresetNamespace.valid(namespace))
        require(source.startsWith("content://") && source.length<=2048 && source.none{it.isISOControl()})
        val raw=source.toByteArray(Charsets.UTF_8);require(raw.size<=2048&&raw.toString(Charsets.UTF_8)==source)
        return PhotoSessionCodec.scope(namespace,"local-photo-project-v1",source)
    }
    fun storageName(namespace:String,source:String)=scope(namespace,source)+".bin"
    fun fingerprint(input:InputStream,requireCurrent:()->Unit = {}):String {
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024);var total=0L;var nextCheck=0L
        while(true){
            if(total>=nextCheck){requireCurrent();nextCheck=total+4L*1024*1024}
            val count=input.read(buffer)
            if(count<0)break
            if(count==0){val single=input.read();if(single<0)break;total++;require(total<=MAX_SOURCE_BYTES){"Editable project sources are limited to 128 MiB"};digest.update(single.toByte())}
            else {total+=count;require(total<=MAX_SOURCE_BYTES){"Editable project sources are limited to 128 MiB"};digest.update(buffer,0,count)}
        }
        requireCurrent()
        require(total>0){"The original photo is empty"}
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
    fun encode(namespace:String,source:String,fingerprint:String,edit:PhotoEdit):ByteArray {
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        val payload=PhotoSessionCodec.encode(PhotoSessionSnapshot(scope(namespace,source),edit))
        return ByteArrayOutputStream().use{bytes->DataOutputStream(bytes).use{out->out.writeInt(MAGIC);out.writeInt(1);out.writeUTF(fingerprint);out.writeInt(payload.size);out.write(MessageDigest.getInstance("SHA-256").digest(payload));out.write(payload)};bytes.toByteArray().also{require(it.size<=MAX_BYTES)}}
    }
    fun decode(bytes:ByteArray,namespace:String,source:String,fingerprint:String):PhotoEdit {
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        require(bytes.size in 111..MAX_BYTES){"Saved photo project is damaged or unsupported"}
        DataInputStream(ByteArrayInputStream(bytes)).use{input->
            require(input.readInt()==MAGIC && input.readInt()==1){"Unsupported photo project version"}
            require(input.readUTF()==fingerprint){"The original photo changed. Saved edits were left untouched."}
            val count=input.readInt();require(count in 1..PhotoSessionCodec.MAX_BYTES && count==input.available()-32)
            val digest=ByteArray(32);input.readFully(digest);val payload=ByteArray(count);input.readFully(payload)
            require(MessageDigest.isEqual(digest,MessageDigest.getInstance("SHA-256").digest(payload))){"Saved photo project is damaged"}
            return PhotoSessionCodec.decode(payload,scope(namespace,source)).edit
        }
    }
}

/** Roll back only permission newly acquired by a failed explicit save/restore. */
object PhotoProjectAccess {
    private val lock=Any()
    fun <T> withDurableRead(hasRead:()->Boolean,takeRead:()->Unit,releaseRead:()->Unit,requireCurrent:()->Unit,action:()->T):T=synchronized(lock){
        var acquired=false
        try {requireCurrent();if(!hasRead()){takeRead();acquired=true};require(hasRead()){"This original needs durable access. Reopen it through Files, then try again."};requireCurrent();action()}
        catch(failure:Throwable){if(acquired)runCatching(releaseRead).exceptionOrNull()?.let(failure::addSuppressed);throw failure}
    }
}
