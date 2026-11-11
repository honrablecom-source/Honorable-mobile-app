package app.honorable.editor

/** Keep the input lease until settlement, and the latest result lease until the next request/epoch. */
class PhotoTextReadGate {
    class Request internal constructor(internal val generation: Long)
    private var latest: Request? = null
    private var pending: Request? = null
    val busy: Boolean @Synchronized get() = pending != null
    @Synchronized fun begin(generation: Long, foreground: Boolean): Request? {
        if (!foreground || pending != null) return null
        return Request(generation).also { latest = it; pending = it }
    }
    @Synchronized fun isCurrent(request: Request, generation: Long, foreground: Boolean) =
        foreground && latest === request && request.generation == generation
    @Synchronized fun settle(request: Request): Boolean {
        if (pending !== request) return false
        pending = null
        return true
    }
}
