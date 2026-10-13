package io.github.nytka_app.core.api

import java.net.URI
import java.net.URLDecoder

/** What a `nytka://pair` QR code decodes to. */
sealed interface QrPairing {
    data class Ok(
        val url: String,
        val token: String,
    ) : QrPairing

    data class Invalid(
        val reason: String,
    ) : QrPairing
}

/**
 * Parses the text a server's pairing QR code carries: `nytka://pair?url=<server url, URL-encoded>&token=<token>`.
 * Only the envelope is checked here. A missing token comes back blank and a non-https url comes back as given: both
 * are left for [ServerUrl.check] and the blank-token check in `FirstRunViewModel.testConnection` to refuse, the same
 * way they refuse manual entry.
 */
object QrPairingPayload {
    private const val SCHEME = "nytka"
    private const val HOST = "pair"
    const val NOT_A_PAIRING_CODE = "That QR code is not a Nytka pairing code."

    fun parse(text: String): QrPairing {
        val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return QrPairing.Invalid(NOT_A_PAIRING_CODE)
        if (uri.scheme != SCHEME || uri.host != HOST) return QrPairing.Invalid(NOT_A_PAIRING_CODE)
        val params = query(uri.rawQuery)
        val url = params["url"]
        if (url.isNullOrBlank()) return QrPairing.Invalid(NOT_A_PAIRING_CODE)
        return QrPairing.Ok(url, params["token"].orEmpty())
    }

    private fun query(raw: String?): Map<String, String> =
        raw
            ?.split("&")
            ?.filter { it.isNotEmpty() }
            ?.associate { pair ->
                val parts = pair.split("=", limit = 2)
                decode(parts[0]) to decode(parts.getOrElse(1) { "" })
            }
            ?: emptyMap()

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
