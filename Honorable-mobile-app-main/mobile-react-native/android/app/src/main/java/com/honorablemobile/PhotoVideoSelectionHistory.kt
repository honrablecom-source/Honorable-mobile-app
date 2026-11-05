package com.honorablemobile

/** Transient pre-encoding edits only; immutable document references never mutate original files. */
internal class PhotoVideoSelectionHistory(initial:PhotoVideoSelection=PhotoVideoSelection.of(emptyList())) {
    companion object { const val LIMIT=20 }
    var current:PhotoVideoSelection=initial
        private set
    private val undo=java.util.ArrayDeque<PhotoVideoSelection>()
    private val redo=java.util.ArrayDeque<PhotoVideoSelection>()
    val canUndo:Boolean get()=undo.isNotEmpty()
    val canRedo:Boolean get()=redo.isNotEmpty()
    fun apply(next:PhotoVideoSelection):Boolean {
        if(current.items==next.items&&current.holds==next.holds)return false
        undo.addLast(current)
        if(undo.size>LIMIT)undo.removeFirst()
        current=next;redo.clear()
        return true
    }
    fun undo():Boolean {
        if(!canUndo)return false
        redo.addLast(current);current=undo.removeLast();return true
    }
    fun redo():Boolean {
        if(!canRedo)return false
        undo.addLast(current);current=redo.removeLast();return true
    }
    fun reset(value:PhotoVideoSelection) {current=value;undo.clear();redo.clear()}
}
