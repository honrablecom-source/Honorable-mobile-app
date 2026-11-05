package com.honorablemobile

import java.io.DataInputStream
import java.io.DataOutputStream

enum class MotionEase { LINEAR, SMOOTH }

/** Source-anchored endpoints keep motion continuous when clips are trimmed, split or sped up. */
internal data class VideoMotion(
    val enabled:Boolean=false,
    val startZoom:Float=1f,val endZoom:Float=1f,
    val startX:Float=0f,val startY:Float=0f,val endX:Float=0f,val endY:Float=0f,
    val anchorStartMs:Long=0,val anchorEndMs:Long=1,
    val ease:MotionEase=MotionEase.SMOOTH,
){
    init{
        require(listOf(startZoom,endZoom,startX,startY,endX,endY).all{it.isFinite()})
        require(startZoom in 1f..3f&&endZoom in 1f..3f)
        require(listOf(startX,startY,endX,endY).all{it in -1f..1f})
        require(anchorStartMs>=0&&anchorEndMs>anchorStartMs&&anchorEndMs<=Int.MAX_VALUE)
    }
    val isIdentity get()=!enabled||(startZoom==1f&&endZoom==1f)
    fun atSource(sourceMs:Double):VideoMotionFrame{
        require(sourceMs.isFinite())
        if(!enabled)return VideoMotionFrame(1f,0f,0f)
        val fraction=((sourceMs-anchorStartMs)/(anchorEndMs-anchorStartMs)).coerceIn(0.0,1.0)
        val t=if(ease==MotionEase.LINEAR)fraction else fraction*fraction*(3-2*fraction)
        fun mix(a:Float,b:Float)=(a+(b-a)*t).toFloat()
        val zoom=mix(startZoom,endZoom);val x=mix(startX,endX);val y=mix(startY,endY)
        return VideoMotionFrame(zoom,-x*(zoom-1),y*(zoom-1))
    }
    /** Controls show the selected clip's effective endpoints. Unchanged Apply retains source timing. */
    fun forEditing(startMs:Long,endMs:Long):VideoMotion{
        require(startMs>=0&&endMs>startMs&&endMs<=Int.MAX_VALUE)
        if(this==VideoMotion())return VideoMotion(true,endZoom=1.2f,anchorStartMs=startMs,anchorEndMs=endMs)
        val active=copy(enabled=true);val from=active.atSource(startMs.toDouble());val to=active.atSource(endMs.toDouble())
        fun pan(offset:Float,zoom:Float)=if(zoom==1f)0f else (offset/(zoom-1)).coerceIn(-1f,1f)
        return copy(startZoom=from.zoom,endZoom=to.zoom,startX=pan(-from.translateX,from.zoom),startY=pan(from.translateY,from.zoom),endX=pan(-to.translateX,to.zoom),endY=pan(to.translateY,to.zoom),anchorStartMs=startMs,anchorEndMs=endMs)
    }
    internal fun write(out:DataOutputStream){out.writeBoolean(enabled);for(value in listOf(startZoom,endZoom,startX,startY,endX,endY))out.writeFloat(value);out.writeLong(anchorStartMs);out.writeLong(anchorEndMs);out.writeByte(ease.ordinal)}
    companion object{
        internal fun read(input:DataInputStream):VideoMotion{
            val enabled=input.readBoolean();val a=input.readFloat();val b=input.readFloat();val x=input.readFloat();val y=input.readFloat();val u=input.readFloat();val v=input.readFloat();val start=input.readLong();val end=input.readLong();val ease=input.readUnsignedByte();require(ease in MotionEase.entries.indices)
            return VideoMotion(enabled,a,b,x,y,u,v,start,end,MotionEase.entries[ease])
        }
    }
}
internal data class VideoMotionFrame(val zoom:Float,val translateX:Float,val translateY:Float){
    fun matrixValues()=floatArrayOf(zoom,0f,translateX,0f,zoom,translateY,0f,0f,1f)
}
/** Final composition timestamps, after speed changes. One immutable lookup table per render. */
internal class VideoMotionTimeline(clips:List<VideoSegment>){
    private val clips=clips.toList()
    private val offsets=LongArray(clips.size+1)
    init{require(this.clips.isNotEmpty());for(i in this.clips.indices)offsets[i+1]=Math.addExact(offsets[i],this.clips[i].durationUs)}
    fun at(positionUs:Long):VideoMotionFrame{
        val time=positionUs.coerceIn(0,offsets.last());var low=0;var high=clips.lastIndex
        while(low<high){val middle=(low+high)/2;if(time>=offsets[middle+1])low=middle+1 else high=middle}
        val clip=clips[low];val local=(time-offsets[low]).coerceIn(0,clip.durationUs)
        val source=clip.startMs+local*clip.speed.toDouble()/1000
        return clip.motion.atSource(source.coerceIn(clip.startMs.toDouble(),clip.endMs.toDouble()))
    }
}
