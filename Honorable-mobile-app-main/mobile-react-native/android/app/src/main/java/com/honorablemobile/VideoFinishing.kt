package com.honorablemobile

internal data class VideoLayout(val ratio:Int=0,val crop:Boolean=false,val rotation:Int=0,val mirrorX:Boolean=false,val mirrorY:Boolean=false) {
    init{require(ratio in 0..4&&rotation in setOf(0,90,180,270))}
    val aspect:Float get()=listOf(0f,1f,9f/16,16f/9,.8f)[ratio]
    val isIdentity:Boolean get()=this==VideoLayout()
}
internal data class VideoTitleStyle(val position:Int=2,val size:Float=.055f,val light:Boolean=true,val plate:Boolean=true){
    init{require(position in 0..2&&size.isFinite()&&size in .025f.. .12f)}
    val isIdentity:Boolean get()=this==VideoTitleStyle()
}
internal object VideoFinishing {
    fun audioGain(durationUs:Long,positionUs:Long):Float {
        require(durationUs>0)
        val edge=minOf(500_000L,durationUs/2).coerceAtLeast(1)
        val t=positionUs.coerceIn(0,durationUs)
        return minOf(t.toDouble()/edge,(durationUs-t).toDouble()/edge,1.0).toFloat().coerceIn(0f,1f)
    }
}
