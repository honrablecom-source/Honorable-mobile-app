package com.honorablemobile

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Actual local representative frames; no synthetic waveform or compositing tracks. */
internal class VideoFilmstripView(context:Context):View(context) {
    private val d=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val frames=LruCache<String,Bitmap>(40)
    private val generation=AtomicInteger()
    private val worker=Executors.newSingleThreadExecutor()
    private var sourceUris:Map<String,Uri> = emptyMap()
    private val unavailable=mutableSetOf<String>()
    private var decoding=false
    private var closed=false
    private var clips:List<VideoSegment> = emptyList()
    private var keys:List<String> = emptyList()
    private var scale:VideoFilmstripScale?=null
    private var selected=0
    private var positionUs=0L
    private var downX=0f
    init{isFocusable=true;isClickable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
    var onSeek:((Long)->Unit)?=null
    fun submit(value:List<VideoSegment>,index:Int,viewport:Float,uris:Map<String,Uri>){
        clips=value.toList();selected=index;scale=VideoFilmstripScale(clips,viewport,d)
        layoutParams=layoutParams.apply{width=scale!!.width.toInt().coerceAtLeast(1)}
        contentDescription="Video clip timeline. ${clips.size} clips. Clip ${selected+1} selected. Swipe for previous or next clip."
        val nextKeys=clips.map{c->"${uris[c.sourceId]}:${c.startMs}:${c.endMs}"}
        sourceUris=uris.toMap()
        if(nextKeys!=keys){keys=nextKeys;generation.incrementAndGet();unavailable.clear()}
        requestLayout();invalidate()
    }
    /** One in-flight decode; scrolls never enqueue all project clips. Cache misses reload on return. */
    private fun requestVisibleFrame(left:Float,right:Float) {
        if(closed || decoding)return
        val s=scale?:return
        // Below 16dp, the clip outline is more useful than a sub-icon-size thumbnail.
        val candidates=clips.indices.filter { i ->
            val x=s.x(s.start(i));val end=s.x(s.start(i)+clips[i].durationUs)
            end>=left && x<=right && end-x>=16*d
        }.take(40)
        val index=candidates.firstOrNull { frames.get(keys[it])==null && keys[it] !in unavailable }?:return
        val key=keys[index];val clip=clips[index];val uri=sourceUris[clip.sourceId]?:return
        val token=generation.get();decoding=true
        worker.execute {
            val frame=runCatching {
                val r=MediaMetadataRetriever()
                try {
                    r.setDataSource(context,uri)
                    val time=clip.startMs*1000+(clip.endMs-clip.startMs)*500
                    if(Build.VERSION.SDK_INT>=27)r.getScaledFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,160,90)
                    else r.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                        val ratio=160f/maxOf(full.width,full.height)
                        Bitmap.createScaledBitmap(full,(full.width*ratio).toInt().coerceAtLeast(1),(full.height*ratio).toInt().coerceAtLeast(1),true).also{if(it!==full)full.recycle()}
                    }
                } finally { r.release() }
            }.getOrNull()
            post {
                decoding=false
                if(closed || token!=generation.get())frame?.recycle()
                else if(frame!=null)frames.put(key,frame)
                else unavailable.add(key)
                if(!closed)invalidate()
            }
        }
    }
    fun playhead(timeUs:Long){if(positionUs!=timeUs){positionUs=timeUs;invalidate()}}
    fun positionX()=scale?.x(positionUs)?:0f
    override fun onDraw(c:Canvas){
        super.onDraw(c);val s=scale?:return
        val canvasState=c.save();c.scale(1f,height/(138*d))
        c.drawColor(Color.WHITE);val left=c.clipBounds.left.toFloat();val right=c.clipBounds.right.toFloat()
        paint.typeface=Typeface.create("monospace",0);paint.textSize=10*d;paint.color=0xFF777777.toInt()
        val step=when{ s.durationUs<=15_000_000->1_000_000L;s.durationUs<=60_000_000->5_000_000L;else->30_000_000L }
        var t=(s.time(left)/step)*step
        while(t<=s.durationUs&&s.x(t)<right+60*d){val x=s.x(t);paint.color=0xFF777777.toInt();c.drawText("${t/60_000_000}:${((t/1_000_000)%60).toString().padStart(2,'0')}",x+3*d,17*d,paint);c.drawLine(x,23*d,x,28*d,paint);t+=step}
        for((i,clip)in clips.withIndex()){
            val x=s.x(s.start(i));val end=s.x(s.start(i)+clip.durationUs);if(end<left||x>right)continue
            val rect=RectF(x+1*d,34*d,end-1*d,104*d);if(rect.width()<=0)continue
            paint.color=0xFFEAEAEA.toInt();c.drawRoundRect(rect,5*d,5*d,paint)
            val frame=frames.get(keys.getOrNull(i)?:"")
            if(frame!=null&&!frame.isRecycled){val save=c.save();c.clipRect(rect);var tile=x
                while(tile<end){val dest=RectF(tile,34*d,tile+86*d,104*d);val ratio=maxOf(dest.width()/frame.width,dest.height()/frame.height);val w=dest.width()/ratio;val h=dest.height()/ratio
                    c.drawBitmap(frame,Rect(((frame.width-w)/2).toInt(),((frame.height-h)/2).toInt(),((frame.width+w)/2).toInt(),((frame.height+h)/2).toInt()),dest,paint);tile+=86*d};c.restoreToCount(save)}
            paint.color=Color.BLACK;paint.style=Paint.Style.STROKE;paint.strokeWidth=(if(i==selected)2 else 1)*d;c.drawRoundRect(rect,5*d,5*d,paint);paint.style=Paint.Style.FILL
            paint.color=0xB3000000.toInt();c.drawRect(rect.left,83*d,rect.right,104*d,paint);paint.color=Color.WHITE;paint.textSize=10*d
            val save=c.save();c.clipRect(rect);c.drawText("${i+1}  ${"%.1f".format(java.util.Locale.ROOT,clip.durationUs/1000000f)}s",rect.left+7*d,97*d,paint);c.restoreToCount(save)
            paint.color=if(i==selected)Color.BLACK else 0xFF777777.toInt();paint.textSize=10*d;c.drawText(if(i==selected)"Selected clip ${i+1}" else "Clip ${i+1}",x+4*d,125*d,paint)
        }
        requestVisibleFrame(left,right)
        paint.color=Color.BLACK;paint.strokeWidth=2*d;val head=s.x(positionUs);c.drawLine(head,26*d,head,112*d,paint);c.drawCircle(head,28*d,3*d,paint)
        c.restoreToCount(canvasState)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{when(e.actionMasked){MotionEvent.ACTION_DOWN->{downX=e.x;return true};MotionEvent.ACTION_UP->{if(kotlin.math.abs(e.x-downX)<12*d){scale?.let{onSeek?.invoke(it.time(e.x))};performClick()};return true}};return true}
    override fun performClick():Boolean{super.performClick();return true}
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.isScrollable=true;info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)}
    override fun performAccessibilityAction(action:Int,args:android.os.Bundle?):Boolean{
        val target=when(action){AccessibilityNodeInfo.ACTION_SCROLL_FORWARD->selected+1;AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD->selected-1;else->return super.performAccessibilityAction(action,args)}
        if(target !in clips.indices)return false;onSeek?.invoke(scale!!.start(target));return true
    }
    fun close(){closed=true;generation.incrementAndGet();worker.shutdownNow();frames.evictAll()}
}
