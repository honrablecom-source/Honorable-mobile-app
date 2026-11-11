package com.honorablemobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import app.honorable.editor.EditorAccessMode
import app.honorable.editor.ImportedPhotoOriginals
import app.honorable.search.MediaRecord
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.LifecycleState
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

internal class PhotoDifferencePreview(val owner:String,val originals:List<LibraryCollectionItem>,val file:File,val plan:PhotoPixelDifference.Plan,val threshold:Int,val changed:Int,val maximum:Int,val mean:Double,val operation:String="difference",val removedColumns:Int=0,val decodedSampled:Boolean=plan.sampled){
    val token=UUID.randomUUID().toString()
    fun dispose(){file.delete()}
    fun uri(context:ReactApplicationContext):Uri=FileProvider.getUriForFile(context,context.packageName+".creation",file)
    fun map(context:ReactApplicationContext):WritableMap=Arguments.createMap().apply{
        putString("token",token);putString("previewUri",uri(context).toString());putInt("width",plan.size.width);putInt("height",plan.size.height)
        putInt("sourceWidth",plan.sourceWidth);putInt("sourceHeight",plan.sourceHeight);putBoolean("sampled",decodedSampled)
        putInt("threshold",threshold);putInt("changedPixels",changed);putInt("maximumDelta",maximum);putDouble("meanDelta",mean)
        putString("operation",operation);putInt("frames",originals.size);putInt("removedColumns",removedColumns)
    }
}

internal object PhotoPixelDifferenceBridge {
    private const val OUTPUT_LIMIT=16L*1024*1024
    fun requireCurrent(context:ReactApplicationContext,owner:String,originals:List<LibraryCollectionItem>,records:()->List<MediaRecord>){
        val activity=context.currentActivity
        check(activity!=null&&!activity.isDestroyed&&!activity.isFinishing&&context.lifecycleState==LifecycleState.RESUMED){"Open Library again before using this map"}
        check(VideoPrivateOwner.namespace(context,EditorAccessMode.ACCOUNT)==owner){"Account changed. Prepare the map again"}
        val current=records().associateBy{it.id.toString()}
        check(originals.all{e->current[e.id]?.let{it.uri==e.uri&&it.kind.name==e.kind}==true}){"A source changed. Select the original photos again"}
    }
    fun prepare(context:ReactApplicationContext,items:ReadableArray,threshold:Double,request:LibraryDuplicateRequest,median:Boolean=false,focus:Boolean=false,retarget:Int=0,exposure:Boolean=false,records:()->List<MediaRecord>):PhotoDifferencePreview {
        require(threshold.isFinite()&&threshold==threshold.toInt().toDouble()&&threshold in 0.0..255.0){"Use a whole threshold from 0 to 255"}
        require(!exposure||(!median&&!focus&&retarget==0)){"Choose one photo operation"}
        require(!(median&&focus)){"Choose one photo computation"}
        require(retarget in 0..12&&(retarget==0||(!median&&!focus))){"Choose one bounded photo operation"}
        if(retarget>0)require(items.size()==1){"Select exactly one photo"}else if(focus)require(items.size() in 2..5){"Select two to five aligned focus-bracket photos"}else if(median)require(items.size()==3||items.size()==5){"Select three or five aligned photos"}else require(items.size()==2){"Select exactly two photos"}
        val originals=(0 until items.size()).map{i->val item=requireNotNull(items.getMap(i));LibraryCollectionItem(requireNotNull(item.getString("id")),requireNotNull(item.getString("uri")),requireNotNull(item.getString("kind")))}
        require(originals.all{it.kind=="IMAGE"}&&originals.map{it.id}.distinct().size==originals.size&&originals.map{it.uri}.distinct().size==originals.size){"Select different photo originals"}
        ImportedPhotoOriginals.validate(originals.map{it.uri})
        val owner=requireNotNull(VideoPrivateOwner.namespace(context,EditorAccessMode.ACCOUNT)){"Sign in to compare Library photos"}
        val started=System.nanoTime()
        fun active(){request.requireActive();val activity=context.currentActivity;check(activity!=null&&!activity.isFinishing&&!activity.isDestroyed&&context.lifecycleState==LifecycleState.RESUMED){"Photo comparison stopped"};check(System.nanoTime()-started<60_000_000_000L){"Photo comparison timed out"}}
        fun current(){active();requireCurrent(context,owner,originals,records)}
        val budget=PhotoDifferenceReadBudget(current=::active)
        fun <T> read(original:LibraryCollectionItem,block:(InputStream)->T):T {
            active()
            val asset=context.contentResolver.openAssetFileDescriptor(Uri.parse(original.uri),"r",request.signal)?:error("Source photo is unavailable")
            try{
                val input=asset.createInputStream()
                try{
                    request.bind(input)
                    val limited=budget.wrap(input)
                    val result=block(limited);limited.finish();active();return result
                }finally{request.detach(input);runCatching{input.close()}}
            }finally{asset.close()}
        }
        current()
        val runtime=Runtime.getRuntime();check(runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())>=(if(exposure)96L else if(retarget>0)96L else if(median||focus)96L else 64L)*1024*1024){"Not enough free memory. Close another workspace and try again"}
        fun metadata(original:LibraryCollectionItem):PhotoPixelDifference.Original {
            val options=BitmapFactory.Options().apply{inJustDecodeBounds=true;inScaled=false}
            read(original){BitmapFactory.decodeStream(it,null,options)};active()
            require(options.outWidth>0&&options.outHeight>0){"A selected photo format could not be decoded"}
            PhotoPixelDifference.Original(options.outWidth,options.outHeight,1)
            val orientation=try{read(original){ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}}catch(_:java.io.IOException){active();1}catch(_:IllegalArgumentException){active();1}
            val normalized=if(orientation==0)1 else orientation
            current();return PhotoPixelDifference.Original(options.outWidth,options.outHeight,normalized)
        }
        var file:File?=null;var output:Bitmap?=null
        try {
            val metadata=originals.map(::metadata)
            val plan=if(retarget>0)PhotoPixelDifference.plan(metadata[0],metadata[0])else PhotoPixelDifference.plan(metadata[0],metadata[1])
            metadata.drop(2).forEach{PhotoPixelDifference.plan(metadata[0],it)}
            fun pixels(original:LibraryCollectionItem,metadata:PhotoPixelDifference.Original):IntArray {
                var bitmap:Bitmap?=null
                try{
                    current()
                    bitmap=read(original){BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=plan.sample;inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888})}?:error("Photo could not be decoded")
                    active();PhotoPixelDifference.Size(bitmap.width,bitmap.height)
                    val raw=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(raw,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    return PhotoPixelDifference.normalize(raw,bitmap.width,bitmap.height,metadata.orientation,plan.size,::active)
                }finally{bitmap?.recycle()}
            }
            val frames=originals.mapIndexed{index,original->pixels(original,metadata[index])}
            current()
            val carved=if(retarget>0)PhotoRetarget.percent(plan.size,frames[0],retarget,::active)else null
            val outputPlan=if(carved!=null)plan.copy(size=PhotoPixelDifference.Size(carved.width,carved.height))else plan
            val result=if(exposure)PhotoExposureBlend.create(plan.size,frames[0],frames[1],::active)else if(carved!=null)PhotoPixelDifference.Result(carved.pixels,0,0,0.0)else if(focus){val stack=PhotoFocusStack.create(plan.size,frames,::active);PhotoPixelDifference.Result(stack.pixels,stack.changedPixels,0,0.0)}else if(median){val plate=PhotoCleanPlate.create(plan.size,frames,::active);PhotoPixelDifference.Result(plate.pixels,plate.changedPixels,0,0.0)}else PhotoPixelDifference.compare(plan.size,frames[0],frames[1],threshold.toInt(),::active)
            output=Bitmap.createBitmap(result.pixels,outputPlan.size.width,outputPlan.size.height,Bitmap.Config.ARGB_8888)
            val directory=File(context.cacheDir,"created-images");check(directory.canonicalFile==File(context.cacheDir.canonicalFile,"created-images"));check(directory.isDirectory||directory.mkdirs())
            file=if(exposure)File.createTempFile("noble-exposure-blend-",".png",directory)else if(retarget>0)File.createTempFile("noble-retarget-",".png",directory)else if(focus)File.createTempFile("noble-focus-stack-",".png",directory)else if(median)File.createTempFile("noble-clean-plate-",".png",directory)else File.createTempFile("noble-pixel-difference-",".png",directory)
            current();FileOutputStream(file).use{stream->check(output.compress(Bitmap.CompressFormat.PNG,100,stream)){"Difference PNG encoding failed"};stream.fd.sync()}
            current();check(file.isFile&&file.length() in 1..OUTPUT_LIMIT){"Difference PNG exceeds its output limit"}
            return PhotoDifferencePreview(owner,originals.toList(),file,outputPlan,threshold.toInt(),result.changedPixels,result.maximumDelta,result.meanDelta,if(exposure)"exposure-blend"else if(retarget>0)"retarget"else if(focus)"focus-stack"else if(median)"clean-plate"else"difference",carved?.removedColumns?:0,plan.sampled)
        }catch(error:OutOfMemoryError){file?.delete();throw IllegalStateException("Not enough memory for this photo comparison",error)}
         catch(error:Throwable){file?.delete();throw error}
         finally{output?.recycle()}
    }
    fun open(context:ReactApplicationContext,preview:PhotoDifferencePreview,records:()->List<MediaRecord>,beforeLaunch:()->Unit={}){
        requireCurrent(context,preview.owner,preview.originals,records)
        check(preview.file.isFile&&preview.file.length() in 1..OUTPUT_LIMIT){"The preview expired. Prepare another map"}
        val intent=Intent(context,app.honorable.editor.PhotoEditorActivity::class.java).apply{
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("uri",preview.uri(context).toString());putExtra(EditorAccessMode.EXTRA,EditorAccessMode.GUEST_BASIC.name)
            putStringArrayListExtra(ImportedPhotoOriginals.EXTRA,ArrayList(ImportedPhotoOriginals.validate(preview.originals.map{it.uri})))
        }
        beforeLaunch();context.startActivity(intent)
    }
}
