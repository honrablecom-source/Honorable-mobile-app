package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class VideoAudioLevelsTest {
    @Test fun rmsUsesDurationWeightedPowerRatherThanAverageAmplitude(){
        val r=VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,20,.2),VideoQuietWindow(20,40,.6)),1)
        assertEquals(sqrt(.2),r.averageRms!!,1e-12);assertEquals(sqrt(.2),r.bars.single().rms!!,1e-12);assertEquals(40L,r.knownMs)
        val partial=VideoAudioLevels.inspect(listOf(VideoQuietWindow(100,120,.2),VideoQuietWindow(120,125,.8)),1)
        assertEquals(.4,partial.bars.single().rms!!,1e-12)
    }
    @Test fun unknownCoverageIsNeverDrawnAsSilenceAndNinetyPercentBoundaryIsExplicit(){
        val r=VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,20,.5),VideoQuietWindow(20,40,null)),1)
        assertEquals(20L,r.knownMs);assertEquals(.5,r.averageRms!!,0.0);assertNull(r.bars.single().rms);assertEquals(.5,r.bars.single().coverage,0.0)
        assertEquals(.5,VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,18,.5),VideoQuietWindow(18,20,null)),1).bars.single().rms!!,0.0)
        assertNull(VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,17,.5),VideoQuietWindow(17,20,null)),1).bars.single().rms)
        val unknown=VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,20,null)));assertNull(unknown.averageRms);assertTrue(VideoAudioLevels.topRegions(unknown).isEmpty())
    }
    @Test fun boundedBinsCoverTheSourceAndMatchIndependentIntersectionEnergyOracle(){
        val windows=(0 until 6000).map{i->VideoQuietWindow(500+i*20L,520+i*20L,(i%7)/6.0)}
        val r=VideoAudioLevels.inspect(windows,320);assertEquals(320,r.bars.size);assertEquals(500L,r.bars.first().startMs);assertEquals(120500L,r.bars.last().endMs);assertTrue(r.bars.zipWithNext().all{(a,b)->a.endMs==b.startMs})
        for(bar in r.bars){val weighted=windows.sumOf{window->maxOf(0,minOf(bar.endMs,window.endMs)-maxOf(bar.startMs,window.startMs))*window.rms!!*window.rms};assertEquals(sqrt(weighted/(bar.endMs-bar.startMs)),bar.rms!!,1e-12)}
    }
    @Test fun sourceCoordinatesClampAndKeepTheExclusiveEndOutOfNavigation(){
        assertEquals(10000L,VideoAudioLevels.selectedTime(10000,11000,-1.0));assertEquals(10500L,VideoAudioLevels.selectedTime(10000,11000,.5));assertEquals(10999L,VideoAudioLevels.selectedTime(10000,11000,2.0));assertEquals(10000L,VideoAudioLevels.selectedTime(10000,10001,1.0))
        val r=VideoAudioLevels.inspect((0 until 50).map{VideoQuietWindow(10000+it*20L,10020+it*20L,.5)})
        assertEquals(.999,VideoAudioLevels.fraction(r,10999),1e-12);assertEquals(10980L,VideoAudioLevels.barAt(r,10999).startMs)
        fails{VideoAudioLevels.fraction(r,11000)};fails{VideoAudioLevels.fraction(r,9999)};fails{VideoAudioLevels.selectedTime(0,100,Double.NaN)}
    }
    @Test fun logarithmicDisplayHasKnownReferenceValuesAndRejectsInvalidEnergy(){
        assertNull(VideoAudioLevels.height(null));assertEquals(0.0,VideoAudioLevels.height(0.0)!!,0.0);assertEquals(0.0,VideoAudioLevels.height(.001)!!,1e-12);assertEquals(1.0/3,VideoAudioLevels.height(.01)!!,1e-12);assertEquals(2.0/3,VideoAudioLevels.height(.1)!!,1e-12);assertEquals(1.0,VideoAudioLevels.height(1.0)!!,0.0)
        assertEquals("Unknown",VideoAudioLevels.levelText(null));assertEquals("At or below -60 dBFS",VideoAudioLevels.levelText(0.0));assertEquals("-6.0 dBFS",VideoAudioLevels.levelText(.5))
        for(value in listOf(-.1,1.1,Double.NaN,Double.POSITIVE_INFINITY)){fails{VideoAudioLevels.height(value)};fails{VideoAudioLevels.levelText(value)}}
    }
    @Test fun markersAreDeterministicKnownPositiveEnergyRegionsWithStableTimeTies(){
        val values=listOf(0.0,.5,.9,.5,null,.9);val r=VideoAudioLevels.inspect(values.mapIndexed{i,value->VideoQuietWindow(i*20L,(i+1)*20L,value)})
        assertEquals(listOf(40L,100L,20L),VideoAudioLevels.topRegions(r).map{it.startMs});assertEquals(1,VideoAudioLevels.topRegions(r,1).size)
        assertTrue(VideoAudioLevels.topRegions(VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,20,0.0)))).isEmpty())
    }
    @Test fun overviewIsDetachedAndNeverChangesSourceWindows(){
        val source=mutableListOf(VideoQuietWindow(0,20,.2),VideoQuietWindow(20,40,.8));val before=source.toList();val overview=VideoAudioLevels.inspect(source);assertEquals(before,source);source.clear();assertEquals(2,overview.bars.size);assertEquals(.2,overview.bars.first().rms!!,0.0)
    }
    @Test fun malformedWindowsAndAllocationBoundsFailBeforeRendering(){
        fails{VideoAudioLevels.inspect(emptyList())};fails{VideoAudioLevels.inspect(List(6001){VideoQuietWindow(it*20L,(it+1)*20L,.5)})}
        fails{VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,20,.5),VideoQuietWindow(21,40,.5)))};fails{VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,21,.5)))}
        for(value in listOf(-.1,1.1,Double.NaN))fails{VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,20,value)))}
        for(limit in listOf(0,321))fails{VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,20,.5)),limit)}
        fails{VideoAudioLevels.topRegions(VideoAudioLevels.inspect(listOf(VideoQuietWindow(0,20,.5))),0)}
    }
    private fun fails(block:()->Unit){try{block();fail("Expected invalid source overview")}catch(expected:IllegalArgumentException){}}
}
