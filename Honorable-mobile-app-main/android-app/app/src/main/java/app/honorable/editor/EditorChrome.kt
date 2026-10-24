package app.honorable.editor

import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.widget.Button

/** Shared monochrome controls; existing labels and click handlers stay authoritative. */
object EditorChrome {
    fun style(button:Button,rail:Boolean=false){
        val d=button.resources.displayMetrics.density
        button.isAllCaps=false;button.textSize=if(rail)10f else 12f
        button.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        button.minWidth=0;button.minimumWidth=0;button.minHeight=(48*d).toInt()
        button.setPadding((8*d).toInt(),(6*d).toInt(),(8*d).toInt(),(6*d).toInt())
        button.setTextColor(Color.rgb(25,25,25))
        val surface=GradientDrawable().apply{setColor(if(rail)Color.WHITE else Color.rgb(246,246,246));cornerRadius=14*d}
        button.background=RippleDrawable(ColorStateList.valueOf(0x18000000),surface,null)
        if(rail){val icon=ToolIcon(button.text.toString()).apply{setBounds(0,0,(22*d).toInt(),(22*d).toInt())};button.compoundDrawablePadding=(5*d).toInt();button.setCompoundDrawables(null,icon,null,null)}
    }
    class ToolIcon(label:String):Drawable(){
        private val key=label.lowercase()
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;style=Paint.Style.STROKE;strokeWidth=1.65f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        override fun draw(c:Canvas){
            val save=c.save();c.translate(bounds.left.toFloat(),bounds.top.toFloat());c.scale(bounds.width()/24f,bounds.height()/24f);if(key.contains("redo")){c.translate(24f,0f);c.scale(-1f,1f)}
            fun line(x:Float,y:Float,a:Float,b:Float)=c.drawLine(x,y,a,b,paint)
            when{
                key.contains("undo")||key.contains("redo")-> {c.drawArc(5f,6f,20f,20f,210f,260f,false,paint);line(4f,5f,4f,11f);line(4f,11f,10f,11f)}
                key.contains("crop")||key.contains("aspect")||key.contains("format")||key.contains("transform")||key.contains("mirror")-> {line(7f,3f,7f,17f);line(3f,7f,17f,7f);line(7f,17f,21f,17f);line(17f,7f,17f,21f)}
                key.contains("title")||key.contains("text")-> {line(5f,5f,19f,5f);line(12f,5f,12f,20f);line(8f,20f,16f,20f)}
                key.contains("music")||key.contains("audio")-> {line(10f,17f,10f,5f);line(10f,5f,20f,3f);line(20f,3f,20f,15f);c.drawOval(4f,15f,10f,20f,paint);c.drawOval(14f,13f,20f,18f,paint)}
                key.contains("assist")||key.contains("ai")||key.contains("person")-> {val p=Path();p.moveTo(12f,3f);p.lineTo(14.5f,9.5f);p.lineTo(21f,12f);p.lineTo(14.5f,14.5f);p.lineTo(12f,21f);p.lineTo(9.5f,14.5f);p.lineTo(3f,12f);p.lineTo(9.5f,9.5f);p.close();c.drawPath(p,paint)}
                key.contains("light")||key.contains("exposure")||key.contains("brightness")-> {c.drawCircle(12f,12f,4f,paint);for(i in 0..7){val a=i*Math.PI/4;line((12+7*Math.cos(a)).toFloat(),(12+7*Math.sin(a)).toFloat(),(12+9*Math.cos(a)).toFloat(),(12+9*Math.sin(a)).toFloat())}}
                key.contains("curve")-> {line(4f,4f,4f,20f);line(4f,20f,20f,20f);val p=Path();p.moveTo(5f,18f);p.cubicTo(16f,18f,8f,6f,20f,4f);c.drawPath(p,paint)}
                key.contains("split")-> {c.drawCircle(6f,6f,3f,paint);c.drawCircle(6f,18f,3f,paint);line(8f,8f,20f,20f);line(8f,16f,20f,4f)}
                key.contains("pause")-> {line(9f,5f,9f,19f);line(15f,5f,15f,19f)}
                key.contains("previous")||key.contains("next")-> {val p=Path();if(key.contains("previous")){p.moveTo(17f,5f);p.lineTo(7f,12f);p.lineTo(17f,19f)}else{p.moveTo(7f,5f);p.lineTo(17f,12f);p.lineTo(7f,19f)};c.drawPath(p,paint)}
                key.contains("preview")||key.contains("play")-> {val p=Path();p.moveTo(8f,4f);p.lineTo(20f,12f);p.lineTo(8f,20f);p.close();c.drawPath(p,paint)}
                else->{line(5f,7f,19f,7f);line(5f,17f,19f,17f);c.drawCircle(9f,7f,2f,paint);c.drawCircle(15f,17f,2f,paint)}
            };c.restoreToCount(save)
        }
        override fun setAlpha(alpha:Int){paint.alpha=alpha}
        override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter}
        @Deprecated("Drawable API") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
}
