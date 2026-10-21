package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.*
import android.text.InputFilter

object PhotoFinishControls {
    private fun panel(a:Activity)=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16)}
    fun adjust(a:Activity,title:String,initial:PhotoFinish,kind:Int,commit:(PhotoFinish)->Unit){
        var f=initial;val p=panel(a)
        fun slider(name:String,initialValue:Float,signed:Boolean,change:(Float)->Unit){
            val label=TextView(a).apply{setTextColor(Color.BLACK)};p.addView(label)
            p.addView(SeekBar(a).apply{max=if(signed)200 else 100;progress=(initialValue*100).toInt()+if(signed)100 else 0;contentDescription=name
                progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
                fun caption(v:Int){label.text="$name: ${v-if(signed)100 else 0}%"};caption(progress)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){caption(v);if(user)change((v-if(signed)100 else 0)/100f)}})
            },LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
        }
        when(kind){
            0->{slider("Warmth",f.warmth,true){f=f.copy(warmth=it)};slider("Tint",f.tint,true){f=f.copy(tint=it)}}
            1->{slider("Shadows",f.shadows,true){f=f.copy(shadows=it)};slider("Highlights",f.highlights,true){f=f.copy(highlights=it)}}
            2->slider("Vignette",f.vignette,false){f=f.copy(vignette=it)}
            3->slider("Sharpen",f.sharpen,false){f=f.copy(sharpen=it)}
        }
        AlertDialog.Builder(a).setTitle(title).setView(p).setNegativeButton("Cancel",null).setPositiveButton("Apply"){_,_->commit(f)}.show()
    }
    fun caption(a:Activity,initial:PhotoCaption?,commit:(PhotoCaption?)->Unit){
        val p=panel(a);val text=EditText(a).apply{setText(initial?.text.orEmpty());hint="Add text";setTextColor(Color.BLACK);filters=arrayOf(InputFilter.LengthFilter(120));maxLines=3};p.addView(text)
        fun choices(label:String,items:List<String>,selected:Int):Spinner {p.addView(TextView(a).apply{this.text=label;setTextColor(Color.BLACK)});return Spinner(a).also{it.adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,items);it.setSelection(selected);p.addView(it)}}
        val positions=listOf(.15f,.5f,.85f);val sizes=listOf(.035f,.06f,.10f)
        val position=choices("Position",listOf("Top","Center","Bottom"),positions.indexOf(initial?.y?:.85f).coerceAtLeast(0))
        val size=choices("Size",listOf("Small","Medium","Large"),sizes.indexOf(initial?.size?:.06f).coerceAtLeast(0))
        val light=CheckBox(a).apply{this.text="White text";isChecked=initial?.light?:true;setTextColor(Color.BLACK)};p.addView(light)
        val plate=CheckBox(a).apply{this.text="Contrast backplate";isChecked=initial?.plate?:true;setTextColor(Color.BLACK)};p.addView(plate)
        val d=AlertDialog.Builder(a).setTitle("Photo text").setView(ScrollView(a).apply{addView(p)}).setNegativeButton("Cancel",null).setNeutralButton("Remove"){_,_->commit(null)}.setPositiveButton("Apply",null).create()
        d.setOnShowListener{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            runCatching{PhotoCaption(text.text.toString(),y=positions[position.selectedItemPosition],size=sizes[size.selectedItemPosition],light=light.isChecked,plate=plate.isChecked)}
                .onSuccess{commit(it);d.dismiss()}.onFailure{text.error="Use 1–120 characters and up to three lines."}
        }};d.show()
    }
}
