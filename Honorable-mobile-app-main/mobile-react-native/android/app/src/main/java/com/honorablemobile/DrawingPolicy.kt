package com.honorablemobile

internal object DrawingPolicy {
    const val HOST = "honorable-drawing.local"
    const val MAX_BYTES = 8 * 1024 * 1024
    const val PNG_PREFIX = "data:image/png;base64,"
    fun assetPath(scheme: String?, host: String?, port: Int, path: String?): String? {
        if(scheme!="https" || host!=HOST || port !in setOf(-1,443) || path==null || !path.startsWith("/drawing/"))return null
        val pieces=path.removePrefix("/drawing/").split('/')
        if(pieces.isEmpty()||pieces.any{it.isEmpty()||it=="."||it==".."||!it.matches(Regex("[A-Za-z0-9_.@-]+"))})return null
        return "drawing/"+pieces.joinToString("/")
    }
    fun base64Payload(value: String): String {
        require(value.startsWith(PNG_PREFIX)&&value.length <= PNG_PREFIX.length+((MAX_BYTES+2)/3)*4) { "Drawing image is too large or unsupported" }
        return value.removePrefix(PNG_PREFIX).also { require(it.isNotEmpty()&&it.length%4==0&&it.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) { "Drawing image is invalid" } }
    }
    fun validatePng(bytes:ByteArray):Pair<Int,Int> {
        require(bytes.size in 33..MAX_BYTES)
        require(bytes.take(8)==listOf(137,80,78,71,13,10,26,10).map{it.toByte()}) { "Expected a PNG drawing" }
        fun integer(offset:Int)=java.nio.ByteBuffer.wrap(bytes,offset,4).int
        require(integer(8)==13&&bytes.copyOfRange(12,16).toString(Charsets.US_ASCII)=="IHDR")
        val width=integer(16);val height=integer(20)
        require(width in 1..2048&&height in 1..2048&&width.toLong()*height<=4_194_304) { "Use a drawing no larger than 2048 pixels" }
        return width to height
    }
}
