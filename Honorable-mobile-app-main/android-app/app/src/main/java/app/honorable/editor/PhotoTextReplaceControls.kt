package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

/** Main-thread, owned form/review. Text is local UI only and is not logged or persisted here. */
internal class PhotoTextReplaceControls(private val activity:Activity,private val base:PhotoEdit,private val isCurrent:()->Boolean,private val apply:(PhotoTextReplacement)->Boolean){
    private var closed=false
    private var dialog:AlertDialog?=null
    private var token:Any?=null
    private fun owns(value:Any)=!closed&&token===value
    private fun valid()=!closed&&isCurrent()
    fun close(){closed=true;token=null;val old=dialog;dialog=null;old?.dismiss()}
    private fun show(value:AlertDialog,next:Any){
        if(!valid()){close();return}
        token=null;dialog?.dismiss();dialog=value;token=next
        value.setOnCancelListener{if(owns(next))close()};value.show()
    }
    fun open(){if(valid())form("","")else close()}
    private fun form(find:String,replacement:String){
        if(!valid()){close();return}
        val next=Any();val fields=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,0);setBackgroundColor(Color.WHITE)}
        fields.addView(TextView(activity).apply{text="Exact, case-sensitive text in this caption and visible unlocked text objects. Hidden, transparent and locked objects are kept. No photo pixels or library text are searched.";setTextColor(Color.BLACK)})
        fun field(label:String,value:String)=EditText(activity).apply{hint=label;contentDescription=label;setTextColor(Color.BLACK);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;setMaxLines(3);filters=arrayOf(android.text.InputFilter.LengthFilter(200));setText(value);fields.addView(this)}
        val query=field("Find exact photo text",find);val replace=field("Replace with (may be empty)",replacement)
        val value=AlertDialog.Builder(activity).setTitle("Find / replace photo text").setView(ScrollView(activity).apply{addView(fields)}).setNegativeButton("Cancel"){_,_->if(owns(next))close()}.setPositiveButton("Review replacements",null).create()
        show(value,next);if(closed)return
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!owns(next))return@setOnClickListener
            if(!valid()){close();return@setOnClickListener}
            val findText=query.text.toString();val replaceText=replace.text.toString()
            val plan=runCatching{PhotoTextReplacement.prepare(base,findText,replaceText)}.getOrElse{replace.error=it.message?:"Replacement cannot fit. Existing text was kept.";return@setOnClickListener}
            if(!plan.changed){query.error="No changed editable text. ${plan.lockedMatches} locked and ${plan.hiddenMatches} hidden matches kept.";return@setOnClickListener}
            review(plan,findText,replaceText)
        }
    }
    private fun review(plan:PhotoTextReplacement,find:String,replacement:String){
        val next=Any();val examples=plan.changes.take(6).joinToString("\n\n"){"${it.label} (${it.matches}):\n${it.before}\n→ ${it.after}"}
        val remaining=if(plan.changes.size>6)"\n\n${plan.changes.size-6} more changed text items are included."else""
        val summary="${plan.matches} exact matches in ${plan.changes.size} text items. ${plan.lockedMatches} locked and ${plan.hiddenMatches} hidden matches are kept. One Undo restores all changed text. Longer text may need layout adjustments; review the photo afterwards.\n\n"
        show(AlertDialog.Builder(activity).setTitle("Replace photo text?").setMessage(summary+examples+remaining)
            .setNegativeButton("Back"){_,_->if(!owns(next))return@setNegativeButton;if(valid())form(find,replacement)else close()}
            .setPositiveButton("Replace ${plan.matches} matches"){_,_->if(!owns(next))return@setPositiveButton;if(valid())apply(plan);close()}.create(),next)
    }
}
