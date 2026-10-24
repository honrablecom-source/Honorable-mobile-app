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
        val encoded = store.read()?.optString("draft") ?: return@synchronized null
        if (encoded.length > 350_000) return@synchronized null
        runCatching { VideoDraftCodec.decode(Base64.decode(encoded, Base64.NO_WRAP), durationMs) }.getOrNull()
    }
}
