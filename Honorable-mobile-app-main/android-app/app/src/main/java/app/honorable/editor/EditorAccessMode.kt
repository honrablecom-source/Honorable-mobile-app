package app.honorable.editor

/** Explicit local editor access; this never authorizes search, credits or account API routes. */
enum class EditorAccessMode {
    ACCOUNT,
    GUEST_BASIC;

    val allowsAi: Boolean get() = this == ACCOUNT
    val allowsAccountDrafts: Boolean get() = this == ACCOUNT

    fun authorizeAccount(authorize: () -> Unit) {
        if (this == ACCOUNT) authorize()
    }

    /** Guest state belongs only to this activity instance and source, never an account draft. */
    fun lifecycleScope(source: String, guestInstance: String?, readAccountScope: () -> String?): String? {
        if (this == ACCOUNT) return readAccountScope()
        require(guestInstance != null && guestInstance.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        return PhotoSessionCodec.scope("guest-activity:$guestInstance", "local-only", source)
    }

    companion object {
        const val EXTRA = "app.honorable.editor.ACCESS_MODE"

        /** Old internal launches retain account authorization; malformed modes fail closed. */
        fun fromExtra(value: String?): EditorAccessMode = when (value) {
            null, ACCOUNT.name -> ACCOUNT
            GUEST_BASIC.name -> GUEST_BASIC
            else -> error("Unsupported editor access mode")
        }
    }
}
