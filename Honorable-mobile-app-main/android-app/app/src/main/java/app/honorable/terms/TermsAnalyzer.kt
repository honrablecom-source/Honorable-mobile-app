package app.honorable.terms

data class TermsAnalysis(val risk: Risk, val summary: String, val good: List<String>, val watchOut: List<String>, val important: List<String>)
enum class Risk { LOW, MEDIUM, HIGH, UNKNOWN }

class TermsAnalyzer {
    fun analyze(text: String): TermsAnalysis {
        val clauses = text.split(Regex("(?<=[.!?])\\s+"))
        val watch = clauses.filter { it.contains(Regex("auto.?renew|arbitration|class.?action|non.?refund|share.*data|terminate", RegexOption.IGNORE_CASE)) }.take(8)
        val good = clauses.filter { it.contains(Regex("cancel|refund|delete.*data|opt.?out", RegexOption.IGNORE_CASE)) }.take(5)
        val risk = when { text.isBlank() -> Risk.UNKNOWN; watch.size >= 4 -> Risk.HIGH; watch.isNotEmpty() -> Risk.MEDIUM; else -> Risk.LOW }
        return TermsAnalysis(risk, if (text.isBlank()) "Add an agreement to analyze." else "Local rule-based review found ${watch.size} clause(s) to examine.", good, watch, clauses.take(3))
    }
    companion object { const val DISCLAIMER = "Informational summary only; not legal advice." }
}
