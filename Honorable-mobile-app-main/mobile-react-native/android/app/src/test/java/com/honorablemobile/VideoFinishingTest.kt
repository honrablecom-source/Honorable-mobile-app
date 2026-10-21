package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class VideoFinishingTest {
    private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    @Test fun formatIsAtomicAndNewSourcesInheritFormatButNotClipRotation(){
        val p=VideoEditProject(2000);p.appendSource("extra",3000);val before=p.segments
        p.setLayout(0,VideoLayout(rotation=90));p.setFormat(2,true)
        assertTrue(p.segments.all{it.layout.ratio==2&&it.layout.crop});assertEquals(90,p.segments[0].layout.rotation)
        p.appendSource("third",4000);assertEquals(VideoLayout(2,true),p.segments.last().layout)
        assertTrue(p.undo());assertTrue(p.undo());assertEquals(0,p.segments[0].layout.ratio);assertTrue(p.undo());assertEquals(before,p.segments)
    }
    @Test fun rotateMirrorAndTitleStylesSurviveEditingAndHistory(){
        val p=VideoEditProject(10000);val layout=VideoLayout(3,true,270,true,true);val title=VideoTitleStyle(0,.08f,false,false)
        p.setLayout(0,layout);p.setTitleStyle(0,title);p.split(0,5000);p.duplicate(0);p.move(2,0);p.trim(0,6000,9000)
        assertTrue(p.segments.all{it.layout==layout&&it.titleStyle==title});p.reset();assertTrue(p.undo());assertTrue(p.segments.all{it.layout==layout})
    }
    @Test fun applyColorToAllIsOneUndoAndLeavesOtherClipPropertiesIntact(){
        val p=VideoEditProject(4000);p.appendSource("extra",3000);val g=VideoGrade(.3f,1.2f,.7f,.4f,-.3f)
        p.setGrade(0,g);p.setTitle(1,"Title");p.setVolume(1,.4f);val before=p.segments
        p.applyGradeToAll(0);assertEquals(g,p.segments[1].grade);assertEquals("Title",p.segments[1].title);assertEquals(.4f,p.segments[1].volume,0f)
        assertTrue(p.undo());assertEquals(before,p.segments)
    }
    @Test fun projectAudioFadeHasBoundedSymmetricEdgesAndIsUndoable(){
        assertEquals(0f,VideoFinishing.audioGain(2_000_000,0),0f);assertEquals(1f,VideoFinishing.audioGain(2_000_000,1_000_000),0f)
        assertEquals(.5f,VideoFinishing.audioGain(2_000_000,250_000),0f);assertEquals(.5f,VideoFinishing.audioGain(2_000_000,1_750_000),0f)
        assertEquals(1f,VideoFinishing.audioGain(200,100),0f);assertEquals(0f,VideoFinishing.audioGain(200,Long.MAX_VALUE),0f)
        val p=VideoEditProject(2000);p.appendSource("extra",1000);p.setProjectAudioFade(true);assertTrue(p.segments.all{it.audioFade});assertTrue(p.undo());assertTrue(p.segments.none{it.audioFade})
    }
    @Test fun whiteBalanceMatrixUsesChannelScalesAndRejectsUnsafeParameters(){
        val neutral=VideoGrade().matrix();val warm=VideoGrade(warmth=1f).matrix();assertTrue(warm[0]>neutral[0]);assertTrue(warm[10]<neutral[10])
        rejected{VideoGrade(tint=Float.NaN)};rejected{VideoGrade(warmth=1.01f)};rejected{VideoLayout(ratio=5)};rejected{VideoLayout(rotation=45)};rejected{VideoTitleStyle(size=Float.NaN)}
    }
    @Test fun versionFiveRoundTripsAllFinishingFieldsAndRejectsMalformedData(){
        val clip=VideoSegment(0,1000,title="Title",grade=VideoGrade(warmth=.4f,tint=-.2f),layout=VideoLayout(2,true,90,true,true),titleStyle=VideoTitleStyle(1,.08f,false,false),audioFade=true)
        val d=VideoDraft(1000,listOf(clip),0,0,0,1000);val bytes=VideoDraftCodec.encode(d)
        assertEquals(5,ByteBuffer.wrap(bytes).getInt(4));assertEquals(d,VideoDraftCodec.decode(bytes,1000))
        for(n in bytes.indices)assertNull(VideoDraftCodec.decode(bytes.copyOf(n),1000))
        assertNull(VideoDraftCodec.decode(bytes+byteArrayOf(0),1000))
    }
    @Test fun oldGradedDraftsStillUseVersionFourWithNeutralNewFields(){
        val d=VideoDraft(1000,listOf(VideoSegment(0,1000,grade=VideoGrade(exposure=.2f))),0,0,0,1000)
        val bytes=VideoDraftCodec.encode(d);assertEquals(4,ByteBuffer.wrap(bytes).getInt(4));assertEquals(d,VideoDraftCodec.decode(bytes,1000))
    }
    @Test fun largeFinishingDraftFitsTheExistingEncryptedAndLifecycleLimit(){
        val emoji=String(Character.toChars(0x1F600));val sources=List(31){VideoSource("s$it","content://synthetic/"+emoji.repeat(500)+it,1000)}
        val clips=List(1000){VideoSegment(0,1000,sourceId=if(it%32==0)"primary" else "s${it%32-1}",title=if(it<500)emoji.repeat(60) else "",grade=VideoGrade(warmth=.2f),layout=VideoLayout(2,true,90,true),titleStyle=VideoTitleStyle(0,.08f),audioFade=true)}
        val d=VideoDraft(1000,clips,0,0,0,1000,sources);val bytes=VideoDraftCodec.encode(d)
        assertTrue(bytes.size<=256_000);assertEquals(d,VideoDraftCodec.decode(bytes,1000))
    }
}
