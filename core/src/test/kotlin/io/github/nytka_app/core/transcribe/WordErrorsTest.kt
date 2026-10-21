package io.github.nytka_app.core.transcribe

import org.junit.Assert.assertEquals
import org.junit.Test

class WordErrorsTest {
    @Test
    fun `case and punctuation do not count`() {
        assertEquals(0.0, WordErrors.rate("This is the Nytka pendant.", "this is the nytka pendant"), 1e-9)
    }

    @Test
    fun `a substitution, an insertion and a deletion each count once`() {
        assertEquals(1.0 / 4, WordErrors.rate("one two three four", "one too three four"), 1e-9)
        assertEquals(1.0 / 4, WordErrors.rate("one two three four", "one two three four five"), 1e-9)
        assertEquals(1.0 / 4, WordErrors.rate("one two three four", "one three four"), 1e-9)
    }

    @Test
    fun `Cyrillic words compare as words`() {
        assertEquals(0.5, WordErrors.rate("привіт світ", "привіт мир"), 1e-9)
    }
}
