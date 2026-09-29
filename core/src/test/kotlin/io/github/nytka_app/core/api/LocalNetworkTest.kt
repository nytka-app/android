package io.github.nytka_app.core.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {
    private fun needsPermission(
        url: String,
        privateNetwork: Boolean = false,
    ) = LocalNetwork.mayNeedPermission(url.toHttpUrl(), privateNetwork)

    @Test
    fun `the ranges Android lists are the local network`() {
        listOf(
            "10.0.0.5",
            "10.255.255.255",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.10",
            "192.168.255.255",
            "169.254.10.1",
        ).forEach { assertTrue(it, needsPermission("http://$it:8080")) }
    }

    @Test
    fun `the shared address range where tailnets live is local from its second octet 64 to 127`() {
        (64..127).forEach { assertTrue("100.$it", needsPermission("http://100.$it.1.1:8080")) }
        (0..63).forEach { assertFalse("100.$it", needsPermission("http://100.$it.1.1:8080")) }
        (128..255).forEach { assertFalse("100.$it", needsPermission("http://100.$it.1.1:8080")) }
    }

    @Test
    fun `addresses beside those ranges are not`() {
        listOf(
            "9.255.255.255",
            "11.0.0.1",
            "172.15.255.255",
            "172.32.0.1",
            "192.167.1.1",
            "192.169.1.1",
            "169.253.1.1",
            "169.255.1.1",
            "8.8.8.8",
            "127.0.0.1",
        ).forEach { assertFalse(it, needsPermission("http://$it:8080")) }
    }

    @Test
    fun `ipv6 link-local and unique local addresses are local`() {
        listOf("[fe80::1]", "[febf::1]", "[fc00::1]", "[fd12:3456::1]").forEach {
            assertTrue(it, needsPermission("http://$it:8080"))
        }
        listOf("[::1]", "[2001:db8::1]", "[fec0::1]", "[fe00::1]").forEach {
            assertFalse(it, needsPermission("http://$it:8080"))
        }
    }

    @Test
    fun `an ipv4 address written as ipv6 counts as the ipv4 one`() {
        assertTrue(needsPermission("http://[::ffff:192.168.1.5]:8080"))
        assertFalse(needsPermission("http://[::ffff:8.8.8.8]:8080"))
    }

    @Test
    fun `an mdns name is local and other names are not`() {
        assertTrue(needsPermission("http://nytka.local:8080"))
        assertFalse(needsPermission("https://nytka.example.com"))
        assertFalse(needsPermission("https://notlocal"))
        assertFalse(needsPermission("https://local.example.com"))
    }

    @Test
    fun `the private-network switch asks whatever the address looks like`() {
        assertTrue(needsPermission("http://nas.home.arpa:8080", privateNetwork = true))
        assertFalse(needsPermission("http://nas.home.arpa:8080"))
    }
}
