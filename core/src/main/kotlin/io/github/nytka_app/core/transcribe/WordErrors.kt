package io.github.nytka_app.core.transcribe

/** Word error rate for the benchmark: lower case, letters and digits only, Levenshtein distance over words. */
object WordErrors {
    fun rate(
        reference: String,
        hypothesis: String,
    ): Double {
        val ref = words(reference)
        val hyp = words(hypothesis)
        if (ref.isEmpty()) return if (hyp.isEmpty()) 0.0 else 1.0
        var previous = IntArray(hyp.size + 1) { it }
        for (i in 1..ref.size) {
            val current = IntArray(hyp.size + 1)
            current[0] = i
            for (j in 1..hyp.size) {
                val substitution = previous[j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            previous = current
        }
        return previous[hyp.size].toDouble() / ref.size
    }

    private fun words(text: String) =
        text
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}']+"))
            .filter { it.isNotEmpty() }
}
