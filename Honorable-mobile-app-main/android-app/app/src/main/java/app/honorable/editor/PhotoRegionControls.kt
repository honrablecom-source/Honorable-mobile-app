package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.*
import java.util.UUID

object PhotoRegionControls {
    private fun label(effect:PhotoRegionEffect)=when(effect){PhotoRegionEffect.PIXELATE->"Pixelate";PhotoRegionEffect.BLUR->"Soft blur";PhotoRegionEffect.GRAYSCALE->"Grayscale"}
    fun show(activity:Activity,initial:PhotoRegions,commit:(PhotoRegions)->Unit){
        val labels=listOf("Add local effect region")+initial.values.mapIndexed{index,value->"${index+1}. ${label(value.effect)} · ${value.shape.name.lowercase()}${if(value.enabled)""else" · disabled"}"}
        AlertDialog.Builder(activity).setTitle("Local effect regions · ${initial.values.size}/8").setItems(labels.toTypedArray()){_,index->
            if(index==0){
                if(initial.values.size>=8){Toast.makeText(activity,"Use up to eight regions",Toast.LENGTH_SHORT).show();return@setItems}
                edit(activity,initial,PhotoRegion(UUID.randomUUID().toString(),PhotoRegionEffect.PIXELATE),true,commit)
            }else{
                val value=initial.values[index-1]
                AlertDialog.Builder(activity).setTitle("Region $index").setItems(arrayOf("Edit region",if(value.enabled)"Disable region"else"Enable region","Delete region")){_,action->
                    if(action==0)edit(activity,initial,value,false,commit)else runCatching{commit(if(action==1)initial.update(value.copy(enabled=!value.enabled))else initial.delete(value.id))}.onFailure{Toast.makeText(activity,it.message?:"Photo changed",Toast.LENGTH_LONG).show()}
                }.setNegativeButton("Cancel",null).show()
            }
        }.setNegativeButton("Close",null).show()
    }
    private fun edit(a:Activity,initial:PhotoRegions,original:PhotoRegion,adding:Boolean,commit:(PhotoRegions)->Unit){
        var value=original
        val panel=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p/2,p,p/2)}
        fun caption(text:String){panel.addView(TextView(a).apply{this.text=text;setTextColor(Color.DKGRAY)})}
        caption("Apply pixelation, blur or grayscale inside a finished-image rectangle or ellipse. Regions process in list order, before text and shape annotations. Original files stay unchanged; preview your exported copy. Apply is one undoable change.")
        caption("Effect")
        val effect=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,PhotoRegionEffect.entries.map(::label));contentDescription="Local region effect";setSelection(value.effect.ordinal)};panel.addView(effect)
        caption("Region shape")
        val shape=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf("Rectangle","Ellipse"));contentDescription="Local region shape";setSelection(value.shape.ordinal)};panel.addView(shape)
        fun slider(name:String,initial:Int,maxValue:Int,minimum:Int=0,change:(Int)->Unit){
            val title=TextView(a).apply{text="$name: $initial%";setTextColor(Color.BLACK)};panel.addView(title)
            panel.addView(SeekBar(a).apply{max=maxValue;progress=initial;contentDescription=name;progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(bar:SeekBar){};override fun onStopTrackingTouch(bar:SeekBar){};override fun onProgressChanged(bar:SeekBar,progress:Int,user:Boolean){val actual=progress.coerceAtLeast(minimum);title.text="$name: $actual%";if(progress!=actual)bar.progress=actual;if(user)change(actual)}})
            },LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
        }
        slider("Region horizontal center",(value.x*100).toInt(),100){value=value.copy(x=it/100f)}
        slider("Region vertical center",(value.y*100).toInt(),100){value=value.copy(y=it/100f)}
        slider("Region width",(value.width*100).toInt(),100,2){value=value.copy(width=it/100f)}
        slider("Region height",(value.height*100).toInt(),100,2){value=value.copy(height=it/100f)}
        slider("Region strength",(value.strength*100).toInt(),100,1){value=value.copy(strength=it/100f)}
        slider("Region edge feather",(value.feather*100).toInt(),50){value=value.copy(feather=it/100f)}
        caption("Width/height minimum: 2%; strength minimum: 1%. Feather softens the boundary. Pixelation and blur are appearance effects; review coverage before sharing.")
        val note=TextView(a).apply{setTextColor(Color.DKGRAY)};panel.addView(note)
        val dialog=AlertDialog.Builder(a).setTitle(if(adding)"Add local effect"else"Edit local effect").setView(ScrollView(a).apply{addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{runCatching{
            val changed=value.copy(effect=PhotoRegionEffect.entries[effect.selectedItemPosition],shape=PhotoRegionShape.entries[shape.selectedItemPosition])
            commit(if(adding)initial.add(changed)else initial.update(changed));dialog.dismiss()
        }.onFailure{note.text=it.message?:"Photo changed. Reopen the local effect controls."}}};dialog.show()
    }
}
