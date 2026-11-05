package com.honorablemobile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.media3.common.util.UnstableApi
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.PhotoProjectCodec
import app.honorable.editor.PhotoSourceDecoder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** User-selected photos become private timed video sources in the existing editable video project. */
@UnstableApi
class PhotoVideoCreatorActivity:Activity(){
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var name:EditText;private lateinit var format:Spinner;private lateinit var duration:Spinner
    private lateinit var note:TextView;private lateinit var start:Button;private lateinit var choose:Button;private lateinit var addPhotos:Button;private lateinit var fitTiming:Button
    private val selectionHistory=PhotoVideoSelectionHistory()
    private var selection:PhotoVideoSelection
        get()=selectionHistory.current
        set(value){selectionHistory.apply(value)}
    private lateinit var undoSelection:Button;private lateinit var redoSelection:Button
    private val photos:List<Uri> get()=selection.items.map{Uri.parse(it)}
    private lateinit var review:LinearLayout
    private val photoNames=mutableMapOf<String,String>()
    private var nameRequest=0
    private var encoder:PhotoVideoEncoder?=null
    @Volatile private var alive=true
    @Volatile private var job:Job?=null
    private class Job(val photos:List<Uri>,val format:PhotoVideoFormat,val durations:List<Long>){val cancelled=AtomicBoolean(false);@Volatile var staging:PhotoVideoProjectStore.Staging?=null;val assets=mutableListOf<PhotoVideoAsset>()}
    private fun requireCurrent(value:Job){check(alive&&job===value&&!value.cancelled.get()){"Photo video creation was cancelled"}}
    override fun onCreate(state:Bundle?){
        super.onCreate(state);if(intent.getStringExtra(EditorAccessMode.EXTRA)!=EditorAccessMode.GUEST_BASIC.name){finish();return}
        window.statusBarColor=Color.WHITE;window.navigationBarColor=if(android.os.Build.VERSION.SDK_INT>=26)Color.WHITE else Color.BLACK
        window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if(android.os.Build.VERSION.SDK_INT>=26)android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);val n=(16*resources.displayMetrics.density).toInt();setPadding(n,n,n,n)}
        app.honorable.editor.NativeWorkspaceInsets.install(this,body)
        fun button(text:String,action:()->Unit)=Button(this).apply{this.text=text;app.honorable.editor.EditorChrome.style(this);setOnClickListener{action()};body.addView(this)}
        button("Close photo video creator"){finish()}
        body.addView(TextView(this).apply{text="Noble · photos to video";textSize=24f;setTextColor(Color.BLACK)})
        body.addView(TextView(this).apply{text="Choose 1–20 photos. Prepare local 720p SDR clips, then trim, reorder, caption, grade and add sound in the video editor. Original photos stay unchanged. Transparency uses a black video background.";setTextColor(Color.DKGRAY)})
        name=EditText(this).apply{hint="Video project name";contentDescription="Video project name";setText(state?.getString("name")?:"Photo story");isSingleLine=true;filters=arrayOf(android.text.InputFilter.LengthFilter(40));setTextColor(Color.BLACK)};body.addView(name)
        format=Spinner(this).apply{adapter=ArrayAdapter(this@PhotoVideoCreatorActivity,android.R.layout.simple_spinner_dropdown_item,PhotoVideoFormat.entries.map{it.title});contentDescription="Photo video format";setSelection((state?.getInt("format")?:0).coerceIn(0,PhotoVideoFormat.entries.lastIndex))};body.addView(format)
        duration=Spinner(this).apply{adapter=ArrayAdapter(this@PhotoVideoCreatorActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Default: 3 seconds","Default: 5 seconds","Default: 8 seconds","Default: 10 seconds"));contentDescription="Default photo hold";setSelection((state?.getInt("duration")?:1).coerceIn(0,3))};body.addView(duration)
        choose=button("Choose photos"){if(job==null)pickPhotos()}
        addPhotos=button("Add more photos"){if(job==null&&selection.items.size<20)pickPhotos(true)}
        fitTiming=button("Fit default holds to total"){fitPhotoTiming()}
        undoSelection=button("Undo photo edit"){restoreSelectionEdit(false)}
        redoSelection=button("Redo photo edit"){restoreSelectionEdit(true)}
        body.addView(TextView(this).apply{text="Undo/Redo keeps the last 20 selection, order and individual hold edits while this screen stays open. Default hold, format and name are separate settings. Reopening restores the current selection without edit history.";setTextColor(Color.DKGRAY)})
        review=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(review)
        start=button("Create editable video project"){if(job==null)begin()}
        button("Cancel preparation"){job?.let{cancel(it,"Preparation cancelled. Original photos and completed projects were kept.")}}
        note=TextView(this).apply{setTextColor(Color.DKGRAY)};body.addView(note)
        val recovered=runCatching{PhotoVideoSelection.restore(state?.getStringArrayList("photos")?:emptyList(),state?.getLongArray("photoHolds"))}
        selectionHistory.reset(recovered.getOrElse{PhotoVideoSelection.of(emptyList())})
        note.text=if(photos.isEmpty())"No photos selected."else"${photos.size} photos selected. Start again when ready."
        if(recovered.isFailure)note.text="The previous selection could not be restored. Choose your photos again; originals were kept."
        setContentView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
        renderSelection();loadPhotoNames()
        duration.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){if(job==null)renderSelection()}
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
        }
    }
    private fun pickPhotos(append:Boolean=false){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);putExtra(Intent.EXTRA_LOCAL_ONLY,true);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},if(append)402 else 401)}catch(failure:Exception){note.text="No local photo picker is available."}}
    @Deprecated("Platform result API") override fun onActivityResult(request:Int,result:Int,data:Intent?){
        super.onActivityResult(request,result,data);if(request !in setOf(401,402)||result!=RESULT_OK||job!=null||!alive||isFinishing||isDestroyed)return
        val selected=buildList{data?.clipData?.let{clip->for(i in 0 until clip.itemCount)add(clip.getItemAt(i).uri)};data?.data?.let(::add)}.distinct()
        if(selected.size !in 1..20||selected.any{it.scheme!="content"}){note.text="Choose 1–20 local photo documents.";return}
        val checked=runCatching{if(request==402)selection.append(selected.map{it.toString()})else PhotoVideoSelection.of(selected.map{it.toString()},selection.holds.filterKeys{key->selected.any{it.toString()==key}})}
        if(checked.isFailure){note.text="Keep up to 20 local photos in total. Existing selection and holds were kept.";return}
        val previousCount=selection.items.size
        selection=checked.getOrThrow();photoNames.clear();renderSelection();loadPhotoNames()
        val added=selection.items.size-previousCount
        note.text=when{
            request!=402->"${photos.size} photos selected. Review their order below before creating clips."
            added==0->"No new photos added. These document references were already selected; the existing plan and Undo history were kept."
            else->"$added new photos added · ${photos.size} selected. Existing order and holds were kept; Undo reverses the addition."
        }
    }
    private fun renderSelection(){
        if(!::review.isInitialized)return
        review.removeAllViews()
        if(::start.isInitialized)start.isEnabled=job==null&&selection.items.isNotEmpty()
        if(::addPhotos.isInitialized)addPhotos.isEnabled=job==null&&selection.items.size<20
        if(::fitTiming.isInitialized)fitTiming.isEnabled=job==null&&selection.items.any{it !in selection.holds}
        if(::choose.isInitialized)choose.text=if(selection.items.isEmpty())"Choose photos"else"Replace photo selection"
        if(::undoSelection.isInitialized)undoSelection.isEnabled=job==null&&selectionHistory.canUndo
        if(::redoSelection.isInitialized)redoSelection.isEnabled=job==null&&selectionHistory.canRedo
        val expected=selection
        val plan=expected.durations(defaultHold())
        review.addView(TextView(this).apply{text=if(expected.items.isEmpty())"No photos in this selection."else "Photo order · ${expected.items.size} selected · about ${String.format(java.util.Locale.ROOT,"%.1f",plan.sum()/1000.0)} seconds. Set holds, move or remove before preparation; original photos are kept.";setTextColor(Color.DKGRAY)})
        expected.items.forEachIndexed{index,uri->
            val label=photoNames[uri]?:"Selected photo ${index+1}"
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            row.addView(TextView(this).apply{text="${index+1}. $label";setTextColor(Color.BLACK);setPadding(0,12,0,0)})
            row.addView(Button(this).apply{
                text="Hold ${String.format(java.util.Locale.ROOT,"%.3f",plan[index]/1000.0)} s · ${if(uri in expected.holds)"custom"else "default"}"
                contentDescription="Set photo ${index+1} hold: $label";app.honorable.editor.EditorChrome.style(this);isEnabled=job==null
                setOnClickListener{if(alive&&job==null&&selection===expected)editHold(index,expected,label)}
            })
            val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            fun action(text:String,enabled:Boolean,update:()->PhotoVideoSelection){
                controls.addView(Button(this).apply{
                    this.text=text;contentDescription="$text photo ${index+1}: $label";app.honorable.editor.EditorChrome.style(this);isEnabled=job==null&&enabled
                    setOnClickListener{
                        if(!alive||job!=null||selection!==expected)return@setOnClickListener
                        selection=update();renderSelection();note.text="${selection.items.size} photos selected. Preparation will use this order; originals are unchanged."
                    }
                },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            }
            action("Earlier",index>0){expected.move(index,-1)}
            action("Later",index<expected.items.lastIndex){expected.move(index,1)}
            action("Remove",true){expected.remove(index)}
            row.addView(controls);review.addView(row)
        }
    }
    private fun fitPhotoTiming(){
        if(!alive||isFinishing||isDestroyed||job!=null||selection.items.none{it !in selection.holds})return
        val expected=selection;val range=expected.fitTotalRange()
        fun seconds(ms:Long)=String.format(java.util.Locale.ROOT,"%.3f",ms/1000.0)
        fun valid()=alive&&!isFinishing&&!isDestroyed&&job==null&&selection===expected
        val field=EditText(this).apply{hint="Requested total seconds";contentDescription="Requested photo story total in seconds";setSingleLine(true);setTextColor(Color.BLACK);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;filters=arrayOf(android.text.InputFilter.LengthFilter(8));setText(seconds(expected.durations(defaultHold()).sum()))}
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Fit default photo holds")
            .setMessage("Keep every custom hold. Split the remaining time equally across photos still using the default,within 1–10 seconds each. Feasible total: ${seconds(range.first)}–${seconds(range.last)} seconds. This one-time edit turns those default holds into custom holds;Use default on a photo resets it. New photos added later use the default menu. Prepared video can round to codec frames;this plans requested timing.")
            .setView(field).setNegativeButton("Cancel",null).setPositiveButton("Review timing",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!valid()){dialog.dismiss();return@setOnClickListener}
            val proposed=runCatching{expected.fitTotal(VideoCaptions.seconds(field.text.toString()))}
                .getOrElse{field.error="Choose ${seconds(range.first)}–${seconds(range.last)} seconds with up to three decimal places.";return@setOnClickListener}
            val durations=proposed.durations(defaultHold())
            val summary=durations.mapIndexed{i,ms->"Photo ${i+1}: ${seconds(ms)} s · "+if(expected.items[i] in expected.holds)"custom unchanged"else"fitted"}.joinToString("\n")
            android.app.AlertDialog.Builder(this).setTitle("Review ${seconds(durations.sum())} seconds")
                .setMessage("$summary\n\nOriginal photos and custom holds stay unchanged. Apply this requested plan as one Undo step;check the prepared timeline before export.")
                .setNegativeButton("Back",null).setPositiveButton("Apply timing"){_,_->
                    if(!valid()){dialog.dismiss();return@setPositiveButton}
                    selection=proposed;dialog.dismiss();renderSelection();note.text="Photo timing fitted. Existing custom holds were kept;Undo restores the previous plan."
                }.show()
        }};dialog.show()
    }
    private fun restoreSelectionEdit(redo:Boolean){
        if(!alive||isFinishing||isDestroyed||job!=null)return
        val changed=if(redo)selectionHistory.redo()else selectionHistory.undo()
        if(!changed)return
        renderSelection();loadPhotoNames()
        note.text="${if(redo)"Redid"else "Undid"} photo selection edit. ${selection.items.size} photos selected; originals are unchanged."
    }
    private fun defaultHold():Long=listOf(3L,5L,8L,10L)[duration.selectedItemPosition]*1000
    private fun editHold(index:Int,expected:PhotoVideoSelection,label:String){
        val field=EditText(this).apply{hint="Hold in seconds";contentDescription="Photo hold in seconds";setTextColor(Color.BLACK);setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(String.format(java.util.Locale.ROOT,"%.3f",expected.durations(defaultHold())[index]/1000.0))}
        fun valid()=alive&&!isFinishing&&!isDestroyed&&job==null&&selection===expected
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Photo ${index+1} hold").setMessage("$label\nChoose 1–10 seconds with up to three decimal places. This affects preparation only; the video timeline stays editable. Use default follows the duration menu for this photo.").setView(field)
            .setNegativeButton("Cancel",null).setNeutralButton("Use default"){_,_->if(valid()){selection=expected.withHold(index,null);renderSelection()}}.setPositiveButton("Set hold",null).create()
        dialog.setOnShowListener{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(!valid()){dialog.dismiss();return@setOnClickListener}
            runCatching{expected.withHold(index,VideoCaptions.seconds(field.text.toString()))}
                .onSuccess{selection=it;dialog.dismiss();renderSelection();note.text="Photo timing updated. Prepared video will use this ordered plan."}
                .onFailure{field.error="Choose 1–10 seconds with up to three decimal places."}
        }};dialog.show()
    }
    private fun loadPhotoNames(){
        val request=++nameRequest;val references=selection.items.toList()
        references.forEachIndexed{index,uri->photoNames.putIfAbsent(uri,"Selected photo ${index+1}")}
        worker.execute{
            val found=references.associateWith{value->runCatching{
                contentResolver.query(Uri.parse(value),arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
                    if(cursor.moveToFirst())PhotoVideoSelection.displayName(cursor.getString(0))else null
                }
            }.getOrNull()}
            runOnUiThread{if(alive&&request==nameRequest){found.forEach{(uri,label)->if(label!=null&&uri in selection.items)photoNames[uri]=label};renderSelection()}}
        }
    }
    private fun begin(){
        val label=name.text.toString().trim();val chosen=PhotoVideoFormat.entries[format.selectedItemPosition];val plan=selection.durations(defaultHold())
        val checked=runCatching{require(photos.size in 1..20){"Choose 1–20 photos first"};PhotoVideoProject("12345678-1234-1234-1234-123456789abc",label,packageName+".creation",chosen,listOf(PhotoVideoAsset(1,"0".repeat(64))))}
        if(checked.isFailure){note.text=checked.exceptionOrNull()?.message?:"Check the project settings";return}
        val value=Job(photos.toList(),chosen,plan);job=value;setBusy(true);note.text="Preparing ${photos.size} photo clips locally…"
        worker.execute{val result=runCatching{
            val staging=PhotoVideoProjectStore(this).begin(label,chosen){requireCurrent(value)}
            value.staging=staging
            if(!alive||job!==value||value.cancelled.get()){PhotoVideoProjectStore(this).abandon(staging);error("Preparation cancelled")}
            staging
        };runOnUiThread{if(alive&&job===value&&!value.cancelled.get())result.onSuccess{prepare(value,0)}.onFailure{fail(value,it)}}}
    }
    private fun setBusy(busy:Boolean){start.isEnabled=!busy&&selection.items.isNotEmpty();choose.isEnabled=!busy;name.isEnabled=!busy;format.isEnabled=!busy;duration.isEnabled=!busy;renderSelection()}
    private fun prepare(value:Job,index:Int){
        if(!alive||job!==value||value.cancelled.get())return
        if(index==value.photos.size){commit(value);return}
        note.text="Preparing photo ${index+1}/${value.photos.size} · original files kept"
        val staging=requireNotNull(value.staging);val image=File(staging.directory,"input.png");val output=File(staging.directory,PhotoVideoProjectCodec.filename(index))
        worker.execute{val normalized=runCatching{
            requireCurrent(value);require(contentResolver.getType(value.photos[index])?.startsWith("image/")==true){"A selected document is not a photo"}
            val bitmap=PhotoSourceDecoder.decode(this,value.photos[index],1280)
            try{requireCurrent(value);FileOutputStream(image).use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)){"Photo preparation failed"}}}finally{bitmap.recycle()}
            requireCurrent(value)
        };runOnUiThread{if(!alive||job!==value||value.cancelled.get())return@runOnUiThread
            normalized.onFailure{fail(value,it)}.onSuccess{
                val current=PhotoVideoEncoder(this);encoder=current
                current.start(image,output,value.format,value.durations[index]){encoded->encoder=null
                    encoded.onFailure{fail(value,it)}.onSuccess{verify(value,index,image,output)}
                }
            }
        }}
    }
    private fun verify(value:Job,index:Int,image:File,output:File){
        worker.execute{val result=runCatching{
            requireCurrent(value);check(output.isFile&&output.length()>0){"Video preparation produced no output"}
            val reader=MediaMetadataRetriever();val duration=try{reader.setDataSource(output.path);require(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)=="yes");reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0}finally{reader.release()}
            PhotoVideoSelection.requirePreparedDuration(value.durations[index],duration)
            val extractor=MediaExtractor();try{extractor.setDataSource(output.path);require((0 until extractor.trackCount).any{extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)=="video/avc"}){"Prepared clip codec is unavailable"}}finally{extractor.release()}
            val digest=FileInputStream(output).use{PhotoProjectCodec.fingerprint(it){requireCurrent(value)}};image.delete();PhotoVideoAsset(duration,digest)
        };runOnUiThread{if(!alive||job!==value||value.cancelled.get())return@runOnUiThread;result.onSuccess{value.assets.add(it);prepare(value,index+1)}.onFailure{fail(value,it)}}}
    }
    private fun commit(value:Job){
        note.text="Saving private photo video project…"
        worker.execute{val result=runCatching{PhotoVideoProjectStore(this).commit(requireNotNull(value.staging),value.assets.toList()){requireCurrent(value)}}
            runOnUiThread{if(!alive||job!==value||value.cancelled.get())return@runOnUiThread;result.onSuccess{project->
                job=null;setBusy(false);startActivity(PhotoVideoProjectsActivity.editorIntent(this,project,project.initialDraft()));finish()
            }.onFailure{fail(value,it)}}}
    }
    private fun fail(value:Job,failure:Throwable){if(alive&&job===value)cancel(value,failure.message?:"Video preparation failed. Original photos were kept.")}
    private fun cancel(value:Job,message:String){
        value.cancelled.set(true);encoder?.cancel();encoder=null
        worker.execute{value.staging?.let{runCatching{PhotoVideoProjectStore(this).abandon(it)}};runOnUiThread{if(alive&&job===value){job=null;setBusy(false);note.text=message}}}
    }
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);if(::name.isInitialized){out.putString("name",name.text.toString());out.putInt("format",format.selectedItemPosition);out.putInt("duration",duration.selectedItemPosition);out.putStringArrayList("photos",ArrayList(photos.map{it.toString()}));out.putLongArray("photoHolds",selection.savedHolds())}}
    override fun onDestroy(){alive=false;nameRequest++;job?.let{it.cancelled.set(true);encoder?.cancel();encoder=null;worker.execute{it.staging?.let{staging->runCatching{PhotoVideoProjectStore(this).abandon(staging)}}}};worker.shutdown();super.onDestroy()}
}
