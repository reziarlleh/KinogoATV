package com.kinogo.atv.data.mirror

import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * In-memory trust registry. Discovery and manual input can add candidates, but only a successful
 * content-fingerprint health result promotes them into automatic selection.
 */
class MirrorRegistry(
    seedOrigins: List<String> = DEFAULT_SEED_ORIGINS,
    private val healthTtlMs: Long = DEFAULT_HEALTH_TTL_MS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val entries = linkedMapOf<String, MirrorEntry>()

    init {
        require(healthTtlMs > 0)
        seedOrigins.forEachIndexed { index, rawOrigin ->
            val origin = MirrorUrlNormalizer.normalize(rawOrigin)
            entries.putIfAbsent(
                origin,
                MirrorEntry(
                    origin = origin,
                    source = MirrorSource.SEED,
                    trustState = MirrorTrustState.BUILT_IN,
                    priority = index,
                    addedAtEpochMs = nowEpochMs(),
                ),
            )
        }
    }

    @Synchronized
    fun all(): List<MirrorEntry> = entries.values.sortedWith(entryOrder())

    @Synchronized
    fun get(rawOrigin: String): MirrorEntry? =
        entries[MirrorUrlNormalizer.normalize(rawOrigin)]

    /** A new manual address is never trusted immediately. */
    @Synchronized
    fun addManual(rawOrigin: String): MirrorEntry {
        val origin = MirrorUrlNormalizer.normalize(rawOrigin)
        val existing = entries[origin]
        if (existing != null) return existing

        return MirrorEntry(
            origin = origin,
            source = MirrorSource.MANUAL,
            trustState = MirrorTrustState.QUARANTINED,
            priority = MANUAL_PRIORITY + entries.size,
            addedAtEpochMs = nowEpochMs(),
        ).also { entries[origin] = it }
    }

    /**
     * Merges untrusted discovery candidates. Invalid URLs are reported per item; valid candidates
     * stay quarantined until [recordHealth] receives a matching service fingerprint.
     */
    @Synchronized
    fun updateDiscoveryCandidates(rawOrigins: Collection<String>): DiscoveryUpdateResult {
        val accepted = linkedMapOf<String, MirrorEntry>()
        val rejected = linkedMapOf<String, String>()

        rawOrigins.forEach { rawOrigin ->
            try {
                val origin = MirrorUrlNormalizer.normalize(rawOrigin)
                val entry =
                    entries[origin]
                        ?: MirrorEntry(
                            origin = origin,
                            source = MirrorSource.DISCOVERY,
                            trustState = MirrorTrustState.QUARANTINED,
                            priority = DISCOVERY_PRIORITY + entries.size,
                            addedAtEpochMs = nowEpochMs(),
                        ).also { entries[origin] = it }
                accepted[origin] = entry
            } catch (error: InvalidMirrorUrlException) {
                rejected[rawOrigin] = error.message ?: "Invalid mirror URL"
            }
        }

        return DiscoveryUpdateResult(accepted.values.toList(), rejected)
    }

    @Synchronized
    fun recordHealth(rawOrigin: String, result: MirrorHealthResult): MirrorEntry {
        val origin = MirrorUrlNormalizer.normalize(rawOrigin)
        val current = requireNotNull(entries[origin]) { "Unknown mirror: $origin" }

        val identityFailed =
            result.status == MirrorHealthStatus.INVALID_CONTENT ||
                result.status == MirrorHealthStatus.REDIRECTED ||
                ((result.status == MirrorHealthStatus.HEALTHY ||
                    result.status == MirrorHealthStatus.DEGRADED) &&
                    !result.contentFingerprintMatched)

        val newTrust =
            when {
                current.source == MirrorSource.SEED -> MirrorTrustState.BUILT_IN
                current.trustState == MirrorTrustState.REJECTED -> MirrorTrustState.REJECTED
                identityFailed -> MirrorTrustState.QUARANTINED
                result.isUsable -> MirrorTrustState.VERIFIED
                else -> current.trustState
            }

        return current.copy(trustState = newTrust, lastHealth = result).also {
            entries[origin] = it
        }
    }

    @Synchronized
    fun reject(rawOrigin: String): MirrorEntry {
        val origin = MirrorUrlNormalizer.normalize(rawOrigin)
        val current = requireNotNull(entries[origin]) { "Unknown mirror: $origin" }
        require(current.source != MirrorSource.SEED) { "Built-in seeds cannot be rejected" }
        return current.copy(trustState = MirrorTrustState.REJECTED).also { entries[origin] = it }
    }

    /** Returns the highest-scoring trusted mirror with a fresh usable health result. */
    @Synchronized
    fun selectBest(): MirrorEntry? {
        val now = nowEpochMs()
        return entries.values
            .asSequence()
            .filter { entry -> isEligibleAt(entry, now) }
            .sortedWith(
                compareByDescending<MirrorEntry> { it.lastHealth?.score ?: 0 }
                    .thenBy { it.priority }
                    .thenByDescending { it.lastHealth?.checkedAtEpochMs ?: 0 },
            )
            .firstOrNull()
    }

    /** Central eligibility check shared by automatic and user-initiated mirror selection. */
    @Synchronized
    fun isEligible(rawOrigin: String): Boolean {
        val origin = MirrorUrlNormalizer.normalize(rawOrigin)
        val entry = entries[origin] ?: return false
        return isEligibleAt(entry, nowEpochMs())
    }

    private fun isEligibleAt(entry: MirrorEntry, now: Long): Boolean {
        if (!entry.isTrusted) return false
        val health = entry.lastHealth ?: return false
        val age = now - health.checkedAtEpochMs
        return health.isUsable && age in 0..healthTtlMs
    }

    private fun entryOrder(): Comparator<MirrorEntry> =
        compareBy<MirrorEntry> { it.priority }.thenBy { it.origin }

    companion object {
        val DEFAULT_SEED_ORIGINS: List<String> =
            listOf(
                "https://kinogo.parts",
                "https://kinogo.online",
            )

        const val DEFAULT_HEALTH_TTL_MS: Long = 6 * 60 * 60 * 1_000L
        private const val MANUAL_PRIORITY = 1_000
        private const val DISCOVERY_PRIORITY = 2_000
    }
}

/** Strict origin-only normalizer used before any network request is allowed. */
object MirrorUrlNormalizer {
    private val schemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val ipv4Pattern = Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$")
    private val blockedSuffixes =
        setOf("localhost", "local", "internal", "lan", "home", "test", "invalid", "example", "arpa")

    fun normalize(rawUrl: String): String {
        val value = rawUrl.trim()
        if (value.isEmpty()) invalid("Mirror URL is empty")
        if (value.any(Char::isWhitespace)) invalid("Whitespace is not allowed")
        if ('\\' in value || '%' in value) invalid("Escaped or backslash URLs are not allowed")

        val candidate = if (schemePattern.containsMatchIn(value)) value else "https://$value"
        val uri =
            try {
                URI(candidate)
            } catch (error: Exception) {
                throw InvalidMirrorUrlException("Malformed mirror URL", error)
            }

        if (!uri.scheme.equals("https", ignoreCase = true)) invalid("Only HTTPS mirrors are allowed")
        if (uri.rawUserInfo != null) invalid("User info is not allowed")
        if (uri.rawQuery != null || uri.rawFragment != null) invalid("Query and fragment are not allowed")
        if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") invalid("Mirror must be an origin without a path")

        val authority = uri.rawAuthority ?: invalid("Mirror host is missing")
        if ('@' in authority || '[' in authority || ']' in authority) invalid("IP literals are not allowed")

        var host = uri.host
        var port = uri.port
        if (host == null) {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0) {
                val portText = authority.substring(colon + 1)
                if (portText.isEmpty() || !portText.all(Char::isDigit)) invalid("Invalid mirror port")
                port = portText.toIntOrNull() ?: invalid("Invalid mirror port")
                host = authority.substring(0, colon)
            } else {
                host = authority
            }
        }
        if (port != -1 && port != 443) invalid("Only the default HTTPS port is allowed")

        val asciiHost =
            try {
                IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            } catch (error: Exception) {
                throw InvalidMirrorUrlException("Invalid mirror host", error)
            }

        if (asciiHost.isEmpty() || asciiHost.length > 253) invalid("Invalid mirror host")
        if (':' in asciiHost || ipv4Pattern.matches(asciiHost)) invalid("IP addresses are not allowed")
        val labels = asciiHost.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) invalid("A public DNS host is required")
        if (labels.last() in blockedSuffixes) invalid("Reserved or local DNS names are not allowed")

        return "https://$asciiHost"
    }

    private fun invalid(message: String): Nothing = throw InvalidMirrorUrlException(message)
}

class InvalidMirrorUrlException : IllegalArgumentException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
