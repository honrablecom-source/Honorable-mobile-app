package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.*

object PhotoSelectiveColorControls{
 fun show(a:Activity,initial:PhotoSelectiveColor,commit:(PhotoSelectiveColor)->Unit){
  var value=initial
  val panel=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p/2,p,p/2)}
  panel.addView(TextView(a).apply{text="Choose a circular hue range, then shift its hue, saturation or exposure. Neutral colors and pixels outside the range stay unchanged. This adjusts photo pixels before background replacement and annotations; it does not select objects or identify subjects. Apply makes one undoable change.";setTextColor(Color.DKGRAY)})
  val enabled=CheckBox(a).apply{text="Enable selective color range";isChecked=value.enabled;setTextColor(Color.BLACK);buttonTintList=ColorStateList.valueOf(Color.BLACK)};panel.addView(enabled)
  val swatch=TextView(a).apply{text="Target hue";setPadding(12,12,12,12)};panel.addView(swatch)
  fun hueSwatch(){swatch.setBackgroundColor(Color.HSVToColor(floatArrayOf(value.hue%360,1f,1f)));swatch.setTextColor(if(value.hue in 45f..180f)Color.BLACK else Color.WHITE)}
  hueSwatch()
  fun slider(name:String,start:Int,maxValue:Int,display:(Int)->String,change:(Int)->Unit){
   val title=TextView(a).apply{text="$name: ${display(start)}";setTextColor(Color.BLACK)};panel.addView(title)
   panel.addView(SeekBar(a).apply{max=maxValue;progress=start;contentDescription=name;progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(bar:SeekBar){};override fun onStopTrackingTouch(bar:SeekBar){};override fun onProgressChanged(bar:SeekBar,v:Int,user:Boolean){title.text="$name: ${display(v)}";if(user)change(v)}})},LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
  }
  slider("Target hue",value.hue.toInt(),360,{"$it°"}){value=value.copy(hue=it.toFloat());hueSwatch()}
  slider("Hue range radius",value.radius.toInt()-1,179,{"${it+1}°"}){value=value.copy(radius=(it+1).toFloat())}
  slider("Range softness",(value.softness*100).toInt(),100,{"$it%"}){value=value.copy(softness=it/100f)}
  slider("Selected hue shift",value.shift.toInt()+180,360,{"${it-180}°"}){value=value.copy(shift=(it-180).toFloat())}
  slider("Selected saturation",(value.saturation*100).toInt(),200,{"$it%"}){value=value.copy(saturation=it/100f)}
  slider("Selected exposure",(value.exposure*100).toInt()+200,400,{"${(it-200)/100f} stops"}){value=value.copy(exposure=(it-200)/100f)}
  val note=TextView(a).apply{setTextColor(Color.DKGRAY)};panel.addView(note)
  val dialog=AlertDialog.Builder(a).setTitle("Selective color range").setView(ScrollView(a).apply{addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
  dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{runCatching{commit(value.copy(enabled=enabled.isChecked));dialog.dismiss()}.onFailure{note.text=it.message?:"Photo changed. Open selective color again."}}};dialog.show()
 }
}
