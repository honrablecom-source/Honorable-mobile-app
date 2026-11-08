package com.honorablemobile

/** A visible stage transfers once to a worker receipt; late callbacks cannot release a successor. */
internal class VideoTitleShelfStages {
    private var stage: Any? = null
    private var pending: Any? = null
    private var closed = false

    @Synchronized fun open(value: Any): Boolean {
        if (closed || pending != null) return false
        stage = value
        return true
    }
    @Synchronized fun owns(value: Any): Boolean = !closed && pending == null && stage === value
    @Synchronized fun canBegin(value: Any?): Boolean = !closed && pending == null && stage === value
    @Synchronized fun begin(value: Any?): Any? {
        if (!canBegin(value)) return null
        stage = null
        return Any().also { pending = it }
    }
    @Synchronized fun permits(receipt: Any): Boolean = !closed && pending === receipt
    @Synchronized fun finish(receipt: Any): Boolean {
        if (!permits(receipt)) return false
        pending = null
        return true
    }
    @Synchronized fun close() { closed = true; stage = null; pending = null }
}
