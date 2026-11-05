package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.*
import java.util.Locale
import kotlin.math.roundToInt

object PhotoMonochromeControls{
 fun show(a:Activity,initial:PhotoMonochrome,commit:(PhotoMonochrome)->Unit){
  var value=initial
  val body=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p/2,p,p/2)}
  body.addView(TextView(a).apply{text="Set how much red, green, and blue contribute to black-and-white tones. Contributions are normalized; white stays white. All zeros produce black. For tonal separation, start with color input; existing color adjustments still apply. Transparency is kept. Added backgrounds, text, and shapes keep their colors; later local edits may add color. Apply makes one Undo step and keeps originals unchanged.";setTextColor(Color.DKGRAY)})
  val enabled=CheckBox(a).apply{text="Enable custom black-and-white mix";setTextColor(Color.BLACK);isChecked=value.enabled;buttonTintList=ColorStateList.valueOf(Color.BLACK);body.addView(this)}
  enabled.setOnCheckedChangeListener{_,checked->value=value.copy(enabled=checked)}
  fun slider(name:String,start:Float,update:(Float)->Unit){
   fun percent(v:Float)=String.format(Locale.ROOT,"%.1f%%",v*100)
   val label=TextView(a).apply{text="$name contribution: ${percent(start)}";setTextColor(Color.BLACK)};body.addView(label)
   body.addView(SeekBar(a).apply{max=1000;progress=(start*1000).roundToInt();contentDescription="$name contribution";progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
    setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(bar:SeekBar){};override fun onStopTrackingTouch(bar:SeekBar){};override fun onProgressChanged(bar:SeekBar,next:Int,user:Boolean){if(user){update(next/1000f);label.text="$name contribution: ${percent(next/1000f)}"}}})
   },LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
  }
  slider("Red",value.red){value=value.copy(red=it)};slider("Green",value.green){value=value.copy(green=it)};slider("Blue",value.blue){value=value.copy(blue=it)}
  val note=TextView(a).apply{setTextColor(Color.DKGRAY)};body.addView(note)
  val dialog=AlertDialog.Builder(a).setTitle("Black-and-white channel mix").setView(ScrollView(a).apply{setBackgroundColor(Color.WHITE);addView(body)}).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
  dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   if(a.isFinishing||a.isDestroyed){dialog.dismiss();return@setOnClickListener}
   runCatching{commit(value)}.onSuccess{dialog.dismiss()}.onFailure{note.text=it.message?:"Mix could not be applied. Existing edits were kept."}
  }};dialog.show()
 }
}
