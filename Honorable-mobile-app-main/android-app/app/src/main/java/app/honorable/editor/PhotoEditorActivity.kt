package app.honorable.editor

import android.app.Activity
import android.app.ActivityManager
import android.os.Bundle
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors
import kotlin.math.pow

/** Local, non-destructive raster editor. Exports through CREATE_DOCUMENT; never writes source URI. */
class PhotoEditorActivity:Activity(){
 data class Edit(val rotation:Int=0,val crop:Float=1f,val brightness:Float=0f,val contrast:Float=1f,val saturation:Float=1f,val exposure:Float=0f)
 private val sliders=mutableListOf<SeekBar>();private var edit=Edit();private val undo=mutableListOf<Edit>();private val redo=mutableListOf<Edit>();private var before=false
 private lateinit var image:ImageView;private lateinit var note:TextView;private lateinit var source:Uri
 private var bitmap:Bitmap?=null;private var rendered:Bitmap?=null;private val worker=Executors.newSingleThreadExecutor();private var revision=0
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);source=Uri.parse(intent.getStringExtra("uri")?:run{finish();return})
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK);setPadding(12,30,12,20)}
 fun row()=LinearLayout(this).also{root.addView(it)}
 fun button(parent:LinearLayout,label:String,action:()->Unit){parent.addView(Button(this).apply{text=label;setOnClickListener{action()}},LinearLayout.LayoutParams(0,52.dp(),1f))}
 val top=row();button(top,"Back"){finish()};button(top,"Undo"){if(undo.isNotEmpty()){redo.add(edit);edit=undo.removeAt(undo.lastIndex);preview()}};button(top,"Redo"){if(redo.isNotEmpty()){undo.add(edit);edit=redo.removeAt(redo.lastIndex);preview()}}
 image=ImageView(this).apply{scaleType=ImageView.ScaleType.FIT_CENTER};root.addView(image,LinearLayout.LayoutParams(-1,0,1f))
 note=TextView(this).apply{setTextColor(Color.LTGRAY);text="Loading original…"};root.addView(note)
 val actions=row();button(actions,"Before / After"){before=!before;preview()};button(actions,"Reset"){change(Edit())};button(actions,"Save as copy"){if(bitmap!=null)startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/png";putExtra(Intent.EXTRA_TITLE,"Honorable-copy.png")},20)}
 val scroll=ScrollView(this);val controls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(controls);root.addView(scroll,LinearLayout.LayoutParams(-1,220.dp()))
 fun adjust(name:String,max:Int,value:Int,update:(Int)->Edit){controls.addView(TextView(this).apply{text=name;setTextColor(Color.WHITE)});controls.addView(SeekBar(this).apply{this.max=max;progress=value;sliders.add(this);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){};override fun onProgressChanged(s:SeekBar,v:Int,user:Boolean){};override fun onStopTrackingTouch(s:SeekBar){change(update(s.progress))}})})}
 adjust("Brightness",200,100){edit.copy(brightness=(it-100)*1.28f)};adjust("Contrast",200,100){edit.copy(contrast=it/100f)};adjust("Saturation",200,100){edit.copy(saturation=it/100f)};adjust("Exposure",400,200){edit.copy(exposure=(it-200)/100f)}
 val transforms=row();button(transforms,"Rotate"){change(edit.copy(rotation=(edit.rotation+90)%360))};button(transforms,"Crop center"){change(edit.copy(crop=if(edit.crop>.51f)edit.crop-.1f else 1f))};button(transforms,"Mono"){change(edit.copy(saturation=0f))}
 setContentView(root)
 worker.execute{try{val low=(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice;val loaded=decode(if(low)768 else 1600);runOnUiThread{if(!isDestroyed){bitmap=loaded;preview()}else loaded.recycle()}}catch(e:Exception){runOnUiThread{note.text="Unable to open image: ${e.message}"}}}
 }
 private fun Int.dp()=(this*resources.displayMetrics.density).toInt()
 private fun change(next:Edit){undo.add(edit);if(undo.size>50)undo.removeAt(0);redo.clear();edit=next;before=false;preview()}
 private fun decode(limit:Int):Bitmap{val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,bounds)};require(bounds.outWidth>0){"Unsupported image"};var sample=1;while(bounds.outWidth/sample>limit||bounds.outHeight/sample>limit)sample*=2;val options=BitmapFactory.Options().apply{inSampleSize=sample};val decoded=contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,options)}?:error("Unable to decode image");val orientation=runCatching{contentResolver.openInputStream(source).use{android.media.ExifInterface(requireNotNull(it)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1);val matrix=Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)};if(orientation==1)return decoded;val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true);if(oriented!==decoded)decoded.recycle();return oriented}
 private fun render(input:Bitmap,e:Edit):Bitmap{val w=(input.width*e.crop).toInt().coerceAtLeast(1);val h=(input.height*e.crop).toInt().coerceAtLeast(1);val rotated=e.rotation%180!=0;val output=Bitmap.createBitmap(if(rotated)h else w,if(rotated)w else h,Bitmap.Config.ARGB_8888);val canvas=Canvas(output);canvas.translate(output.width/2f,output.height/2f);canvas.rotate(e.rotation.toFloat());val gain=2f.pow(e.exposure)*e.contrast;val offset=e.brightness+128*(1-e.contrast);val matrix=ColorMatrix().apply{setSaturation(e.saturation);postConcat(ColorMatrix(floatArrayOf(gain,0f,0f,0f,offset,0f,gain,0f,0f,offset,0f,0f,gain,0f,offset,0f,0f,0f,1f,0f)))};canvas.drawBitmap(input,Rect((input.width-w)/2,(input.height-h)/2,(input.width+w)/2,(input.height+h)/2),RectF(-w/2f,-h/2f,w/2f,h/2f),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply{colorFilter=ColorMatrixColorFilter(matrix)});return output}
 private fun preview(){listOf((edit.brightness/1.28f+100).toInt(),(edit.contrast*100).toInt(),(edit.saturation*100).toInt(),(edit.exposure*100+200).toInt()).forEachIndexed{i,v->sliders.getOrNull(i)?.progress=v};val input=bitmap?:return;val state=if(before)Edit()else edit;val request=++revision;worker.execute{val result=render(input,state);runOnUiThread{if(request==revision&&!isDestroyed){val old=rendered;rendered=result;image.setImageBitmap(result);old?.recycle();note.text=if(before)"Original preview" else "Edited preview · original preserved"}else result.recycle()}}}
 @Deprecated("Platform result API") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=20||resultCode!=RESULT_OK)return;val destination=data?.data?:return;val state=edit;note.text="Exporting copy…";worker.execute{try{val input=decode(8192);val output=render(input,state);try{contentResolver.openOutputStream(destination,"w").use{requireNotNull(it);check(output.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{input.recycle();output.recycle()};runOnUiThread{note.text="Copy saved. Original preserved. Export capped at 8192 px for memory safety."}}catch(e:Exception){runOnUiThread{note.text="Export failed: ${e.message}"}}}}
 override fun onDestroy(){revision++;worker.shutdown();super.onDestroy()}
}
