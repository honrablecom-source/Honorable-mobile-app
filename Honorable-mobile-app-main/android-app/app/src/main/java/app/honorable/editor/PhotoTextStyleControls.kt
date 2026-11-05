package app.honorable.editor

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputFilter
import android.widget.*
import kotlin.math.roundToInt

object PhotoTextStyleControls {
    /** Optional expanded controls; values are committed with the parent object as one edit. */
    fun add(a:Activity,parent:LinearLayout,initial:PhotoTextStyle):()->PhotoTextStyle {
        var value=initial
        val panel=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE}
        val toggle=Button(a).apply{text="Text appearance";setTextColor(Color.BLACK);setOnClickListener{panel.visibility=if(panel.visibility==android.view.View.VISIBLE)android.view.View.GONE else android.view.View.VISIBLE}}
        parent.addView(toggle);parent.addView(panel)
        panel.addView(TextView(a).apply{text="Uses built-in fonts. Outlines and shadows stay inside the object's frame. Increase the frame size for more room. Apply the object to preview these choices.";setTextColor(Color.DKGRAY)})
        fun choices(name:String,items:List<String>,selected:Int,change:(Int)->Unit){
            panel.addView(TextView(a).apply{text=name;setTextColor(Color.BLACK)})
            panel.addView(Spinner(a).apply{adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,items);setSelection(selected);contentDescription=name
                onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,view:android.view.View?,position:Int,id:Long){change(position)}}})
        }
        fun check(name:String,current:Boolean,change:(Boolean)->Unit){panel.addView(CheckBox(a).apply{text=name;isChecked=current;setTextColor(Color.BLACK);buttonTintList=ColorStateList.valueOf(Color.BLACK);setOnCheckedChangeListener{_,checked->change(checked)}})}
        fun slider(name:String,current:Int,maximum:Int,format:(Int)->String,change:(Int)->Unit){
            val label=TextView(a).apply{text="$name: ${format(current)}";setTextColor(Color.BLACK)};panel.addView(label)
            panel.addView(SeekBar(a).apply{max=maximum;progress=current.coerceIn(0,maximum);contentDescription=name;progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,p:Int,user:Boolean){label.text="$name: ${format(p)}";if(user)change(p)}})},LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
        }
        fun color(name:String,current:Int):EditText {
            panel.addView(TextView(a).apply{text="$name · #RRGGBB";setTextColor(Color.BLACK)})
            return EditText(a).apply{setText(PhotoEffectColors.format(current));contentDescription=name;isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(7));setTextColor(Color.BLACK);panel.addView(this)}
        }
        choices("Font family",listOf("Sans serif","Serif","Monospace"),value.family){value=value.copy(family=it)}
        choices("Text alignment",listOf("Start","Center","End"),value.alignment){value=value.copy(alignment=it)}
        check("Bold text",value.bold){value=value.copy(bold=it)};check("Italic text",value.italic){value=value.copy(italic=it)}
        slider("Letter spacing",(value.tracking*100).roundToInt()+5,55,{"${(it-5)/100f} em"}){value=value.copy(tracking=(it-5)/100f)}
        slider("Outline width",(value.outline*1000).roundToInt(),30,{"${it/10f}% of canvas edge"}){value=value.copy(outline=it/1000f)}
        val outline=color("Outline color",value.outlineColor)
        slider("Shadow blur",(value.shadow*1000).roundToInt(),40,{"${it/10f}% of canvas edge"}){value=value.copy(shadow=it/1000f)}
        slider("Shadow horizontal offset",(value.shadowX*1000).roundToInt()+50,100,{"${(it-50)/10f}%"}){value=value.copy(shadowX=(it-50)/1000f)}
        slider("Shadow vertical offset",(value.shadowY*1000).roundToInt()+50,100,{"${(it-50)/10f}%"}){value=value.copy(shadowY=(it-50)/1000f)}
        slider("Shadow opacity",((value.shadowColor ushr 24)*100/255f).roundToInt(),100,{"$it%"}){value=value.copy(shadowColor=((it*255/100) shl 24) or (value.shadowColor and 0xffffff))}
        val shadow=color("Shadow color",value.shadowColor)
        return {
            fun parsed(field:EditText,original:Int)=PhotoObjectColorInput.parse(field.text.toString(),original)?:run{
                field.error="Use #RRGGBB or six HEX digits"
                throw IllegalArgumentException("Use #RRGGBB or six HEX digits for outline and shadow colors")
            }
            value.copy(outlineColor=parsed(outline,value.outlineColor),shadowColor=parsed(shadow,value.shadowColor))
        }
    }
}
