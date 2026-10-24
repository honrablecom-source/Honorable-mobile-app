package com.honorablemobile

/** Source time ranges in playback order. The original media is never modified. */
internal data class VideoSegment(
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val sourceId: String = "primary",
    val title: String = "",
    val fadeEdges: Boolean = false,
    val grade: VideoGrade = VideoGrade(),
    val layout:VideoLayout=VideoLayout(),
    val titleStyle:VideoTitleStyle=VideoTitleStyle(),
    val audioFade:Boolean=false,
    val captions: List<VideoCaption> = emptyList(),
    val captionSourceUri: String? = null,
) {
    init {
        VideoCaptions.validate(captions)
        captionSourceUri?.let { require(it.startsWith("content://") && it.none { c -> c.isISOControl() } && it.toByteArray(Charsets.UTF_8).size <= 2048 && it.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == it) }
        require(title.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == title)
        require(title.length <= 120 && title.count { it == '\n' } <= 2 && title.none { it.isISOControl() && it != '\n' })
        require(!sourceId.startsWith("__") && sourceId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(startMs >= 0 && endMs > startMs)
        require(speed in setOf(.5f, 1f, 1.5f, 2f))
        require(volume in 0f..1f)
    }
    val durationUs: Long get() = (((endMs - startMs) * 1000.0) / speed).toLong().coerceAtLeast(1)
    val durationMs: Long get() = durationUs / 1000
}

internal class VideoEditProject(durationMs: Long) {
    init { require(durationMs > 0) }
    private val sourceDurations = mutableMapOf("primary" to durationMs)
    private val original = listOf(VideoSegment(0, durationMs))
    private data class State(val clips: List<VideoSegment>, val soundtrack: VideoSoundtrack?)
    private val undo = ArrayDeque<State>()
    private val redo = ArrayDeque<State>()
    var soundtrack: VideoSoundtrack? = null
        private set
    private fun state() = State(segments,soundtrack)
    var segments: List<VideoSegment> = original
        private set

    private fun validate(clips: List<VideoSegment>, sources: Map<String, Long>) {
        VideoDraftCodec.requireRecoverableCaptions(clips)
        require(clips.mapNotNull { it.captionSourceUri }.distinct().size <= 32) { "Use up to 32 subtitle source documents" }
        require(clips.sumOf { it.title.toByteArray(Charsets.UTF_8).size } <= 128_000) { "Project title text limit reached" }
        require(clips.sumOf { it.captions.size } <= VideoCaptions.MAX_PROJECT_CUES) { "Project caption count limit reached" }
        require(clips.sumOf { clip -> clip.captions.sumOf { it.text.toByteArray(Charsets.UTF_8).size } } <= VideoCaptions.MAX_PROJECT_TEXT_BYTES) { "Project caption text limit reached" }
        require(clips.all { clip -> clip.captions.all { it.endMs <= (sources[clip.sourceId] ?: 0) } }) { "Caption exceeds original source duration" }
        require(clips.size in 1..1000 && sources.size in 1..32)
        require(sources.all { (id, duration) -> id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && duration > 0 })
        require(clips.all { it.endMs <= (sources[it.sourceId] ?: 0) }) { "Unknown source or clip outside its source" }
    }
    private fun mergedSources(extra: Map<String, Long>): Map<String, Long> {
        require(extra.all { (id, duration) -> sourceDurations[id]?.let { it == duration } ?: true })
        return sourceDurations + extra
    }
    fun appendSource(id: String, duration: Long) {
        val catalog = mergedSources(mapOf(id to duration))
        val template=segments.first()
        val next = segments + VideoSegment(0, duration, sourceId = id,layout=VideoLayout(template.layout.ratio,template.layout.crop),audioFade=template.audioFade)
        validate(next, catalog)
        sourceDurations.putAll(catalog)
        change(next)
    }
    fun restore(clips: List<VideoSegment>, sources: Map<String, Long> = emptyMap(), soundtrack: VideoSoundtrack? = null) {
        val catalog = mergedSources(sources)
        validate(clips, catalog)
        sourceDurations.putAll(catalog)
        segments = clips.toList(); this.soundtrack = soundtrack
        undo.clear()
        redo.clear()
    }

    fun applyDraft(clips: List<VideoSegment>, sources: Map<String, Long> = emptyMap(), soundtrack: VideoSoundtrack? = null) {
        val catalog = mergedSources(sources)
        validate(clips, catalog)
        sourceDurations.putAll(catalog)
        change(clips.toList(), soundtrack)
    }

    val totalDurationMs: Long get() = segments.sumOf { it.durationMs }
    fun soundtrackUris(): Set<String> = (undo.toList() + redo.toList() + state()).mapNotNull { it.soundtrack?.uri }.toSet()
    fun captionSourceUris(): Set<String> = (undo.toList() + redo.toList() + state()).flatMap { state -> state.clips.mapNotNull { it.captionSourceUri } }.toSet()
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    private fun change(next: List<VideoSegment>, music: VideoSoundtrack? = soundtrack) {
        validate(next, sourceDurations)
        if (next == segments && music == soundtrack) return
        undo.addLast(state())
        if (undo.size > 50) undo.removeFirst()
        redo.clear()
        segments = next; soundtrack = music
    }

    fun split(index: Int, sourceTimeMs: Long) {
        val clip = segments[index]
        require(sourceTimeMs > clip.startMs && sourceTimeMs < clip.endMs) { "Split inside the clip" }
        change(segments.toMutableList().apply {
            removeAt(index)
            add(index, clip.copy(startMs = sourceTimeMs, captions = clip.captions.filter { it.endMs > sourceTimeMs && it.startMs < clip.endMs }))
            add(index, clip.copy(endMs = sourceTimeMs, captions = clip.captions.filter { it.startMs < sourceTimeMs && it.endMs > clip.startMs }))
        })
    }

    fun trim(index: Int, startMs: Long, endMs: Long) {
        val clip = segments[index]
        require(startMs >= clip.startMs && endMs <= clip.endMs) { "Trim inside the clip" }
        change(segments.toMutableList().apply { set(index, clip.copy(startMs = startMs, endMs = endMs, captions = clip.captions.filter { it.endMs > startMs && it.startMs < endMs })) })
    }

    fun setSpeed(index: Int, speed: Float) {
        change(segments.toMutableList().apply { set(index, get(index).copy(speed = speed)) })
    }

    fun setVolume(index: Int, volume: Float) {
        change(segments.toMutableList().apply { set(index, get(index).copy(volume = volume)) })
    }

    fun setSoundtrack(track: VideoSoundtrack?) = change(segments,track)

    fun setTitle(index: Int, title: String) = change(segments.toMutableList().apply { set(index, get(index).copy(title = title)) })
    fun setCaptions(index: Int, cues: List<VideoCaption>, sourceUri: String? = segments[index].captionSourceUri) = change(segments.toMutableList().apply { set(index, get(index).copy(captions = cues.toList(), captionSourceUri = sourceUri)) })
    fun setFadeEdges(index: Int, enabled: Boolean) = change(segments.toMutableList().apply { set(index, get(index).copy(fadeEdges = enabled)) })
    fun setGrade(index: Int, grade: VideoGrade) = change(segments.toMutableList().apply { set(index, get(index).copy(grade = grade)) })
    fun setLayout(index:Int,layout:VideoLayout)=change(segments.toMutableList().apply{set(index,get(index).copy(layout=layout))})
    fun setTitleStyle(index:Int,style:VideoTitleStyle)=change(segments.toMutableList().apply{set(index,get(index).copy(titleStyle=style))})
    fun setFormat(ratio:Int,crop:Boolean)=change(segments.map{it.copy(layout=it.layout.copy(ratio=ratio,crop=crop))})
    fun setProjectAudioFade(enabled:Boolean)=change(segments.map{it.copy(audioFade=enabled)})
    fun applyGradeToAll(index:Int)=change(segments.map{it.copy(grade=segments[index].grade)})

    fun duplicate(index: Int) {
        require(segments.size < 1_000) { "A project supports up to 1000 clips" }
        val clip = segments[index]
        change(segments.toMutableList().apply { add(index + 1, clip.copy()) })
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
        redo.addLast(state())
        val restored = undo.removeLast();segments = restored.clips;soundtrack = restored.soundtrack
        return true
    }

    fun redo(): Boolean {
        if (redo.isEmpty()) return false
        undo.addLast(state())
        val restored = redo.removeLast();segments = restored.clips;soundtrack = restored.soundtrack
        return true
    }

    fun reset() = change(original,null)
}
