package com.honorablemobile

/** Literal text proposals only; source timestamps/provenance and every other edit stay attached. */
internal object VideoCaptionReplace {
    private fun checkProject(clips: List<VideoSegment>) {
        require(clips.size in 1..1000) { "Use a project with 1–1000 clips" }
        require(clips.sumOf { it.captions.size } <= VideoCaptions.MAX_PROJECT_CUES) { "Project caption count limit reached" }
        require(clips.sumOf { clip -> clip.captions.sumOf { it.text.toByteArray(Charsets.UTF_8).size } } <= VideoCaptions.MAX_PROJECT_TEXT_BYTES) { "Project caption text limit reached" }
    }
    fun propose(clips: List<VideoSegment>, find: String, replacement: String, clipIndex: Int?, ignoreCase: Boolean = false): List<VideoSegment> {
        checkProject(clips)
        require(clipIndex == null || clipIndex in clips.indices) { "Choose a valid clip scope" }
        require(find.length in 1..240 && replacement.length <= 240) { "Use 1–240 find characters and up to 240 replacement characters" }
        require(listOf(find, replacement).all { value -> value.none { it.isISOControl() } && value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == value }) { "Use valid single-line find and replacement text" }
        var changed = false
        val result = clips.mapIndexed { index, clip ->
            if (clipIndex != null && index != clipIndex) clip else {
                val cues = clip.captions.map { cue ->
                    val text = cue.text.replace(find, replacement, ignoreCase)
                    if (text == cue.text) cue else {
                        changed = true
                        cue.copy(text = text)
                    }
                }
                clip.copy(captions = cues)
            }
        }
        require(changed) { "No caption text changed. Check the find text, case setting and scope." }
        checkProject(result)
        return result
    }
}
