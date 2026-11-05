package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputFilter
import android.widget.*
import java.util.Locale
import java.util.UUID

object PhotoLayerControls {
    fun show(activity:Activity,initial:PhotoLayers,commit:(PhotoLayers)->Unit) {
        val objects=initial.objects.asReversed()
        val labels=listOf("Add rectangle","Add ellipse","Add text","Visibility and locks for all objects")+objects.mapIndexed{index,value->objectLabel(value,objects.size-index)}
        AlertDialog.Builder(activity).setTitle("Objects · front first · ${objects.size}/32")
            .setItems(labels.toTypedArray()){_,index->
                if(index<3) {
                    if(initial.objects.size>=32){Toast.makeText(activity,"Use up to 32 objects",Toast.LENGTH_SHORT).show();return@setItems}
                    val type=PhotoObjectType.entries[index]
                    edit(activity,initial,PhotoObject(UUID.randomUUID().toString(),type,text=if(type==PhotoObjectType.TEXT)"Your text"else ""),true,commit)
                } else if(index==3)batch(activity,initial,commit)else manage(activity,initial,objects[index-4],commit)
            }.setNegativeButton("Close",null).show()
    }
    /** Opens the same actions as the object list, including unlock for locked picks. */
    fun showObject(activity:Activity,initial:PhotoLayers,id:String,commit:(PhotoLayers)->Unit){
        if(activity.isFinishing||activity.isDestroyed)return
        val value=initial.objects.find{it.id==id}?:return
        manage(activity,initial,value,commit)
    }
    private fun batch(a:Activity,initial:PhotoLayers,commit:(PhotoLayers)->Unit){
        if(a.isFinishing||a.isDestroyed)return
        if(initial.objects.isEmpty()){Toast.makeText(a,"Add an object first",Toast.LENGTH_SHORT).show();return}
        val actions=arrayOf("Show all objects","Hide all objects","Lock all objects","Unlock all objects")
        AlertDialog.Builder(a).setTitle("Visibility and locks").setItems(actions){_,index->
            if(a.isFinishing||a.isDestroyed)return@setItems
            val next=when(index){0->initial.withVisibility(true);1->initial.withVisibility(false);2->initial.withLocking(true);3->initial.withLocking(false);else->return@setItems}
            val changed=initial.objects.zip(next.objects).count{it.first!=it.second}
            if(changed==0){Toast.makeText(a,"All objects already use this setting",Toast.LENGTH_SHORT).show();return@setItems}
            AlertDialog.Builder(a).setTitle(actions[index]+"?")
                .setMessage("Changes $changed of ${initial.objects.size} objects. Visibility includes locked objects;locking includes hidden objects without changing their visibility. Position,style and stacking stay unchanged. Apply is one Undo step. Original photos are kept.")
                .setNegativeButton("Cancel",null).setPositiveButton("Apply to objects"){_,_->
                    if(a.isFinishing||a.isDestroyed)return@setPositiveButton
                    runCatching{commit(next)}.onFailure{Toast.makeText(a,it.message?:"Objects changed. Reopen the controls.",Toast.LENGTH_LONG).show()}
                }.show()
        }.setNegativeButton("Close",null).show()
    }
    private fun objectLabel(value:PhotoObject,layer:Int):String {
        val kind=when(value.type){PhotoObjectType.RECTANGLE->"Rectangle";PhotoObjectType.ELLIPSE->"Ellipse";PhotoObjectType.TEXT->"Text"}
        val clean=value.text.replace(Regex("[\\s\\p{Z}]+")," ").trim()
        val snippet=if(clean.codePointCount(0,clean.length)>28)clean.substring(0,clean.offsetByCodePoints(0,28))+"…"else clean
        val title="Layer $layer · $kind"+if(value.type==PhotoObjectType.TEXT)": $snippet"else""
        val position=String.format(Locale.ROOT,"Center %.0f%%, %.0f%%",value.x*100,value.y*100)
        return "$title\n$position"+(if(!value.visible)" · hidden"else "")+(if(value.locked)" · locked"else "")
    }
    private fun manage(a:Activity,initial:PhotoLayers,value:PhotoObject,commit:(PhotoLayers)->Unit) {
        val labels=if(value.locked)listOf("Unlock",if(value.visible)"Hide"else"Show")else listOf("Edit object","Bring to front","Send to back",if(value.visible)"Hide"else"Show","Lock","Duplicate object","Delete object")
        AlertDialog.Builder(a).setTitle(objectLabel(value,initial.objects.indexOf(value)+1).substringBefore('\n')).setItems(labels.toTypedArray()){_,index->
            if(!value.locked&&index==0){edit(a,initial,value,false,commit);return@setItems}
            runCatching {
                val next=if(value.locked)initial.update(if(index==0)value.copy(locked=false)else value.copy(visible=!value.visible))else when(index) {
                    1->initial.move(value.id,true)
                    2->initial.move(value.id,false)
                    3->initial.update(value.copy(visible=!value.visible))
                    4->initial.update(value.copy(locked=true))
                    5->initial.add(value.copy(id=UUID.randomUUID().toString(),x=(value.x+.03f).coerceAtMost(1f),y=(value.y+.03f).coerceAtMost(1f)))
                    6->initial.delete(value.id)
                    else->error("Choose an object action")
                }
                commit(next)
            }.onFailure{Toast.makeText(a,it.message?:"Object change could not complete",Toast.LENGTH_LONG).show()}
        }.setNegativeButton("Cancel",null).show()
    }
    private fun edit(a:Activity,initial:PhotoLayers,original:PhotoObject,adding:Boolean,commit:(PhotoLayers)->Unit) {
        var value=original
        val panel=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;val padding=(20*resources.displayMetrics.density).toInt();setPadding(padding,padding/2,padding,padding/2)}
        panel.addView(TextView(a).apply{text="Objects sit above photo adjustments and existing captions. Position and size are relative to the finished canvas. Text is clipped to its box; PNG keeps transparency. Apply makes one undoable change.";setTextColor(Color.DKGRAY)})
        val textField=if(value.type==PhotoObjectType.TEXT)EditText(a).apply{setText(value.text);contentDescription="Object text";maxLines=3;filters=arrayOf(InputFilter.LengthFilter(200));setTextColor(Color.BLACK);panel.addView(this)}else null
        val color=EditText(a).apply{setText(String.format(Locale.ROOT,"%06X",value.color and 0xFFFFFF));contentDescription="Object color hex, six digits with optional number sign";hint="#RRGGBB or RRGGBB";isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(64));setTextColor(Color.BLACK);panel.addView(this)}
        fun slider(name:String,start:Int,maximum:Int,format:(Int)->String,change:(Int)->Unit){
            val label=TextView(a).apply{text="$name: ${format(start)}";setTextColor(Color.BLACK)};panel.addView(label)
            panel.addView(SeekBar(a).apply{max=maximum;progress=start;contentDescription=name;progressTintList=ColorStateList.valueOf(Color.BLACK);thumbTintList=ColorStateList.valueOf(Color.BLACK)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(bar:SeekBar){};override fun onStopTrackingTouch(bar:SeekBar){};override fun onProgressChanged(bar:SeekBar,position:Int,user:Boolean){label.text="$name: ${format(position)}";if(user)change(position)}})
            },LinearLayout.LayoutParams(-1,(48*a.resources.displayMetrics.density).toInt()))
        }
        fun percent(number:Int)="${number/10f}%"
        slider("Horizontal position",(value.x*1000).toInt(),1000,::percent){value=value.copy(x=it/1000f)}
        slider("Vertical position",(value.y*1000).toInt(),1000,::percent){value=value.copy(y=it/1000f)}
        slider("Object width",(value.width*1000).toInt()-20,980,{percent(it+20)}){value=value.copy(width=(it+20)/1000f)}
        slider("Object height",(value.height*1000).toInt()-20,980,{percent(it+20)}){value=value.copy(height=(it+20)/1000f)}
        slider("Object rotation",value.rotation.toInt()+180,360,{"${it-180}°"}){value=value.copy(rotation=(it-180).toFloat())}
        slider("Object opacity",(value.opacity*1000).toInt(),1000,::percent){value=value.copy(opacity=it/1000f)}
        if(value.type==PhotoObjectType.TEXT)slider("Text size",(value.textSize*1000).toInt()-20,180,{percent(it+20)}){value=value.copy(textSize=(it+20)/1000f)}
        if(value.type==PhotoObjectType.TEXT){
            panel.addView(TextView(a).apply{text="Curve text around an arc; zero uses normal multiline text. Curved text uses one line, with line breaks shown as spaces.";setTextColor(Color.DKGRAY)})
            slider("Text curve",value.curve.toInt()+180,360,{"${it-180}°"}){value=value.copy(curve=(it-180).toFloat())}
        }
        val textStyles=if(value.type==PhotoObjectType.TEXT)PhotoTextStyleControls.add(a,panel,value.textStyle)else null
        val note=TextView(a).apply{setTextColor(Color.DKGRAY)};panel.addView(note)
        val dialog=AlertDialog.Builder(a).setTitle(if(adding)"Add composition object"else"Edit composition object").setView(ScrollView(a).apply{addView(panel)}).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val parsedColor=PhotoObjectColorInput.parse(color.text.toString(),value.color)?:run{color.error="Use six hexadecimal digits, optionally starting with #";return@setOnClickListener}
            runCatching {
                val edited=value.copy(color=parsedColor,text=textField?.text?.toString()?:value.text,textStyle=textStyles?.invoke()?:value.textStyle)
                commit(if(adding)initial.add(edited)else initial.update(edited));dialog.dismiss()
            }.onFailure{note.text=it.message?:"Check the object settings"}
        }};dialog.show()
    }
}
