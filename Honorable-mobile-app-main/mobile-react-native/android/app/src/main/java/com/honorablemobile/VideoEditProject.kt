package com.honorablemobile

/** Source time ranges in playback order. The original media is never modified. */
internal data class VideoSegment(
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
) {
    init {
        require(startMs >= 0 && endMs > startMs)
        require(speed in setOf(.5f, 1f, 1.5f, 2f))
        require(volume in 0f..1f)
    }
    val durationMs: Long get() = ((endMs - startMs) / speed).toLong()
}

internal class VideoEditProject(durationMs: Long) {
    init { require(durationMs > 0) }
    private val sourceDurationMs = durationMs
    private val original = listOf(VideoSegment(0, durationMs))
    private val undo = ArrayDeque<List<VideoSegment>>()
    private val redo = ArrayDeque<List<VideoSegment>>()
    var segments: List<VideoSegment> = original
        private set

    fun restore(clips: List<VideoSegment>) {
        require(clips.isNotEmpty() && clips.all { it.endMs <= sourceDurationMs })
        segments = clips.toList()
        undo.clear()
        redo.clear()
    }

    val totalDurationMs: Long get() = segments.sumOf { it.durationMs }
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    private fun change(next: List<VideoSegment>) {
        require(next.isNotEmpty()) { "A project needs at least one clip" }
        if (next == segments) return
        undo.addLast(segments)
        if (undo.size > 50) undo.removeFirst()
        redo.clear()
        segments = next
    }

    fun split(index: Int, sourceTimeMs: Long) {
        val clip = segments[index]
        require(sourceTimeMs > clip.startMs && sourceTimeMs < clip.endMs) { "Split inside the clip" }
        change(segments.toMutableList().apply {
            removeAt(index)
            add(index, clip.copy(startMs = sourceTimeMs))
            add(index, clip.copy(endMs = sourceTimeMs))
        })
    }

    fun trim(index: Int, startMs: Long, endMs: Long) {
        val clip = segments[index]
        require(startMs >= clip.startMs && endMs <= clip.endMs) { "Trim inside the clip" }
        change(segments.toMutableList().apply { set(index, clip.copy(startMs = startMs, endMs = endMs)) })
    }

    fun setSpeed(index: Int, speed: Float) {
        change(segments.toMutableList().apply { set(index, get(index).copy(speed = speed)) })
    }

    fun setVolume(index: Int, volume: Float) {
        change(segments.toMutableList().apply { set(index, get(index).copy(volume = volume)) })
    }

    fun delete(index: Int) {
        require(segments.size > 1) { "Cannot delete the last clip" }
        change(segments.toMutableList().apply { removeAt(index) })
    }

    fun move(from: Int, to: Int) {
        require(to in segments.indices)
        change(segments.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun undo(): Boolean {
        if (undo.isEmpty()) return false
        redo.addLast(segments)
        segments = undo.removeLast()
        return true
    }

    fun redo(): Boolean {
        if (redo.isEmpty()) return false
        undo.addLast(segments)
        segments = redo.removeLast()
        return true
    }

    fun reset() = change(original)
}
