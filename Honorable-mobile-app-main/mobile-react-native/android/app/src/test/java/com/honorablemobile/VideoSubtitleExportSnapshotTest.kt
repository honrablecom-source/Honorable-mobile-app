package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import android.net.Uri
import sun.misc.Unsafe

class VideoSubtitleExportSnapshotTest {
    @Test fun selectedRangeSrtUsesTrimAndSpeedWithoutEditingProject() {
        val original=VideoSegment(0,8000,speed=2f,volume=.25f,title="Keep title",captions=listOf(VideoCaption(1000,3000,"First"),VideoCaption(3500,6000,"Second")),captionSourceUri="content://original/captions")
        val project=VideoEditProject(8000);project.restore(listOf(original))
        val snapshot=VideoExportSnapshot.clips(project.segments,0,2000,5000)
        val srt=VideoCaptions.exportSrt(snapshot).toString(Charsets.UTF_8)
        assertEquals("1\n00:00:00,000 --> 00:00:00,500\nFirst\n\n2\n00:00:00,750 --> 00:00:01,500\nSecond\n",srt)
        assertEquals(listOf(original),project.segments);assertFalse(project.canUndo);assertFalse(project.canRedo)
    }
    @Test fun otherClipsAndOutputOffsetsRemainCorrectInCopy() {
        val a=VideoSegment(0,2000,captions=listOf(VideoCaption(0,1000,"A")))
        val b=VideoSegment(2000,8000,speed=.5f,captions=listOf(VideoCaption(3000,5000,"B")))
        val project=VideoEditProject(8000);project.restore(listOf(a,b))
        val snapshot=VideoExportSnapshot.clips(project.segments,1,4000,6000)
        assertSame(a,snapshot[0])
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nA\n\n2\n00:00:02,000 --> 00:00:04,000\nB\n",VideoCaptions.exportSrt(snapshot).toString(Charsets.UTF_8))
        assertEquals(listOf(a,b),project.segments);assertFalse(project.canUndo)
    }
    @Test fun failedOrDiscardedSrtPreparationDoesNotCreateAnUndoStep() {
        val project=VideoEditProject(8000);project.split(0,4000);val before=project.segments.toList()
        try{VideoCaptions.exportSrt(VideoExportSnapshot.clips(project.segments,0,1000,3000));fail("Empty captions exported")}catch(_:IllegalArgumentException){}
        assertEquals(before,project.segments)
        assertTrue(project.undo());assertEquals(listOf(VideoSegment(0,8000)),project.segments);assertFalse(project.canUndo)
        assertTrue(project.redo());assertEquals(before,project.segments)
    }
    @Test fun protectedDocumentsIncludeCurrentUndoAndRedoSubtitleAndAudioOriginals() {
        val project=VideoEditProject(8000)
        val old=VideoSegment(0,8000,captions=listOf(VideoCaption(0,1000,"Old")),captionSourceUri="content://original/old-srt")
        val oldMusic=VideoSoundtrack("content://original/old-audio",8000)
        val newMusic=VideoSoundtrack("content://original/new-audio",8000)
        project.restore(listOf(old),soundtrack=oldMusic)
        project.setCaptions(0,listOf(VideoCaption(1000,2000,"New")),"content://original/new-srt")
        project.setSoundtrack(newMusic);assertTrue(project.undo())
        val before=project.segments.toList()
        val field=Unsafe::class.java.getDeclaredField("theUnsafe").apply{isAccessible=true}
        val activity=(field.get(null)as Unsafe).allocateInstance(HonorableVideoEditorActivity::class.java)as HonorableVideoEditorActivity
        for((name,value) in mapOf("project" to project,"source" to Uri.parse("content://original/video"),"importedSources" to linkedMapOf<String,VideoSource>()))
            activity.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(activity,value)
        val method=activity.javaClass.getDeclaredMethod("sourceUris").apply{isAccessible=true}
        val originals=(method.invoke(activity)as Map<*,*>).values.map{it.toString()}.toSet()
        assertEquals(setOf("content://original/video","content://original/old-srt","content://original/new-srt","content://original/old-audio","content://original/new-audio"),originals)
        assertEquals(before,project.segments);assertEquals(oldMusic,project.soundtrack)
        assertTrue(project.redo());assertEquals(newMusic,project.soundtrack)
    }
}
