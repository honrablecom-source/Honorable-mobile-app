package com.honorablemobile

import android.graphics.Bitmap
import android.graphics.Color
import app.honorable.editor.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic pixels only. Must execute on Android; host API compilation is not execution. */
class PhotoLayersIntegrationTest {
    @Test fun objectAlphaAndSourcePixelsArePreserved(){
        val source=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.TRANSPARENT)}
        val box=PhotoObject("box",PhotoObjectType.RECTANGLE,width=.4f,height=.4f,opacity=.5f)
        val output=PhotoRasterRenderer.render(source,PhotoEdit(layers=PhotoLayers(listOf(box))),null)
        try {assertEquals(127,Color.alpha(output.getPixel(50,50)));assertEquals(0,Color.alpha(output.getPixel(0,0)));assertEquals(0,Color.alpha(source.getPixel(50,50)))}finally{output.recycle();source.recycle()}
    }
    @Test fun visibilityAndStackingMatchTheSharedRenderer(){
        val source=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.TRANSPARENT)}
        val black=PhotoObject("black",PhotoObjectType.ELLIPSE,width=.6f,height=.6f)
        val white=PhotoObject("white",PhotoObjectType.RECTANGLE,width=.4f,height=.4f,color=Color.WHITE)
        for((layers,color) in listOf(PhotoLayers(listOf(black,white)) to Color.WHITE,PhotoLayers(listOf(black,white)).move("black",true) to Color.BLACK,PhotoLayers(listOf(black,white.copy(visible=false))) to Color.BLACK)){
            val result=PhotoRasterRenderer.render(source,PhotoEdit(layers=layers),null)
            try {assertEquals(color,result.getPixel(50,50))}finally{result.recycle()}
        }
        source.recycle()
    }
    @Test fun twoTextObjectsProduceIndependentInkOnBothSides(){
        val source=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.TRANSPARENT)}
        val left=PhotoObject("left",PhotoObjectType.TEXT,x=.25f,width=.4f,height=.4f,text="A",textSize=.15f)
        val right=left.copy(id="right",x=.75f,text="B")
        val result=PhotoRasterRenderer.render(source,PhotoEdit(layers=PhotoLayers(listOf(left,right))),null)
        try {var leftInk=0;var rightInk=0;for(y in 0 until result.height)for(x in 0 until result.width)if(Color.alpha(result.getPixel(x,y))>0){if(x<100)leftInk++ else rightInk++};assertTrue(leftInk>0);assertTrue(rightInk>0)}finally{result.recycle();source.recycle()}
    }
    @Test fun curvedOutlinedShadowTextUsesOneObjectOpacity(){
        val source=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.TRANSPARENT)}
        val text=PhotoObject("curve",PhotoObjectType.TEXT,width=.9f,height=.8f,text="Noble",textSize=.15f,curve=120f,opacity=.5f,textStyle=PhotoTextStyle(bold=true,outline=.01f,shadow=.02f))
        val result=PhotoRasterRenderer.render(source,PhotoEdit(layers=PhotoLayers(listOf(text))),null)
        try {var ink=0;var maximumAlpha=0;for(y in 0 until result.height)for(x in 0 until result.width){val alpha=Color.alpha(result.getPixel(x,y));if(alpha>0)ink++;maximumAlpha=maxOf(maximumAlpha,alpha)};assertTrue(ink>0);assertTrue(maximumAlpha<=127);assertEquals(0,Color.alpha(source.getPixel(100,100)))}finally{result.recycle();source.recycle()}
    }
}
