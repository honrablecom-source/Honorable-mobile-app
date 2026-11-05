package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.*
import java.util.Locale

object PhotoHistogramControls {
 fun show(activity:Activity,width:Int,height:Int,stats:PhotoHistogram){
  val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p,p,p);setBackgroundColor(Color.WHITE)}
  fun text(value:String){body.addView(TextView(activity).apply{text=value;setTextColor(Color.BLACK);textSize=15f;setPadding(0,8,0,8)})}
  fun percent(count:Long)=if(stats.visible==0L)"0.0%" else String.format(Locale.ROOT,"%.1f%%",100.0*count/stats.visible)
  text("Edited preview: $width × $height px. Includes visible backgrounds, text and shapes. This is a snapshot, not a full-resolution export measurement. Original photo stays unchanged.")
  text("${stats.visible} nontransparent pixels; ${stats.transparent} fully transparent pixels excluded. ${stats.partialAlpha} partly transparent pixels count equally, before compositing onto a background.")
  if(stats.visible==0L)text("No nontransparent pixels to measure.") else {
   text("Encoded sRGB luma (0–255): darkest ${stats.minimum}, lightest ${stats.maximum}, average ${String.format(Locale.ROOT,"%.1f",stats.mean)}. Bins run from dark at the top to light at the bottom. Bars use the same scale.")
   val peak=stats.bins.maxOrNull()?:0L
   stats.bins.forEachIndexed{index,count->
    val label="${index*16}–${index*16+15}: $count pixels (${percent(count)})"
    text(label)
    body.addView(ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply{max=1000;progress=if(peak==0L)0 else (count.toDouble()/peak*1000).toInt();progressTintList=ColorStateList.valueOf(Color.BLACK);progressBackgroundTintList=ColorStateList.valueOf(Color.LTGRAY);contentDescription=label},LinearLayout.LayoutParams(-1,(8*activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)))
   }
   text("Channel endpoints (not proof of lost detail):")
   listOf("Red","Green","Blue").forEachIndexed{index,name->text("$name: 0 = ${percent(stats.channelZero[index])}; 255 = ${percent(stats.channelFull[index])}")}
  }
  text("Luma uses encoded RGB weights, not linear-light luminance. Endpoint values can be intentional artwork or solid fills. Export size, background compositing and JPEG compression may change this distribution.")
  AlertDialog.Builder(activity).setTitle("Preview tonal histogram").setView(ScrollView(activity).apply{addView(body)}).setPositiveButton("Close",null).show()
 }
}
