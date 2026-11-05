package app.honorable.editor

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.view.Window
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context

/** Bottom-anchored scrollable inspector. Every tile invokes the existing editor action. */
object NativeInspectorPanel {
    data class Tool(val label:String,val run:()->Unit)
    fun show(a:Activity,title:String,summary:String,sections:List<Pair<String,List<Tool>>>){
        if(a.isFinishing||a.isDestroyed)return
        val catalogue=sections.map{(name,tools)->name to tools.toList()}
        val labels=catalogue.map{(name,tools)->name to tools.map{it.label}}
        fun dp(v:Int)=(v*a.resources.displayMetrics.density).toInt()
        val dialog=Dialog(a).apply{requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(true)}
        val root=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(10),dp(20),dp(20));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadii=floatArrayOf(dp(26).toFloat(),dp(26).toFloat(),dp(26).toFloat(),dp(26).toFloat(),0f,0f,0f,0f)}}
        root.addView(android.view.View(a).apply{background=GradientDrawable().apply{setColor(0xFFD5D5D5.toInt());cornerRadius=dp(2).toFloat()}},LinearLayout.LayoutParams(dp(36),dp(4)).apply{gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(20)})
        val header=LinearLayout(a).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(TextView(a).apply{text=title;textSize=22f;setTextColor(Color.BLACK);typeface=Typeface.create("sans-serif-medium",0)},LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(a).apply{text="×";contentDescription="Close $title";EditorChrome.style(this);textSize=22f;setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(header)
        root.addView(TextView(a).apply{text=summary;textSize=12f;setTextColor(0xFF666666.toInt());setPadding(0,dp(4),0,dp(16))})
        val searchRow=LinearLayout(a).apply{gravity=Gravity.CENTER_VERTICAL}
        val search=EditText(a).apply{
            hint="Search names or categories";contentDescription="Find an editing tool";textSize=14f;setTextColor(Color.BLACK);setHintTextColor(0xFF666666.toInt())
            isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(InspectorToolSearch.MAX_QUERY))
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;imeOptions=EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener{_,action,_->if(action==EditorInfo.IME_ACTION_DONE){(a.getSystemService(Context.INPUT_METHOD_SERVICE)as?InputMethodManager)?.hideSoftInputFromWindow(windowToken,0);true}else false}
        }
        val clear=Button(a).apply{text="Clear";contentDescription="Clear tool search";EditorChrome.style(this);isEnabled=false;setOnClickListener{search.text.clear()}}
        searchRow.addView(search,LinearLayout.LayoutParams(0,dp(48),1f))
        searchRow.addView(clear,LinearLayout.LayoutParams(dp(76),dp(48)).apply{leftMargin=dp(8)})
        root.addView(searchRow)
        val count=TextView(a).apply{textSize=12f;setTextColor(0xFF666666.toInt());setPadding(0,dp(8),0,dp(4));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        root.addView(count)
        val content=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(a).apply{isVerticalScrollBarEnabled=false;addView(content)}
        fun render(query:String){
          val result=InspectorToolSearch.find(labels,query)
          clear.isEnabled=query.isNotEmpty();count.text="${result.found} of ${result.total} tools"
          content.removeAllViews()
          if(result.found==0)content.addView(TextView(a).apply{text="No tools found. Try part of a tool name or a category.";textSize=14f;setTextColor(Color.BLACK);setPadding(0,dp(20),0,dp(20))})
          for(section in result.sections){
            val(name,allTools)=catalogue[section.index]
            val tools=section.toolIndices.map{allTools[it]}
            content.addView(TextView(a).apply{text=name;textSize=13f;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.BLACK);setPadding(0,dp(20),0,dp(10))})
            for(group in tools.chunked(3)){
                val row=LinearLayout(a);content.addView(row)
                for(tool in group)row.addView(Button(a).apply{text=tool.label;contentDescription=tool.label;EditorChrome.style(this,true);maxLines=2;setOnClickListener{dialog.dismiss();if(!a.isFinishing&&!a.isDestroyed)tool.run()}},LinearLayout.LayoutParams(0,dp(80),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})
                repeat(3-group.size){row.addView(Space(a),LinearLayout.LayoutParams(0,dp(80),1f))}
            }
        }
          scroll.scrollTo(0,0)
        }
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){}
            override fun afterTextChanged(value:Editable?){render(value?.toString().orEmpty())}
        })
        render("")
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.isFocusableInTouchMode=true;root.requestFocus()
        dialog.setContentView(root)
        dialog.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setDimAmount(.35f);setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);setGravity(Gravity.BOTTOM);addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)}
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,(a.resources.displayMetrics.heightPixels*.82f).toInt())
    }
}
