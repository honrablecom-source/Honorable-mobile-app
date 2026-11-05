package com.honorablemobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.EditorChrome
import app.honorable.editor.NativeWorkspaceInsets
import java.io.File
import java.util.concurrent.Executors

/** Private guest shelf. A broken saved edit never silently falls back to the initial sequence. */
class PhotoVideoProjectsActivity:Activity(){
    private val worker=Executors.newSingleThreadExecutor()
    @Volatile private var alive=true
    private var busy=false
    private var active=false
    private var renameCancellation:java.util.concurrent.atomic.AtomicBoolean?=null
    private lateinit var body:LinearLayout
    private lateinit var note:TextView
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        if(intent.getStringExtra(EditorAccessMode.EXTRA)!=EditorAccessMode.GUEST_BASIC.name){finish();return}
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);val p=(16*resources.displayMetrics.density).toInt();setPadding(p,p,p,p)}
        NativeWorkspaceInsets.install(this,body)
        setContentView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
        refresh()
    }
    private fun button(label:String,action:()->Unit){body.addView(Button(this).apply{text=label;EditorChrome.style(this);setOnClickListener{if(!busy)action()}})}
    private fun refresh(){
        body.removeAllViews();button("Close saved photo videos"){finish()};button("Refresh saved photo videos"){refresh()}
        body.addView(TextView(this).apply{text="Noble · saved photo videos";textSize=24f;setTextColor(Color.BLACK)})
        note=TextView(this).apply{setTextColor(Color.DKGRAY);text="Loading private projects…"};body.addView(note)
        busy=true;worker.execute{val result=runCatching{PhotoVideoProjectStore(this).list()};runOnUiThread{
            if(!alive)return@runOnUiThread;busy=false
            result.onSuccess{items->note.text=if(items.isEmpty())"No photo video projects yet."else"${items.size} projects · stored only on this device. Save edits inside the video editor before closing."
                items.forEach{item->if(item.readable){
                    button("Open ${item.name}"){open(item.id)}
                    button("Rename ${item.name}"){rename(item)}
                    }
                    button("Delete ${item.name}"){AlertDialog.Builder(this).setTitle("Delete photo video?").setMessage("Remove this project's prepared clips and its saved guest timeline. Original photos stay unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->delete(item.id)}.show()}
                }
            }.onFailure{note.text="Project shelf unavailable: ${it.message}"}
        }}
    }
    private fun open(id:String){
        busy=true;note.text="Checking prepared clips and saved timeline…"
        worker.execute{val result=runCatching{
            val project=PhotoVideoProjectStore(this).load(id){check(alive)}
            val saved=GuestVideoProjectStore(File(noBackupFilesDir,"guest-video-projects"),project.source(0)).load(project.assets.first().durationMs)
            project to (saved?:project.initialDraft())
        };runOnUiThread{if(!alive)return@runOnUiThread;busy=false
            result.onSuccess{(project,draft)->startActivity(editorIntent(this,project,draft))}.onFailure{note.text="Project could not be opened: ${it.message}. Saved files were kept."}
        }}
    }
    private fun rename(item:PhotoVideoProjectStore.Summary){
        if(busy||!alive||!active||!item.readable)return
        val field=EditText(this).apply{setText(item.name);setTextColor(Color.BLACK);contentDescription="Photo video project name";setSingleLine(true);filters=arrayOf(android.text.InputFilter.LengthFilter(40))}
        val dialog=AlertDialog.Builder(this).setTitle("Rename photo video").setMessage("Changes only this project's local name. Prepared clips and saved timeline edits stay attached; original photos are unchanged.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save name",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(busy||!alive||!active){dialog.dismiss();return@setOnClickListener}
            val label=field.text.toString().trim()
            if(label.isEmpty()||label==item.name||label.length>40||label.any{it.isISOControl()}||label.toByteArray(Charsets.UTF_8).size>120||label.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)!=label){field.error="Use a different name of 1–40 characters (up to 120 UTF-8 bytes).";return@setOnClickListener}
            val cancelled=java.util.concurrent.atomic.AtomicBoolean(false);renameCancellation=cancelled
            dialog.dismiss();busy=true;note.text="Renaming private project…"
            worker.execute{val result=runCatching{PhotoVideoProjectStore(this).rename(item.id,item.name,label){check(alive&&!cancelled.get()){"Rename cancelled"}}}
                runOnUiThread{
                    if(!alive)return@runOnUiThread
                    if(renameCancellation===cancelled)renameCancellation=null
                    busy=false
                    result.onSuccess{refresh()}.onFailure{note.text="Rename could not be confirmed. Refresh the shelf before trying again; prepared clips and saved edits were kept."}
                }
            }
        }};dialog.show()
    }
    private fun delete(id:String){
        if(busy)return;busy=true;note.text="Deleting private project…"
        worker.execute{val result=runCatching{
            val store=PhotoVideoProjectStore(this)
            // Derive the source from a validated ID even when project metadata is unreadable.
            val source=store.sourceUri(id,0).toString()
            GuestVideoProjectStore(File(noBackupFilesDir,"guest-video-projects"),source).delete{check(alive)}
            store.delete(id){check(alive)}
        };runOnUiThread{if(!alive)return@runOnUiThread;busy=false;result.onSuccess{refresh()}.onFailure{note.text="Project deletion incomplete: ${it.message}. Try again."}}}
    }
    override fun onResume(){super.onResume();active=true}
    override fun onPause(){active=false;renameCancellation?.set(true);super.onPause()}
    override fun onDestroy(){renameCancellation?.set(true);alive=false;worker.shutdown();super.onDestroy()}
    companion object{
        const val PROJECT_EXTRA="createdPhotoVideoProject"
        const val DRAFT_EXTRA="createdPhotoVideoDraft"
        internal fun editorIntent(context:Context,project:PhotoVideoProject,draft:VideoDraft):Intent {
            require(draft.sourceDurationMs==project.assets.first().durationMs)
            return Intent().setClassName(context,context.packageName+".HonorableVideoEditorActivity")
                .putExtra("uri",project.source(0)).putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name)
                .putExtra(PROJECT_EXTRA,project.id).putExtra(DRAFT_EXTRA,VideoDraftCodec.encode(draft))
        }
    }
}
