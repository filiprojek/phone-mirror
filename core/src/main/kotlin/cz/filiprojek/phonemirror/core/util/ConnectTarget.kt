package cz.filiprojek.phonemirror.core.util

import cz.filiprojek.phonemirror.core.protocol.Protocol
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder

/** Where a display should connect: from NSD, a QR code (`phonemirror://host:port?name=&id=`) or typed input. */
data class ConnectTarget(val host: String, val port: Int, val name: String? = null, val deviceId: String? = null) {

    fun toUri(): String {
        val hostPart = if (':' in host) "[$host]" else host
        val query = listOfNotNull(name?.let { "name=${encode(it)}" }, deviceId?.let { "id=${encode(it)}" })
        return "$SCHEME://$hostPart:$port" + if (query.isEmpty()) "" else query.joinToString("&", prefix = "?")
    }

    companion object {
        private const val SCHEME = "phonemirror"

        fun parseUri(text: String): ConnectTarget? {
            val uri = try {
                URI(text.trim())
            } catch (e: URISyntaxException) {
                return null
            }
            if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
            val host = uri.host?.removeSurrounding("[", "]")?.takeIf { it.isNotEmpty() } ?: return null
            val port = if (uri.port == -1) Protocol.DEFAULT_PORT else uri.port
            if (port !in 1..65535) return null
            val params = uri.rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
                val (k, v) = it.split('=', limit = 2)
                k to decode(v)
            }
            return ConnectTarget(host, port, params["name"], params["id"])
        }

        /** Accepts `host`, `host:port`, or a full `phonemirror://` URI. */
        fun parseManual(text: String): ConnectTarget? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.endsWith(':')) return null
            if (trimmed.startsWith("$SCHEME://", ignoreCase = true)) return parseUri(trimmed)
            return parseUri("$SCHEME://$trimmed")
        }

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
        private fun decode(value: String) = URLDecoder.decode(value, Charsets.UTF_8)
    }
}
