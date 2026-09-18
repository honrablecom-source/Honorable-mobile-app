package app.honorable.editor

import android.app.Activity
import app.honorable.auth.SafeBetaTelemetry
import org.json.JSONObject
import android.app.ActivityManager
import android.os.Bundle
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.view.ViewGroup
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.widget.*
import java.util.concurrent.Executors
import kotlin.math.pow

/** Local, non-destructive raster editor. Exports through CREATE_DOCUMENT; never writes source URI. */
class PhotoEditorActivity:Activity(){
 data class Edit(val rotation:Int=0,val crop:Float=1f,val brightness:Float=0f,val contrast:Float=1f,val saturation:Float=1f,val exposure:Float=0f)
 private val openedAt=System.currentTimeMillis()
 private fun tool(name:String){SafeBetaTelemetry.emit(this,"edit_tool_used",JSONObject().put("tool",name))}
 private val sliders=mutableListOf<SeekBar>();private var edit=Edit();private val undo=mutableListOf<Edit>();private val redo=mutableListOf<Edit>();private var before=false
 private lateinit var image:ImageView;private lateinit var note:TextView;private lateinit var source:Uri
 private var bitmap:Bitmap?=null;private var rendered:Bitmap?=null;private val worker=Executors.newSingleThreadExecutor();private var revision=0
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);source=Uri.parse(intent.getStringExtra("uri")?:run{finish();return})
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK);setPadding(12.dp(),24.dp(),12.dp(),16.dp())}
 fun row()=LinearLayout(this).also{root.addView(it)}
 fun button(parent:LinearLayout,label:String,action:()->Unit){parent.addView(Button(this).apply{text=label;isAllCaps=false;textSize=12f;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.rgb(16,16,16));cornerRadius=6.dp().toFloat()};setOnClickListener{action()}},LinearLayout.LayoutParams(0,52.dp(),1f))}
 val top=row();button(top,"Back"){finish()};button(top,"Undo"){if(undo.isNotEmpty()){tool("undo");redo.add(edit);edit=undo.removeAt(undo.lastIndex);preview()}};button(top,"Redo"){if(redo.isNotEmpty()){tool("redo");undo.add(edit);edit=redo.removeAt(redo.lastIndex);preview()}}
 image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER};root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
 note=TextView(this).apply{setTextColor(Color.LTGRAY);text="Loading original…"};root.addView(note)
 val actions=row();button(actions,"Compare"){before=!before;preview()};button(actions,"Reset"){tool("reset");change(Edit())};button(top,"Save copy"){if(bitmap!=null)startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/png";putExtra(Intent.EXTRA_TITLE,"Honorable-copy.png")},20)}
 val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8.dp(),8.dp(),8.dp(),8.dp())};root.addView(panel)
 val groups=mutableListOf<LinearLayout>()
 fun adjust(name:String,max:Int,value:Int,update:(Int)->Edit){
  val group=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE};groups.add(group);panel.addView(group)
  val label=TextView(this).apply{text="$name   0";setTextColor(Color.WHITE);textSize=14f};group.addView(label)
  group.addView(SeekBar(this).apply{this.max=max;progress=value;contentDescription=name;sliders.add(this);progressTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){label.text="$name   ${v-value}"};override fun onStopTrackingTouch(s:SeekBar){tool(name.lowercase());change(update(s.progress))}})},LinearLayout.LayoutParams(-1,48.dp()))
 }
 adjust("Brightness",200,100){edit.copy(brightness=(it-100)*1.28f)};adjust("Contrast",200,100){edit.copy(contrast=it/100f)};adjust("Saturation",200,100){edit.copy(saturation=it/100f)};adjust("Exposure",400,200){edit.copy(exposure=(it-200)/100f)}
 val toolScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false};val rail=LinearLayout(this);toolScroll.addView(rail);root.addView(toolScroll)
 val toolButtons=mutableListOf<Button>()
 listOf("Brightness","Contrast","Color","Exposure").forEachIndexed{i,name->
  val control=Button(this).apply{text=name;isAllCaps=false;textSize=12f;setTextColor(Color.LTGRAY);setBackgroundColor(Color.BLACK);setOnClickListener{groups.forEachIndexed{index,view->view.visibility=if(index==i)View.VISIBLE else View.GONE};toolButtons.forEachIndexed{index,view->view.isSelected=index==i;view.setTextColor(if(index==i)Color.WHITE else Color.GRAY)}}};toolButtons.add(control);rail.addView(control,LinearLayout.LayoutParams(104.dp(),48.dp()))
 }
 val transforms=row();button(transforms,"Rotate"){tool("rotate");change(edit.copy(rotation=(edit.rotation+90)%360))};button(transforms,"Crop center"){tool("crop");change(edit.copy(crop=if(edit.crop>.51f)edit.crop-.1f else 1f))};button(transforms,"Monochrome"){tool("monochrome");change(edit.copy(saturation=0f))}

 setContentView(root)
 SafeBetaTelemetry.emit(this,"editor_opened",JSONObject().put("mediaType","IMAGE"));
 worker.execute{try{SafeBetaTelemetry.authorizeEditor(this);val low=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice;if(low)SafeBetaTelemetry.emit(this,"performance_sample",JSONObject().put("metric","PROXY_USE").put("durationMs",0));val loaded=decode(if(low)768 else 1600);runOnUiThread{if(!isDestroyed){bitmap=loaded;preview()}else loaded.recycle()}}catch(e:Exception){runOnUiThread{note.text="Unable to open image: ${e.message}"}}}
 }
 private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
 private fun change(next:Edit){undo.add(edit);if(undo.size>50)undo.removeAt(0);redo.clear();edit=next;before=false;preview()}
 private fun decode(limit:Int):Bitmap{val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,bounds)};require(bounds.outWidth>0){"Unsupported image"};var sample=1;while(bounds.outWidth/sample>limit||bounds.outHeight/sample>limit)sample*=2;val options=BitmapFactory.Options().apply{inSampleSize=sample};val decoded=contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,options)}?:error("Unable to decode image");val orientation=runCatching{contentResolver.openInputStream(source).use{android.media.ExifInterface(requireNotNull(it)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1);val matrix=Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)};if(orientation==1)return decoded;val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true);if(oriented!==decoded)decoded.recycle();return oriented}
 private fun render(input:Bitmap,e:Edit):Bitmap{val w=(input.width*e.crop).toInt().coerceAtLeast(1);val h=(input.height*e.crop).toInt().coerceAtLeast(1);val rotated=e.rotation%180!=0;val output=Bitmap.createBitmap(if(rotated)h else w,if(rotated)w else h,Bitmap.Config.ARGB_8888);val canvas=Canvas(output);canvas.translate(output.width/2f,output.height/2f);canvas.rotate(e.rotation.toFloat());val gain=2f.pow(e.exposure)*e.contrast;val offset=e.brightness+128*(1-e.contrast);val matrix=ColorMatrix().apply{setSaturation(e.saturation);postConcat(ColorMatrix(floatArrayOf(gain,0f,0f,0f,offset,0f,gain,0f,0f,offset,0f,0f,gain,0f,offset,0f,0f,0f,1f,0f)))};canvas.drawBitmap(input,Rect((input.width-w)/2,(input.height-h)/2,(input.width+w)/2,(input.height+h)/2),RectF(-w/2f,-h/2f,w/2f,h/2f),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply{colorFilter=ColorMatrixColorFilter(matrix)});return output}
 private fun preview(){listOf((edit.brightness/1.28f+100).toInt(),(edit.contrast*100).toInt(),(edit.saturation*100).toInt(),(edit.exposure*100+200).toInt()).forEachIndexed{i,v->sliders.getOrNull(i)?.progress=v};val input=bitmap?:return;val state=if(before)Edit()else edit;val request=++revision;val started=System.currentTimeMillis();worker.execute{val result=render(input,state);runOnUiThread{if(request==revision&&!isDestroyed){val old=rendered;rendered=result;SafeBetaTelemetry.duration(this,"EDITOR_RESPONSE",started);image.setImageBitmap(result);old?.recycle();note.text=if(before)"Original preview" else "Edited preview · original preserved"}else result.recycle()}}}
 @Deprecated("Platform result API") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=20||resultCode!=RESULT_OK)return;val destination=data?.data?:return;val state=edit;note.text="Exporting copy…";worker.execute{try{SafeBetaTelemetry.authorizeEditor(this);val input=decode(8192);val output=render(input,state);try{contentResolver.openOutputStream(destination,"w").use{requireNotNull(it);check(output.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{input.recycle();output.recycle()};SafeBetaTelemetry.emit(this,"edit_saved",JSONObject().put("mediaType","IMAGE"));runOnUiThread{note.text="Copy saved. Original preserved. Export capped at 8192 px for memory safety."}}catch(e:Exception){SafeBetaTelemetry.emit(this,"client_error",JSONObject().put("errorCode","SAVE_FAILED"));runOnUiThread{note.text="Export failed: ${e.message}"}}}}
 override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)SafeBetaTelemetry.emit(this,"performance_sample",JSONObject().put("metric","MEMORY_PRESSURE").put("durationMs",0))}
 override fun onDestroy(){SafeBetaTelemetry.emit(this,"editor_closed",JSONObject().put("durationMs",(System.currentTimeMillis()-openedAt).coerceIn(0,3600000)));revision++;worker.shutdown();super.onDestroy()}
}
