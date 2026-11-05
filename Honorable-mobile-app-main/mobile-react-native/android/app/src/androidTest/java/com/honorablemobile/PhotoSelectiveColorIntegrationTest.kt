package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.Color
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test

class PhotoSelectiveColorIntegrationTest{
 @Test fun sharedRasterChangesOnlySelectedPhotoColorAndKeepsOriginal(){
  val source=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLUE);for(y in 0 until height)for(x in 0 until width/2)setPixel(x,y,Color.RED)}
  val edit=PhotoEdit(selectiveColor=PhotoSelectiveColor(enabled=true,hue=0f,radius=15f,softness=0f,shift=120f))
  try{val output=PhotoRasterRenderer.render(source,edit,null);try{assertEquals(Color.RED,source.getPixel(2,2));assertEquals(Color.GREEN,output.getPixel(2,2));assertEquals(Color.BLUE,output.getPixel(17,2))}finally{output.recycle()}}finally{source.recycle()}
 }
}
