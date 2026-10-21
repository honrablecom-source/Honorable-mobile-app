package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.*

/** A phone inspector over the current canvas, with real tool callbacks. */
object NativeInspectorPanel {
    data class Tool(val label:String,val run:()->Unit)
    fun show(a:Activity,title:String,summary:String,sections:List<Pair<String,List<Tool>>>){
        fun dp(v:Int)=(v*a.resources.displayMetrics.density).toInt()
        val content=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(4),dp(16),dp(12));setBackgroundColor(Color.WHITE)}
        content.addView(TextView(a).apply{text=summary;textSize=12f;setTextColor(Color.DKGRAY);setPadding(0,dp(8),0,dp(12))})
        lateinit var dialog:AlertDialog
        for((name,tools)in sections){
            content.addView(TextView(a).apply{text=name;textSize=12f;setTextColor(Color.BLACK);setPadding(0,dp(16),0,dp(6))})
            for(group in tools.chunked(2)){
                val row=LinearLayout(a);content.addView(row)
                for(tool in group)row.addView(Button(a).apply{text=tool.label;isAllCaps=false;textSize=12f;setTextColor(Color.BLACK);background=GradientDrawable().apply{setColor(Color.WHITE);setStroke(dp(1),0xFFD4D4D4.toInt());cornerRadius=dp(8).toFloat()};setOnClickListener{dialog.dismiss();tool.run()}},LinearLayout.LayoutParams(0,dp(52),1f).apply{setMargins(dp(2),dp(3),dp(2),dp(3))})
                if(group.size==1)row.addView(Space(a),LinearLayout.LayoutParams(0,dp(52),1f))
            }
        }
        val scroll=ScrollView(a).apply{addView(content)}
        dialog=AlertDialog.Builder(a).setTitle(title).setView(scroll).setNegativeButton("Close",null).create()
        dialog.show();scroll.layoutParams=scroll.layoutParams.apply{height=(a.resources.displayMetrics.heightPixels*.6f).toInt()}
    }
}
