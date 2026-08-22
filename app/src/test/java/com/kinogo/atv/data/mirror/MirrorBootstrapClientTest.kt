package com.kinogo.atv.data.mirror

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorBootstrapClientTest {
    @Test
    fun `fresh manifest adds only quarantined candidates`() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val client = client(manifest())

        val result = client.refresh(registry)

        assertTrue(result is MirrorBootstrapResult.Applied)
        result as MirrorBootstrapResult.Applied
        assertEquals(
            listOf("https://w.kinogo.solar", "https://kinogo.parts"),
            result.manifest.origins,
        )
        assertTrue(result.candidates.all { it.source == MirrorSource.DISCOVERY })
        assertTrue(result.candidates.all { it.trustState == MirrorTrustState.QUARANTINED })
        assertFalse(registry.isEligible("https://w.kinogo.solar"))
        assertEquals(null, registry.selectBest())
    }

    @Test
    fun `candidate becomes eligible only after matching health fingerprint`() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val client = client(manifest())
        client.refresh(registry)

        registry.recordHealth(
            "https://w.kinogo.solar",
            MirrorHealthResult(
                status = MirrorHealthStatus.HEALTHY,
                checkedAtEpochMs = NOW,
                latencyMs = 120,
                contentFingerprintMatched = true,
                httpStatusCode = 200,
            ),
        )

        assertTrue(registry.isEligible("https://w.kinogo.solar"))
    }

    @Test
    fun `expired manifest is ignored without mutating registry`() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val expired = manifest(
            generatedAt = "2026-04-01T00:00:00Z",
            expiresAt = "2026-04-30T00:00:00Z",
        )

        val result = client(expired).refresh(registry)

        assertTrue(result is MirrorBootstrapResult.Unavailable)
        assertTrue(registry.all().isEmpty())
    }

    @Test
    fun `unsafe candidate rejects whole manifest`() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val unsafe = manifest(origins = listOf("https://w.kinogo.solar", "http://192.168.1.2"))

        val result = client(unsafe).refresh(registry)

        assertTrue(result is MirrorBootstrapResult.Unavailable)
        assertTrue(registry.all().isEmpty())
    }

    @Test
    fun `unknown schema field is rejected`() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val changed = manifest().replace("\n}", ",\n  \"trustAll\": true\n}")

        val result = client(changed).refresh(registry)

        assertTrue(result is MirrorBootstrapResult.Unavailable)
        assertTrue(registry.all().isEmpty())
    }

    @Test
    fun `non-json content type is rejected`() = runTest {
        val registry = MirrorRegistry(seedOrigins = emptyList(), nowEpochMs = { NOW })
        val client = MirrorBootstrapClient(
            transport = MirrorBootstrapTransport {
                MirrorBootstrapHttpResponse(200, "text/html", manifest())
            },
            nowEpochMs = { NOW },
        )

        val result = client.refresh(registry)

        assertTrue(result is MirrorBootstrapResult.Unavailable)
        assertTrue(registry.all().isEmpty())
    }

    private fun client(body: String): MirrorBootstrapClient = MirrorBootstrapClient(
        transport = MirrorBootstrapTransport {
            MirrorBootstrapHttpResponse(200, "application/json; charset=utf-8", body)
        },
        nowEpochMs = { NOW },
    )

    private fun manifest(
        generatedAt: String = "2026-08-15T00:00:00Z",
        expiresAt: String = "2026-11-13T00:00:00Z",
        origins: List<String> = listOf("https://w.kinogo.solar", "https://kinogo.parts"),
    ): String = """
        {
          "schemaVersion": 1,
          "generatedAt": "$generatedAt",
          "expiresAt": "$expiresAt",
          "origins": [${origins.joinToString(",") { "\"$it\"" }}]
        }
    """.trimIndent()

    private companion object {
        val NOW: Long = java.time.Instant.parse("2026-08-15T12:00:00Z").toEpochMilli()
    }
}
