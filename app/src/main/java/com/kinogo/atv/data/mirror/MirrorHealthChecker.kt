package com.kinogo.atv.data.mirror

import com.kinogo.atv.data.network.ResilientPublicDns
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class MirrorProbeReport(
    val requestedOrigin: String,
    val resolvedOrigin: String,
    val result: MirrorHealthResult,
)

data class MirrorRefreshResult(
    val entries: List<MirrorEntry>,
    val active: MirrorEntry?,
    val reports: List<MirrorProbeReport>,
)

/**
 * Bounded HTTPS probe with redirect-by-redirect DNS validation. A transport success alone is not
 * enough: the response must also match a conservative Kinogo HTML fingerprint.
 */
class MirrorHealthChecker(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 6_000,
    private val maxRedirects: Int = 4,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val dns: Dns = ResilientPublicDns(),
) {
    private val client = OkHttpClient.Builder()
        .dns(dns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .callTimeout((connectTimeoutMs + readTimeoutMs + 1_000L), TimeUnit.MILLISECONDS)
        .build()

    init {
        require(connectTimeoutMs > 0)
        require(readTimeoutMs > 0)
        require(maxRedirects >= 0)
    }

    suspend fun probe(rawOrigin: String): MirrorProbeReport = withContext(Dispatchers.IO) {
        val requestedOrigin = MirrorUrlNormalizer.normalize(rawOrigin)
        val startedAtNs = System.nanoTime()
        var currentUri = URI.create("$requestedOrigin/")
        var currentOrigin = requestedOrigin
        var redirects = 0

        try {
            var completedReport: MirrorProbeReport? = null
            while (completedReport == null) {
                NetworkDestinationValidator.validateHttpsPublic(currentUri, dns)
                val request = Request.Builder()
                    .url(currentUri.toASCIIString())
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "ru,en;q=0.7")
                    .header("Range", "bytes=0-${MAX_BODY_BYTES - 1}")
                    .header("User-Agent", USER_AGENT)
                    .build()

                val response = client.newCall(request).execute()
                try {
                    val statusCode = response.code
                    if (statusCode in 300..399) {
                        if (redirects >= maxRedirects) error("Too many redirects")
                        val location = response.header("Location")
                            ?: error("Redirect without Location")
                        val redirectedUri = currentUri.resolve(location)
                        NetworkDestinationValidator.validateHttpsPublic(redirectedUri, dns)
                        val redirectedOrigin = normalizeRedirectOrigin(redirectedUri)
                        if (redirectedOrigin != currentOrigin) {
                            currentOrigin = redirectedOrigin
                            completedReport = MirrorProbeReport(
                                requestedOrigin = requestedOrigin,
                                resolvedOrigin = redirectedOrigin,
                                result = MirrorHealthResult(
                                    status = MirrorHealthStatus.REDIRECTED,
                                    checkedAtEpochMs = nowEpochMs(),
                                    latencyMs = elapsedMs(startedAtNs),
                                    contentFingerprintMatched = false,
                                    httpStatusCode = statusCode,
                                    redirectOrigin = redirectedOrigin,
                                ),
                            )
                            continue
                        }
                        currentUri = redirectedUri
                        redirects++
                        continue
                    }

                    val body = readBody(response)
                    val fingerprintMatched = KinogoHtmlFingerprint.matches(body)
                    val challenge = KinogoHtmlFingerprint.isChallenge(body)
                    val latencyMs = elapsedMs(startedAtNs)
                    val healthStatus = when {
                        challenge -> MirrorHealthStatus.CHALLENGE_REQUIRED
                        statusCode !in 200..299 -> MirrorHealthStatus.UNREACHABLE
                        !fingerprintMatched -> MirrorHealthStatus.INVALID_CONTENT
                        latencyMs > DEGRADED_LATENCY_MS -> MirrorHealthStatus.DEGRADED
                        else -> MirrorHealthStatus.HEALTHY
                    }
                    completedReport = MirrorProbeReport(
                        requestedOrigin = requestedOrigin,
                        resolvedOrigin = currentOrigin,
                        result = MirrorHealthResult(
                            status = healthStatus,
                            checkedAtEpochMs = nowEpochMs(),
                            latencyMs = latencyMs,
                            contentFingerprintMatched = fingerprintMatched,
                            httpStatusCode = statusCode,
                        ),
                    )
                } finally {
                    response.close()
                }
            }
            requireNotNull(completedReport)
        } catch (error: Exception) {
            MirrorProbeReport(
                requestedOrigin = requestedOrigin,
                resolvedOrigin = currentOrigin,
                result = MirrorHealthResult(
                    status = MirrorHealthStatus.UNREACHABLE,
                    checkedAtEpochMs = nowEpochMs(),
                    latencyMs = elapsedMs(startedAtNs),
                    contentFingerprintMatched = false,
                    diagnostic = probeErrorLabel(error),
                ),
            )
        }
    }

    private fun normalizeRedirectOrigin(uri: URI): String {
        require(uri.scheme.equals("https", ignoreCase = true)) { "Redirected away from HTTPS" }
        val authority = requireNotNull(uri.rawAuthority) { "Redirect host is missing" }
        return MirrorUrlNormalizer.normalize("https://$authority")
    }

    private fun readBody(response: Response): String {
        val body = response.body ?: return ""
        val declaredLength = body.contentLength()
        require(declaredLength < 0L || declaredLength <= MAX_BODY_BYTES) {
            "Mirror response is too large"
        }
        val stream = body.byteStream()
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1_024)
            var remaining = MAX_BODY_BYTES
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun elapsedMs(startedAtNs: Long): Long =
        ((System.nanoTime() - startedAtNs) / 1_000_000L).coerceAtLeast(0L)

    private fun probeErrorLabel(error: Exception): String = when (error) {
        is SocketTimeoutException -> "Истекло время ожидания ответа"
        is InterruptedIOException -> "Истекло время ожидания ответа"
        is UnknownHostException -> "DNS не нашёл адрес зеркала"
        is SSLException -> "Не удалось установить защищённое TLS-соединение"
        is SecurityException -> "Адрес не прошёл проверку безопасности"
        is IllegalArgumentException -> error.message?.take(160) ?: "Адрес не прошёл проверку"
        else -> "Не удалось подключиться: ${error.javaClass.simpleName}"
    }

    private companion object {
        const val MAX_BODY_BYTES = 128 * 1_024
        const val DEGRADED_LATENCY_MS = 2_500L
        const val USER_AGENT = "KinogoATV/0.5 (Android TV; mirror health check)"
    }
}

/** Shared redirect-by-redirect destination policy for probes and catalog HTML requests. */
object NetworkDestinationValidator {
    fun validateHttpsPublic(uri: URI, dns: Dns = Dns.SYSTEM) {
        require(uri.scheme.equals("https", ignoreCase = true)) { "Redirected away from HTTPS" }
        require(uri.rawUserInfo == null) { "Redirect contains user info" }
        val host = requireNotNull(uri.host) { "Redirect host is missing" }
        val addresses = dns.lookup(host)
        require(addresses.isNotEmpty() && addresses.all(NetworkAddressPolicy::isPublic)) {
            "Mirror resolves to a non-public address"
        }
    }
}

/** Refreshes seeds, manual candidates and domains learned from verified HTTPS redirects. */
class MirrorRefreshCoordinator internal constructor(
    private val registry: MirrorRegistry,
    private val probe: suspend (String) -> MirrorProbeReport,
    private val maxProbesPerRefresh: Int = DEFAULT_MAX_PROBES_PER_REFRESH,
    private val maxConcurrentProbes: Int = DEFAULT_MAX_CONCURRENT_PROBES,
) {
    constructor(
        registry: MirrorRegistry,
        checker: MirrorHealthChecker,
    ) : this(registry = registry, probe = checker::probe)

    init {
        require(maxProbesPerRefresh > 0)
        require(maxConcurrentProbes > 0)
    }

    suspend fun refresh(): MirrorRefreshResult = coroutineScope {
        val initialCandidates =
            registry.all()
                .asSequence()
                .filter { it.trustState != MirrorTrustState.REJECTED }
                .take(maxProbesPerRefresh)
                .toList()
        val queue = ArrayDeque<String>()
        val scheduled = linkedSetOf<String>()
        initialCandidates.forEach { entry ->
            if (scheduled.add(entry.origin)) queue.addLast(entry.origin)
        }
        val probed = linkedSetOf<String>()
        val reports = mutableListOf<MirrorProbeReport>()

        while (queue.isNotEmpty() && probed.size < maxProbesPerRefresh) {
            val batch = buildList {
                while (
                    queue.isNotEmpty() &&
                    size < maxConcurrentProbes &&
                    probed.size + size < maxProbesPerRefresh
                ) {
                    val origin = queue.removeFirst()
                    if (origin !in probed) add(origin)
                }
            }
            if (batch.isEmpty()) break
            probed += batch

            val rawReports = batch.map { origin -> async { probe(origin) } }.awaitAll()
            rawReports.forEach { rawReport ->
                val report = if (rawReport.resolvedOrigin == rawReport.requestedOrigin) {
                    rawReport
                } else {
                    // A redirector never inherits the final site's identity. The target is queued
                    // for its own direct probe during this same bounded refresh.
                    rawReport.copy(
                        result = rawReport.result.copy(
                            status = MirrorHealthStatus.REDIRECTED,
                            contentFingerprintMatched = false,
                            redirectOrigin = rawReport.resolvedOrigin,
                        ),
                    )
                }
                registry.recordHealth(report.requestedOrigin, report.result)
                reports += report

                if (report.resolvedOrigin != report.requestedOrigin) {
                    registry.updateDiscoveryCandidates(listOf(report.resolvedOrigin))
                    if (scheduled.add(report.resolvedOrigin)) {
                        // A freshly discovered target is more useful than another stale candidate,
                        // so verify it in the next batch while keeping the overall probe cap.
                        queue.addFirst(report.resolvedOrigin)
                    }
                }
            }
        }

        MirrorRefreshResult(
            entries = registry.all(),
            active = registry.selectBest(),
            reports = reports,
        )
    }

    companion object {
        const val DEFAULT_MAX_PROBES_PER_REFRESH = 24
        const val DEFAULT_MAX_CONCURRENT_PROBES = 4
    }
}

object KinogoHtmlFingerprint {
    fun matches(html: String): Boolean {
        val value = html.lowercase()
        val hasBrand = "kinogo" in value || "киного" in value
        val hasStructure = "dle-content" in value || "shortstory" in value
        return hasBrand && hasStructure
    }

    fun isChallenge(html: String): Boolean {
        val value = html.lowercase()
        return "just a moment" in value ||
            "cf-chl-" in value ||
            "cloudflare ray id" in value
    }
}

/** Rejects local, private, documentation and other non-routable destinations after DNS lookup. */
object NetworkAddressPolicy {
    fun isPublic(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        val bytes = address.address.map { it.toInt() and 0xFF }
        return when (address) {
            is Inet4Address -> isPublicIpv4(bytes)
            is Inet6Address -> isPublicIpv6(bytes)
            else -> false
        }
    }

    private fun isPublicIpv4(bytes: List<Int>): Boolean {
        val first = bytes[0]
        val second = bytes[1]
        if (first == 0 || first == 10 || first == 127 || first >= 224) return false
        if (first == 100 && second in 64..127) return false
        if (first == 169 && second == 254) return false
        if (first == 172 && second in 16..31) return false
        if (first == 192 && (second == 0 || second == 168)) return false
        if (first == 198 && second in 18..19) return false
        if (first == 198 && second == 51 && bytes[2] == 100) return false
        if (first == 203 && second == 0 && bytes[2] == 113) return false
        return true
    }

    private fun isPublicIpv6(bytes: List<Int>): Boolean {
        if (bytes[0] and 0xFE == 0xFC) return false // fc00::/7 unique-local
        if (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0D && bytes[3] == 0xB8) {
            return false // 2001:db8::/32 documentation
        }
        return true
    }
}
