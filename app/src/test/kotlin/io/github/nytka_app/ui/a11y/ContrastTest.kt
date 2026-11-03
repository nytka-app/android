package io.github.nytka_app.ui.a11y

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ContrastTest {
    @Test
    fun `black on white is 21 to 1`() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 0.001)
    }

    @Test
    fun `a colour on itself is 1 to 1`() {
        assertEquals(1.0, Contrast.ratio(Color(0xFF77737D), Color(0xFF77737D)), 0.001)
    }

    @Test
    fun `order does not matter`() {
        val a = Color(0xFF33427E)
        val b = Color(0xFFF7F3EC)
        assertEquals(Contrast.ratio(a, b), Contrast.ratio(b, a), 0.0)
    }

    @Test
    fun `mid grey on white matches the WCAG reference 4_48 to 1`() {
        assertEquals(4.48, Contrast.ratio(Color(0xFF777777), Color.White), 0.01)
    }
}
