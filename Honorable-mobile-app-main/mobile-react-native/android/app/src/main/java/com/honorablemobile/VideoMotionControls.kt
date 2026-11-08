package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.*

internal object VideoMotionControls{
    fun show(a:Activity,clip:VideoSegment,commit:(VideoMotion)->Unit){
        create(a,clip,{it.isShowing}){_,motion->commit(motion)}.show()
    }
    fun create(a:Activity,clip:VideoSegment,isCurrent:(AlertDialog)->Boolean,commit:(AlertDialog,VideoMotion)->Unit):AlertDialog {
        val baseline=clip.motion.forEditing(clip.startMs,clip.endMs);var value=baseline
        val panel=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p/2,p,p/2)}
        panel.addView(TextView(a).apply{text="Animate the picture and its clip title between two zoom/pan endpoints. Timed captions stay screen-aligned. Motion follows source time, preserving framing through trims and splits. Changing endpoints fits a new animation across this clip. Original media stays unchanged.";setTextColor(Color.DKGRAY)})
        val enabled=CheckBox(a).apply{text="Enable pan and zoom";isChecked=baseline.enabled;setTextColor(Color.BLACK);buttonTintList=ColorStateList.valueOf(Color.BLACK)};panel.addView(enabled)
        panel.addView(TextView(a).apply{text="Motion easing";setTextColor(Color.BLACK)})
        val ease=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf("Linear","Smooth in and out"));contentDescription="Motion easing";setSelection(baseline.ease.ordinal)};panel.addView(ease)
        fun slider(name:String,start:Int,maxValue:Int,display:(Int)->String,change:(Int)->Unit){
            val label=TextView(a).apply{text="$name: ${display(start)}";setTextColor(Color.BLACK)};panel.addView(label)
            panel.addView(SeekBar(a).apply{max=maxValue;progress=start;contentDescription=name;progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(bar:SeekBar){};override fun onStopTrackingTouch(bar:SeekBar){};override fun onProgressChanged(bar:SeekBar,v:Int,user:Boolean){label.text="$name: ${display(v)}";if(user)change(v)}})},LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
        }
        slider("Start zoom",(value.startZoom*100).toInt()-100,200,{"${it+100}%"}){value=value.copy(startZoom=(it+100)/100f)}
        slider("Start horizontal pan",(value.startX*100).toInt()+100,200,{"${it-100}%"}){value=value.copy(startX=(it-100)/100f)}
        slider("Start vertical pan",(value.startY*100).toInt()+100,200,{"${it-100}%"}){value=value.copy(startY=(it-100)/100f)}
        slider("End zoom",(value.endZoom*100).toInt()-100,200,{"${it+100}%"}){value=value.copy(endZoom=(it+100)/100f)}
        slider("End horizontal pan",(value.endX*100).toInt()+100,200,{"${it-100}%"}){value=value.copy(endX=(it-100)/100f)}
        slider("End vertical pan",(value.endY*100).toInt()+100,200,{"${it-100}%"}){value=value.copy(endY=(it-100)/100f)}
        panel.addView(TextView(a).apply{text="Pan −100% selects the left/top edge; +100% selects the right/bottom edge. At 100% zoom the whole picture remains visible.";setTextColor(Color.DKGRAY)})
        val note=TextView(a).apply{setTextColor(Color.DKGRAY)};panel.addView(note)
        val dialog=AlertDialog.Builder(a).setTitle("Motion pan and zoom").setView(ScrollView(a).apply{addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!dialog.isShowing||!isCurrent(dialog)){dialog.dismiss();return@setOnClickListener}
            runCatching{
                val edited=value.copy(enabled=enabled.isChecked,ease=MotionEase.entries[ease.selectedItemPosition])
                commit(dialog,if(clip.motion!=VideoMotion()&&edited==baseline)clip.motion else edited);dialog.dismiss()
            }.onFailure{note.text=it.message?:"Video changed. Open motion again."}
        }}
        return dialog
    }
}
