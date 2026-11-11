package com.honorablemobile

import android.content.Context
import android.util.Base64
import app.honorable.auth.SecureAccountStore
import java.security.MessageDigest
import org.json.JSONObject

/** Existing Android Keystore/AtomicFile storage, with a separate account namespace and filename. */
internal class EncryptedLibraryCollectionRecord(context:Context,owner:String):LibraryCollectionRecord {
    private val digest=MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
    private val store=SecureAccountStore(context,"noble-library-collections-$digest")
    override fun read():ByteArray? {
        val saved=store.readPreserving()?:return null
        val text=saved.opt("collections")
        require(text is String&&text.length in 1..533_336){"Saved collections are damaged or too large"}
        return Base64.decode(text,Base64.NO_WRAP).also{bytes->
            require(bytes.size in 1..LibraryCollectionCodec.MAX_BYTES&&Base64.encodeToString(bytes,Base64.NO_WRAP)==text){"Saved collection encoding is invalid"}
        }
    }
    override fun write(bytes:ByteArray) {
        require(bytes.size in 1..LibraryCollectionCodec.MAX_BYTES)
        store.write(JSONObject().put("collections",Base64.encodeToString(bytes,Base64.NO_WRAP)))
    }
}
