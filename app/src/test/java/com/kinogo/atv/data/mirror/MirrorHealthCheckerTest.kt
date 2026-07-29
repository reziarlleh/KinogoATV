package com.kinogo.atv.data.mirror

import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorHealthCheckerTest {
    @Test
    fun fingerprintNeedsBrandAndExpectedStructure() {
        assertTrue(KinogoHtmlFingerprint.matches("<title>KinoGo</title><div id='dle-content'>"))
        assertTrue(KinogoHtmlFingerprint.matches("КиноГо <article class='shortStory'>"))
        assertFalse(KinogoHtmlFingerprint.matches("<title>KinoGo copy</title>"))
        assertFalse(KinogoHtmlFingerprint.matches("<div id='dle-content'>unrelated</div>"))
    }

    @Test
    fun cloudflareInterstitialIsDetected() {
        assertTrue(KinogoHtmlFingerprint.isChallenge("<title>Just a moment...</title>"))
        assertFalse(KinogoHtmlFingerprint.isChallenge("<title>KinoGo</title>"))
    }

    @Test
    fun localAndDocumentationAddressesAreBlocked() {
        assertFalse(NetworkAddressPolicy.isPublic(InetAddress.getByName("127.0.0.1")))
        assertFalse(NetworkAddressPolicy.isPublic(InetAddress.getByName("192.168.1.10")))
        assertFalse(NetworkAddressPolicy.isPublic(InetAddress.getByName("100.64.1.1")))
        assertFalse(NetworkAddressPolicy.isPublic(InetAddress.getByName("203.0.113.7")))
        assertFalse(NetworkAddressPolicy.isPublic(InetAddress.getByName("fc00::1")))
        assertFalse(NetworkAddressPolicy.isPublic(InetAddress.getByName("2001:db8::1")))
        assertTrue(NetworkAddressPolicy.isPublic(InetAddress.getByName("8.8.8.8")))
        assertTrue(NetworkAddressPolicy.isPublic(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test
    fun redirectTargetIsDirectlyProbedAndActivatedInTheSameRefresh() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val redirector = registry.addManual("redirector-mirror.tv")
        val target = "https://verified-target.tv"
        val probedOrigins = mutableListOf<String>()
        val coordinator =
            MirrorRefreshCoordinator(registry = registry, probe = { origin ->
                probedOrigins += origin
                if (origin == redirector.origin) {
                    healthyReport(requestedOrigin = origin, resolvedOrigin = target)
                } else {
                    healthyReport(requestedOrigin = origin, resolvedOrigin = origin)
                }
            })

        val result = coordinator.refresh()

        assertEquals(target, result.active?.origin)
        assertEquals(listOf(redirector.origin, target), probedOrigins)
        assertEquals(2, result.reports.size)
        assertEquals(MirrorHealthStatus.REDIRECTED, result.reports[0].result.status)
        assertFalse(result.reports[0].result.contentFingerprintMatched)
        assertEquals(target, result.reports[0].result.redirectOrigin)
        assertEquals(MirrorHealthStatus.HEALTHY, result.reports[1].result.status)
        assertEquals(MirrorTrustState.QUARANTINED, registry.get(redirector.origin)?.trustState)
        assertEquals(MirrorTrustState.VERIFIED, registry.get(target)?.trustState)
        assertTrue(registry.isEligible(target))
        assertFalse(registry.isEligible(redirector.origin))
    }

    @Test
    fun redirectFingerprintCannotPromoteTargetWhenDirectProbeFails() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val redirector = registry.addManual("redirector-mirror.tv")
        val target = "https://lookalike-target.tv"
        val coordinator = MirrorRefreshCoordinator(registry = registry, probe = { origin ->
            if (origin == redirector.origin) {
                healthyReport(origin, target)
            } else {
                MirrorProbeReport(
                    requestedOrigin = origin,
                    resolvedOrigin = origin,
                    result = MirrorHealthResult(
                        status = MirrorHealthStatus.INVALID_CONTENT,
                        checkedAtEpochMs = NOW,
                        latencyMs = 30,
                        contentFingerprintMatched = false,
                        httpStatusCode = 200,
                    ),
                )
            }
        })

        val result = coordinator.refresh()

        assertNull(result.active)
        assertEquals(2, result.reports.size)
        assertEquals(MirrorTrustState.QUARANTINED, registry.get(target)?.trustState)
        assertFalse(registry.isEligible(target))
    }

    @Test
    fun duplicateRedirectTargetsAreDirectlyProbedOnlyOnce() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val first = registry.addManual("redirector-one.tv")
        val second = registry.addManual("redirector-two.tv")
        val target = "https://shared-target.tv"
        val calls = mutableListOf<String>()
        val coordinator = MirrorRefreshCoordinator(registry = registry, probe = { origin ->
            calls += origin
            if (origin == first.origin || origin == second.origin) {
                healthyReport(origin, target)
            } else {
                healthyReport(origin, origin)
            }
        })

        val result = coordinator.refresh()

        assertEquals(target, result.active?.origin)
        assertEquals(1, calls.count { it == target })
        assertEquals(3, calls.size)
    }

    @Test
    fun redirectTargetTakesPriorityOverQueuedStaleCandidatesWithinProbeBudget() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val redirector = registry.addManual("redirector-priority.tv")
        val staleOne = registry.addManual("stale-one.tv")
        val staleTwo = registry.addManual("stale-two.tv")
        val target = "https://fresh-target.tv"
        val calls = mutableListOf<String>()
        val coordinator = MirrorRefreshCoordinator(
            registry = registry,
            probe = { origin ->
                calls += origin
                when (origin) {
                    redirector.origin -> healthyReport(origin, target)
                    target -> healthyReport(origin, origin)
                    else -> MirrorProbeReport(
                        requestedOrigin = origin,
                        resolvedOrigin = origin,
                        result = MirrorHealthResult(
                            status = MirrorHealthStatus.UNREACHABLE,
                            checkedAtEpochMs = NOW,
                        ),
                    )
                }
            },
            maxProbesPerRefresh = 3,
            maxConcurrentProbes = 2,
        )

        val result = coordinator.refresh()

        assertEquals(target, result.active?.origin)
        assertEquals(listOf(redirector.origin, staleOne.origin, target), calls)
        assertFalse(calls.contains(staleTwo.origin))
    }

    @Test
    fun refreshLimitsTheNumberOfCandidatesProbed() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        repeat(8) { index -> registry.addManual("mirror-$index.tv") }
        val probedOrigins = mutableListOf<String>()
        val coordinator =
            MirrorRefreshCoordinator(
                registry = registry,
                probe = { origin ->
                    probedOrigins += origin
                    healthyReport(requestedOrigin = origin, resolvedOrigin = origin)
                },
                maxProbesPerRefresh = 3,
                maxConcurrentProbes = 2,
            )

        coordinator.refresh()

        assertEquals(3, probedOrigins.size)
        assertEquals(
            listOf("https://mirror-0.tv", "https://mirror-1.tv", "https://mirror-2.tv"),
            probedOrigins,
        )
        assertEquals(3, registry.all().count { it.lastHealth != null })
    }

    private fun healthyReport(requestedOrigin: String, resolvedOrigin: String): MirrorProbeReport =
        MirrorProbeReport(
            requestedOrigin = requestedOrigin,
            resolvedOrigin = resolvedOrigin,
            result =
                MirrorHealthResult(
                    status = MirrorHealthStatus.HEALTHY,
                    checkedAtEpochMs = NOW,
                    latencyMs = 20,
                    contentFingerprintMatched = true,
                    httpStatusCode = 200,
                ),
        )

    private companion object {
        const val NOW = 20_000L
    }
}
