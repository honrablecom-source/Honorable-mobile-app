package app.honorable.editor

/** Provenance passed only between private creation/editor activities; never a write permission. */
object ImportedPhotoOriginals {
    const val EXTRA = "honorable.protectedPhotoOriginals"
    const val MAX_SOURCES = 16
    fun validate(values: List<String>): List<String> {
        require(values.size <= MAX_SOURCES) { "Use up to 16 source photos per painting project" }
        return values.map { value ->
            require(value.toByteArray(Charsets.UTF_8).size <= 2048 && value.none { it.isISOControl() })
            require(value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == value)
            val uri = java.net.URI(value)
            require(uri.scheme == "content" && !uri.rawAuthority.isNullOrEmpty() && uri.rawFragment == null) { "Choose local source photos" }
            value
        }.distinct()
    }
}
