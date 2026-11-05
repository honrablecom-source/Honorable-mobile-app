package com.honorablemobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** A real source-energy overview. Accessible controls live beside this decorative graph. */
internal class VideoAudioLevelsView(context:Context,private val overview:VideoAudioLevels.Overview,private val selected:(Long)->Unit):View(context){
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG)
    private var position=overview.startMs
    private val density=resources.displayMetrics.density
    init{setBackgroundColor(Color.WHITE);setPadding((44*density).toInt(),(14*density).toInt(),(10*density).toInt(),(18*density).toInt());importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    fun position(timeMs:Long){VideoAudioLevels.fraction(overview,timeMs);position=timeMs;invalidate()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val left=paddingLeft.toFloat();val right=(width-paddingRight).toFloat();val top=paddingTop.toFloat();val bottom=(height-paddingBottom).toFloat()
        if(right<=left||bottom<=top)return
        val span=(overview.endMs-overview.startMs).toDouble()
        ink.color=Color.DKGRAY;ink.textSize=10*resources.displayMetrics.scaledDensity
        canvas.drawText("0 dBFS",2*density,top+ink.textSize,ink);canvas.drawText("-60",2*density,bottom,ink)
        ink.color=0xFFBBBBBB.toInt();ink.strokeWidth=density;canvas.drawLine(left,bottom,right,bottom,ink)
        for(bar in overview.bars){
            val x=left+((bar.startMs-overview.startMs)/span*(right-left)).toFloat();val end=left+((bar.endMs-overview.startMs)/span*(right-left)).toFloat()
            val level=VideoAudioLevels.height(bar.rms)
            if(level==null){ink.color=0xFFEEEEEE.toInt();canvas.drawRect(x,top,end,bottom,ink)}
            else{val gap=minOf(2*density,(end-x)*.2f);ink.color=Color.BLACK;canvas.drawRect(x+gap/2,bottom-(level*(bottom-top)).toFloat(),end-gap/2,bottom,ink)}
        }
        val x=left+(VideoAudioLevels.fraction(overview,position)*(right-left)).toFloat()
        ink.color=Color.WHITE;ink.strokeWidth=5*density;canvas.drawLine(x,top,x,bottom,ink)
        ink.color=Color.BLACK;ink.strokeWidth=2*density;canvas.drawLine(x,top,x,bottom,ink)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{
        if(event.pointerCount!=1)return false
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{
                val span=(width-paddingLeft-paddingRight).toDouble();if(span<=0)return false
                position=VideoAudioLevels.selectedTime(overview.startMs,overview.endMs,(event.x-paddingLeft)/span);selected(position);invalidate()
                parent?.requestDisallowInterceptTouchEvent(event.actionMasked!=MotionEvent.ACTION_UP)
                if(event.actionMasked==MotionEvent.ACTION_UP)performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL->{parent?.requestDisallowInterceptTouchEvent(false);return true}
        }
        return super.onTouchEvent(event)
    }
    override fun performClick():Boolean{super.performClick();return true}
}
