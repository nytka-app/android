package io.github.nytka_app.core.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `codecs` field of `/api/v1/info`: a server without it reads Opus FS320 only. */
class ServerInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(body: String) = json.decodeFromString<ServerInfo>(body)

    @Test
    fun `a server without codecs reads Opus FS320 only`() {
        val info = parse("""{"serverVersion":"0.36.7","apiVersion":1}""")

        assertTrue(info.reads(21))
        assertFalse(info.reads(40))
    }

    @Test
    fun `a server reads the codecs it lists`() {
        val info = parse("""{"serverVersion":"0.40.0","apiVersion":1,"codecs":[21,40]}""")

        assertTrue(info.reads(21))
        assertTrue(info.reads(40))
        assertFalse(info.reads(3))
    }
}
