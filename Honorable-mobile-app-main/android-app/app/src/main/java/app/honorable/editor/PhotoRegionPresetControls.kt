package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.InputFilter
import android.widget.*

object PhotoRegionPresetControls{
    fun show(a:Activity,initial:PhotoEdit,store:PhotoRegionPresetStore,requireCurrent:()->Unit,apply:(PhotoEdit)->Unit){
        fun failure(error:Throwable){Toast.makeText(a,error.message?:"Region recipes unavailable. Saved data was kept.",Toast.LENGTH_LONG).show()}
        AlertDialog.Builder(a).setTitle("Local region recipes").setItems(arrayOf("Save current regions","Apply saved regions","Delete saved recipe")){_,action->
            if(action==0){
                if(initial.regions.values.isEmpty()){failure(IllegalArgumentException("Add a local effect region before saving a recipe"));return@setItems}
                val field=EditText(a).apply{hint="Recipe name";contentDescription="Region recipe name";isSingleLine=true;filters=arrayOf(InputFilter.LengthFilter(40));setTextColor(Color.BLACK)}
                val panel=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12);addView(TextView(a).apply{text="Reuse this normalized region stack on another photo. It replaces that photo's local regions; other edits stay unchanged. Guest and account shelves stay separate.";setTextColor(Color.DKGRAY)});addView(field)}
                val dialog=AlertDialog.Builder(a).setTitle("Save region recipe").setView(panel).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
                dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{runCatching{requireCurrent();store.add(NamedPhotoRegions(field.text.toString().trim(),initial.regions),requireCurrent)}.onSuccess{Toast.makeText(a,"Region recipe saved locally",Toast.LENGTH_SHORT).show();dialog.dismiss()}.onFailure{field.error=it.message?:"Recipe could not be saved"}}};dialog.show()
            }else{
                val catalog=runCatching{requireCurrent();store.load()}.getOrElse{failure(it);return@setItems}
                if(catalog.entries.isEmpty()){Toast.makeText(a,"No saved local region recipes",Toast.LENGTH_SHORT).show();return@setItems}
                AlertDialog.Builder(a).setTitle(if(action==1)"Apply saved regions"else"Delete region recipe").setItems(catalog.entries.map{it.name}.toTypedArray()){_,index->
                    val value=catalog.entries[index]
                    AlertDialog.Builder(a).setTitle(if(action==1)"Replace local regions?"else"Delete this recipe?").setMessage(value.name+if(action==1)"\nOther edits stay unchanged. Undo restores your current regions."else"\nCurrent photo regions stay unchanged.").setNegativeButton("Cancel",null).setPositiveButton(if(action==1)"Apply"else"Delete"){_,_->runCatching{requireCurrent();if(action==1)apply(value.applyTo(initial))else store.remove(value.name,requireCurrent)}.onFailure(::failure)}.show()
                }.setNegativeButton("Cancel",null).show()
            }
        }.setNegativeButton("Close",null).show()
    }
}
