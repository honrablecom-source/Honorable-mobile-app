package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoTranslationPlanTest {
    private val source=listOf(VideoCaption(100,800,"Hello"),VideoCaption(1200,1800,"Welcome"))
    private fun refuse(action:()->Unit){try{action();fail("Must refuse incomplete/retimed translation")}catch(_:IllegalArgumentException){}}
    @Test fun completeDraftKeepsEveryTimeAndCanBeCorrectedBeforeApply(){
        val srt=VideoTranslationPlan.draft(source,listOf("Hola","Bienvenido"));val result=VideoTranslationPlan.corrected(source,srt,5000)
        assertEquals(source.mapIndexed{i,c->c.copy(text=listOf("Hola","Bienvenido")[i])},result);assertEquals("Hello",source[0].text)
    }
    @Test fun expandedModelOutputRemainsFullyVisibleUntilUserShortensIt(){
        val srt=VideoTranslationPlan.draft(source,listOf("x".repeat(300),"Bienvenido"));assertTrue(srt.contains("x".repeat(300)))
        refuse{VideoTranslationPlan.corrected(source,srt,5000)}
    }
    @Test fun missingExtraRetimedAndUnknownCueCannotApplyAsTranslation(){
        refuse{VideoTranslationPlan.draft(source,listOf("Only one"))}
        val valid=VideoTranslationPlan.draft(source,listOf("Hola","Bienvenido"))
        refuse{VideoTranslationPlan.corrected(source,valid.replace("00:00:00,100","00:00:00,101"),5000)}
        refuse{VideoTranslationPlan.corrected(source,VideoTranslationPlan.draft(source,listOf("<unk>","Bienvenido")),5000)}
    }
}
