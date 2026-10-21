package app.honorable.editor

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

object PhotoFinishRenderer {
    fun pixels(output:Bitmap,finish:PhotoFinish) {
        val kernel=PhotoFinishKernel(finish,output.width,output.height)
        if(kernel.active){val row=IntArray(output.width)
            for(y in 0 until output.height){output.getPixels(row,0,row.size,0,y,row.size,1);for(x in row.indices)row[x]=kernel.apply(row[x],x,y);output.setPixels(row,0,row.size,0,y,row.size,1)}
        }
        if(finish.sharpen>0){
            val width=output.width;var previous=IntArray(width);var current=IntArray(width);var next=IntArray(width);val result=IntArray(width)
            output.getPixels(current,0,width,0,0,width,1);current.copyInto(previous)
            for(y in 0 until output.height){
                if(y+1<output.height)output.getPixels(next,0,width,0,y+1,width,1)else current.copyInto(next)
                for(x in 0 until width)result[x]=PhotoFinishKernel.sharp(current[x],current[maxOf(0,x-1)],current[minOf(width-1,x+1)],previous[x],next[x],finish.sharpen)
                output.setPixels(result,0,width,0,y,width,1)
                val swap=previous;previous=current;current=next;next=swap
            }
        }
    }
    fun caption(output:Bitmap,caption:PhotoCaption?) {
        if(caption==null)return
        val padding=(output.width*.02f).toInt().coerceAtLeast(2);val width=(output.width*.9f).toInt().coerceAtLeast(1)
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=if(caption.light)Color.WHITE else Color.BLACK;textSize=(minOf(output.width,output.height)*caption.size).coerceAtLeast(1f)}
        val layout=StaticLayout.Builder.obtain(caption.text,0,caption.text.length,paint,maxOf(1,width-padding*2))
            .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        val height=layout.height+padding*2
        val left=(output.width*caption.x-width/2f).coerceIn(0f,maxOf(0,output.width-width).toFloat())
        val top=(output.height*caption.y-height/2f).coerceIn(0f,maxOf(0,output.height-height).toFloat())
        val canvas=Canvas(output);canvas.save();canvas.translate(left,top)
        if(caption.plate)canvas.drawRoundRect(RectF(0f,0f,width.toFloat(),height.toFloat()),padding.toFloat(),padding.toFloat(),Paint().apply{color=if(caption.light)0xCC000000.toInt() else 0xDDFFFFFF.toInt()})
        canvas.translate(padding.toFloat(),padding.toFloat());layout.draw(canvas);canvas.restore()
    }
}
