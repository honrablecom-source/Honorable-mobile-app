package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.*

internal object VideoTitleSizeChoices {
    fun values(style:VideoTitleStyle):List<Float>{
        val presets=listOf(.035f,.055f,.08f)
        return if(style.size in presets)presets else presets+style.size
    }
}

internal object VideoInspectorControls {
    private fun panel(a:Activity)=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16)}
    private fun choices(a:Activity,p:LinearLayout,label:String,items:List<String>,selected:Int):Spinner{
        p.addView(TextView(a).apply{text=label;setTextColor(Color.BLACK)})
        return Spinner(a).also{it.adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,items);it.setSelection(selected);p.addView(it)}
    }
    fun format(a:Activity,initial:VideoLayout,commit:(Int,Boolean)->Unit){
        createFormat(a,initial,{it.isShowing}){_,ratio,crop->commit(ratio,crop)}.show()
    }
    fun createFormat(a:Activity,initial:VideoLayout,isCurrent:(AlertDialog)->Boolean,commit:(AlertDialog,Int,Boolean)->Unit):AlertDialog {
        val p=panel(a)
        val ratio=choices(a,p,"Canvas",listOf("Original","Square 1:1","Vertical 9:16","Wide 16:9","Portrait 4:5"),initial.ratio)
        val crop=CheckBox(a).apply{text="Fill frame with a center crop";isChecked=initial.crop;setTextColor(Color.BLACK)};p.addView(crop)
        p.addView(TextView(a).apply{text="Unchecked fits the whole image with black bars. Applies to every clip.";setTextColor(Color.DKGRAY)})
        lateinit var dialog:AlertDialog
        dialog=AlertDialog.Builder(a).setTitle("Output format").setView(p).setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->
            if(dialog.isShowing&&isCurrent(dialog))commit(dialog,ratio.selectedItemPosition,crop.isChecked)
        }.create()
        return dialog
    }
    fun title(a:Activity,initial:VideoTitleStyle,commit:(VideoTitleStyle)->Unit){
        createTitle(a,initial,{it.isShowing}){_,style->commit(style)}.show()
    }
    fun createTitle(a:Activity,initial:VideoTitleStyle,isCurrent:(AlertDialog)->Boolean,commit:(AlertDialog,VideoTitleStyle)->Unit):AlertDialog {
        val p=panel(a)
        val position=choices(a,p,"Position",listOf("Top","Center","Bottom"),initial.position)
        val sizes=VideoTitleSizeChoices.values(initial)
        val sizeLabels=listOf("Small","Medium","Large")+sizes.drop(3).map{String.format(java.util.Locale.ROOT,"Keep current (%.1f%%)",it*100)}
        val size=choices(a,p,"Text size",sizeLabels,sizes.indexOf(initial.size))
        val light=CheckBox(a).apply{text="White text";isChecked=initial.light;setTextColor(Color.BLACK)};p.addView(light)
        val plate=CheckBox(a).apply{text="Contrast backplate";isChecked=initial.plate;setTextColor(Color.BLACK)};p.addView(plate)
        lateinit var dialog:AlertDialog
        dialog=AlertDialog.Builder(a).setTitle("Title style").setView(p).setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->
            if(dialog.isShowing&&isCurrent(dialog))commit(dialog,VideoTitleStyle(position.selectedItemPosition,sizes[size.selectedItemPosition],light.isChecked,plate.isChecked))
        }.create()
        return dialog
    }
}
