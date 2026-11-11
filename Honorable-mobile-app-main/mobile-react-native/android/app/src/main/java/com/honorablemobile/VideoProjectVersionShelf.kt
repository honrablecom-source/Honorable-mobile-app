package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.EditText
import app.honorable.editor.EditorAccessMode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** UI owns dialogs/busy state; the private worker owns account, encrypted shelf and media I/O. */
internal class VideoProjectVersionShelf(
    private val activity:Activity,private val mode:EditorAccessMode,
    private val primary:String,private val snapshot:VideoDraft,
    private val isCurrent:()->Boolean,private val busy:(Boolean)->Unit,
    private val message:(String)->Unit,
    private val withSaveAccess:(VideoDraft,()->Unit,()->Unit)->Unit,
    private val validateOpen:(VideoDraft,()->Unit)->Unit,
    private val apply:(VideoDraft)->Unit
) {
    private val context=activity.applicationContext
    private val closed=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor()
    private var dialog:AlertDialog?=null
    private var dialogToken:Any?=null
    private var ownsBusy=false // UI only
    private var canUndo=false
    private var owner:String?=null // worker only
    private var store:VideoProjectVersionStore?=null
    private var deletion:VideoProjectVersionStore.Deletion?=null
    private fun valid():Boolean=!closed.get()&&isCurrent()
    private fun owns(token:Any):Boolean=!closed.get()&&dialogToken===token
    private fun requireCurrent() {
        check(!closed.get()){"Saved version operation cancelled"}
        check(VideoPrivateOwner.namespace(context,mode)==owner){"Account changed. Reopen saved versions."}
    }
    private fun releaseBusy(){if(ownsBusy){ownsBusy=false;busy(false)}}
    fun close() {
        if(!closed.compareAndSet(false,true))return
        dialogToken=null;dialog?.dismiss();dialog=null;worker.shutdownNow();releaseBusy()
    }
    private fun show(value:AlertDialog,token:Any) {
        if(!valid()){close();return}
        dialogToken=null;dialog?.dismiss();dialog=value;dialogToken=token
        value.setOnCancelListener{if(owns(token))close()};value.show()
    }
    private fun <T> job(action:()->T,done:(T)->Unit) {
        if(ownsBusy)return
        if(!valid()){close();return}
        val token=Any()
        show(AlertDialog.Builder(activity).setTitle("Saved video versions").setMessage("Checking local versions and originals…")
            .setNegativeButton("Cancel"){_,_->if(owns(token))close()}.create(),token)
        if(closed.get())return
        ownsBusy=true;busy(true)
        try{worker.execute {
            val result=runCatching{action().also{requireCurrent()}}
            activity.runOnUiThread {
                if(closed.get())return@runOnUiThread
                releaseBusy()
                if(!valid()){close();return@runOnUiThread}
                result.onSuccess(done).onFailure{message(it.message?:"Saved video versions are unavailable.");close()}
            }
        }}catch(_:java.util.concurrent.RejectedExecutionException){releaseBusy();close()}
    }
    fun open() {
        job({
            owner=VideoPrivateOwner.namespace(context,mode);check(owner!=null){"Session changed. Reopen the video."}
            val scope=VideoVersionScope(owner!!,primary)
            store=VideoProjectVersionStore(scope,snapshot.sourceDurationMs,EncryptedVideoVersionRecord(context,scope))
            requireCurrent();store!!.load().also{requireCurrent()}
        }){menu(it)}
    }
    private fun menu(catalog:VideoProjectVersions) {
        val token=Any()
        val choices=mutableListOf("Save current as a new version","Open a saved version","Delete a saved version")
        if(canUndo)choices.add("Undo last deletion")
        val comparisonIndex=choices.size;choices.add("Compare saved version with current")
        val recoveryIndex=choices.size;choices.add("Recover captions from a saved version")
        show(AlertDialog.Builder(activity).setTitle("Video versions · ${catalog.entries.size}/20")
            .setItems(choices.toTypedArray()){_,index->
                if(!owns(token))return@setItems
                if(!valid()){close();return@setItems}
                if(index==recoveryIndex){chooseCaptionRecovery(catalog);return@setItems}
                if(index==comparisonIndex){chooseComparison(catalog);return@setItems}
                when(index){
                    0->saveName()
                    1,2->choose(catalog,index==2)
                    3->job({requireCurrent();store!!.undo(checkNotNull(deletion)){requireCurrent()};deletion=null;store!!.load()}){
                        canUndo=false;message("Saved version deletion undone.");menu(it)
                    }
                }
            }.setNegativeButton("Close"){_,_->if(owns(token))close()}.create(),token)
    }
    private fun saveName() {
        val token=Any()
        val name=EditText(activity).apply{hint="Version name";contentDescription="New saved video version name";setSingleLine(true);setTextColor(Color.BLACK);filters=arrayOf(android.text.InputFilter.LengthFilter(80))}
        val value=AlertDialog.Builder(activity).setTitle("Save a new editable version")
            .setMessage("Keeps this timeline and selected range separately. Original documents must remain available. Media is not copied. Large timelines can fill this shelf before 20 versions.")
            .setView(name).setNegativeButton("Cancel"){_,_->if(owns(token))close()}.setPositiveButton("Save new version",null).create()
        show(value,token)
        if(closed.get())return
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!owns(token))return@setOnClickListener
            val chosen=name.text.toString().trim()
            runCatching{VideoVersionName.requireValid(chosen)}.onFailure{name.error=it.message}.getOrElse{return@setOnClickListener}
            value.dismiss()
            job({
                requireCurrent();val version=VideoProjectVersion.create(chosen,snapshot)
                withSaveAccess(snapshot,{requireCurrent()}){store!!.save(version){requireCurrent()}}
                requireCurrent();store!!.load()
            }){canUndo=false;deletion=null;message("Editable video version saved locally. Media was not copied.");menu(it)}
        }
    }
    private fun chooseCaptionRecovery(catalog:VideoProjectVersions){
        if(catalog.entries.isEmpty()){message("No saved version to recover captions from. Save a version first.");menu(catalog);return}
        val token=Any()
        show(AlertDialog.Builder(activity).setTitle("Recover saved captions only")
            .setItems(catalog.entries.map{it.name}.toTypedArray()){_,index->
                if(!owns(token))return@setItems
                if(!valid()){close();return@setItems}
                val selected=catalog.entries[index]
                job({
                    requireCurrent();val saved=store!!.requireEntry(selected).draft()
                    VideoCaptionRecovery.prepare(snapshot,saved).also{requireCurrent()}
                }){prepared->captionRecovery(selected,prepared)}
            }.setNegativeButton("Close"){_,_->if(owns(token))close()}.create(),token)
    }
    private fun captionRecovery(version:VideoProjectVersion,prepared:VideoCaptionRecovery.Result){
        if(!valid()){close();return}
        val token=Any()
        val body=android.widget.TextView(activity).apply{text=prepared.reviewText;setTextColor(Color.BLACK);setPadding(20,12,20,12);contentDescription="Complete before and after caption recovery review"}
        val view=AlertDialog.Builder(activity).setTitle("Recover captions from ${version.name}")
            .setView(android.widget.ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Keep current"){_,_->if(owns(token))close()}
            .setPositiveButton("Apply captions only"){_,_->
                if(!owns(token))return@setPositiveButton
                if(!valid()){close();return@setPositiveButton}
                job({
                    requireCurrent();val saved=store!!.requireEntry(version).draft()
                    val latest=VideoCaptionRecovery.prepare(snapshot,saved)
                    check(latest.draft==prepared.draft){"Saved caption recovery changed. Review again."}
                    validateOpen(latest.draft){requireCurrent()};requireCurrent();latest.draft
                }){draft->try{apply(draft)}finally{close()}}
            }.create()
        view.setOnDismissListener{body.text=""}
        show(view,token)
    }

    private fun chooseComparison(catalog:VideoProjectVersions){
        if(catalog.entries.isEmpty()){message("No saved version to compare. Save a version first.");menu(catalog);return}
        val token=Any()
        show(AlertDialog.Builder(activity).setTitle("Compare saved version with current")
            .setItems(catalog.entries.map{it.name}.toTypedArray()){_,index->
                if(!owns(token))return@setItems
                if(!valid()){close();return@setItems}
                val selected=catalog.entries[index]
                job({requireCurrent();val saved=store!!.requireEntry(selected).draft();val report=VideoVersionDiff.compare(snapshot,saved);requireCurrent();report}){report->comparison(selected,report)}
            }.setNegativeButton("Close"){_,_->if(owns(token))close()}.create(),token)
    }
    private fun comparison(version:VideoProjectVersion,report:VideoVersionDiff.Report){
        if(!valid()){close();return}
        val token=Any()
        val body=android.widget.TextView(activity).apply{text=report.text();setTextColor(Color.BLACK);setPadding(20,12,20,12);contentDescription="Current versus saved video version metadata"}
        val view=AlertDialog.Builder(activity).setTitle("Compare with ${version.name}")
            .setView(android.widget.ScrollView(activity).apply{setBackgroundColor(Color.WHITE);addView(body)})
            .setNegativeButton("Close"){_,_->if(owns(token))close()}
            .setPositiveButton("Back to versions"){_,_->
                if(!owns(token))return@setPositiveButton
                if(!valid()){close();return@setPositiveButton}
                job({requireCurrent();store!!.load()}){menu(it)}
            }.create()
        view.setOnDismissListener{body.text=""}
        show(view,token)
    }

    private fun choose(catalog:VideoProjectVersions,delete:Boolean) {
        if(catalog.entries.isEmpty()){message("No versions saved for this original yet. Save a new version first.");menu(catalog);return}
        val token=Any()
        show(AlertDialog.Builder(activity).setTitle(if(delete)"Delete saved version" else "Open saved version")
            .setItems(catalog.entries.map{it.name}.toTypedArray()){_,index->if(owns(token))confirm(catalog.entries[index],delete)}
            .setNegativeButton("Cancel"){_,_->if(owns(token))close()}.create(),token)
    }
    private fun confirm(version:VideoProjectVersion,delete:Boolean) {
        val token=Any()
        val text=if(delete)"Removes only this saved edit version. Current edits and original media are kept. Deletion Undo is available in this shelf until it changes or closes."
            else "Replaces the current timeline and selected range. Save a new named version first to keep editable current work. Original documents will be checked; timeline Undo is session-only and does not keep a range you only selected."
        show(AlertDialog.Builder(activity).setTitle(if(delete)"Delete ${version.name}?" else "Open ${version.name}?").setMessage(text)
            .setNegativeButton("Keep current"){_,_->if(owns(token))job({requireCurrent();store!!.load()}){menu(it)}}
            .setPositiveButton(if(delete)"Delete version" else "Open version"){_,_->
                if(!owns(token))return@setPositiveButton
                if(delete)job({requireCurrent();deletion=store!!.delete(version){requireCurrent()};store!!.load()}){
                    canUndo=true;message("Saved version deleted. Deletion Undo is available in this shelf.");menu(it)
                }else job({
                    requireCurrent();val draft=store!!.requireEntry(version).draft()
                    validateOpen(draft){requireCurrent()};requireCurrent();draft
                }){draft->try{apply(draft)}finally{close()}}
            }.create(),token)
    }
}
