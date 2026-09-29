package io.github.nytka_app.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    private fun ok(
        text: String,
        privateNetwork: Boolean = false,
    ) = (ServerUrl.check(text, privateNetwork) as UrlCheck.Ok).base.toString()

    private fun invalid(
        text: String,
        privateNetwork: Boolean = false,
    ) = (ServerUrl.check(text, privateNetwork) as UrlCheck.Invalid).reason

    @Test
    fun `accepts https and normalises the trailing slash`() {
        assertEquals("https://nytka.example.com/", ok(" https://nytka.example.com "))
        assertEquals("https://example.com/nytka/", ok("https://example.com/nytka"))
    }

    @Test
    fun `plain http needs the private network switch`() {
        assertTrue(invalid("http://10.0.0.5:8080").contains("private network"))
        assertEquals("http://10.0.0.5:8080/", ok("http://10.0.0.5:8080", privateNetwork = true))
    }

    @Test
    fun `rejects text that is not a URL`() {
        assertTrue(invalid("nytka.example.com").contains("https://"))
        assertTrue(invalid("").isNotEmpty())
    }
}
