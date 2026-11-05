package app.honorable.editor

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

object PhotoLayersRenderer {
    fun draw(output:Bitmap,layers:PhotoLayers) {
        if(layers.objects.isEmpty())return
        val canvas=Canvas(output)
        for(value in layers.objects) {
            if(!value.visible||value.opacity==0f)continue
            val plan=PhotoObjectPlan.create(value,output.width,output.height)
            val alpha=((value.color ushr 24)*value.opacity).toInt().coerceIn(0,255)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=value.color;this.alpha=alpha;style=Paint.Style.FILL}
            canvas.save()
            try {
                canvas.translate(plan.centerX,plan.centerY);canvas.rotate(value.rotation)
                val rect=RectF(-plan.width/2,-plan.height/2,plan.width/2,plan.height/2)
                when(value.type) {
                    PhotoObjectType.RECTANGLE->canvas.drawRect(rect,paint)
                    PhotoObjectType.ELLIPSE->canvas.drawOval(rect,paint)
                    PhotoObjectType.TEXT->{
                        canvas.clipRect(rect)
                        val layer=if(value.opacity<1f)canvas.saveLayer(rect,Paint().apply{this.alpha=(value.opacity*255).toInt().coerceIn(0,255)})else canvas.save()
                        try{drawText(canvas,value,plan,minOf(output.width,output.height).toFloat())}finally{canvas.restoreToCount(layer)}
                    }
                }
            } finally {canvas.restore()}
        }
    }
    private fun drawText(canvas:Canvas,value:PhotoObject,plan:PhotoObjectPlan,unit:Float) {
        val style=value.textStyle
        val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{
            color=value.color;textSize=plan.textSize;letterSpacing=style.tracking
            typeface=Typeface.create(listOf("sans-serif","serif","monospace")[style.family],(if(style.bold)Typeface.BOLD else 0) or (if(style.italic)Typeface.ITALIC else 0))
        }
        fun pass(paint:TextPaint){
            if(value.curve!=0f){
                val maximum=PhotoTextArcPlan.create(plan.width,value.curve)
                val text=TextUtils.ellipsize(value.text.replace('\n',' '),paint,maximum.length,TextUtils.TruncateAt.END).toString()
                if(text.isEmpty())return
                val arc=PhotoTextArcPlan.fit(plan.width,value.curve,paint.measureText(text))
                val metrics=paint.fontMetrics;val center=arc.centerY-(metrics.ascent+metrics.descent)/2
                val chord=(2*arc.radius*kotlin.math.sin(Math.toRadians(kotlin.math.abs(value.curve).toDouble())/2)).toFloat()
                val inset=((plan.width-chord)/2).coerceAtLeast(0f)
                val horizontal=when(style.alignment){0->-inset;2->inset;else->0f}
                val path=Path().apply{addArc(RectF(horizontal-arc.radius,center-arc.radius,horizontal+arc.radius,center+arc.radius),arc.startDegrees,arc.sweepDegrees)}
                val remaining=(arc.length-paint.measureText(text)).coerceAtLeast(0f)
                val offset=when(style.alignment){0->0f;2->remaining;else->remaining/2}
                canvas.drawTextOnPath(text,path,offset,0f,paint)
            } else {
                val alignment=when(style.alignment){0->Layout.Alignment.ALIGN_NORMAL;2->Layout.Alignment.ALIGN_OPPOSITE;else->Layout.Alignment.ALIGN_CENTER}
                val layout=StaticLayout.Builder.obtain(value.text,0,value.text.length,paint,plan.width.toInt().coerceAtLeast(1))
                    .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setAlignment(alignment).build()
                canvas.save();try{canvas.translate(-plan.width/2,-layout.height/2f);layout.draw(canvas)}finally{canvas.restore()}
            }
        }
        if(style.shadow>0)textPaint.setShadowLayer((style.shadow*unit).coerceAtLeast(1f),style.shadowX*unit,style.shadowY*unit,style.shadowColor)
        pass(textPaint)
        if(style.outline>0){val outline=TextPaint(textPaint).apply{clearShadowLayer();this.style=Paint.Style.STROKE;strokeWidth=(style.outline*unit).coerceAtLeast(1f);color=style.outlineColor};pass(outline)}
    }
}
