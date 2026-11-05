package app.honorable.editor

import android.graphics.Bitmap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/* Squared distance-transform routines adapted from TinySDF v2.0.7:
 https://github.com/mapbox/tiny-sdf/blob/v2.0.7/index.js
 Kotlin adaptation uses a Float grid and Double scratch values for bounded image masks.
BSD-2-Clause
Copyright (c) 2016-2024 Mapbox, Inc.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

data class PhotoStickerPixels(val width:Int,val height:Int,val pixels:IntArray)
object PhotoSticker {
 private const val INF=1e20
 fun pixels(source:IntArray,width:Int,height:Int,radius:Int,color:Int,requireCurrent:()->Unit={}):PhotoStickerPixels {
  require(width in 1..4096 && height in 1..4096 && width.toLong()*height<=4194304 && source.size==width*height){"Sticker source exceeds the image limit"}
  require(radius in 2..32 && color ushr 24==255){"Choose a border from 2 to 32 pixels and an opaque color"}
  requireCurrent()
  var transparent=false
  val bounds=PhotoAlphaTrim.bounds(width,height){y,row->
   if(y%64==0)requireCurrent()
   for(x in row.indices){val pixel=source[y*width+x];row[x]=pixel;if(pixel ushr 24==0)transparent=true}
  }?:error("Add visible artwork before making a sticker.")
  require(transparent){"Use a transparent PNG or a person cutout before making a sticker."}
  val padding=radius+2;val w=bounds.width+padding*2;val h=bounds.height+padding*2
  require(w<=4096 && h<=4096 && w.toLong()*h<=8388608){"The sticker is too large. Use a smaller image or border."}
  val output=IntArray(w*h)
  for(y in 0 until bounds.height){
   if(y%64==0)requireCurrent()
   for(x in 0 until bounds.width){val pixel=source[(y+bounds.y)*width+x+bounds.x];if(pixel ushr 24!=0)output[(y+padding)*w+x+padding]=pixel}
  }
  val grid=FloatArray(output.size){if(output[it] ushr 24==0)INF.toFloat() else 0f}
  val size=maxOf(w,h);val f=DoubleArray(size);val v=IntArray(size);val z=DoubleArray(size+1)
  for(x in 0 until w){if(x%64==0)requireCurrent();edt1d(grid,x,w,h,f,v,z)}
  for(y in 0 until h){if(y%64==0)requireCurrent();edt1d(grid,y*w,1,w,f,v,z)}
  for(y in 0 until h){
   if(y%64==0)requireCurrent()
   for(x in 0 until w){val i=y*w+x;if(output[i] ushr 24==0){
    val alpha=((radius+.5-sqrt(grid[i].toDouble())).coerceIn(0.0,1.0)*255).roundToInt()
    if(alpha>0)output[i]=(alpha shl 24) or (color and 0x00ffffff)
   }}
  }
  requireCurrent()
  return PhotoStickerPixels(w,h,output)
 }
 private fun edt1d(grid:FloatArray,offset:Int,stride:Int,length:Int,f:DoubleArray,v:IntArray,z:DoubleArray){
  v[0]=0;z[0]=-INF;z[1]=INF;f[0]=grid[offset].toDouble()
  var k=0
  for(q in 1 until length){
   f[q]=grid[offset+q*stride].toDouble();val q2=q.toDouble()*q
   var s:Double
   do{val r=v[k];s=(f[q]-f[r]+q2-r.toDouble()*r)/(q-r)/2;if(s>z[k])break;k--}while(k>=0)
   k++;v[k]=q;z[k]=s;z[k+1]=INF
  }
  k=0
  for(q in 0 until length){while(z[k+1]<q)k++;val r=v[k];val d=q-r;grid[offset+q*stride]=(f[r]+d.toDouble()*d).toFloat()}
 }
 fun render(input:Bitmap,radius:Int,color:Int,requireCurrent:()->Unit):Bitmap {
  require(input.width in 1..4096 && input.height in 1..4096 && input.width.toLong()*input.height<=4194304){"Sticker source exceeds the image limit"}
  requireCurrent()
  val source=IntArray(input.width*input.height)
  input.getPixels(source,0,input.width,0,0,input.width,input.height)
  val result=pixels(source,input.width,input.height,radius,color,requireCurrent)
  return Bitmap.createBitmap(result.pixels,result.width,result.height,Bitmap.Config.ARGB_8888)
 }
}
