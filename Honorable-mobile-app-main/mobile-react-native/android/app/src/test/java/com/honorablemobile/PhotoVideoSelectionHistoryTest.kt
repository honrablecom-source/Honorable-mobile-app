package com.honorablemobile

import org.junit.Assert.*
import org.junit.Test

class PhotoVideoSelectionHistoryTest {
    private val selected=PhotoVideoSelection.of(listOf("content://fixture/a","content://fixture/b","content://fixture/c"))
    @Test fun orderRemovalAndHoldUndoRedoRestoreExactSnapshots(){
        val h=PhotoVideoSelectionHistory(selected)
        val timed=selected.withHold(1,1250);h.apply(timed)
        val moved=timed.move(1,-1);h.apply(moved)
        val removed=moved.remove(0);h.apply(removed)
        assertTrue(h.undo());assertSame(moved,h.current)
        assertEquals(listOf(1250L,5000L,5000L),h.current.durations(5000))
        assertTrue(h.undo());assertSame(timed,h.current)
        assertTrue(h.undo());assertSame(selected,h.current)
        assertFalse(h.undo())
        assertTrue(h.redo());assertSame(timed,h.current)
        assertTrue(h.redo());assertSame(moved,h.current)
        assertTrue(h.redo());assertSame(removed,h.current);assertFalse(h.redo())
        assertEquals(3,selected.items.size);assertTrue(selected.holds.isEmpty())
    }
    @Test fun emptySelectionAndPickerReplacementCanBeRecovered(){
        val h=PhotoVideoSelectionHistory(selected)
        val empty=PhotoVideoSelection.of(emptyList());h.apply(empty)
        assertTrue(h.undo());assertEquals(selected.items,h.current.items)
        assertTrue(h.redo());assertTrue(h.current.items.isEmpty())
        val replacement=PhotoVideoSelection.of(listOf("content://fixture/new"));h.apply(replacement)
        assertTrue(h.undo());assertTrue(h.current.items.isEmpty())
        assertTrue(h.undo());assertSame(selected,h.current)
    }
    @Test fun equivalentSnapshotsDoNotConsumeHistoryOrDiscardRedo(){
        val h=PhotoVideoSelectionHistory(selected)
        assertFalse(h.apply(PhotoVideoSelection.of(selected.items)))
        assertFalse(h.canUndo)
        h.apply(selected.withHold(0,1000));h.undo()
        assertFalse(h.apply(PhotoVideoSelection.of(selected.items)))
        assertTrue(h.canRedo);assertTrue(h.redo())
        h.undo();h.apply(selected.withHold(1,2000))
        assertFalse(h.canRedo)
    }
    @Test fun retentionIsBoundedAcrossUndoRedoAndNewBranch(){
        val h=PhotoVideoSelectionHistory(selected)
        for(i in 1..100)h.apply(selected.withHold(0,1000L+i))
        repeat(20){assertTrue(h.undo())}
        assertFalse(h.undo());assertEquals(1080L,h.current.holds[selected.items[0]])
        repeat(20){assertTrue(h.redo())}
        assertFalse(h.redo());assertEquals(1100L,h.current.holds[selected.items[0]])
        h.undo();h.apply(selected.withHold(0,9000))
        assertFalse(h.canRedo);repeat(20){assertTrue(h.undo())};assertFalse(h.undo())
    }
    @Test fun restoredCurrentSelectionStartsWithNoStaleSessionHistory(){
        val h=PhotoVideoSelectionHistory(selected)
        h.apply(selected.remove(0));h.undo()
        val restored=PhotoVideoSelection.restore(selected.items,longArrayOf(0,2500,0))
        h.reset(restored)
        assertSame(restored,h.current);assertFalse(h.canUndo);assertFalse(h.canRedo)
        assertFalse(h.undo());assertFalse(h.redo())
        assertEquals(listOf(5000L,2500L,5000L),h.current.durations(5000))
    }
}
