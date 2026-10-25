package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputFilter
import android.widget.*

object PhotoEffectsControls {
    fun show(a:Activity,tool:PhotoEffectTool,initial:PhotoEffects,commit:(PhotoEffects)->Unit) {
        var value=initial
        val panel=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p/2,p,p/2)}
        fun slider(name:String,current:Int,maxValue:Int,label:(Int)->String,update:(Int)->Unit){
            val title=TextView(a).apply{text="$name: ${label(current)}";setTextColor(Color.BLACK)};panel.addView(title)
            panel.addView(SeekBar(a).apply{max=maxValue;progress=current;contentDescription=name
                progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                    override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){}
                    override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){title.text="$name: ${label(v)}";if(user)update(v)}
                })},LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
        }
        fun amount(name:String,current:Float,update:(Float)->Unit)=slider(name,(current*100).toInt(),100,{"$it%"}){update(it/100f)}
        fun enabled(name:String,checked:Boolean,update:(Boolean)->Unit)=CheckBox(a).apply{
            text=name;isChecked=checked;setTextColor(Color.BLACK);panel.addView(this);setOnCheckedChangeListener{_,on->update(on)}
        }
        val colors=mutableListOf<EditText>()
        when(tool){
            PhotoEffectTool.NEGATIVE->enabled("Invert colors",value.negative){value=value.copy(negative=it)}
            PhotoEffectTool.SOLARIZE->{var cut=value.solarize.takeIf{it>=0}?:.5f
                val check=enabled("Enable solarize",value.solarize>=0){value=value.copy(solarize=if(it)cut else -1f)}
                amount("Threshold",cut){cut=it;if(check.isChecked)value=value.copy(solarize=cut)}}
            PhotoEffectTool.SEPIA->amount("Sepia",value.sepia){value=value.copy(sepia=it)}
            PhotoEffectTool.DUOTONE->{amount("Strength",value.duotone){value=value.copy(duotone=it)}
                for((name,color) in listOf("Dark color" to value.dark,"Light color" to value.light)){
                    panel.addView(TextView(a).apply{text="$name · six-digit hex";setTextColor(Color.BLACK)})
                    colors.add(EditText(a).apply{setText(String.format(java.util.Locale.ROOT,"%06X",color and 0xFFFFFF));contentDescription=name;isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(6));setTextColor(Color.BLACK);panel.addView(this)})}}
            PhotoEffectTool.GAMMA->slider("Gamma",(value.gamma*100).toInt()-40,210,{String.format(java.util.Locale.ROOT,"%.2f",(it+40)/100f)}){value=value.copy(gamma=(it+40)/100f)}
            PhotoEffectTool.PIXELATE->{var size=value.pixelate.takeIf{it>0}?:12
                val check=enabled("Enable pixelate",value.pixelate>0){value=value.copy(pixelate=if(it)size else 0)}
                slider("Block size",size-2,62,{"${it+2}"}){size=it+2;if(check.isChecked)value=value.copy(pixelate=size)}
                panel.addView(TextView(a).apply{text="Creative effect only. Pixelation is not secure redaction.";setTextColor(Color.DKGRAY)})}
            PhotoEffectTool.BLUR->slider("Blur radius",value.blur,20,{"$it"}){value=value.copy(blur=it)}
            PhotoEffectTool.EDGES->amount("Outline strength",value.edges){value=value.copy(edges=it)}
            PhotoEffectTool.EMBOSS->amount("Emboss strength",value.emboss){value=value.copy(emboss=it)}
            PhotoEffectTool.CHROMATIC->slider("Color offset",value.chromatic+20,40,{"${it-20}"}){value=value.copy(chromatic=it-20)}
            PhotoEffectTool.HALFTONE->{var size=value.halftone.takeIf{it>0}?:10
                val check=enabled("Enable dots",value.halftone>0){value=value.copy(halftone=if(it)size else 0)}
                slider("Dot cell size",size-3,29,{"${it+3}"}){size=it+3;if(check.isChecked)value=value.copy(halftone=size)}}
            PhotoEffectTool.SCANLINES->amount("Line strength",value.scanlines){value=value.copy(scanlines=it)}
        }
        val dialog=AlertDialog.Builder(a).setTitle(tool.title).setView(ScrollView(a).apply{addView(panel)})
            .setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(colors.isNotEmpty()){
                val invalid=colors.firstOrNull{!it.text.toString().matches(Regex("[A-Fa-f0-9]{6}"))}
                if(invalid!=null){invalid.error="Use six hexadecimal digits, for example 14243A";return@setOnClickListener}
                value=value.copy(dark=Color.parseColor("#"+colors[0].text),light=Color.parseColor("#"+colors[1].text))
            }
            commit(value);dialog.dismiss()
        }};dialog.show()
    }
}
