package com.kinogo.atv.data.network

import com.kinogo.atv.data.mirror.NetworkAddressPolicy
import java.io.Reader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import okhttp3.Dns

/**
 * Public-only DNS with a short-lived Cloudflare DoH fallback.
 *
 * Android TV ISP resolvers can return a different Cloudflare anycast pool than a desktop browser
 * using secure DNS. A valid DoH result is authoritative; the system resolver is used only when
 * DoH is unavailable. Any private/non-routable answer rejects the complete chosen result,
 * preserving the app's SSRF boundary.
 */
class ResilientPublicDns internal constructor(
    private val systemLookup: (String) -> List<InetAddress>,
    private val dohLookup: (String) -> List<InetAddress>,
    private val nowMs: () -> Long,
    private val cacheTtlMs: Long,
) : Dns {
    constructor() : this(
        systemLookup = { hostname -> Dns.SYSTEM.lookup(hostname) },
        dohLookup = CloudflareDnsOverHttps::lookupIpv4,
        nowMs = { System.nanoTime() / 1_000_000L },
        cacheTtlMs = DEFAULT_CACHE_TTL_MS,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    init {
        require(cacheTtlMs > 0L)
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val key = hostname.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) throw UnknownHostException("DNS hostname is empty")
        val now = nowMs()
        cache[key]?.takeIf { now - it.createdAtMs in 0..cacheTtlMs }?.let { return it.addresses }

        val secureAnswers = if (isIpv4Literal(key)) {
            emptyList()
        } else {
            runCatching { dohLookup(key) }.getOrDefault(emptyList())
        }
        val raw = secureAnswers.ifEmpty {
            runCatching { systemLookup(key) }.getOrDefault(emptyList())
        }
        if (raw.isEmpty() || raw.any { !NetworkAddressPolicy.isPublic(it) }) {
            throw UnknownHostException("Host did not resolve exclusively to public addresses")
        }
        val addresses = raw
            .distinctBy { it.address.toList() }
            .sortedBy { if (it is Inet4Address) 0 else 1 }
        cache[key] = CacheEntry(addresses, now)
        return addresses
    }

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val createdAtMs: Long,
    )

    private companion object {
        const val DEFAULT_CACHE_TTL_MS = 5 * 60 * 1_000L
        val IPV4_LITERAL = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")

        fun isIpv4Literal(value: String): Boolean = IPV4_LITERAL.matches(value)
    }
}

internal object CloudflareDnsOverHttps {
    private const val ENDPOINT = "https://1.1.1.1/dns-query"
    private const val MAX_RESPONSE_CHARS = 64 * 1_024
    private val answerObject = Regex(
        // Android's ICU regex engine treats an unescaped closing brace as a syntax error,
        // while the desktop JVM accepts it. Character classes keep both literal braces
        // unambiguous on every supported Android version.
        """[{][^{}]*"type"\s*:\s*1[^{}]*"data"\s*:\s*"([0-9.]+)"[^{}]*[}]""",
    )

    fun lookupIpv4(hostname: String): List<InetAddress> {
        val encoded = URLEncoder.encode(hostname, StandardCharsets.UTF_8.name())
        val uri = URI.create("$ENDPOINT?name=$encoded&type=A")
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 2_500
            connection.readTimeout = 2_500
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/dns-json")
            connection.setRequestProperty("User-Agent", "KinogoATV/0.5 DNS")
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use(::readLimited)
            answerObject.findAll(body)
                .mapNotNull { match -> ipv4Address(hostname, match.groupValues[1]) }
                .toList()
        } finally {
            connection.disconnect()
        }
    }

    private fun ipv4Address(hostname: String, value: String): InetAddress? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        parts.forEachIndexed { index, part ->
            val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            bytes[index] = octet.toByte()
        }
        return InetAddress.getByAddress(hostname, bytes)
    }

    private fun readLimited(reader: Reader): String {
        val result = StringBuilder()
        val buffer = CharArray(2 * 1_024)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            require(result.length + read <= MAX_RESPONSE_CHARS) { "DNS response is too large" }
            result.append(buffer, 0, read)
        }
        return result.toString()
    }
}
