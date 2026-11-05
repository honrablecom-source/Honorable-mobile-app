package com.honorablemobile

import android.content.Context
import android.util.Base64
import app.honorable.auth.SecureAccountStore
import org.json.JSONObject

/** Reuse existing keystore encryption, AtomicFile writes and no-backup storage. */
internal class VideoDraftPersistence(private val context: Context, sourceUri: String) {
    companion object { private val draftLock = Any() }
    private val identity = currentIdentity()
    private val store = SecureAccountStore(context, VideoDraftCodec.storageName(identity.first, identity.second, sourceUri))

    private fun currentIdentity(): Pair<String, String> {
        val session = requireNotNull(SecureAccountStore(context).read()) { "Sign in to save or restore drafts" }
        require(session.optLong("expiresAt") > System.currentTimeMillis()) { "Sign in to save or restore drafts" }
        val account = session.optJSONObject("cached")?.optJSONObject("account")?.optString("accountId").orEmpty()
        val api = session.optString("apiUrl")
        require(account.isNotBlank() && api.startsWith("https://")) { "Sign in to save or restore drafts" }
        return account to api
    }

    fun requireCurrentOwner() {
        require(currentIdentity() == identity) { "Sign in to the same account to restore this draft" }
    }

    fun save(draft: VideoDraft) = synchronized(draftLock) {
        requireCurrentOwner()
        store.write(JSONObject().put("draft", Base64.encodeToString(VideoDraftCodec.encode(draft), Base64.NO_WRAP)))
    }

    fun load(durationMs: Long): VideoDraft? = synchronized(draftLock) {
        requireCurrentOwner()
        val record=try { store.readPreserving() } catch (_:java.io.IOException) { throw VideoDraftRecordException() }
        VideoAccountDraftRecord.decode(record,durationMs)
    }
}

/** Only an absent record is absent; invalid saved data must never look like an empty shelf. */
internal class VideoDraftRecordException : IllegalArgumentException(
    "Saved draft could not be opened or is incompatible. Saved data and current edits were kept."
)

internal object VideoAccountDraftRecord {
    private const val MAX_ENCODED_CHARS = 350_000
    fun decode(record:JSONObject?,durationMs:Long):VideoDraft? {
        if(record==null)return null
        val encoded=record.opt("draft")
        if(encoded !is String || encoded.length !in 1..MAX_ENCODED_CHARS)throw VideoDraftRecordException()
        return try {
            VideoDraftCodec.decode(Base64.decode(encoded,Base64.NO_WRAP),durationMs)
                ?: throw VideoDraftRecordException()
        } catch (_:IllegalArgumentException) { throw VideoDraftRecordException() }
    }
}
