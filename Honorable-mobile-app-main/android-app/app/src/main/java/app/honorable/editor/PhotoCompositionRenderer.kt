package app.honorable.editor

import android.graphics.*
import kotlin.math.min

/** Mutates only the editor-owned raster, never an imported original. */
object PhotoCompositionRenderer {
    private fun pixels(bitmap:Bitmap,value:PhotoComposition) {
        if(!value.needsPixels)return
        val source=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(source,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val kernel=PhotoCompositionKernel(source,bitmap.width,bitmap.height,value)
        val row=IntArray(bitmap.width)
        for(y in 0 until bitmap.height){for(x in row.indices)row[x]=kernel.sample(x,y);bitmap.setPixels(row,0,row.size,0,y,row.size,1)}
    }
    fun render(input:Bitmap,value:PhotoComposition):Bitmap {
        if(value.isIdentity)return input
        pixels(input,value)
        if(!value.needsCanvas){watermark(input,value);return input}
        val p=PhotoCompositionPlan.create(input.width,input.height,value)
        val output=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(output);canvas.drawColor(value.canvasColor)
            val width=input.width.toFloat();val height=input.height.toFloat()
            val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val image=RectF(p.left,p.top,p.left+width*p.scale,p.top+height*p.scale)
            if(p.frame>0)canvas.drawRect(RectF(image.left-p.frame/2,image.top-p.frame/2,image.right+p.frame/2,image.bottom+p.frame/2),Paint().apply{color=value.frameColor;style=Paint.Style.STROKE;strokeWidth=p.frame})
            val inset=width*kotlin.math.abs(value.perspective)
            val points=if(value.perspective>=0)floatArrayOf(inset,0f,width-inset,0f,width,height,0f,height)else floatArrayOf(0f,0f,width,0f,width-inset,height,inset,height)
            val perspective=Matrix().apply{check(setPolyToPoly(floatArrayOf(0f,0f,width,0f,width,height,0f,height),0,points,0,4))}
            canvas.save();canvas.translate(p.left,p.top);canvas.scale(p.scale,p.scale);canvas.concat(perspective)
            if(value.shadow>0) {
                val offset=IntArray(2)
                val shadow=input.extractAlpha(Paint().apply{maskFilter=BlurMaskFilter(min(width,height)*.05f*value.shadow,BlurMaskFilter.Blur.NORMAL)},offset)
                try{val shift=min(width,height)*.035f*value.shadow;canvas.drawBitmap(shadow,offset[0]+shift,offset[1]+shift,Paint().apply{color=Color.BLACK;alpha=(value.shadow*180).toInt()})}finally{shadow.recycle()}
            }
            canvas.drawBitmap(input,0f,0f,paint);canvas.restore()
            if(p.reflectionHeight>0) {
                val top=image.bottom+min(width,height)*.015f*p.scale
                val bounds=RectF(image.left,top,image.right,minOf(output.height.toFloat(),top+p.reflectionHeight))
                val layer=canvas.saveLayer(bounds,null)
                canvas.save();canvas.clipRect(bounds);canvas.translate(image.left,top);canvas.scale(p.scale,-p.scale);canvas.translate(0f,-height);canvas.concat(perspective);canvas.drawBitmap(input,0f,0f,paint);canvas.restore()
                canvas.drawRect(bounds,Paint().apply{shader=LinearGradient(0f,bounds.top,0f,bounds.bottom,0x80000000.toInt(),0,Shader.TileMode.CLAMP);xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)})
                canvas.restoreToCount(layer)
            }
            watermark(output,value)
            return output
        }catch(error:Throwable){output.recycle();throw error}
    }
    private fun watermark(bitmap:Bitmap,value:PhotoComposition) {
        if(value.watermark.isEmpty())return
        val canvas=Canvas(bitmap)
        val size=(min(bitmap.width,bitmap.height)*.04f).coerceAtLeast(8f)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=if(value.watermarkLight)Color.WHITE else Color.BLACK;alpha=(255*value.watermarkOpacity).toInt();textSize=size;textAlign=Paint.Align.CENTER;typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)}
        for(row in 0..8)for(column in 0..6){val x=(column+(if(row%2==0)0f else .5f))*bitmap.width/6f;val y=row*bitmap.height/8f;canvas.save();canvas.rotate(-25f,x,y);canvas.drawText(value.watermark,x,y,paint);canvas.restore()}
    }
}
