package io.github.nytka_app.ui.ask

/** A run of an answer: plain [Text], or the marker `[n]` of a source ([Cite]) with the conversation it opens. */
sealed interface AnswerPart {
    data class Text(
        val text: String,
    ) : AnswerPart

    data class Cite(
        val n: Int,
        val openId: String?,
    ) : AnswerPart
}

object AnswerParts {
    private val marker = Regex("""\[(\d{1,3})]""")

    /**
     * Splits [answer] at its `[n]` markers. A marker naming a source in [openIds] (source number to the conversation
     * it opens, null when it opens none) becomes a [AnswerPart.Cite]; any other bracketed number stays text.
     */
    fun parse(
        answer: String,
        openIds: Map<Int, String?>,
    ): List<AnswerPart> {
        val parts = mutableListOf<AnswerPart>()
        var last = 0
        for (match in marker.findAll(answer)) {
            val n = match.groupValues[1].toInt()
            if (n !in openIds) continue
            if (match.range.first > last) parts += AnswerPart.Text(answer.substring(last, match.range.first))
            parts += AnswerPart.Cite(n, openIds[n])
            last = match.range.last + 1
        }
        if (last < answer.length) parts += AnswerPart.Text(answer.substring(last))
        return parts
    }

    /** A snippet as plain text: search's `<mark>` tags removed and the common entities decoded. */
    fun plain(snippet: String): String =
        snippet
            .replace("<mark>", "")
            .replace("</mark>", "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
}
