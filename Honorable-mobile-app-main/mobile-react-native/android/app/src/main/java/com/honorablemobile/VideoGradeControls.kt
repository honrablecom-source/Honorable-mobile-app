package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.*
import kotlin.math.roundToInt

internal object VideoGradeControls {
    fun show(activity:Activity,initial:VideoGrade,balanceOnly:Boolean=false,commit:(VideoGrade)->Unit) {
        var grade=initial
        fun dp(value:Int)=(value*activity.resources.displayMetrics.density).toInt()
        val panel=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(activity).apply{text="Applies to this entire clip. Preview clip or project to see the rendered color.";setTextColor(Color.DKGRAY)})
        val bars=mutableListOf<SeekBar>()
        fun slider(name:String,max:Int,value:Int,update:(Int)->Unit) {
            val label=TextView(activity).apply{setTextColor(Color.BLACK)};panel.addView(label)
            fun caption(v:Int){label.text=if(name=="Exposure")"Exposure: ${(v-200)/100f} EV" else if(name=="Warmth"||name=="Tint")"$name: ${v-100}%" else "$name: $v%"}
            panel.addView(SeekBar(activity).apply{
                this.max=max;progress=value;contentDescription=name;caption(value)
                progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){}
                    override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){caption(v);if(user)update(v)}
                });bars.add(this)
            },LinearLayout.LayoutParams(-1,dp(48)))
        }
        if(balanceOnly){
            slider("Warmth",200,(grade.warmth*100+100).roundToInt()){grade=grade.copy(warmth=(it-100)/100f)}
            slider("Tint",200,(grade.tint*100+100).roundToInt()){grade=grade.copy(tint=(it-100)/100f)}
        }else{
            slider("Exposure",400,(grade.exposure*100+200).roundToInt()){grade=grade.copy(exposure=(it-200)/100f)}
            slider("Contrast",200,(grade.contrast*100).roundToInt()){grade=grade.copy(contrast=it/100f)}
            slider("Saturation",200,(grade.saturation*100).roundToInt()){grade=grade.copy(saturation=it/100f)}
        }
        panel.addView(Button(activity).apply{text="Reset";setOnClickListener{
            grade=if(balanceOnly)grade.copy(warmth=0f,tint=0f)else grade.copy(exposure=0f,contrast=1f,saturation=1f)
            (if(balanceOnly)listOf(100,100)else listOf(200,100,100)).forEachIndexed{i,v->bars[i].progress=v}
        }})
        AlertDialog.Builder(activity).setTitle(if(balanceOnly)"Video white balance" else "Video color").setView(ScrollView(activity).apply{addView(panel)})
            .setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->commit(grade)}.show()
    }
}
