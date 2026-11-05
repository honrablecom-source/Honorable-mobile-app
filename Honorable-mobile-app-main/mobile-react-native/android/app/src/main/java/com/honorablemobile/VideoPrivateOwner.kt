package com.honorablemobile

import android.content.Context
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.PhotoPresetNamespace
import app.honorable.auth.SecureAccountStore

/** Same private guest/account identity policy as existing saved local editor shelves. */
internal object VideoPrivateOwner {
    fun namespace(context:Context,mode:EditorAccessMode):String?=runCatching{
        if(mode==EditorAccessMode.GUEST_BASIC)return@runCatching PhotoPresetNamespace.GUEST
        val session=SecureAccountStore(context).read()?:return@runCatching null
        if(session.optLong("expiresAt")<=System.currentTimeMillis())return@runCatching null
        val account=session.optJSONObject("cached")?.optJSONObject("account")?.optString("accountId").orEmpty()
        val api=session.optString("apiUrl")
        if(account.isBlank()||!api.startsWith("https://"))return@runCatching null
        PhotoPresetNamespace.account(account,api)
    }.getOrNull()
    fun requireReadable(context:Context,namespace:String) {
        require(PhotoPresetNamespace.valid(namespace))
        require(namespace==PhotoPresetNamespace.GUEST||VideoPrivateOwner.namespace(context,EditorAccessMode.ACCOUNT)==namespace) { "Sign in to the same account to use this voiceover" }
    }
}
