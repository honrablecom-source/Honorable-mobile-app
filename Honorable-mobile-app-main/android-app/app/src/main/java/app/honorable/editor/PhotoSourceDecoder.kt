package app.honorable.editor

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri

/** Bounded source decode shared by photo editing and local photo-video preparation. Never writes a source. */
object PhotoSourceDecoder {
 fun decode(context:Context,source:Uri,limit:Int):Bitmap {
  require(limit in 1..8192)
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};context.contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,bounds)}
  require(bounds.outWidth>0&&bounds.outHeight>0){"Unsupported image"}
  val runtime=Runtime.getRuntime();val headroom=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory());require(headroom>8L*1024*1024){"Not enough memory. Close other tools and try again."}
  val low=(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
  val maxPixels=(headroom/24).coerceAtMost(if(low)1_500_000L else 6_000_000L)
  val sample=PhotoDecodeBudget.sampleSize(bounds.outWidth,bounds.outHeight,limit,maxPixels)
  val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888}
  val decoded=context.contentResolver.openInputStream(source).use{BitmapFactory.decodeStream(it,null,options)}?:error("Unable to decode image")
  val orientation=runCatching{context.contentResolver.openInputStream(source).use{android.media.ExifInterface(requireNotNull(it)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1)
  val matrix=Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)}
  if(orientation==1)return decoded
  try {val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true);if(oriented!==decoded)decoded.recycle();return oriented}catch(failure:Throwable){decoded.recycle();throw failure}
 }
}
