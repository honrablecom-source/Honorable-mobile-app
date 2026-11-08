package com.honorablemobile

/** One visible shelf stage owns approval. Synchronous guards survive consumption only in its action. */
internal class VideoLookReview {
    private var stage: Any? = null
    private var executing: Any? = null
    var isClosed = false
        private set

    fun open(next: Any): Boolean {
        if (isClosed) return false
        stage = next
        return true
    }
    fun owns(value: Any): Boolean = !isClosed && stage === value
    fun isExecuting(value: Any): Boolean = executing === value
    fun dismiss(value: Any) { if (owns(value)) close() }
    fun close() { isClosed = true; stage = null; executing = null }

    fun execute(value: Any, requireCurrent: () -> Unit, action: () -> Unit): Boolean {
        if (!owns(value)) return false
        close()
        executing = value
        try {
            requireCurrent()
            action()
            return true
        } finally { executing = null }
    }

    companion object {
        fun requireAvailable(catalog: VideoColorLookCatalog, look: NamedVideoColorLook) {
            require(catalog.looks.any { it == look }) { "Saved look changed. Open saved looks again." }
        }
    }
}
