package io.github.nytka_app.core.api

import okhttp3.HttpUrl

/**
 * An app that targets Android 17 may use the local network only with ACCESS_LOCAL_NETWORK. What counts is traffic
 * over a broadcast-capable interface, Wi-Fi or Ethernet, to the ranges below; cellular and VPN connections are left
 * out (developer.android.com/privacy-and-security/local-network-definition). The app cannot know which interface a
 * request will take, so a server counts as local when its address is in one of those ranges.
 */
object LocalNetwork {
    /**
     * True when the server at [base] may need the permission: the user turned the private-network switch on, or the
     * host is an address in a local range or a `.local` (mDNS) name. A name that only resolves to a local address, or
     * an IPv6 address on the phone's own network, is not caught: telling would take a DNS lookup or the phone's routes.
     * The app shows a hint under a server that does not answer instead, whatever its address looks like.
     */
    fun mayNeedPermission(
        base: HttpUrl,
        privateNetwork: Boolean,
    ): Boolean = privateNetwork || isLocalHost(base.host)

    fun isLocalHost(host: String): Boolean =
        when {
            host.endsWith(".local") -> true
            ':' in host -> isLocalIpv6(host)
            else -> octets(host)?.let(::isLocalIpv4) ?: false
        }

    private fun octets(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != IPV4_PARTS) return null
        return IntArray(IPV4_PARTS) { parts[it].toIntOrNull()?.takeIf { octet -> octet in 0..255 } ?: return null }
    }

    private fun isLocalIpv4(octets: IntArray): Boolean {
        val (first, second) = octets
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            // Link-local.
            (first == 169 && second == 254) ||
            // CGNAT, where tailnets live.
            (first == 100 && second in 64..127)
    }

    /** Link-local fe80::/10 and unique local fc00::/7; OkHttp hands over the host without brackets. */
    private fun isLocalIpv6(host: String): Boolean {
        val first = host.substringBefore(':').toIntOrNull(radix = 16) ?: return false
        return (first and 0xffc0) == 0xfe80 || (first and 0xfe00) == 0xfc00
    }

    private const val IPV4_PARTS = 4
}
