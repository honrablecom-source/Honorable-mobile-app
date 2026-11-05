package com.honorablemobile

/** Local picker/copy bounds. No original URI is serialized into the drawing document. */
internal object DrawingPhotoPolicy {
    const val REQUEST_CODE = 8301
    const val MAX_SIDE = 1024
    const val MAX_BYTES = 192 * 1024
    fun acceptsSource(scheme:String?) = scheme == "content"
    fun acceptsRequest(id:Int) = id in 1..1_000_000_000
    fun fitted(width:Int,height:Int,side:Int):Pair<Int,Int> {
        require(width>0&&height>0&&side in 1..MAX_SIDE)
        val scale=minOf(1.0,side.toDouble()/maxOf(width,height))
        return maxOf(1,(width*scale).toInt()) to maxOf(1,(height*scale).toInt())
    }
    fun smaller(side:Int):Int? {
        require(side in 1..MAX_SIDE)
        return if(side<=64)null else maxOf(64,(side*.75).toInt())
    }
}
