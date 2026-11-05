package com.honorablemobile

/** Bound allocations before touching the filesystem or allocating an ARGB bitmap. */
internal object CanvasDimensions {
    const val MAX_PIXELS = 4_194_304
    fun validate(width:Int,height:Int) {
        require(width in 64..4096 && height in 64..4096 && width.toLong()*height <= MAX_PIXELS) {
            "Use dimensions from 64 to 4096 pixels, with at most 4,194,304 pixels total."
        }
    }
}
