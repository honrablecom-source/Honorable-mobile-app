package app.honorable.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/** A guest-only private local shelf; never mounts account/search/billing routes. */
class CanvasProjectsActivity:Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var rows:LinearLayout
    private lateinit var note:TextView
    private var busy=false
    private fun store()=CanvasProjectStore(File(filesDir,"canvas-projects"))
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        if(intent.getStringExtra(EditorAccessMode.EXTRA)!=EditorAccessMode.GUEST_BASIC.name){finish();return}
        window.statusBarColor=Color.WHITE;window.navigationBarColor=if(android.os.Build.VERSION.SDK_INT>=26)Color.WHITE else Color.BLACK
        window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if(android.os.Build.VERSION.SDK_INT>=26)android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);val padding=(16*resources.displayMetrics.density).toInt();setPadding(padding,padding,padding,padding)}
        NativeWorkspaceInsets.install(this,root)
        root.addView(Button(this).apply{EditorChrome.style(this);text="Close saved image projects";setOnClickListener{finish()}})
        root.addView(TextView(this).apply{text="Noble · image projects";textSize=24f;setTextColor(Color.BLACK)})
        note=TextView(this).apply{text="Editable projects and their source images stay on this device. Export a copy from the editor to share a finished image.";setTextColor(Color.DKGRAY)};root.addView(note)
        rows=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(ScrollView(this).apply{addView(rows)},LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }
    override fun onResume(){super.onResume();if(::rows.isInitialized)refresh()}
    private fun refresh(){
        worker.execute{
            val result=runCatching{store().list()}
            runOnUiThread{if(isDestroyed||isFinishing)return@runOnUiThread;rows.removeAllViews();result.onSuccess{projects->
                if(projects.isEmpty())note.text="No saved image projects yet. Create an image, add objects, and choose Save editable photo edits in its inspector."
                for(project in projects){
                    val line=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
                    line.addView(Button(this).apply{EditorChrome.style(this);text="Open ${project.name}";contentDescription="Open image project ${project.name}";isEnabled=project.readable;setOnClickListener{open(project)}})
                    line.addView(Button(this).apply{EditorChrome.style(this);text="Delete ${project.name}";contentDescription="Delete image project ${project.name}";setOnClickListener{confirmDelete(project)}});rows.addView(line)
                }
            }.onFailure{note.text="Saved image projects could not be read. Existing files were kept."}}
        }
    }
    private fun open(project:CanvasProjectStore.Summary){
        if(busy)return;busy=true;note.text="Checking saved source…"
        worker.execute{val result=runCatching{val saved=store().load(project.id){check(!isDestroyed&&!isFinishing)};FileProvider.getUriForFile(this,packageName+".creation",store().sourceFile(saved.id))}
            runOnUiThread{busy=false;if(isDestroyed||isFinishing)return@runOnUiThread;result.onSuccess{uri->startActivity(Intent(this,PhotoEditorActivity::class.java).apply{putExtra("uri",uri.toString());putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name);putExtra(PhotoEditorActivity.CANVAS_PROJECT_EXTRA,project.id);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}.onFailure{note.text=it.message?:"Saved project could not open. Existing data was kept."}}
        }
    }
    private fun confirmDelete(project:CanvasProjectStore.Summary){
        if(busy)return
        AlertDialog.Builder(this).setTitle("Delete ${project.name}?").setMessage("Deletes this local editable project and its private source copy. Exported images are kept.").setNegativeButton("Cancel",null).setPositiveButton("Delete project"){_,_->
            if(busy)return@setPositiveButton;busy=true
            worker.execute{val result=runCatching{store().delete(project.id){check(!isDestroyed&&!isFinishing)}};runOnUiThread{busy=false;if(!isDestroyed&&!isFinishing){result.onFailure{note.text=it.message?:"Project could not be deleted"};refresh()}}}
        }.show()
    }
    override fun onDestroy(){worker.shutdown();super.onDestroy()}
}
