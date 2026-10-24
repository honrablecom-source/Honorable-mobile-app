package com.honorablemobile

import app.honorable.editor.PhotoLightStats
import app.honorable.editor.PhotoLook
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class VideoGradeTest {
    private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    private fun rgb(g:VideoGrade,r:Float,green:Float,b:Float,hdr:Boolean=false):List<Float>{val m=g.matrix(hdr);return (0..2).map{row->m[row]*r+m[4+row]*green+m[8+row]*b+m[12+row]}}
    @Test fun identityLeavesRgbAndAlphaUnchangedInBothWorkingSpaces(){
        for(hdr in listOf(false,true)){
            assertEquals(listOf(.2f,.4f,.6f),rgb(VideoGrade(),.2f,.4f,.6f,hdr))
            val m=VideoGrade().matrix(hdr);assertEquals(1f,m[15],0f)
            for(index in listOf(3,7,11,12,13,14))assertEquals(0f,m[index],0f)
        }
    }
    @Test fun desaturationUsesTheCorrectLuminanceAndDoesNotMixAlpha(){
        val g=VideoGrade(saturation=0f)
        for(hdr in listOf(false,true))for(value in rgb(g,1f,0f,0f,hdr))assertEquals(if(hdr).2627f else .2126f,value,.00001f)
        for(value in rgb(g,.3f,.3f,.3f))assertEquals(.3f,value,.00001f)
    }
    @Test fun exposureDoublesLinearLightAndContrastPreservesItsNeutralPivot(){
        for(value in rgb(VideoGrade(exposure=1f),.1f,.1f,.1f))assertEquals(.2f,value,.00001f)
        for(value in rgb(VideoGrade(contrast=2f),.18f,.18f,.18f))assertEquals(.18f,value,.00001f)
        for(value in rgb(VideoGrade(contrast=0f),0f,1f,.6f))assertEquals(.18f,value,.00001f)
    }
    @Test fun invalidScalarsAreRejectedBeforeRenderingOrSerialization(){
        rejected{VideoGrade(exposure=Float.NaN)};rejected{VideoGrade(contrast=Float.POSITIVE_INFINITY)}
        rejected{VideoGrade(exposure=2.01f)};rejected{VideoGrade(saturation=-.01f)};rejected{VideoGrade(contrast=2.01f)}
    }
    @Test fun gradingSurvivesClipEditsAndAtomicUndoWithoutChangingMusic(){
        val p=VideoEditProject(10_000);val grade=VideoGrade(.4f,1.1f,.8f);val music=VideoSoundtrack("content://synthetic/music",10_000)
        p.setSoundtrack(music);p.setGrade(0,grade);p.split(0,5000);p.duplicate(0);p.move(2,0);p.trim(0,6000,9000)
        assertTrue(p.segments.all{it.grade==grade});assertEquals(music,p.soundtrack)
        val before=p.segments;p.setGrade(1,VideoGrade(saturation=0f));assertTrue(p.undo());assertEquals(before,p.segments)
        assertTrue(p.redo());assertEquals(0f,p.segments[1].grade.saturation,0f)
        p.reset();assertTrue(p.segments.single().grade.isIdentity);assertTrue(p.undo());assertEquals(music,p.soundtrack)
    }
    @Test fun versionFourRoundTripsImportsTitlesMusicAndRejectsCorruptColor(){
        val clip=VideoSegment(1000,9000,2f,.5f,"extra","Title",true,VideoGrade(.3f,1.2f,.7f))
        val d=VideoDraft(10_000,listOf(clip),0,2000,2000,8000,listOf(VideoSource("extra","content://synthetic/video",12_000)),VideoSoundtrack("content://synthetic/music",5000))
        val bytes=VideoDraftCodec.encode(d);assertEquals(4,ByteBuffer.wrap(bytes).getInt(4));assertEquals(d,VideoDraftCodec.decode(bytes,10_000))
        for(length in bytes.indices)assertNull(VideoDraftCodec.decode(bytes.copyOf(length),10_000))
        assertNull(VideoDraftCodec.decode(bytes+byteArrayOf(0),10_000))
        val simple=VideoDraftCodec.encode(VideoDraft(1000,listOf(VideoSegment(0,1000,grade=VideoGrade(exposure=.1f))),0,0,0,1000))
        assertNull(VideoDraftCodec.decode(simple.clone().apply{ByteBuffer.wrap(this).putFloat(81,Float.NaN)},1000))
    }
    @Test fun identityGradesKeepAllThreeOlderDraftFormatsReadable(){
        val base=VideoDraft(1000,listOf(VideoSegment(0,1000)),0,0,0,1000)
        val drafts=listOf(base,base.copy(sources=listOf(VideoSource("extra","content://synthetic/video",1000))),base.copy(segments=listOf(VideoSegment(0,1000,title="Title"))))
        drafts.forEachIndexed{i,draft->val bytes=VideoDraftCodec.encode(draft);assertEquals(i+1,ByteBuffer.wrap(bytes).getInt(4));assertEquals(draft,VideoDraftCodec.decode(bytes,1000))}
    }
    @Test fun sceneProposalsProtectHighlightsNightAndRequireActualAnalysis(){
        val dark=PhotoLightStats(.01f,.05f,.6f)
        val night=VideoAutoGrade.suggest(listOf(dark),setOf("Night"),PhotoLook.AUTO);assertTrue(night.exposure<=.25f)
        val mixed=VideoAutoGrade.suggest(listOf(dark,PhotoLightStats(.1f,.5f,.98f)),setOf("Person"),PhotoLook.AUTO);assertTrue(mixed.exposure<=.08f)
        assertEquals(0f,VideoAutoGrade.suggest(listOf(dark),setOf("Person"),PhotoLook.MONOCHROME).saturation,0f)
        rejected{VideoAutoGrade.suggest(emptyList(),setOf("Person"),PhotoLook.AUTO)}
        rejected{VideoAutoGrade.suggest(listOf(dark),emptySet(),PhotoLook.AUTO)}
        rejected{VideoAutoGrade.suggest(listOf(PhotoLightStats(.8f,.3f,.9f)),setOf("Person"),PhotoLook.AUTO)}
    }
    @Test fun sampleTimesStayInsideTrimAndGradeChangesInvalidateLateInference(){
        assertEquals(listOf(7L),VideoAutoGrade.sampleTimes(7,8))
        for(time in VideoAutoGrade.sampleTimes(1000,2000))assertTrue(time in 1000 until 2000)
        rejected{VideoAutoGrade.sampleTimes(0,Long.MAX_VALUE)}
        val p=VideoEditProject(10_000);val selection=VideoSceneSelection.capture(p.segments,0,1000,9000)
        p.setGrade(0,VideoGrade(exposure=.1f));assertFalse(selection.stillMatches(p.segments,0,1000,9000))
    }
    @Test fun largeVersionFourDraftStaysWithinTheExistingRecoveryBudget(){
        val emoji=String(Character.toChars(0x1F600))
        val sources=List(31){VideoSource("s$it","content://synthetic/"+emoji.repeat(500)+it,1000)}
        val clips=List(1000){VideoSegment(0,1000,sourceId=if(it%32==0)"primary" else "s${it%32-1}",title=if(it<500)emoji.repeat(60) else "",grade=VideoGrade(exposure=.3f))}
        val d=VideoDraft(1000,clips,0,0,0,1000,sources,VideoSoundtrack("content://synthetic/music",1000))
        val bytes=VideoDraftCodec.encode(d);assertTrue(bytes.size<=256_000);assertEquals(d,VideoDraftCodec.decode(bytes,1000))
    }
}
