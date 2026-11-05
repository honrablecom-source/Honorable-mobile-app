package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class VideoStoryboardPlanTest {
    @Test fun smallProjectsIncludeEveryClipAndSingleFrameIsValid(){
        for(count in 1..12){
            val clips=List(count){VideoSegment(0,1000,title="Shot $it")}
            val plan=VideoStoryboardPlan.create(clips)
            assertFalse(plan.representative);assertEquals(count,plan.totalClips)
            assertEquals(clips.indices.toList(),plan.shots.map{it.clipIndex})
            plan.shots.forEach{assertEquals(clips[it.clipIndex],it.request.clip);assertTrue(it.request.sourceMs in 0..999)}
        }
    }
    @Test fun largeProjectSamplingIsDeterministicOrderedAndIncludesBothEnds(){
        val clips=List(1000){VideoSegment(0,1000)}
        val plan=VideoStoryboardPlan.create(clips)
        assertTrue(plan.representative);assertEquals(1000,plan.totalClips)
        assertEquals(listOf(0,90,181,272,363,454,544,635,726,817,908,999),plan.shots.map{it.clipIndex})
        assertEquals(plan.shots,VideoStoryboardPlan.create(clips).shots)
        assertEquals(499_000L,plan.shots.first().outputUs)
        assertEquals(999_499_000L,plan.shots.last().outputUs)
        assertTrue(runCatching{(plan.shots as MutableList).clear()}.isFailure)
    }
    @Test fun framesUseTrimmedSourceClockAndSpeedAdjustedAccumulatedPlayback(){
        val clips=listOf(VideoSegment(1000,3000,speed=2f),VideoSegment(6000,10000,speed=.5f,sourceId="second"))
        val plan=VideoStoryboardPlan.create(clips)
        assertEquals(1999L,plan.shots[0].request.sourceMs);assertEquals(499_500L,plan.shots[0].outputUs)
        assertEquals(7999L,plan.shots[1].request.sourceMs);assertEquals(4_998_000L,plan.shots[1].outputUs)
        assertEquals("second",plan.shots[1].request.clip.sourceId)
        val tiny=VideoStoryboardPlan.create(listOf(VideoSegment(1000,1001))).shots.single()
        assertEquals(1000L,tiny.request.sourceMs)
    }
    @Test fun geometryIsBoundedAndCellsCannotOverlapHeaderFooterOrEachOther(){
        for(count in 1..12){
            val plan=VideoStoryboardPlan.create(List(count){VideoSegment(0,1000)})
            assertTrue(plan.width in 1..1920);assertTrue(plan.height in 1..1200)
            val cells=plan.shots.indices.map(plan::cell)
            assertEquals(count,cells.distinct().size)
            for((left,top) in cells){
                assertTrue(left>=0&&left+VideoStoryboardPlan.CELL_WIDTH<=plan.width)
                assertTrue(top>=VideoStoryboardPlan.HEADER&&top+VideoStoryboardPlan.CELL_HEIGHT<=plan.height-VideoStoryboardPlan.FOOTER)
            }
            assertTrue(runCatching{plan.cell(-1)}.isFailure)
            assertTrue(runCatching{plan.cell(count)}.isFailure)
        }
    }
    @Test fun titleLabelsKeepOriginalClipDataAndDoNotAlterProjectHistory(){
        val project=VideoEditProject(3000);project.setTitle(0,"Stage\nTwo")
        val before=project.segments.toList();val canUndo=project.canUndo
        val bytes=VideoDraftCodec.encode(VideoDraft(3000,before,0,0,0,3000))
        val plan=VideoStoryboardPlan.create(project.segments)
        assertEquals("Stage Two",plan.shots.single().title)
        assertEquals("Stage\nTwo",plan.shots.single().request.clip.title)
        assertEquals(before,project.segments);assertEquals(canUndo,project.canUndo)
        assertArrayEquals(bytes,VideoDraftCodec.encode(VideoDraft(3000,project.segments,0,0,0,3000)))
        assertTrue(project.undo());assertEquals("",project.segments.single().title)
    }
    @Test fun invalidCountsOverflowAndUnrepresentableSourceTimestampsFailBeforeDecoding(){
        assertTrue(runCatching{VideoStoryboardPlan.create(emptyList())}.isFailure)
        assertTrue(runCatching{VideoStoryboardPlan.create(List(1001){VideoSegment(0,1000)})}.isFailure)
        val huge=VideoSegment(0,Long.MAX_VALUE)
        assertTrue(runCatching{VideoStoryboardPlan.create(listOf(huge,huge))}.isFailure)
        val from=Long.MAX_VALUE/1000+1
        assertTrue(runCatching{VideoStoryboardPlan.create(listOf(VideoSegment(from,from+1)))}.isFailure)
    }
    @Test fun representativePlanNeverRequiresMoreThanTwelveProtectedOriginals(){
        val clips=List(1000){VideoSegment(0,1000,sourceId="source_${it%31}")}
        val plan=VideoStoryboardPlan.create(clips)
        assertEquals(12,plan.shots.map{it.request.clip.sourceId}.distinct().size)
        assertTrue(plan.shots.all{it.request.sourceMs>=it.request.clip.startMs&&it.request.sourceMs<it.request.clip.endMs})
    }
}
