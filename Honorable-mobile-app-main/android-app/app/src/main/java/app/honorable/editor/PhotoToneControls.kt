package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.*

/** Dialog changes are committed once, so Cancel and Undo preserve the previous edit. */
object PhotoToneControls {
    private fun Activity.dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun panel(a:Activity)=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(a.dp(20),a.dp(12),a.dp(20),a.dp(12))}
    private fun slider(a:Activity,p:LinearLayout,name:String,max:Int,value:Int,min:Int=0,changed:(Int)->Unit) {
        val title=TextView(a).apply{text="$name: $value";setTextColor(Color.BLACK);textSize=13f};p.addView(title)
        p.addView(SeekBar(a).apply{this.max=max;progress=value;contentDescription=name;progressTintList=android.content.res.ColorStateList.valueOf(Color.BLACK);thumbTintList=android.content.res.ColorStateList.valueOf(Color.BLACK)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){}
                override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){if(v<min){s.progress=min;return};title.text="$name: $v";if(user)changed(v)}
            })},LinearLayout.LayoutParams(-1,a.dp(48)))
    }
    fun curve(a:Activity,initial:PhotoToneCurve,commit:(PhotoToneCurve)->Unit) {
        var curve=initial
        val content=panel(a)
        val graph=object:View(a){
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas:Canvas){
                super.onDraw(canvas);paint.color=Color.LTGRAY;paint.strokeWidth=1f
                for(i in 0..4){val x=width*i/4f;val y=height*i/4f;canvas.drawLine(x,0f,x,height.toFloat(),paint);canvas.drawLine(0f,y,width.toFloat(),y,paint)}
                val values=floatArrayOf(0f,curve.shadows,curve.midtones,curve.highlights,1f)
                paint.color=Color.BLACK;paint.strokeWidth=a.dp(2).toFloat()
                for(i in 0..3)canvas.drawLine(width*i/4f,height*(1-values[i]),width*(i+1)/4f,height*(1-values[i+1]),paint)
            }
        }
        content.addView(graph,LinearLayout.LayoutParams(-1,a.dp(140)))
        val bars=mutableListOf<SeekBar>()
        fun point(name:String,index:Int,value:Float){
            slider(a,content,name,100,(value*100).toInt()){v->
                val points=floatArrayOf(curve.shadows,curve.midtones,curve.highlights)
                points[index]=(v/100f).coerceIn(if(index==0)0f else points[index-1],if(index==2)1f else points[index+1])
                curve=PhotoToneCurve(points[0],points[1],points[2]);bars[index].progress=(points[index]*100).toInt();graph.invalidate()
            }
            bars.add(content.getChildAt(content.childCount-1) as SeekBar)
        }
        point("Shadows",0,curve.shadows);point("Midtones",1,curve.midtones);point("Highlights",2,curve.highlights)
        content.addView(Button(a).apply{text="Reset curve";setOnClickListener{curve=PhotoToneCurve();listOf(25,50,75).forEachIndexed{i,v->bars[i].progress=v};graph.invalidate()}})
        AlertDialog.Builder(a).setTitle("RGB tone curve").setView(ScrollView(a).apply{addView(content)})
            .setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->commit(curve)}.show()
    }
    fun light(a:Activity,initial:PhotoRadialLight?,commit:(PhotoRadialLight?)->Unit,place:(PhotoRadialLight)->Unit) {
        var light=initial?:PhotoRadialLight(exposure=.35f)
        val content=panel(a)
        content.addView(TextView(a).apply{text="Position is relative to the original photo. Apply, or choose Place on photo and tap the edited preview.";setTextColor(Color.DKGRAY)})
        slider(a,content,"Center X (%)",100,(light.centerX*100).toInt()){light=light.copy(centerX=it/100f)}
        slider(a,content,"Center Y (%)",100,(light.centerY*100).toInt()){light=light.copy(centerY=it/100f)}
        slider(a,content,"Radius (%)",100,(light.radius*100).toInt(),min=5){light=light.copy(radius=(it/100f).coerceAtLeast(.05f))}
        slider(a,content,"Feather (%)",100,(light.feather*100).toInt()){light=light.copy(feather=it/100f)}
        slider(a,content,"Exposure (-2 to +2 EV, zero = 200)",400,(light.exposure*100+200).toInt()){light=light.copy(exposure=(it-200)/100f)}
        lateinit var dialog:AlertDialog
        if(initial!=null)content.addView(Button(a).apply{text="Remove local light";setOnClickListener{commit(null);dialog.dismiss()}})
        dialog=AlertDialog.Builder(a).setTitle("Local light").setView(ScrollView(a).apply{addView(content)})
            .setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->commit(light)}.setNeutralButton("Place on photo"){_,_->place(light)}.create()
        dialog.show()
    }
}
