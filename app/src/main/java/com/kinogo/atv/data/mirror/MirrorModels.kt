package com.kinogo.atv.data.mirror

enum class MirrorSource {
    SEED,
    MANUAL,
    DISCOVERY,
}

enum class MirrorTrustState {
    BUILT_IN,
    QUARANTINED,
    VERIFIED,
    REJECTED,
}

enum class MirrorHealthStatus {
    HEALTHY,
    DEGRADED,
    REDIRECTED,
    CHALLENGE_REQUIRED,
    UNREACHABLE,
    INVALID_CONTENT,
}

/** Result produced by a bounded external health/fingerprint check. */
data class MirrorHealthResult(
    val status: MirrorHealthStatus,
    val checkedAtEpochMs: Long,
    val latencyMs: Long? = null,
    val contentFingerprintMatched: Boolean = false,
    val httpStatusCode: Int? = null,
    val redirectOrigin: String? = null,
    val diagnostic: String? = null,
) {
    init {
        require(checkedAtEpochMs >= 0)
        require(latencyMs == null || latencyMs >= 0)
        require(httpStatusCode == null || httpStatusCode in 100..599)
        require(redirectOrigin == null || redirectOrigin.startsWith("https://"))
    }

    /** Only an identified, actually usable mirror may be selected or promoted. */
    val isUsable: Boolean
        get() =
            contentFingerprintMatched &&
                (status == MirrorHealthStatus.HEALTHY || status == MirrorHealthStatus.DEGRADED)

    /** Stable 0..100 ranking; identity mismatch always wins over transport success. */
    val score: Int
        get() {
            if (!contentFingerprintMatched) return 0
            val base =
                when (status) {
                    MirrorHealthStatus.HEALTHY -> 100
                    MirrorHealthStatus.DEGRADED -> 70
                    MirrorHealthStatus.REDIRECTED -> 0
                    MirrorHealthStatus.CHALLENGE_REQUIRED -> 10
                    MirrorHealthStatus.UNREACHABLE,
                    MirrorHealthStatus.INVALID_CONTENT,
                    -> 0
                }
            val latencyPenalty = (((latencyMs ?: 0) / 500).coerceAtMost(20)).toInt()
            return (base - latencyPenalty).coerceAtLeast(0)
        }
}

data class MirrorEntry(
    val origin: String,
    val source: MirrorSource,
    val trustState: MirrorTrustState,
    val priority: Int,
    val addedAtEpochMs: Long,
    val lastHealth: MirrorHealthResult? = null,
) {
    val isTrusted: Boolean
        get() =
            trustState == MirrorTrustState.BUILT_IN ||
                trustState == MirrorTrustState.VERIFIED
}

data class DiscoveryUpdateResult(
    val accepted: List<MirrorEntry>,
    val rejected: Map<String, String>,
)
