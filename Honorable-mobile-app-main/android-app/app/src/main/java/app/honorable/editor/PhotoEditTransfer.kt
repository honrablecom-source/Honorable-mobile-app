package app.honorable.editor

import android.util.Base64
import java.security.MessageDigest

/** Source-independent full-edit metadata. The fixed flat JSON envelope needs no recursive JSON parser. */
internal object PhotoEditTransfer {
    const val MAX_TEXT_BYTES=90_000
    private val scope=PhotoSessionCodec.scope("noble-photo-settings","portable-edit-v1","no-original")
    private val envelope=Regex("""\{"format":"noble.photo-settings","version":1,"data":"([A-Za-z0-9+/]+={0,2})","sha256":"([0-9a-f]{64})"\}""")
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun encode(edit:PhotoEdit):String {
        val bytes=PhotoSessionCodec.encode(PhotoSessionSnapshot(scope,edit))
        val data=Base64.encodeToString(bytes,Base64.NO_WRAP)
        return """{"format":"noble.photo-settings","version":1,"data":"$data","sha256":"${digest(bytes)}"}""".also{require(it.length<=MAX_TEXT_BYTES)}
    }
    fun decode(text:CharSequence):PhotoEdit {
        require(text.length in 1..MAX_TEXT_BYTES){"Photo settings are empty or too large"}
        val raw=text.toString().trim();require(raw.toByteArray(Charsets.UTF_8).size<=MAX_TEXT_BYTES)
        val fields=requireNotNull(envelope.matchEntire(raw)){"Incompatible photo settings"}
        val encoded=fields.groupValues[1];val bytes=Base64.decode(encoded,Base64.NO_WRAP)
        require(bytes.size in 1..PhotoSessionCodec.MAX_BYTES&&Base64.encodeToString(bytes,Base64.NO_WRAP)==encoded){"Invalid photo settings encoding"}
        require(digest(bytes)==fields.groupValues[2]){"Photo settings are damaged"}
        val saved=PhotoSessionCodec.decode(bytes,scope)
        require(saved.undo.isEmpty()&&saved.redo.isEmpty()&&!saved.compareOriginal&&saved.activeTool== -1&&saved.pendingExport==null){"Session data is not portable photo settings"}
        return saved.edit
    }
}
