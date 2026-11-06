package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.EditText
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** UI owns dialogs/busy state; the worker owns encrypted shelf and original fingerprint I/O. */
internal class PhotoVersionShelf(private val activity:Activity,private val primary:String,private val edit:PhotoEdit,private val originals:List<String>,
    private val namespace:()->String?,private val assertSource:()->Unit,private val isCurrent:()->Boolean,private val busy:(Boolean)->Unit,
    private val message:(String)->Unit,private val withOriginal:(()->Unit,(String)->Unit)->Unit,private val apply:(PhotoSavedVersion,PhotoEdit)->Unit){
    private val context=activity.applicationContext
    private val closed=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor()
    private var dialog:AlertDialog?=null
    private var dialogToken:Any?=null
    private var ownsBusy=false
    private var owner:String?=null
    private var scope:PhotoVersionScope?=null
    private var store:PhotoVersionStore?=null
    private var deletion:PhotoVersionStore.Deletion?=null
    private var canUndo=false
    private fun owns(token:Any)=!closed.get()&&dialogToken===token
    private fun valid()=!closed.get()&&isCurrent()
    private fun guard(){check(!closed.get()){ "Photo version operation stopped" };assertSource();check(namespace()==owner){"Account changed. Reopen versions."}}
    private fun releaseBusy(){if(ownsBusy){ownsBusy=false;busy(false)}}
    fun close(){if(!closed.compareAndSet(false,true))return;dialogToken=null;dialog?.dismiss();dialog=null;worker.shutdownNow();releaseBusy()}
    private fun show(value:AlertDialog,token:Any){
        if(!valid()){close();return}
        dialogToken=null;dialog?.dismiss();dialog=value;dialogToken=token
        value.setOnCancelListener{if(owns(token))close()};value.show()
    }
    private fun <T> job(action:()->T,done:(T)->Unit){
        if(ownsBusy)return
        if(!valid()){close();return}
        val token=Any();show(AlertDialog.Builder(activity).setTitle("Saved photo versions").setMessage("Checking private versions and original access…").setNegativeButton("Cancel"){_,_->if(owns(token))close()}.create(),token)
        if(closed.get())return
        ownsBusy=true;busy(true)
        try{worker.execute{
            val result=runCatching{action().also{guard()}}
            activity.runOnUiThread{
                if(closed.get())return@runOnUiThread
                releaseBusy();if(!valid()){close();return@runOnUiThread}
                result.onSuccess(done).onFailure{error->message(if(error is IllegalArgumentException)error.message?:"Saved versions are incompatible."else"Photo versions or original access are unavailable. Existing edits and saved data were kept.");close()}
            }
        }}catch(_:java.util.concurrent.RejectedExecutionException){releaseBusy();close()}
    }
    fun open(){job({
        owner=namespace();check(owner!=null){"Session changed"};guard()
        scope=PhotoVersionScope(owner!!,primary);store=PhotoVersionStore(scope!!,EncryptedPhotoVersionRecord(context,scope!!));store!!.load()
    }){menu(it)}}
    private fun menu(catalog:PhotoVersionCatalog){
        val token=Any();val labels=mutableListOf("Save current as a new version","Open a saved version","Delete a saved version");if(canUndo)labels.add("Undo last deletion")
        show(AlertDialog.Builder(activity).setTitle("Photo versions · ${catalog.entries.size}/20").setItems(labels.toTypedArray()){_,index->
            if(!owns(token))return@setItems
            when(index){0->saveName();1,2->choose(catalog,index==2);3->job({guard();store!!.undo(checkNotNull(deletion),::guard);deletion=null;store!!.load()}){canUndo=false;message("Saved photo version deletion undone.");menu(it)}}
        }.setNegativeButton("Close"){_,_->if(owns(token))close()}.create(),token)
    }
    private fun saveName(){
        val token=Any();val name=EditText(activity).apply{hint="Version name";contentDescription="New saved photo version name";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(40))}
        val value=AlertDialog.Builder(activity).setTitle("Save a new editable version").setMessage("Keeps full photo settings separately, including captions and objects. Original media is not copied and must remain available and unchanged. Large edits can fill the shelf before 20 versions.")
            .setView(name).setNegativeButton("Cancel"){_,_->if(owns(token))close()}.setPositiveButton("Save new version",null).create()
        show(value,token);if(closed.get())return
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!owns(token))return@setOnClickListener
            val chosen=name.text.toString().trim();runCatching{PhotoVersionName.validate(chosen)}.onFailure{name.error=it.message}.getOrElse{return@setOnClickListener}
            value.dismiss();job({guard();withOriginal(::guard){fingerprint->store!!.save(PhotoSavedVersion.create(scope!!,chosen,fingerprint,edit,originals),::guard)};guard();store!!.load()}){
                deletion=null;canUndo=false;message("Editable photo version saved privately. Original media was not copied.");menu(it)
            }
        }
    }
    private fun choose(catalog:PhotoVersionCatalog,delete:Boolean){
        if(catalog.entries.isEmpty()){message("No versions for this original yet. Save a new version first.");menu(catalog);return}
        val token=Any();show(AlertDialog.Builder(activity).setTitle(if(delete)"Delete photo version"else"Open photo version")
            .setItems(catalog.entries.map{it.name}.toTypedArray()){_,index->if(owns(token))confirm(catalog.entries[index],delete)}.setNegativeButton("Cancel"){_,_->if(owns(token))close()}.create(),token)
    }
    private fun confirm(entry:PhotoSavedVersion,delete:Boolean){
        val token=Any();val text=if(delete)"Removes only this saved version. Current edits and original media are kept. Deletion Undo lasts until this shelf changes or closes."
            else"Replaces current photo settings. Save a named version first to keep editable current work. Original bytes must still match; photo Undo is available only in this editor session."
        show(AlertDialog.Builder(activity).setTitle(if(delete)"Delete ${entry.name}?"else"Open ${entry.name}?").setMessage(text)
            .setNegativeButton("Keep current"){_,_->if(owns(token))job({guard();store!!.load()}){menu(it)}}
            .setPositiveButton(if(delete)"Delete version"else"Open version"){_,_->
                if(!owns(token))return@setPositiveButton
                if(delete)job({guard();deletion=store!!.delete(entry,::guard);store!!.load()}){canUndo=true;message("Saved photo version deleted. Deletion Undo is available in this shelf.");menu(it)}
                else job({guard();val selected=store!!.requireEntry(entry);var settings:PhotoEdit?=null;withOriginal(::guard){fingerprint->settings=selected.edit(scope!!,fingerprint)};guard();store!!.requireEntry(entry);selected to checkNotNull(settings)}){(version,settings)->try{apply(version,settings)}finally{close()}}
            }.create(),token)
    }
}
