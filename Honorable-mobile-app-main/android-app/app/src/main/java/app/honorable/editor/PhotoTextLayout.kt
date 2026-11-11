package app.honorable.editor

import java.util.Collections
import java.util.UUID

/** Geometry/text originate from real OCR; this planner neither recognizes text nor infers fonts. */
object PhotoTextLayout {
    const val MAX_LINES = 100
    const val MAX_RAW_LINE = 1000
    const val MAX_RAW_TOTAL = 16_000

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        init { require(left < right && top < bottom) { "Invalid OCR line geometry" } }
    }
    data class RawLine(val text: String, val box: Box)
    data class Line(val id: Int, val text: String, val box: Box)

    class Batch private constructor(val width: Int, val height: Int, lines: List<Line>) {
        val lines: List<Line> = Collections.unmodifiableList(lines.toList())
        companion object {
            fun capture(width: Int, height: Int, lines: List<RawLine>): Batch {
                require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 6_000_000) { "OCR image exceeds the working-preview limit" }
                require(lines.size in 1..MAX_LINES) { "Use a crop with 1–100 recognized lines" }
                var total = 0
                val owned = lines.mapIndexed { index, line ->
                    require(line.text.isNotBlank() && line.text.length <= MAX_RAW_LINE && line.text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == line.text) { "OCR line text exceeds the review limit" }
                    total += line.text.length
                    require(total <= MAX_RAW_TOTAL) { "OCR text exceeds the review limit; use a smaller crop" }
                    val box = line.box
                    val left = box.left.coerceIn(0, width); val right = box.right.coerceIn(0, width)
                    val top = box.top.coerceIn(0, height); val bottom = box.bottom.coerceIn(0, height)
                    require(left < right && top < bottom) { "OCR line lies outside this working image" }
                    Line(index + 1, line.text, Box(left, top, right, bottom))
                }
                return Batch(width, height, owned)
            }
        }
    }

    data class Frame(val left: Float, val top: Float, val width: Float, val height: Float)
    fun interface Fitter {
        /** Return a supported normalized font size only if the complete corrected text really fits. */
        fun fit(text: String, frame: Frame, unit: Float, idealSize: Float): Float?
    }
    data class ReviewedLine(val id: Int, val text: String, val objectId: String)
    class Plan internal constructor(val base: PhotoEdit, val edit: PhotoEdit, selected: List<ReviewedLine>, inserted: List<PhotoObject>) {
        val selected: List<ReviewedLine> = Collections.unmodifiableList(selected.toList())
        val inserted: List<PhotoObject> = Collections.unmodifiableList(inserted.toList())
        fun matches(current: PhotoEdit) = current === base
    }

    /** White paper and chosen text are appended together; existing pixels/layers/scalars stay intact. */
    fun prepare(base: PhotoEdit, batch: Batch, corrections: Map<Int, String>, fitter: Fitter,
                id: (Int) -> String = { "ocr-${UUID.randomUUID()}" }, current: () -> Unit = {}): Plan {
        current()
        require(corrections.isNotEmpty() && corrections.keys.all { key -> batch.lines.any { it.id == key } }) { "Choose recognized lines from this review" }
        require(base.layers.objects.size + corrections.size + 1 <= 32) { "There is room for ${maxOf(0, 31 - base.layers.objects.size)} text lines plus white paper; remove old objects or select fewer lines" }
        val identifiers = base.layers.objects.mapTo(mutableSetOf()) { it.id }
        fun next(index: Int): String {
            val value = id(index)
            require(value.matches(Regex("[A-Za-z0-9_-]{1,64}")) && identifiers.add(value)) { "New text object identifiers collided; reopen this control" }
            return value
        }
        val paper = PhotoObject(next(0), PhotoObjectType.RECTANGLE, width = 1f, height = 1f, color = -1)
        val inserted = mutableListOf(paper)
        val selected = mutableListOf<ReviewedLine>()
        val unit = minOf(batch.width, batch.height).toFloat()
        for (line in batch.lines) {
            current()
            val text = corrections[line.id] ?: continue
            require(text.isNotBlank() && text.length <= 200 && text.count { it == '\n' } <= 2 && text.none { it.isISOControl() && it != '\n' } &&
                text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8) == text) { "Correct line ${line.id}: use 1–200 valid characters and at most three lines" }
            val box = line.box
            val width = maxOf(box.right.toFloat() - box.left + 4f, batch.width * .02f).coerceAtMost(batch.width.toFloat())
            val height = maxOf((box.bottom.toFloat() - box.top) * 1.5f + 4f, batch.height * .02f, unit * .04f).coerceAtMost(batch.height.toFloat())
            val centerX = ((box.left.toFloat() + box.right) / 2).coerceIn(width / 2, batch.width - width / 2)
            val centerY = ((box.top.toFloat() + box.bottom) / 2).coerceIn(height / 2, batch.height - height / 2)
            val relativeWidth = (width / batch.width).coerceIn(.02f, 1f)
            val relativeHeight = (height / batch.height).coerceIn(.02f, 1f)
            val relativeX = (centerX / batch.width).coerceIn(relativeWidth / 2, 1f - relativeWidth / 2)
            val relativeY = (centerY / batch.height).coerceIn(relativeHeight / 2, 1f - relativeHeight / 2)
            // Measure the exact float-normalized frame used by PhotoObjectPlan/StaticLayout,
            // including its integer width conversion; measuring the pre-normalized box can differ.
            val actualWidth = (relativeWidth * batch.width).coerceAtLeast(1f)
            val actualHeight = (relativeHeight * batch.height).coerceAtLeast(1f)
            val frame = Frame(relativeX * batch.width - actualWidth / 2, relativeY * batch.height - actualHeight / 2, actualWidth, actualHeight)
            val ideal = ((box.bottom.toFloat() - box.top) * .8f / unit).coerceIn(.02f, .2f)
            val size = fitter.fit(text, frame, unit, ideal)
            require(size != null && size.isFinite() && size in .02f.. .2f) { "Correct line ${line.id}: the complete text cannot fit at a supported font size; shorten it or use a closer crop" }
            val objectId = next(inserted.size)
            inserted += PhotoObject(objectId, PhotoObjectType.TEXT, x = relativeX, y = relativeY,
                width = relativeWidth, height = relativeHeight, text = text, textSize = size,
                textStyle = PhotoTextStyle(alignment = 0))
            selected += ReviewedLine(line.id, text, objectId)
        }
        current()
        return Plan(base, base.copy(layers = PhotoLayers(base.layers.objects + inserted)), selected, inserted)
    }
}
