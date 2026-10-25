package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputFilter
import android.widget.*

object PhotoCompositionControls {
    fun show(a:Activity,tool:PhotoCompositionTool,initial:PhotoComposition,commit:(PhotoComposition)->Unit) {
        var value=initial
        val p=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;val n=(20*resources.displayMetrics.density).toInt();setPadding(n,n/2,n,n/2)}
        fun note(text:String){p.addView(TextView(a).apply{this.text=text;setTextColor(Color.DKGRAY)})}
        fun slider(name:String,current:Int,maximum:Int,format:(Int)->String={it.toString()},update:(Int)->Unit){
            val label=TextView(a).apply{text="$name: ${format(current)}";setTextColor(Color.BLACK)};p.addView(label)
            p.addView(SeekBar(a).apply{max=maximum;progress=current;contentDescription=name;progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){label.text="$name: ${format(v)}";if(user)update(v)}})},LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
        }
        fun check(name:String,current:Boolean,update:(Boolean)->Unit){p.addView(CheckBox(a).apply{text=name;isChecked=current;setTextColor(Color.BLACK);setOnCheckedChangeListener{_,on->update(on)}})}
        var color:EditText?=null;var text:EditText?=null
        fun colorField(current:Int){note("Output color · six-digit hex");color=EditText(a).apply{setText(String.format(java.util.Locale.ROOT,"%06X",current and 0xFFFFFF));isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(6));contentDescription="Output color";setTextColor(Color.BLACK);p.addView(this)}}
        when(tool){
            PhotoCompositionTool.CANVAS->{slider("Padding percent",(value.padding*100).toInt(),40){value=value.copy(padding=it/100f)};check("Transparent canvas",value.canvasColor ushr 24==0){value=value.copy(canvasColor=if(it)0 else -1)};colorField(value.canvasColor);note("Adds solid or transparent space. No generated fill. The photo may scale down to stay within its export pixel budget.")}
            PhotoCompositionTool.FRAME->{slider("Frame percent",(value.frame*100).toInt(),12){value=value.copy(frame=it/100f)};colorField(value.frameColor)}
            PhotoCompositionTool.SHADOW->slider("Shadow percent",(value.shadow*100).toInt(),100){value=value.copy(shadow=it/100f)}
            PhotoCompositionTool.REFLECTION->slider("Reflection height percent",(value.reflection*100).toInt(),50){value=value.copy(reflection=it/100f)}
            PhotoCompositionTool.SHAPE->{val items=listOf("No mask","Circle","Rounded corners","Diamond","Heart");val s=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,items);setSelection(value.shape);contentDescription="Cutout shape"};p.addView(s);s.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(v:android.widget.AdapterView<*>?){};override fun onItemSelected(v:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){value=value.copy(shape=position)}}}
            PhotoCompositionTool.TILES->{slider("Tiles per side",value.tiles-1,5,{(it+1).toString()}){value=value.copy(tiles=it+1)};note("Creates a repeated pattern from this photo; one returns to the original layout.")}
            PhotoCompositionTool.KALEIDOSCOPE->{val s=Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf("Off","2 sectors","4 sectors","6 sectors","8 sectors","12 sectors"));setSelection(listOf(0,2,4,6,8,12).indexOf(value.sectors).coerceAtLeast(0));contentDescription="Kaleidoscope sectors"};p.addView(s);s.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(v:android.widget.AdapterView<*>?){};override fun onItemSelected(v:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){value=value.copy(sectors=listOf(0,2,4,6,8,12)[position])}}}
            PhotoCompositionTool.PERSPECTIVE->{slider("Perspective percent",(value.perspective*100).toInt()+40,80,{(it-40).toString()}){value=value.copy(perspective=(it-40)/100f)};note("Pinches the top or bottom edge into a poster perspective. It does not repair lens distortion.")}
            PhotoCompositionTool.WATERMARK->{note("Repeated text · leave empty to remove");text=EditText(a).apply{setText(value.watermark);isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(60));contentDescription="Watermark text";setTextColor(Color.BLACK);p.addView(this)};slider("Opacity percent",(value.watermarkOpacity*100).toInt()-5,55,{(it+5).toString()}){value=value.copy(watermarkOpacity=(it+5)/100f)};check("White watermark",value.watermarkLight){value=value.copy(watermarkLight=it)}}
            PhotoCompositionTool.COLOR_KEY->{check("Remove matching color",value.keyColor!=null){value=value.copy(keyColor=if(it)value.keyColor?:0x00FF00 else null)};colorField(value.keyColor?:0x00FF00);slider("Color tolerance percent",(value.keyTolerance*100).toInt()-2,78,{(it+2).toString()}){value=value.copy(keyTolerance=(it+2)/100f)};note("Color-based transparency, not subject recognition or secure redaction. Use PNG to retain transparency.")}
        }
        note("Preview and saved copies use these same settings. Reset this tool or undo to restore its previous state.")
        val d=AlertDialog.Builder(a).setTitle(tool.title).setView(ScrollView(a).apply{addView(p)}).setNegativeButton("Cancel",null).setNeutralButton("Reset tool",null).setPositiveButton("Apply",null).create()
        d.setOnShowListener{
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{
                val reset=when(tool){PhotoCompositionTool.CANVAS->initial.copy(padding=0f);PhotoCompositionTool.FRAME->initial.copy(frame=0f);PhotoCompositionTool.SHADOW->initial.copy(shadow=0f);PhotoCompositionTool.REFLECTION->initial.copy(reflection=0f);PhotoCompositionTool.SHAPE->initial.copy(shape=0);PhotoCompositionTool.TILES->initial.copy(tiles=1);PhotoCompositionTool.KALEIDOSCOPE->initial.copy(sectors=0);PhotoCompositionTool.PERSPECTIVE->initial.copy(perspective=0f);PhotoCompositionTool.WATERMARK->initial.copy(watermark="");PhotoCompositionTool.COLOR_KEY->initial.copy(keyColor=null)}
                commit(reset);d.dismiss()
            }
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val field=color
                if(field!=null && !field.text.toString().matches(Regex("[A-Fa-f0-9]{6}"))){field.error="Use six hexadecimal digits";return@setOnClickListener}
                val rgb=field?.text?.toString()?.toInt(16)
                if(rgb!=null)value=when(tool){PhotoCompositionTool.CANVAS->value.copy(canvasColor=if(value.canvasColor ushr 24==0)0 else rgb or -0x1000000);PhotoCompositionTool.FRAME->value.copy(frameColor=rgb or -0x1000000);PhotoCompositionTool.COLOR_KEY->value.copy(keyColor=if(value.keyColor==null)null else rgb);else->value}
                val result=runCatching{value.copy(watermark=text?.text?.toString()?:value.watermark)}
                if(result.isFailure){text?.error="Use up to 60 characters / 180 UTF-8 bytes, without control characters";return@setOnClickListener}
                commit(result.getOrThrow());d.dismiss()
            }
        };d.show()
    }
}
