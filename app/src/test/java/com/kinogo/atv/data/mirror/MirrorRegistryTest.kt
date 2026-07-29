package com.kinogo.atv.data.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRegistryTest {
    @Test
    fun `normalizer accepts a bare host and canonicalizes HTTPS origin`() {
        assertEquals("https://kinogo.parts", MirrorUrlNormalizer.normalize(" KINOGO.PARTS/ "))
        assertEquals("https://kinogo.parts", MirrorUrlNormalizer.normalize("https://kinogo.parts:443"))
    }

    @Test
    fun `normalizer rejects unsafe or non-origin URLs`() {
        listOf(
            "http://kinogo.parts",
            "https://kinogo.parts/catalog",
            "https://user@kinogo.parts",
            "https://kinogo.parts?q=1",
            "https://127.0.0.1",
            "https://localhost",
            "https://mirror.local",
            "https://kinogo.parts:8443",
        ).forEach { input ->
            assertThrows(InvalidMirrorUrlException::class.java) {
                MirrorUrlNormalizer.normalize(input)
            }
        }
    }

    @Test
    fun `manual mirror remains quarantined until matching health check`() {
        val registry = registry()
        val manual = registry.addManual("manual-mirror.tv")

        assertEquals(MirrorTrustState.QUARANTINED, manual.trustState)
        assertNull(registry.selectBest())

        val verified = registry.recordHealth(manual.origin, healthy(checkedAt = NOW, latencyMs = 80))
        assertEquals(MirrorTrustState.VERIFIED, verified.trustState)
        assertEquals(manual.origin, registry.selectBest()?.origin)
    }

    @Test
    fun `discovery update never trusts candidates without verification`() {
        val registry = registry()
        val update =
            registry.updateDiscoveryCandidates(
                listOf("https://discovered-mirror.tv", "http://unsafe-mirror.tv"),
            )

        assertEquals(1, update.accepted.size)
        assertEquals(1, update.rejected.size)
        assertEquals(MirrorTrustState.QUARANTINED, update.accepted.single().trustState)
        assertNull(registry.selectBest())
    }

    @Test
    fun `automatic choice prefers higher health score`() {
        val registry = registry()
        registry.recordHealth(
            "https://kinogo.parts",
            MirrorHealthResult(
                status = MirrorHealthStatus.DEGRADED,
                checkedAtEpochMs = NOW,
                latencyMs = 2_000,
                contentFingerprintMatched = true,
            ),
        )
        registry.recordHealth(
            "https://kinogo.online",
            healthy(checkedAt = NOW, latencyMs = 100),
        )

        assertEquals("https://kinogo.online", registry.selectBest()?.origin)
    }

    @Test
    fun `identity mismatch cannot promote a reachable candidate`() {
        val registry = registry()
        val candidate = registry.addManual("lookalike-mirror.tv")

        val result =
            registry.recordHealth(
                candidate.origin,
                healthy(checkedAt = NOW, latencyMs = 20).copy(contentFingerprintMatched = false),
            )

        assertEquals(MirrorTrustState.QUARANTINED, result.trustState)
        assertFalse(result.lastHealth!!.isUsable)
        assertEquals(0, result.lastHealth.score)
    }

    @Test
    fun `stale health result is excluded from automatic selection`() {
        var now = NOW
        val registry =
            MirrorRegistry(
                healthTtlMs = 1_000,
                nowEpochMs = { now },
            )
        registry.recordHealth("kinogo.parts", healthy(checkedAt = now, latencyMs = 10))
        assertTrue(registry.selectBest() != null)
        assertTrue(registry.isEligible("kinogo.parts"))

        now += 1_001
        assertNull(registry.selectBest())
        assertFalse(registry.isEligible("kinogo.parts"))
    }

    @Test
    fun `redirect-only result cannot promote a candidate`() {
        val registry = registry()
        val candidate = registry.addManual("redirector-mirror.tv")

        val result = registry.recordHealth(
            candidate.origin,
            healthy(checkedAt = NOW, latencyMs = 20).copy(
                status = MirrorHealthStatus.REDIRECTED,
                contentFingerprintMatched = false,
            ),
        )

        assertEquals(MirrorTrustState.QUARANTINED, result.trustState)
        assertFalse(registry.isEligible(candidate.origin))
    }

    @Test
    fun `redirect-only result quarantines a previously verified candidate`() {
        val registry = registry()
        val candidate = registry.addManual("redirector-mirror.tv")
        registry.recordHealth(candidate.origin, healthy(checkedAt = NOW, latencyMs = 20))
        assertTrue(registry.isEligible(candidate.origin))

        val redirected =
            registry.recordHealth(
                candidate.origin,
                healthy(checkedAt = NOW, latencyMs = 30).copy(
                    status = MirrorHealthStatus.REDIRECTED,
                    contentFingerprintMatched = false,
                ),
            )

        assertEquals(MirrorTrustState.QUARANTINED, redirected.trustState)
        assertFalse(registry.isEligible(candidate.origin))
    }

    @Test
    fun `future-dated health result is not eligible`() {
        val registry = registry()
        registry.recordHealth("kinogo.parts", healthy(checkedAt = NOW + 1, latencyMs = 10))

        assertFalse(registry.isEligible("kinogo.parts"))
        assertNull(registry.selectBest())
    }

    private fun registry(): MirrorRegistry = MirrorRegistry(nowEpochMs = { NOW })

    private fun healthy(checkedAt: Long, latencyMs: Long): MirrorHealthResult =
        MirrorHealthResult(
            status = MirrorHealthStatus.HEALTHY,
            checkedAtEpochMs = checkedAt,
            latencyMs = latencyMs,
            contentFingerprintMatched = true,
            httpStatusCode = 200,
        )

    private companion object {
        const val NOW = 10_000L
    }
}
