package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.*
import java.util.Locale
import kotlin.math.roundToInt

object PhotoAlphaGradientControls {
 fun show(activity:Activity,initial:PhotoAlphaGradient,commit:(PhotoAlphaGradient)->Unit){
  var enabled=initial.enabled;var angle=initial.angle;var start=initial.start;var end=initial.end;var reverse=initial.reverse
  val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)}
  body.addView(TextView(activity).apply{setTextColor(Color.DKGRAY);text="Fade the framed photo and any person background to transparency. Added canvas fills, frames, captions and objects keep their own opacity. Direction follows photo-relative axes: 0° left to right; 90° top to bottom. Start is transparent; end is opaque. Reverse swaps sides. PNG keeps transparency; JPEG uses white. Apply updates the preview as one Undo step. Editable photo projects/recovery keep this fade; global saved looks do not transfer it."})
  fun check(label:String,initialValue:Boolean,update:(Boolean)->Unit){body.addView(CheckBox(activity).apply{text=label;setTextColor(Color.BLACK);buttonTintList=ColorStateList.valueOf(Color.BLACK);isChecked=initialValue;setOnCheckedChangeListener{_,value->update(value)}})}
  check("Enable transparency fade",enabled){enabled=it}
  fun slider(name:String,value:Float,steps:Int,scale:Float,format:(Float)->String,update:(Float)->Unit){
   val label=TextView(activity).apply{text="$name: ${format(value)}";setTextColor(Color.BLACK)};body.addView(label)
   body.addView(SeekBar(activity).apply{max=steps;progress=(value*scale).roundToInt();contentDescription=name;progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
    setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(bar:SeekBar){};override fun onStopTrackingTouch(bar:SeekBar){};override fun onProgressChanged(bar:SeekBar,next:Int,user:Boolean){if(user){val v=next/scale;update(v);label.text="$name: ${format(v)}"}}})
   },LinearLayout.LayoutParams(-1,(48*activity.resources.displayMetrics.density).toInt()))
  }
  slider("Direction",angle,360,1f,{String.format(Locale.ROOT,"%.1f°",it)}){angle=it}
  slider("Fade start",start,1000,1000f,{String.format(Locale.ROOT,"%.1f%%",it*100)}){start=it}
  slider("Fade end",end,1000,1000f,{String.format(Locale.ROOT,"%.1f%%",it*100)}){end=it}
  check("Reverse transparent and opaque sides",reverse){reverse=it}
  val status=TextView(activity).apply{setTextColor(Color.DKGRAY)};body.addView(status)
  val dialog=AlertDialog.Builder(activity).setTitle("Fade to transparency").setView(ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(body)}).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
  dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   if(activity.isFinishing||activity.isDestroyed){dialog.dismiss();return@setOnClickListener}
   runCatching{commit(PhotoAlphaGradient(enabled,angle,start,end,reverse))}.onSuccess{dialog.dismiss()}.onFailure{status.text=it.message?:"Fade could not be applied. Existing edits were kept."}
  }};dialog.show()
 }
}
