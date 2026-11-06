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
    private val planGate=PhotoVideoPlanGate()
    private lateinit var savePlan:Button;private lateinit var openPlans:Button
    private var planDialog:android.app.AlertDialog?=null
    private data class PlanView(val selection:PhotoVideoSelection,val name:String,val format:PhotoVideoFormat,val defaultMs:Long)
    private fun editingLocked()=job!=null||planGate.busy()
    private fun planView()=PlanView(selection,name.text.toString(),PhotoVideoFormat.entries[format.selectedItemPosition],defaultHold())
    private fun samePlanView(view:PlanView)=selection===view.selection&&name.text.toString()==view.name&&PhotoVideoFormat.entries[format.selectedItemPosition]==view.format&&defaultHold()==view.defaultMs
    private fun planStore()=PhotoVideoPlanStore(File(filesDir,"photo-video-preparation-plans"))
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
        choose=button("Choose photos"){if(!editingLocked())pickPhotos()}
        addPhotos=button("Add more photos"){if(!editingLocked()&&selection.items.size<20)pickPhotos(true)}
        fitTiming=button("Fit default holds to total"){fitPhotoTiming()}
        undoSelection=button("Undo photo edit"){restoreSelectionEdit(false)}
        redoSelection=button("Redo photo edit"){restoreSelectionEdit(true)}
        body.addView(TextView(this).apply{text="Undo/Redo keeps the last 20 selection, order and individual hold edits while this screen stays open. Default hold, format and name are separate settings. Reopening restores the current selection without edit history.";setTextColor(Color.DKGRAY)})
        review=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(review)
        body.addView(TextView(this).apply{text="Preparation plans keep photo locations, order and timing privately on this device before encoding. Save keeps read access where Android allows; photos are not copied and access can still expire. Prepared video projects are separate.";setTextColor(Color.DKGRAY)})
        savePlan=button("Save preparation plan"){savePreparationPlan()}
        openPlans=button("Open preparation plans"){openPreparationPlans()}
        start=button("Create editable video project"){if(!editingLocked())begin()}
        button("Cancel preparation"){job?.let{cancel(it,"Preparation cancelled. Original photos and completed projects were kept.")}}
        note=TextView(this).apply{setTextColor(Color.DKGRAY)};body.addView(note)
        val recovered=runCatching{PhotoVideoEntry.initial(state,intent)}
        selectionHistory.reset(recovered.getOrElse{PhotoVideoSelection.of(emptyList())})
        note.text=if(photos.isEmpty())"No photos selected."else"${photos.size} photos selected. Start again when ready."
        if(recovered.isFailure)note.text="The previous selection could not be restored. Choose your photos again; originals were kept."
        setContentView(ScrollView(this).apply{setBackgroundColor(Color.WHITE);addView(body)})
        renderSelection();loadPhotoNames()
        duration.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){if(!editingLocked())renderSelection()}
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
        }
    }
    private fun pickPhotos(append:Boolean=false){try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);putExtra(Intent.EXTRA_LOCAL_ONLY,true);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},if(append)402 else 401)}catch(failure:Exception){note.text="No local photo picker is available."}}
    @Deprecated("Platform result API") override fun onActivityResult(request:Int,result:Int,data:Intent?){
        super.onActivityResult(request,result,data);if(request !in setOf(401,402)||result!=RESULT_OK||editingLocked()||!alive||isFinishing||isDestroyed)return
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
        if(::savePlan.isInitialized)savePlan.isEnabled=!editingLocked()&&selection.items.isNotEmpty()
        if(::openPlans.isInitialized)openPlans.isEnabled=!editingLocked()
        if(::start.isInitialized)start.isEnabled=!editingLocked()&&selection.items.isNotEmpty()
        if(::addPhotos.isInitialized)addPhotos.isEnabled=!editingLocked()&&selection.items.size<20
        if(::fitTiming.isInitialized)fitTiming.isEnabled=!editingLocked()&&selection.items.any{it !in selection.holds}
        if(::choose.isInitialized)choose.text=if(selection.items.isEmpty())"Choose photos"else"Replace photo selection"
        if(::undoSelection.isInitialized)undoSelection.isEnabled=!editingLocked()&&selectionHistory.canUndo
        if(::redoSelection.isInitialized)redoSelection.isEnabled=!editingLocked()&&selectionHistory.canRedo
        val expected=selection
        val plan=expected.durations(defaultHold())
        review.addView(TextView(this).apply{text=if(expected.items.isEmpty())"No photos in this selection."else "Photo order · ${expected.items.size} selected · about ${String.format(java.util.Locale.ROOT,"%.1f",plan.sum()/1000.0)} seconds. Set holds, move or remove before preparation; original photos are kept.";setTextColor(Color.DKGRAY)})
        expected.items.forEachIndexed{index,uri->
            val label=photoNames[uri]?:"Selected photo ${index+1}"
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            row.addView(TextView(this).apply{text="${index+1}. $label";setTextColor(Color.BLACK);setPadding(0,12,0,0)})
            row.addView(Button(this).apply{
                text="Hold ${String.format(java.util.Locale.ROOT,"%.3f",plan[index]/1000.0)} s · ${if(uri in expected.holds)"custom"else "default"}"
                contentDescription="Set photo ${index+1} hold: $label";app.honorable.editor.EditorChrome.style(this);isEnabled=!editingLocked()
                setOnClickListener{if(alive&&!editingLocked()&&selection===expected)editHold(index,expected,label)}
            })
            val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            fun action(text:String,enabled:Boolean,update:()->PhotoVideoSelection){
                controls.addView(Button(this).apply{
                    this.text=text;contentDescription="$text photo ${index+1}: $label";app.honorable.editor.EditorChrome.style(this);isEnabled=!editingLocked()&&enabled
                    setOnClickListener{
                        if(!alive||editingLocked()||selection!==expected)return@setOnClickListener
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
        if(!alive||isFinishing||isDestroyed||editingLocked()||selection.items.none{it !in selection.holds})return
        val expected=selection;val range=expected.fitTotalRange()
        fun seconds(ms:Long)=String.format(java.util.Locale.ROOT,"%.3f",ms/1000.0)
        fun valid()=alive&&!isFinishing&&!isDestroyed&&!editingLocked()&&selection===expected
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
        if(!alive||isFinishing||isDestroyed||editingLocked())return
        val changed=if(redo)selectionHistory.redo()else selectionHistory.undo()
        if(!changed)return
        renderSelection();loadPhotoNames()
        note.text="${if(redo)"Redid"else "Undid"} photo selection edit. ${selection.items.size} photos selected; originals are unchanged."
    }
    private fun defaultHold():Long=listOf(3L,5L,8L,10L)[duration.selectedItemPosition]*1000
    private fun editHold(index:Int,expected:PhotoVideoSelection,label:String){
        val field=EditText(this).apply{hint="Hold in seconds";contentDescription="Photo hold in seconds";setTextColor(Color.BLACK);setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(String.format(java.util.Locale.ROOT,"%.3f",expected.durations(defaultHold())[index]/1000.0))}
        fun valid()=alive&&!isFinishing&&!isDestroyed&&!editingLocked()&&selection===expected
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
    private fun <T> planWork(action:(()->Unit)->T,success:(T)->Unit){
        if(!alive||isFinishing||isDestroyed||editingLocked())return
        val lease=planGate.begin()?:return
        planDialog?.dismiss();planDialog=null
        val expected=planView();setBusy(true);note.text="Working with private preparation plan metadata… Original photos are kept."
        worker.execute{
            val result=runCatching{action{check(alive&&planGate.current(lease)){"Preparation plan operation was cancelled"}}}
            runOnUiThread{
                if(!alive)return@runOnUiThread
                val accept=planGate.finish(lease);setBusy(editingLocked())
                if(!accept)return@runOnUiThread
                if(!samePlanView(expected)){note.text="Plan settings changed. Open the preparation shelf to check saved plans.";return@runOnUiThread}
                result.onSuccess(success).onFailure{note.text=it.message?:"Preparation plan operation failed. Current photos and saved plans were kept."}
            }
        }
    }
    private fun readablePlan(plan:PhotoVideoPlan,requireCurrent:()->Unit){
        plan.selection.items.forEach{reference->
            requireCurrent();val uri=Uri.parse(reference)
            require(uri.scheme=="content"&&contentResolver.getType(uri)?.startsWith("image/")==true){"A planned photo is unavailable or is no longer an image. Current work and the saved plan were kept."}
            contentResolver.openFileDescriptor(uri,"r").use{check(it!=null){"A planned photo is unreadable. Current work and the saved plan were kept."}}
            requireCurrent()
        }
    }
    private fun savePreparationPlan(){
        if(!alive||isFinishing||isDestroyed||editingLocked()||planGate.review()==null)return
        val view=planView()
        val plan=runCatching{PhotoVideoPlan(java.util.UUID.randomUUID().toString(),view.name.trim(),view.format,view.defaultMs,view.selection)}
            .getOrElse{note.text=it.message?:"Check the plan name and chosen photos.";return}
        planWork({current->
            readablePlan(plan,current);planStore().save(plan,current)
            // Retain only an already offered read grant, only after this explicit save commits.
            var kept=0
            plan.selection.items.forEach{reference->
                current();val uri=Uri.parse(reference)
                val retained=try{
                    contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission}
                }catch(_:Exception){false}
                if(retained)kept++
                current()
            }
            kept
        }){kept->
            note.text="Saved preparation plan. No clips were encoded. Kept read access to $kept/${plan.selection.items.size} photos where Android allowed; other access follows current library permissions. You may need permission again."
        }
    }
    private fun openPreparationPlans(){
        planWork({current->planStore().list(current)}){rows->showPlanShelf(rows)}
    }
    private fun showPlanShelf(rows:List<PhotoVideoPlanStore.Summary>){
        if(rows.isEmpty()){note.text="No preparation plans saved. Choose photos, set their order and holds, then save a plan before encoding.";return}
        val epoch=planGate.review()?:return;val expected=planView()
        lateinit var dialog:android.app.AlertDialog
        dialog=android.app.AlertDialog.Builder(this).setTitle("Preparation plans · ${rows.size}/20")
            .setItems(rows.map{if(it.readable)it.name else "${it.name} · ${it.id.take(8)}"}.toTypedArray()){_,index->
                if(planDialog!==dialog||!alive||isFinishing||isDestroyed||!planGate.canReview(epoch)||!samePlanView(expected))return@setItems
                planDialog=null;val row=rows[index]
                if(row.readable)planWork({current->
                    val record=planStore().load(row.id,current)
                    require(record.token==row.token){"Saved plan changed. Open the shelf again."}
                    readablePlan(record.plan,current);record
                }){record->reviewPreparationPlan(record)}
                else confirmPlanDelete(row)
            }.setNegativeButton("Cancel",null).create()
        showOwnedPlanDialog(dialog)
    }
    private fun showOwnedPlanDialog(dialog:android.app.AlertDialog){
        planDialog?.dismiss();planDialog=dialog
        dialog.setOnDismissListener{if(planDialog===dialog)planDialog=null}
        dialog.show()
    }
    private fun showPlanReview(title:String,message:String,positive:Pair<String,()->Unit>?,neutral:Pair<String,()->Unit>?=null){
        val epoch=planGate.review()?:return;val expected=planView()
        lateinit var dialog:android.app.AlertDialog
        fun act(action:()->Unit){
            if(planDialog!==dialog||!alive||isFinishing||isDestroyed||!planGate.canReview(epoch)||!samePlanView(expected))return
            planDialog=null;action()
        }
        val builder=android.app.AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null)
        positive?.let{button->builder.setPositiveButton(button.first){_,_->act(button.second)}}
        neutral?.let{button->builder.setNeutralButton(button.first){_,_->act(button.second)}}
        dialog=builder.create();showOwnedPlanDialog(dialog)
    }
    private fun reviewPreparationPlan(record:PhotoVideoPlanStore.Record){
        val plan=record.plan
        val total=String.format(java.util.Locale.ROOT,"%.1f",plan.selection.durations(plan.defaultMs).sum()/1000.0)
        showPlanReview("Open preparation plan?","${plan.name}\n${plan.selection.items.size} photos · about $total seconds\n${plan.format.title} · default ${plan.defaultMs/1000} seconds\n\nReplaces the current chosen photos, order, holds, name and format, and clears selection Undo/Redo. No encoding starts. Original photos are not copied; access can expire. Review everything before Create.",
            "Open plan" to {
                selectionHistory.reset(plan.selection);name.setText(plan.name);format.setSelection(plan.format.ordinal)
                duration.setSelection(PhotoVideoPlan.DEFAULT_HOLDS.indexOf(plan.defaultMs));photoNames.clear();renderSelection();loadPhotoNames()
                note.text="Preparation plan opened for review. No clips were encoded; current selection Undo/Redo was reset."
            },
            "Delete plan" to {confirmPlanDelete(PhotoVideoPlanStore.Summary(plan.id,plan.name,true,record.token))})
    }
    private fun confirmPlanDelete(row:PhotoVideoPlanStore.Summary){
        val token=row.token
        if(token==null){showPlanReview("Preparation plan kept","This saved plan is too large or cannot be read safely. It was kept unchanged. No photos or prepared projects were deleted.",null);return}
        showPlanReview("Delete preparation plan?","${row.name}\n\n${if(row.readable)""else "This plan is unreadable and was not repaired. "}Delete only this exact saved metadata record? Original photos, the current selection and prepared video projects stay. Shared photo permissions are not revoked. This cannot be undone.",
            "Delete metadata" to {planWork({current->planStore().delete(row.id,token,current)}){note.text="Deleted preparation plan metadata. Current photos, originals and prepared video projects were kept."}})
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
    private fun setBusy(busy:Boolean){val locked=busy||editingLocked();start.isEnabled=!locked&&selection.items.isNotEmpty();choose.isEnabled=!locked;name.isEnabled=!locked;format.isEnabled=!locked;duration.isEnabled=!locked;if(::savePlan.isInitialized)savePlan.isEnabled=!locked&&selection.items.isNotEmpty();if(::openPlans.isInitialized)openPlans.isEnabled=!locked;renderSelection()}
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
    override fun onResume(){super.onResume();planGate.resume();if(::start.isInitialized)setBusy(editingLocked())}
    override fun onPause(){planGate.pause();planDialog?.dismiss();planDialog=null;super.onPause()}
    override fun onDestroy(){planGate.destroy();planDialog?.dismiss();planDialog=null;alive=false;nameRequest++;job?.let{it.cancelled.set(true);encoder?.cancel();encoder=null;worker.execute{it.staging?.let{staging->runCatching{PhotoVideoProjectStore(this).abandon(staging)}}}};worker.shutdown();super.onDestroy()}
}
