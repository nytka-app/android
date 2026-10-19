package io.github.nytka_app.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPairingPayloadTest {
    private fun ok(text: String) = QrPairingPayload.parse(text) as QrPairing.Ok

    private fun invalid(text: String) = (QrPairingPayload.parse(text) as QrPairing.Invalid).reason

    @Test
    fun `parses the server's url and token`() {
        val result = ok("nytka://pair?url=https%3A%2F%2Fnytka.example.com&token=abc123")

        assertEquals("https://nytka.example.com", result.url)
        assertEquals("abc123", result.token)
    }

    @Test
    fun `toString hides the token`() {
        val text = ok("nytka://pair?url=https%3A%2F%2Fnytka.example.com&token=abc123").toString()

        assertTrue(text.contains("token=set"))
        assertTrue(!text.contains("abc123"))
    }

    @Test
    fun `a missing token comes back blank, for the blank-token check to refuse like manual entry`() {
        val result = ok("nytka://pair?url=https%3A%2F%2Fnytka.example.com")

        assertEquals("https://nytka.example.com", result.url)
        assertEquals("", result.token)
    }

    @Test
    fun `a non-https url is passed through, for ServerUrl#check to refuse like manual entry`() {
        val result = ok("nytka://pair?url=http%3A%2F%2F10.0.0.5%3A8080&token=abc123")

        assertEquals("http://10.0.0.5:8080", result.url)
        val check = ServerUrl.check(result.url, privateNetwork = false) as UrlCheck.Invalid
        assertTrue(check.reason.contains("private network"))
    }

    @Test
    fun `rejects junk`() {
        assertTrue(invalid("not a pairing code").isNotEmpty())
        assertTrue(invalid("").isNotEmpty())
        assertTrue(invalid("https://nytka.example.com/pair?token=abc123").isNotEmpty())
        assertTrue(invalid("nytka://wrong?url=https%3A%2F%2Fx&token=y").isNotEmpty())
        assertTrue(invalid("nytka://pair?token=abc123").isNotEmpty())
    }
}
