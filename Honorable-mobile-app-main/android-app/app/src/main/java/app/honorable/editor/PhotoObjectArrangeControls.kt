package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import java.util.Locale

/** UI-only owned selection and review; all geometry is validated before the editor callback. */
internal class PhotoObjectArrangeControls(private val activity:Activity,private val base:PhotoEdit,private val isCurrent:()->Boolean,private val apply:(PhotoObjectArrangement)->Boolean,private val message:(String)->Unit){
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
    private fun label(value:PhotoObject):String {
        val layer=base.layers.objects.indexOf(value)+1;val kind=when(value.type){PhotoObjectType.TEXT->"Text";PhotoObjectType.ELLIPSE->"Ellipse";PhotoObjectType.RECTANGLE->"Rectangle"}
        val clean=value.text.replace(Regex("[\\s\\p{Z}]+")," ").trim()
        val snippet=if(clean.codePointCount(0,clean.length)>24)clean.substring(0,clean.offsetByCodePoints(0,24))+"…"else clean
        return "Layer $layer · $kind"+(if(value.type==PhotoObjectType.TEXT)": $snippet"else"")+String.format(Locale.ROOT," · center %.1f%%, %.1f%%",value.x*100,value.y*100)
    }
    fun open(){
        if(!valid()){close();return}
        val eligible=base.layers.objects.filter(PhotoObjectArrangement::eligible)
        if(eligible.size<2){message("Add or unlock at least two visible objects to arrange. Hidden and fully transparent objects are kept.");close();return}
        selection(emptySet())
    }
    private fun selection(chosen:Set<String>){
        if(!valid()){close();return}
        val eligible=base.layers.objects.filter(PhotoObjectArrangement::eligible);val selected=chosen.toMutableSet();val next=Any()
        val value=AlertDialog.Builder(activity).setTitle("Arrange object centers · choose at least 2")
            .setMultiChoiceItems(eligible.map(::label).toTypedArray(),eligible.map{it.id in selected}.toBooleanArray()){_,index,checked->
                if(!owns(next))return@setMultiChoiceItems
                if(!valid()){close();return@setMultiChoiceItems}
                if(checked)selected.add(eligible[index].id)else selected.remove(eligible[index].id)
            }.setNegativeButton("Cancel"){_,_->if(owns(next))close()}.setPositiveButton("Choose arrangement",null).create()
        show(value,next);if(closed)return
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!owns(next))return@setOnClickListener
            if(!valid()){close();return@setOnClickListener}
            if(selected.size<2){value.setTitle("Choose at least 2 objects first");return@setOnClickListener}
            actions(selected.toSet())
        }
    }
    private fun actions(ids:Set<String>){
        val next=Any();val actions=PhotoObjectArrangement.Action.entries
        show(AlertDialog.Builder(activity).setTitle("Arrange ${ids.size} selected objects")
            .setItems(actions.map{it.title}.toTypedArray()){_,index->
                if(!owns(next))return@setItems
                if(!valid()){close();return@setItems}
                val plan=runCatching{PhotoObjectArrangement.prepare(base,ids.toList(),actions[index])}.getOrElse{notice(it.message?:"Arrangement is unavailable.",ids);return@setItems}
                if(!plan.changed){notice("Selected centers already use this arrangement. No edits were added.",ids);return@setItems}
                review(plan,ids)
            }.setNegativeButton("Back to selection"){_,_->if(!owns(next))return@setNegativeButton;if(valid())selection(ids)else close()}.create(),next)
    }
    private fun notice(text:String,ids:Set<String>){
        val next=Any()
        show(AlertDialog.Builder(activity).setTitle("Object positions kept").setMessage(text)
            .setNegativeButton("Cancel"){_,_->if(owns(next))close()}
            .setPositiveButton("Back to selection"){_,_->if(!owns(next))return@setPositiveButton;if(valid())selection(ids)else close()}.create(),next)
    }
    private fun review(plan:PhotoObjectArrangement,ids:Set<String>){
        val next=Any();val rows=plan.changes.joinToString("\n"){String.format(Locale.ROOT,"Layer %d: (%.1f%%, %.1f%%) → (%.1f%%, %.1f%%)",base.layers.objects.indexOf(it.before)+1,it.before.x*100,it.before.y*100,it.after.x*100,it.after.y*100)}
        val rule=if(plan.action.distribute)"Keeps the outer selected centers fixed and spaces the others evenly between them."else"Aligns selected centers to the midpoint between their current outer centers."
        val text="${plan.changes.size} of ${plan.selected.size} selected objects move. $rule\nSizes, text, rotation and layer order are kept. Unselected, hidden, transparent and locked objects are kept. This uses centers, not visible edges or equal edge gaps; objects can overlap or clip. One Undo restores all positions.\n\n$rows"
        show(AlertDialog.Builder(activity).setTitle(plan.action.title+"?").setMessage(text)
            .setNegativeButton("Back"){_,_->if(!owns(next))return@setNegativeButton;if(valid())actions(ids)else close()}
            .setPositiveButton("Apply arrangement"){_,_->if(!owns(next))return@setPositiveButton;if(valid())apply(plan);close()}.create(),next)
    }
}
