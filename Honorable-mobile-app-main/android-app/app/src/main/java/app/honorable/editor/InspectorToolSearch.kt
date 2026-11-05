package app.honorable.editor

import java.text.Normalizer
import java.util.Locale

/** Local discovery over the caller's already-authorized tool catalogue; never media search. */
object InspectorToolSearch {
    const val MAX_QUERY = 80
    data class Section(val index: Int, val toolIndices: List<Int>)
    data class Result(val sections: List<Section>, val found: Int, val total: Int, val searching: Boolean)
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private fun normalized(value: String): String = separators.replace(
        marks.replace(Normalizer.normalize(Normalizer.normalize(value, Normalizer.Form.NFKC), Normalizer.Form.NFD), "")
            .lowercase(Locale.ROOT), " "
    ).trim()

    fun find(catalogue: List<Pair<String, List<String>>>, query: String): Result {
        require(query.length <= MAX_QUERY) { "Tool search is limited to 80 characters." }
        val searching = query.isNotBlank()
        val terms = normalized(query).split(' ').filter { it.isNotEmpty() }.distinct()
        val sections = catalogue.mapIndexedNotNull { sectionIndex, (name, labels) ->
            val indices = labels.indices.filter { toolIndex ->
                if (!searching) true else {
                    val searchable = normalized("$name ${labels[toolIndex]}")
                    terms.isNotEmpty() && terms.all { searchable.contains(it) }
                }
            }
            indices.takeIf { it.isNotEmpty() }?.let { Section(sectionIndex, it) }
        }
        return Result(sections, sections.sumOf { it.toolIndices.size }, catalogue.sumOf { it.second.size }, searching)
    }
}
