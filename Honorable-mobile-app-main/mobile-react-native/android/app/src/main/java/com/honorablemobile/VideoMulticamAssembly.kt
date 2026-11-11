package com.honorablemobile

/** Manual fixed-offset camera synchronization; all resulting cuts run at normal speed. */
internal object VideoMulticamAssembly {
    data class Anchor(val sourceId: String, val sourceMs: Long, val programMs: Long) {
        val offsetMs get() = sourceMs - programMs
    }
    data class Cut(val programFromMs: Long, val programToMs: Long, val templateIndex: Int, val clip: VideoSegment)
    data class Result(val clips: List<VideoSegment>, val anchors: List<Anchor>, val cuts: List<Cut>, val durationMs: Long)
    fun prepare(clips: List<VideoSegment>, durations: Map<String, Long>, anchorText: String,
                switchText: String, end: String, music: VideoSoundtrack? = null): Result {
        VideoPlaybackTime.durationUs(clips)
        require(durations.size in 2..32 && durations.all { (id, duration) ->
            id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && !id.startsWith("__") && duration in 1..Int.MAX_VALUE.toLong()
        }) { "Add at least two valid original sources first" }
        require(clips.all { it.endMs <= (durations[it.sourceId] ?: 0) }) { "An original source duration changed" }
        require(anchorText.length <= 16000 && switchText.length <= 16000) { "Use at most 16000 characters per worksheet" }
        val templates = clips.withIndex().groupBy { it.value.sourceId }.mapValues { it.value.first() }
        val anchors = anchorText.trim().lines().filter { it.isNotBlank() }.mapIndexed { row, line ->
            val columns = line.split(',').map { it.trim() }
            require(columns.size == 3) { "Anchor line ${row + 1}: use sourceID,sourceSeconds,programSeconds" }
            val id = columns[0]
            require(id in durations && id in templates) { "Anchor line ${row + 1}: choose a source already present in the timeline" }
            val source = VideoCaptions.seconds(columns[1]); val program = VideoCaptions.seconds(columns[2])
            require(source <= durations.getValue(id)) { "Anchor line ${row + 1}: source anchor exceeds the original duration" }
            Anchor(id, source, program)
        }
        require(anchors.size in 2..32 && anchors.map { it.sourceId }.distinct().size == anchors.size) { "Use 2–32 distinct source anchors" }
        val byId = anchors.associateBy { it.sourceId }
        val duration = VideoCaptions.seconds(end)
        require(duration > 0) { "Choose a positive program end" }
        val switches = switchText.trim().lines().filter { it.isNotBlank() }.mapIndexed { row, line ->
            val columns = line.split(',').map { it.trim() }
            require(columns.size == 2) { "Switch line ${row + 1}: use programSeconds,sourceID" }
            val time = VideoCaptions.seconds(columns[0]); val id = columns[1]
            require(id in byId) { "Switch line ${row + 1}: calibrate this source first" }
            time to id
        }
        require(switches.size in 2..200 && switches.map { it.second }.distinct().size >= 2) { "Use 2–200 switches involving at least two calibrated cameras" }
        require(switches.first().first == 0L && switches.last().first < duration && switches.zipWithNext().all { it.first.first < it.second.first }) { "Start switches at zero, increase strictly, and finish before program end" }
        val cuts = switches.mapIndexed { index, (from, id) ->
            val to = switches.getOrNull(index + 1)?.first ?: duration
            require(to - from >= 100) { "Switch ${index + 1}: keep at least 0.1 program second" }
            val offset = byId.getValue(id).offsetMs
            val sourceFrom = from + offset; val sourceTo = to + offset
            require(sourceFrom >= 0 && sourceTo <= durations.getValue(id)) { "Switch ${index + 1}: synchronized window lies outside that camera original" }
            val template = templates.getValue(id)
            val clip = template.value.copy(startMs = sourceFrom, endMs = sourceTo, speed = 1f,
                captions = template.value.captions.filter { it.startMs < sourceTo && it.endMs > sourceFrom })
            Cut(from, to, template.index, clip)
        }
        val result = cuts.map { it.clip }
        require(result != clips) { "This camera-switch worksheet leaves the timeline unchanged" }
        require(result.sumOf { it.captions.size } <= VideoCaptions.MAX_PROJECT_CUES && result.sumOf { clip -> clip.captions.sumOf { it.text.toByteArray(Charsets.UTF_8).size } } <= VideoCaptions.MAX_PROJECT_TEXT_BYTES) { "Camera switches exceed project caption limits" }
        require(result.sumOf { it.title.toByteArray(Charsets.UTF_8).size } <= 128000) { "Camera switches exceed project title limits" }
        VideoDraftCodec.requireRecoverableCaptions(result, music?.loop == false, music?.hasExcerpt == true)
        check(VideoPlaybackTime.durationUs(result) == duration * 1000)
        return Result(result, anchors.toList(), cuts, duration)
    }
}
