package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.*

internal object VideoInspectorControls {
    private fun panel(a:Activity)=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16)}
    private fun choices(a:Activity,p:LinearLayout,label:String,items:List<String>,selected:Int):Spinner{
        p.addView(TextView(a).apply{text=label;setTextColor(Color.BLACK)})
        return Spinner(a).also{it.adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,items);it.setSelection(selected);p.addView(it)}
    }
    fun format(a:Activity,initial:VideoLayout,commit:(Int,Boolean)->Unit){
        val p=panel(a)
        val ratio=choices(a,p,"Canvas",listOf("Original","Square 1:1","Vertical 9:16","Wide 16:9","Portrait 4:5"),initial.ratio)
        val crop=CheckBox(a).apply{text="Fill frame with a center crop";isChecked=initial.crop;setTextColor(Color.BLACK)};p.addView(crop)
        p.addView(TextView(a).apply{text="Unchecked fits the whole image with black bars. Applies to every clip.";setTextColor(Color.DKGRAY)})
        AlertDialog.Builder(a).setTitle("Output format").setView(p).setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->commit(ratio.selectedItemPosition,crop.isChecked)}.show()
    }
    fun title(a:Activity,initial:VideoTitleStyle,commit:(VideoTitleStyle)->Unit){
        val p=panel(a)
        val position=choices(a,p,"Position",listOf("Top","Center","Bottom"),initial.position)
        val sizes=listOf(.035f,.055f,.08f)
        val size=choices(a,p,"Text size",listOf("Small","Medium","Large"),sizes.indexOf(initial.size).coerceAtLeast(0))
        val light=CheckBox(a).apply{text="White text";isChecked=initial.light;setTextColor(Color.BLACK)};p.addView(light)
        val plate=CheckBox(a).apply{text="Contrast backplate";isChecked=initial.plate;setTextColor(Color.BLACK)};p.addView(plate)
        AlertDialog.Builder(a).setTitle("Title style").setView(p).setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->commit(VideoTitleStyle(position.selectedItemPosition,sizes[size.selectedItemPosition],light.isChecked,plate.isChecked))}.show()
    }
}
