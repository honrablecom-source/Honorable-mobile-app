package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class PhotoVideoSelectionTest {
    private val originals=listOf("content://fixture/a","content://fixture/b","content://fixture/c")
    @Test fun reordersBeforeEncodingWithoutChangingOriginalReferences(){
        val selected=PhotoVideoSelection.of(originals)
        val later=selected.move(0,1)
        assertEquals(listOf(originals[1],originals[0],originals[2]),later.items)
        assertEquals(originals,later.move(1,-1).items)
        assertEquals(originals,selected.items)
        assertEquals(listOf(originals[0],originals[2],originals[1]),selected.move(2,-1).items)
    }
    @Test fun removesOnlyTheSelectedReferenceAndCanEmptyASelection(){
        val selected=PhotoVideoSelection.of(originals)
        assertEquals(listOf(originals[0],originals[2]),selected.remove(1).items)
        assertEquals(originals,selected.items)
        assertTrue(selected.remove(2).remove(1).remove(0).items.isEmpty())
    }
    @Test fun refusesStaleIndicesAndBoundaryMovesWithoutMutatingSnapshot(){
        val selected=PhotoVideoSelection.of(originals)
        for(action in listOf<()->Unit>({selected.move(0,-1)},{selected.move(2,1)},{selected.move(-1,1)},{selected.move(0,2)},{selected.move(0,0)},{selected.remove(3)}))assertTrue(runCatching(action).isFailure)
        assertEquals(originals,selected.items)
    }
    @Test fun snapshotsAreDefensiveAndCannotBeModifiedThroughListCasts(){
        val input=originals.toMutableList();val selected=PhotoVideoSelection.of(input)
        input.clear();assertEquals(originals,selected.items)
        assertTrue(runCatching{(selected.items as MutableList).clear()}.isFailure)
        assertEquals(originals,PhotoVideoSelection.of(selected.items).items)
    }
    @Test fun boundsRecoveryAndRejectsInvalidOrDuplicateDocumentReferences(){
        val max=List(20){"content://fixture/$it"};assertEquals(max,PhotoVideoSelection.of(max).items)
        for(value in listOf(max+"content://fixture/20",listOf(originals[0],originals[0]),listOf("file:///private/photo"),listOf("content:///missing"),listOf("content://fixture/\n"),listOf("content://fixture/"+"x".repeat(2048)),listOf("content://fixture/\uD800")))assertTrue(runCatching{PhotoVideoSelection.of(value)}.isFailure)
        assertEquals(originals,PhotoVideoSelection.of(originals).items)
    }
    @Test fun labelsAreBoundedPlainLocalTextAndNeverNeedUriFallback(){
        assertNull(PhotoVideoSelection.displayName(null));assertNull(PhotoVideoSelection.displayName("  \n  "))
        assertEquals("Family  story.jpg",PhotoVideoSelection.displayName("  Family\n story.jpg  "))
        assertEquals(80,PhotoVideoSelection.displayName("x".repeat(200))!!.length)
    }
    @Test fun customHoldsFollowTheirPhotoThroughReorderAndRemoval(){
        val before=PhotoVideoSelection.of(originals)
        val chosen=before.withHold(0,1000).withHold(2,10000)
        assertEquals(listOf(1000L,5000L,10000L),chosen.durations(5000))
        assertEquals(listOf(5000L,1000L,10000L),chosen.move(0,1).durations(5000))
        assertEquals(listOf(1000L,10000L),chosen.remove(1).durations(5000))
        assertFalse(chosen.remove(0).holds.containsKey(originals[0]))
        assertEquals(listOf(5000L,5000L,5000L),before.durations(5000))
    }
    @Test fun defaultChangesOnlyUntimedPhotosAndResetRemovesOverride(){
        val selected=PhotoVideoSelection.of(originals).withHold(1,6500)
        assertEquals(listOf(3000L,6500L,3000L),selected.durations(3000))
        assertEquals(listOf(10000L,6500L,10000L),selected.durations(10000))
        assertEquals(listOf(8000L,8000L,8000L),selected.withHold(1,null).durations(8000))
        for(value in listOf(0L,999L,10001L,Long.MAX_VALUE))assertTrue(runCatching{selected.withHold(0,value)}.isFailure)
        assertTrue(runCatching{selected.durations(0)}.isFailure)
    }
    @Test fun savedTimingRoundTripsWithDefaultSentinelsAndRejectsMalformedRecovery(){
        val selected=PhotoVideoSelection.of(originals).withHold(0,1250).withHold(2,9000)
        assertArrayEquals(longArrayOf(1250,0,9000),selected.savedHolds())
        assertEquals(selected.durations(5000),PhotoVideoSelection.restore(originals,selected.savedHolds()).durations(5000))
        assertEquals(listOf(5000L,5000L,5000L),PhotoVideoSelection.restore(originals,null).durations(5000))
        for(saved in listOf(longArrayOf(1000),longArrayOf(-1,0,0),longArrayOf(10001,0,0)))assertTrue(runCatching{PhotoVideoSelection.restore(originals,saved)}.isFailure)
        assertTrue(runCatching{PhotoVideoSelection.of(originals,mapOf("content://other/not-selected" to 1000L))}.isFailure)
        val encoded=selected.savedHolds();encoded[0]=10000
        assertEquals(1250L,selected.holds[originals[0]])
    }
    @Test fun holdPlansAndMapsAreDefensiveAndMaximumSelectionIsBounded(){
        val input=mutableMapOf(originals[0] to 1000L);val selected=PhotoVideoSelection.of(originals,input)
        input.clear();assertEquals(1000L,selected.holds[originals[0]])
        assertTrue(runCatching{(selected.holds as MutableMap).clear()}.isFailure)
        assertTrue(runCatching{(selected.durations(5000) as MutableList).clear()}.isFailure)
        val max=PhotoVideoSelection.of(List(20){"content://fixture/$it"})
        assertEquals(200000L,max.durations(10000).sum())
    }
    @Test fun preparedTimingRejectsNearlyEmptyAndTruncatedClipsAtTheMinimumHold(){
        PhotoVideoSelection.requirePreparedDuration(1000,1000)
        PhotoVideoSelection.requirePreparedDuration(1000,900)
        PhotoVideoSelection.requirePreparedDuration(1000,1100)
        PhotoVideoSelection.requirePreparedDuration(10000,10033)
        for(value in listOf(0L,1L,500L,899L,1101L,Long.MAX_VALUE,Long.MIN_VALUE))assertTrue(runCatching{PhotoVideoSelection.requirePreparedDuration(1000,value)}.isFailure)
        assertTrue(runCatching{PhotoVideoSelection.requirePreparedDuration(999,999)}.isFailure)
    }
    @Test fun labelLimitNeverLeavesHalfAnEmojiOrMalformedSurrogate(){
        assertEquals("x".repeat(79),PhotoVideoSelection.displayName("x".repeat(79)+"🌍.jpg"))
        assertEquals("x".repeat(78)+"🌍",PhotoVideoSelection.displayName("x".repeat(78)+"🌍.jpg"))
        assertEquals("bad�name�.jpg",PhotoVideoSelection.displayName("bad\uD800name\uDC00.jpg"))
        val emoji=PhotoVideoSelection.displayName("🌍".repeat(100))!!
        assertEquals(80,emoji.length)
        assertEquals(emoji,emoji.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }
    @Test fun hugeProviderLabelsStayBoundedAndKeepWhitespaceFallback(){
        assertEquals("Family  story.jpg",PhotoVideoSelection.displayName(" \t Family\n story.jpg \r "))
        assertEquals("x".repeat(80),PhotoVideoSelection.displayName(" ".repeat(100000)+"x".repeat(100000)))
        assertNull(PhotoVideoSelection.displayName("\u0000 \t".repeat(10000)))
        assertEquals("🌍",PhotoVideoSelection.displayName("\n 🌍 \t"))
    }
    @Test fun appendingKeepsOriginalOrderAndHoldsAndDeduplicatesExactReferences(){
        val selected=PhotoVideoSelection.of(originals).withHold(1,1250)
        val added=selected.append(listOf(originals[1],"content://fixture/new","content://fixture/new"))
        assertEquals(originals+"content://fixture/new",added.items)
        assertEquals(listOf(5000L,1250L,5000L,5000L),added.durations(5000))
        assertEquals(originals,selected.items)
        assertEquals(selected.items,selected.append(emptyList()).items)
        assertEquals(selected.holds,selected.append(originals).holds)
    }
    @Test fun appendLimitsAndMalformedReferencesLeaveExistingPlanUntouched(){
        val selected=PhotoVideoSelection.of(List(19){"content://fixture/$it"}).withHold(0,2000)
        assertEquals(20,selected.append(listOf("content://fixture/new")).items.size)
        for(incoming in listOf(listOf("content://fixture/new","content://fixture/extra"),List(21){"content://fixture/0"},listOf("file:///private/photo"),listOf("content://fixture/\uD800")))assertTrue(runCatching{selected.append(incoming)}.isFailure)
        assertEquals(19,selected.items.size);assertEquals(2000L,selected.holds[selected.items[0]])
    }
    @Test fun appendIsOneUndoAndDuplicateOnlyResultKeepsAvailableRedo(){
        val selected=PhotoVideoSelection.of(originals).withHold(1,1750)
        val history=PhotoVideoSelectionHistory(selected)
        val added=selected.append(listOf("content://fixture/new"));assertTrue(history.apply(added))
        assertTrue(history.undo());assertSame(selected,history.current)
        assertFalse(history.apply(history.current.append(originals)));assertTrue(history.canRedo)
        assertTrue(history.redo());assertSame(added,history.current)
        assertEquals(listOf(5000L,1750L,5000L,5000L),history.current.durations(5000))
    }
    @Test fun fittingTotalPreservesCustomHoldsAndDistributesRemainderInOrder(){
        val selected=PhotoVideoSelection.of(originals).withHold(1,2500)
        assertEquals(4500L..22500L,selected.fitTotalRange())
        val fitted=selected.fitTotal(10001)
        assertEquals(listOf(3751L,2500L,3750L),fitted.durations(5000))
        assertEquals(10001L,fitted.durations(1000).sum())
        assertEquals(selected.items,fitted.items);assertEquals(mapOf(originals[1] to 2500L),selected.holds)
    }
    @Test fun fittingRejectsImpossibleAndEmptyTotalsAndKeepsAllCustomPlan(){
        val selected=PhotoVideoSelection.of(originals).withHold(1,2500)
        assertEquals(listOf(1000L,2500L,1000L),selected.fitTotal(4500).durations(5000))
        assertEquals(listOf(10000L,2500L,10000L),selected.fitTotal(22500).durations(5000))
        for(total in listOf(Long.MIN_VALUE,4499L,22501L,Long.MAX_VALUE))assertTrue(runCatching{selected.fitTotal(total)}.isFailure)
        assertTrue(runCatching{PhotoVideoSelection.of(emptyList()).fitTotal(0)}.isFailure)
        val custom=selected.withHold(0,1000).withHold(2,3000)
        assertEquals(6500L..6500L,custom.fitTotalRange());assertSame(custom,custom.fitTotal(6500))
        assertTrue(runCatching{custom.fitTotal(6501)}.isFailure)
    }
    @Test fun fittingUsesOneHistoryStepAndSavedTimingKeepsExactPlan(){
        val selected=PhotoVideoSelection.of(originals).withHold(1,2500)
        val history=PhotoVideoSelectionHistory(selected);val fitted=selected.fitTotal(10001);history.apply(fitted)
        val restored=PhotoVideoSelection.restore(fitted.items,fitted.savedHolds())
        assertEquals(fitted.holds,restored.holds);assertEquals(10001L,restored.durations(8000).sum())
        assertTrue(history.undo());assertSame(selected,history.current);assertFalse(history.canUndo)
        assertTrue(history.redo());assertSame(fitted,history.current)
        assertEquals(fitted.durations(3000)+3000L,fitted.append(listOf("content://fixture/new")).durations(3000))
    }
    @Test fun allSelectionSizesFitBoundsAndInteriorExactlyWithoutChangingCustomHolds(){
        for(size in 1..20){
            var selected=PhotoVideoSelection.of(List(size){"content://fixture/$it"})
            for(i in 0 until size step 3)selected=selected.withHold(i,1250L+i)
            val range=selected.fitTotalRange()
            for(total in listOf(range.first,range.first+(range.last-range.first)/2,range.last)){
                val fitted=selected.fitTotal(total)
                assertEquals(total,fitted.durations(5000).sum())
                assertTrue(fitted.durations(5000).all{it in 1000L..10000L})
                for((uri,hold)in selected.holds)assertEquals(hold,fitted.holds[uri])
            }
        }
    }
}
