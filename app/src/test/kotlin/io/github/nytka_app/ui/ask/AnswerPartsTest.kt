package io.github.nytka_app.ui.ask

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerPartsTest {
    @Test
    fun `markers of known sources become cites between text`() {
        val parts = AnswerParts.parse("Anna [1] and Ben [2][1].", mapOf(1 to "c1", 2 to null))

        assertEquals(
            listOf(
                AnswerPart.Text("Anna "),
                AnswerPart.Cite(1, "c1"),
                AnswerPart.Text(" and Ben "),
                AnswerPart.Cite(2, null),
                AnswerPart.Cite(1, "c1"),
                AnswerPart.Text("."),
            ),
            parts,
        )
    }

    @Test
    fun `a marker naming no source stays text`() {
        assertEquals(
            listOf(AnswerPart.Text("See [7] and [x].")),
            AnswerParts.parse("See [7] and [x].", mapOf(1 to "c1")),
        )
    }

    @Test
    fun `text without markers is one part and an empty answer has none`() {
        assertEquals(listOf(AnswerPart.Text("Nothing.")), AnswerParts.parse("Nothing.", mapOf(1 to "c1")))
        assertEquals(emptyList<AnswerPart>(), AnswerParts.parse("", mapOf(1 to "c1")))
    }

    @Test
    fun `plain removes mark tags and decodes entities`() {
        assertEquals(
            "a & b <c> \"d\" 'e'",
            AnswerParts.plain("a <mark>&amp;</mark> b &lt;c&gt; &quot;d&quot; &#39;e&#39;"),
        )
    }
}
