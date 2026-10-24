package app.honorable.editor

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.*

/** Bottom-anchored scrollable inspector. Every tile invokes the existing editor action. */
object NativeInspectorPanel {
    data class Tool(val label:String,val run:()->Unit)
    fun show(a:Activity,title:String,summary:String,sections:List<Pair<String,List<Tool>>>){
        fun dp(v:Int)=(v*a.resources.displayMetrics.density).toInt()
        val dialog=Dialog(a).apply{requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(true)}
        val root=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(10),dp(20),dp(20));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadii=floatArrayOf(dp(26).toFloat(),dp(26).toFloat(),dp(26).toFloat(),dp(26).toFloat(),0f,0f,0f,0f)}}
        root.addView(android.view.View(a).apply{background=GradientDrawable().apply{setColor(0xFFD5D5D5.toInt());cornerRadius=dp(2).toFloat()}},LinearLayout.LayoutParams(dp(36),dp(4)).apply{gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(20)})
        val header=LinearLayout(a).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(TextView(a).apply{text=title;textSize=22f;setTextColor(Color.BLACK);typeface=Typeface.create("sans-serif-medium",0)},LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(a).apply{text="×";contentDescription="Close $title";EditorChrome.style(this);textSize=22f;setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(header)
        root.addView(TextView(a).apply{text=summary;textSize=12f;setTextColor(0xFF666666.toInt());setPadding(0,dp(4),0,dp(16))})
        val content=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL}
        for((name,tools)in sections){
            content.addView(TextView(a).apply{text=name;textSize=13f;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.BLACK);setPadding(0,dp(20),0,dp(10))})
            for(group in tools.chunked(3)){
                val row=LinearLayout(a);content.addView(row)
                for(tool in group)row.addView(Button(a).apply{text=tool.label;contentDescription=tool.label;EditorChrome.style(this,true);maxLines=2;setOnClickListener{dialog.dismiss();tool.run()}},LinearLayout.LayoutParams(0,dp(80),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})
                repeat(3-group.size){row.addView(Space(a),LinearLayout.LayoutParams(0,dp(80),1f))}
            }
        }
        root.addView(ScrollView(a).apply{isVerticalScrollBarEnabled=false;addView(content)},LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(root)
        dialog.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setDimAmount(.35f);setGravity(Gravity.BOTTOM);addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)}
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,(a.resources.displayMetrics.heightPixels*.82f).toInt())
    }
}
