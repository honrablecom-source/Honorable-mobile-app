package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.PersistableBundle
import android.widget.*
import java.util.Locale

object PhotoPaletteControls {
 fun show(activity:Activity,width:Int,height:Int,palette:PhotoPalette,canCopy:()->Boolean){
  val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;val p=(20*resources.displayMetrics.density).toInt();setPadding(p,p,p,p);setBackgroundColor(Color.WHITE)}
  fun text(value:String)=TextView(activity).apply{text=value;setTextColor(Color.BLACK);textSize=15f;setPadding(0,8,0,8);body.addView(this)}
  fun percent(weight:Long)=if(palette.totalAlphaWeight==0L)"0.0%" else String.format(Locale.ROOT,"%.1f%%",100.0*weight/palette.totalAlphaWeight)
  text("Edited preview: $width × $height px. Up to eight approximate RGB color groups, ordered by alpha-weighted coverage. Includes visible text, shapes and backgrounds; original photo stays unchanged.")
  text("${palette.visible} nontransparent pixels measured; ${palette.transparent} transparent pixels excluded. Partly transparent pixels contribute by opacity. Colors are measured before compositing onto a new background.")
  palette.swatches.forEach{swatch->
   val label="${swatch.hex()} · ${percent(swatch.alphaWeight)} of measured coverage"
   val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,8,0,8)}
   row.addView(TextView(activity).apply{setBackgroundColor(swatch.color);contentDescription="Color ${swatch.hex()}"},LinearLayout.LayoutParams((40*activity.resources.displayMetrics.density).toInt(),(40*activity.resources.displayMetrics.density).toInt()))
   row.addView(TextView(activity).apply{text=label;setTextColor(Color.BLACK);setTextIsSelectable(true);setPadding(12,0,0,0)},LinearLayout.LayoutParams(0,-2,1f));body.addView(row)
  }
  if(palette.swatches.isEmpty())text("No nontransparent colors to extract.")
  else text("Shown groups cover ${percent(palette.swatches.sumOf{it.alphaWeight})}; remaining colors are omitted. RGB channels are grouped in steps of8 and averaged within each group. Similar-looking colors can remain separate. This is not perceptual clustering or a full-resolution export measurement.")
  text("Copy HEX codes only when you want to reuse these colors. Preview size, transparency compositing and export encoding can change the palette. This tool does not upload photos or palettes or add them to saved projects.")
  val status=text("")
  val builder=AlertDialog.Builder(activity).setTitle("Extract preview palette").setView(ScrollView(activity).apply{addView(body)}).setNegativeButton("Close",null)
  if(palette.swatches.isNotEmpty())builder.setPositiveButton("Copy HEX codes",null)
  val dialog=builder.create()
  if(palette.swatches.isNotEmpty())dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   if(activity.isFinishing||activity.isDestroyed||!runCatching{canCopy()}.getOrDefault(false)){status.text="Photo or session changed. Close and extract a fresh palette.";return@setOnClickListener}
   runCatching{
    val clip=ClipData.newPlainText("Noble photo palette",palette.hexText())
    if(Build.VERSION.SDK_INT>=33)clip.description.extras=PersistableBundle().apply{putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true)}
    (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
   }.onSuccess{status.text="HEX codes copied. Paste one code into an object color field, with or without #."}.onFailure{status.text="Clipboard unavailable. You can select and copy individual HEX labels."}
  }}
  dialog.show()
 }
}
