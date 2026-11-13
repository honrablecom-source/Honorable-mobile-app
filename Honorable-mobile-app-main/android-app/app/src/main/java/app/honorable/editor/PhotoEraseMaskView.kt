package app.honorable.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor

/** Touch mask in exact working-image coordinates. Source pixels are never changed. */
internal class PhotoEraseMaskView(context:Context):View(context){
    private var photo:Bitmap?=null
    private var overlay:Bitmap?=null
    private var selected=BooleanArray(0)
    private var last:Pair<Float,Float>?=null
    private var drawing=false
    var radius=16f
        set(value){require(value.isFinite()&&value in 1f..96f);field=value}
    init{contentDescription="Paint the object to remove. Shaded marks show the selected pixels.";isClickable=true;setBackgroundColor(Color.WHITE)}
    fun setPhoto(bitmap:Bitmap){
        require(!bitmap.isRecycled&&bitmap.width in 16..1024&&bitmap.height in 16..1024)
        release();photo=bitmap;selected=BooleanArray(bitmap.width*bitmap.height);overlay=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888);invalidate()
    }
    fun mask()=selected.copyOf()
    fun clear(){selected.fill(false);overlay?.eraseColor(Color.TRANSPARENT);last=null;invalidate()}
    fun rectangle(left:Int,top:Int,right:Int,bottom:Int){
        require(left in 0..99&&top in 0..99&&right in 1..100&&bottom in 1..100&&left<right&&top<bottom)
        val source=requireNotNull(photo);clear()
        val x0=source.width*left/100;val y0=source.height*top/100
        val x1=ceil(source.width*right/100.0).toInt().coerceAtMost(source.width);val y1=ceil(source.height*bottom/100.0).toInt().coerceAtMost(source.height)
        for(y in y0 until y1)for(x in x0 until x1)mark(x,y);invalidate()
    }
    fun release(){photo=null;overlay?.recycle();overlay=null;selected=BooleanArray(0);last=null;drawing=false}
    private fun bounds():RectF{
        val source=photo?:return RectF();val scale=minOf(width.toFloat()/source.width,height.toFloat()/source.height)
        val w=source.width*scale;val h=source.height*scale;return RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
    }
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);val source=photo?:return;val target=bounds();canvas.drawBitmap(source,null,target,Paint(Paint.FILTER_BITMAP_FLAG));overlay?.let{canvas.drawBitmap(it,null,target,null)}}
    private fun point(x:Float,y:Float):Pair<Float,Float>?{
        val source=photo?:return null;val target=bounds();if(!x.isFinite()||!y.isFinite()||!target.contains(x,y)||target.width()<=0||target.height()<=0)return null
        return (x-target.left)/target.width()*source.width to (y-target.top)/target.height()*source.height
    }
    private fun mark(x:Int,y:Int){val source=photo?:return;selected[y*source.width+x]=true;overlay?.setPixel(x,y,0x88000000.toInt())}
    private fun stroke(a:Pair<Float,Float>,b:Pair<Float,Float>){
        val source=photo?:return;val r=radius;val x0=floor(minOf(a.first,b.first)-r).toInt().coerceAtLeast(0);val x1=ceil(maxOf(a.first,b.first)+r).toInt().coerceAtMost(source.width-1)
        val y0=floor(minOf(a.second,b.second)-r).toInt().coerceAtLeast(0);val y1=ceil(maxOf(a.second,b.second)+r).toInt().coerceAtMost(source.height-1)
        val dx=b.first-a.first;val dy=b.second-a.second;val length=dx*dx+dy*dy
        for(y in y0..y1)for(x in x0..x1){
            val fraction=if(length==0f)0f else (((x+.5f-a.first)*dx+(y+.5f-a.second)*dy)/length).coerceIn(0f,1f)
            val px=a.first+fraction*dx;val py=a.second+fraction*dy
            if((x+.5f-px)*(x+.5f-px)+(y+.5f-py)*(y+.5f-py)<=r*r)mark(x,y)
        };invalidate()
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{
        if(!isEnabled||photo==null)return false
        if(event.pointerCount>1){drawing=false;last=null;parent?.requestDisallowInterceptTouchEvent(false);return true}
        val at=point(event.x,event.y)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{if(at==null)return false;drawing=true;last=at;parent?.requestDisallowInterceptTouchEvent(true);stroke(at,at);return true}
            MotionEvent.ACTION_MOVE->{if(!drawing)return false;if(at!=null)stroke(last?:at,at);last=at;return true}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(!drawing)return false;if(event.actionMasked==MotionEvent.ACTION_UP&&at!=null)stroke(last?:at,at);drawing=false;last=null;parent?.requestDisallowInterceptTouchEvent(false);if(event.actionMasked==MotionEvent.ACTION_UP)performClick();return true}
        };return drawing
    }
    override fun performClick():Boolean{super.performClick();return true}
}
