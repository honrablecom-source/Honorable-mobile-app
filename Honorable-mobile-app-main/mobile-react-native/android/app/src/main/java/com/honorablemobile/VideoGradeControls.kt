package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.*
import kotlin.math.roundToInt

internal object VideoGradeControls {
    fun show(activity:Activity,initial:VideoGrade,commit:(VideoGrade)->Unit) {
        var grade=initial
        fun dp(value:Int)=(value*activity.resources.displayMetrics.density).toInt()
        val panel=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        panel.addView(TextView(activity).apply{text="Applies to this entire clip. Preview clip or project to see the rendered color.";setTextColor(Color.DKGRAY)})
        val bars=mutableListOf<SeekBar>()
        fun slider(name:String,max:Int,value:Int,update:(Int)->Unit) {
            val label=TextView(activity).apply{setTextColor(Color.BLACK)};panel.addView(label)
            fun caption(v:Int){label.text=if(name=="Exposure")"Exposure: ${(v-200)/100f} EV" else "$name: $v%"}
            panel.addView(SeekBar(activity).apply{
                this.max=max;progress=value;contentDescription=name;caption(value)
                progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){}
                    override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){caption(v);if(user)update(v)}
                });bars.add(this)
            },LinearLayout.LayoutParams(-1,dp(48)))
        }
        slider("Exposure",400,(grade.exposure*100+200).roundToInt()){grade=grade.copy(exposure=(it-200)/100f)}
        slider("Contrast",200,(grade.contrast*100).roundToInt()){grade=grade.copy(contrast=it/100f)}
        slider("Saturation",200,(grade.saturation*100).roundToInt()){grade=grade.copy(saturation=it/100f)}
        panel.addView(Button(activity).apply{text="Reset color";setOnClickListener{grade=VideoGrade();listOf(200,100,100).forEachIndexed{i,v->bars[i].progress=v}}})
        AlertDialog.Builder(activity).setTitle("Video color").setView(ScrollView(activity).apply{addView(panel)})
            .setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->commit(grade)}.show()
    }
}
