package app.honorable.editor

/** Deterministic starting adjustments. Uses the existing render/history path. */
enum class PhotoQuickStart {
    BRIGHTEN, SOFTEN, VIVID, MONOCHROME;
    companion object {
        fun apply(edit:PhotoEdit,preset:PhotoQuickStart):PhotoEdit = when(preset) {
            BRIGHTEN -> edit.copy(exposure=.3f)
            SOFTEN -> edit.copy(contrast=.9f,saturation=.9f)
            VIVID -> edit.copy(contrast=1.1f,saturation=1.25f)
            MONOCHROME -> edit.copy(saturation=0f)
        }
    }
}
