package com.kinogo.atv.data.playback

import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import com.kinogo.atv.data.playback.cinemar.CinemarNativeSourceAdapter
import com.kinogo.atv.data.playback.collaps.CollapsNativePlaybackAdapter
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KinogoPlaybackPreparationServiceTest {
    @Test
    fun `combines fresh Cinemar and Collaps documents and keeps explicit web fallbacks`() = runTest {
        val pages = mapOf(
            "https://cinemar-fixture.example/embed/9001" to fixture("cinemar/movie_v2.html"),
            "https://api.ortified.ws/embed/movie/42" to fixture("collaps_movie_public_config.html"),
        )
        var calls = 0
        val service = service(
            ProviderDocumentTransport { uri, _ ->
                calls++
                ProviderDocumentHttpResponse(
                    statusCode = 200,
                    contentType = "text/html; charset=utf-8",
                    location = null,
                    body = requireNotNull(pages[uri.toASCIIString()]).encodeToByteArray(),
                )
            },
        )

        val result = service.prepare(request(pages.keys.toList()))

        assertTrue(result is PlaybackPreparationResult.Ready)
        val session = (result as PlaybackPreparationResult.Ready).session
        assertEquals(listOf("cinemar", "collaps"), session.nativePlan?.sourceOptions?.map { it.id })
        assertEquals(listOf("cinemar", "collaps"), session.webFallbacks.map { it.providerId })
        assertEquals(2, calls)
        assertFalse(session.toString().contains("fixture.example"))
    }

    @Test
    fun `Cinemar native config stays bound to requested embed after safe runtime redirect`() =
        runTest {
            val requestedEmbed = "https://cinemar-fixture.example/embed/9010/offer"
            val resolvedRuntimeRoute = "https://cinemar-fixture.example/runtime/player/session"
            val calls = mutableListOf<String>()
            val service = service(
                ProviderDocumentTransport { uri, _ ->
                    calls += uri.toASCIIString()
                    when (uri.toASCIIString()) {
                        requestedEmbed -> ProviderDocumentHttpResponse(
                            statusCode = 302,
                            contentType = null,
                            location = resolvedRuntimeRoute,
                            body = ByteArray(0),
                        )
                        resolvedRuntimeRoute -> ProviderDocumentHttpResponse(
                            statusCode = 200,
                            contentType = "text/html; charset=utf-8",
                            location = null,
                            body = fixture("cinemar/movie_deferred_grant.html")
                                .encodeToByteArray(),
                        )
                        else -> error("Unexpected provider route")
                    }
                },
            )

            val result = service.prepare(request(listOf(requestedEmbed)))

            assertTrue(result is PlaybackPreparationResult.Ready)
            val session = (result as PlaybackPreparationResult.Ready).session
            assertNotNull(session.nativePlan)
            assertTrue(session.nativePlan!!.variants.single().mediaUrl.startsWith("kinogo-cinemar:"))
            assertEquals(resolvedRuntimeRoute, session.webFallbacks.single().embedUrl)
            assertEquals(listOf(requestedEmbed, resolvedRuntimeRoute), calls)
        }

    @Test
    fun `current exact-origin Cinemar runtime offer produces native deferred plan`() = runTest {
        val runtimeDocument = "https://cinemar.cc/runtime/player/session"
        val service = service(
            ProviderDocumentTransport { uri, _ ->
                assertEquals(runtimeDocument, uri.toASCIIString())
                ProviderDocumentHttpResponse(
                    statusCode = 200,
                    contentType = "text/html; charset=utf-8",
                    location = null,
                    body = fixture("cinemar/movie_deferred_grant.html").encodeToByteArray(),
                )
            },
        )

        val result = service.prepare(request(listOf(runtimeDocument)))

        assertTrue(result is PlaybackPreparationResult.Ready)
        val session = (result as PlaybackPreparationResult.Ready).session
        assertNotNull(session.nativePlan)
        assertTrue(session.nativePlan!!.variants.single().mediaUrl.startsWith("kinogo-cinemar:"))
        assertEquals(runtimeDocument, session.webFallbacks.single().embedUrl)
    }

    @Test
    fun `a native parser failure preserves the isolated web alternative`() = runTest {
        val service = service(
            ProviderDocumentTransport { _, _ ->
                ProviderDocumentHttpResponse(
                    statusCode = 200,
                    contentType = "text/html",
                    location = null,
                    body = "<html><body>unsupported provider</body></html>".encodeToByteArray(),
                )
            },
        )

        val result = service.prepare(request(listOf("https://unknown.example/embed/7")))

        assertTrue(result is PlaybackPreparationResult.Ready)
        val session = (result as PlaybackPreparationResult.Ready).session
        assertEquals(null, session.nativePlan)
        assertNotNull(session.webFallbacks.singleOrNull())
    }

    @Test
    fun `preparation performs a new fetch on every launch`() = runTest {
        var calls = 0
        val service = service(
            ProviderDocumentTransport { _, _ ->
                calls++
                ProviderDocumentHttpResponse(
                    statusCode = 200,
                    contentType = "text/html",
                    location = null,
                    body = fixture("cinemar/movie_v2.html").encodeToByteArray(),
                )
            },
        )
        val request = request(listOf("https://cinemar-fixture.example/embed/9001"))

        service.prepare(request)
        service.prepare(request)

        assertEquals(2, calls)
    }

    @Test
    fun `validated iframe remains a web fallback when native preflight returns an error`() =
        runTest {
            val service = service(
                ProviderDocumentTransport { _, _ ->
                    ProviderDocumentHttpResponse(
                        statusCode = 503,
                        contentType = "text/html",
                        location = null,
                        body = "temporarily unavailable".encodeToByteArray(),
                    )
                },
            )

            val result = service.prepare(
                request(listOf("https://cinemar-fixture.example/embed/9001")),
            )

            assertTrue(result is PlaybackPreparationResult.Ready)
            val session = (result as PlaybackPreparationResult.Ready).session
            assertEquals(null, session.nativePlan)
            assertEquals("cinemar", session.webFallbacks.single().providerId)
            assertTrue(session.notices.single().contains("503"))
        }

    @Test
    fun `direct media document is not misrepresented as a web player`() = runTest {
        var transportCalls = 0
        val service = service(
            ProviderDocumentTransport { _, _ ->
                transportCalls++
                ProviderDocumentHttpResponse(
                    statusCode = 503,
                    contentType = "text/plain",
                    location = null,
                    body = ByteArray(0),
                )
            },
        )

        val result = service.prepare(request(listOf("https://kinogo.example/video/master.m3u8")))

        assertTrue(result is PlaybackPreparationResult.Unavailable)
        assertEquals(0, transportCalls)
    }

    @Test
    fun `unsafe iframe is never retained as a web fallback`() = runTest {
        var transportCalls = 0
        val service = service(
            transport = ProviderDocumentTransport { _, _ ->
                transportCalls++
                error("Unsafe destination must be rejected before transport")
            },
            destinationValidator = { throw SecurityException("private destination") },
        )

        val result = service.prepare(
            request(listOf("https://private-provider.example/embed/42")),
        )

        assertTrue(result is PlaybackPreparationResult.Unavailable)
        assertEquals(0, transportCalls)
    }

    @Test
    fun `unsafe redirect removes the preliminary web fallback`() = runTest {
        val originalHost = "cinemar-fixture.example"
        val service = service(
            ProviderDocumentTransport { _, _ ->
                ProviderDocumentHttpResponse(
                    statusCode = 302,
                    contentType = null,
                    location = "https://127.0.0.1/embed/42",
                    body = ByteArray(0),
                )
            },
            destinationValidator = { uri ->
                require(uri.host == originalHost) { "private redirect" }
            },
        )

        val result = service.prepare(
            request(listOf("https://cinemar-fixture.example/embed/42")),
        )

        assertTrue(result is PlaybackPreparationResult.Unavailable)
    }

    @Test
    fun `different fallbacks from one provider are both preserved`() = runTest {
        val service = service(
            ProviderDocumentTransport { _, _ ->
                ProviderDocumentHttpResponse(
                    statusCode = 503,
                    contentType = "text/html",
                    location = null,
                    body = ByteArray(0),
                )
            },
        )

        val result = service.prepare(
            request(
                listOf(
                    "https://cinemar-fixture.example/embed/one",
                    "https://cinemar-fixture.example/embed/two",
                ),
            ),
        ) as PlaybackPreparationResult.Ready

        assertEquals(2, result.session.webFallbacks.size)
        assertEquals(2, result.session.webFallbacks.map { it.id }.distinct().size)
        assertTrue(result.session.webFallbacks.all { it.providerId == "cinemar" })
    }

    @Test
    fun `working page native source does not wait for official gateway`() = runTest {
        var gatewayCalls = 0
        val service = service(
            transport = ProviderDocumentTransport { _, _ ->
                ProviderDocumentHttpResponse(
                    statusCode = 200,
                    contentType = "text/html",
                    location = null,
                    body = fixture("cinemar/movie_v2.html").encodeToByteArray(),
                )
            },
            officialDiscovery = OptionalOfficialPlayerDiscovery { lookup ->
                gatewayCalls++
                OfficialPlayerDiscoveryResult.Rejected(
                    htmlPostId = lookup.htmlPostId,
                    reason = OfficialPlayerDiscoveryRejection.UNAVAILABLE,
                )
            },
        )

        val result = service.prepare(
            request(listOf("https://cinemar-fixture.example/embed/9001")),
        )

        assertTrue(result is PlaybackPreparationResult.Ready)
        assertNotNull((result as PlaybackPreparationResult.Ready).session.nativePlan)
        assertEquals(0, gatewayCalls)
    }

    @Test
    fun `provisional page fallback still attempts official gateway recovery`() = runTest {
        var gatewayCalls = 0
        val service = service(
            transport = ProviderDocumentTransport { _, _ ->
                ProviderDocumentHttpResponse(
                    statusCode = 503,
                    contentType = "text/html",
                    location = null,
                    body = ByteArray(0),
                )
            },
            officialDiscovery = OptionalOfficialPlayerDiscovery { lookup ->
                gatewayCalls++
                OfficialPlayerDiscoveryResult.Rejected(
                    htmlPostId = lookup.htmlPostId,
                    reason = OfficialPlayerDiscoveryRejection.UNAVAILABLE,
                )
            },
        )

        val result = service.prepare(
            request(listOf("https://cinemar-fixture.example/embed/9001")),
        )

        assertTrue(result is PlaybackPreparationResult.Ready)
        assertEquals(1, (result as PlaybackPreparationResult.Ready).session.webFallbacks.size)
        assertEquals(1, gatewayCalls)
    }

    @Test
    fun `confirmed page web document does not wait for official gateway`() = runTest {
        var gatewayCalls = 0
        val service = service(
            transport = ProviderDocumentTransport { _, _ ->
                ProviderDocumentHttpResponse(
                    statusCode = 200,
                    contentType = "text/html",
                    location = null,
                    body = "<html><body>unknown provider</body></html>".encodeToByteArray(),
                )
            },
            officialDiscovery = OptionalOfficialPlayerDiscovery { lookup ->
                gatewayCalls++
                OfficialPlayerDiscoveryResult.Rejected(
                    htmlPostId = lookup.htmlPostId,
                    reason = OfficialPlayerDiscoveryRejection.UNAVAILABLE,
                )
            },
        )

        val result = service.prepare(
            request(listOf("https://unknown-provider.example/embed/9001")),
        )

        assertTrue(result is PlaybackPreparationResult.Ready)
        assertEquals(1, (result as PlaybackPreparationResult.Ready).session.webFallbacks.size)
        assertEquals(0, gatewayCalls)
    }

    @Test
    fun `direct native plan can disable official recovery lookup`() = runTest {
        var gatewayCalls = 0
        val service = service(
            transport = ProviderDocumentTransport { _, _ ->
                error("No provider document should be fetched")
            },
            officialDiscovery = OptionalOfficialPlayerDiscovery { lookup ->
                gatewayCalls++
                OfficialPlayerDiscoveryResult.Rejected(
                    htmlPostId = lookup.htmlPostId,
                    reason = OfficialPlayerDiscoveryRejection.UNAVAILABLE,
                )
            },
        )

        val result = service.prepare(
            request(
                urls = emptyList(),
                useOfficialDiscoveryFallback = false,
            ),
        )

        assertTrue(result is PlaybackPreparationResult.Unavailable)
        assertEquals(0, gatewayCalls)
    }

    private fun service(
        transport: ProviderDocumentTransport,
        destinationValidator: (URI) -> Unit = { uri: URI -> require(uri.scheme == "https") },
        officialDiscovery: OptionalOfficialPlayerDiscovery? = null,
    ) =
        KinogoPlaybackPreparationService(
            documentClient = ProviderEmbedDocumentClient(
                transport = transport,
                destinationValidator = destinationValidator,
                maxRedirects = 2,
            ),
            cinemarAdapter = CinemarNativeSourceAdapter { },
            collapsAdapter = CollapsNativePlaybackAdapter { },
            officialDiscovery = officialDiscovery,
        )

    private fun request(
        urls: List<String>,
        useOfficialDiscoveryFallback: Boolean = true,
    ) = PlaybackPreparationRequest(
        contentId = "42",
        title = "Fixture",
        year = 2026,
        originalTitle = "Fixture Original",
        documentOrigin = "https://kinogo.example",
        documentUrl = "https://kinogo.example/films/42-fixture.html",
        useOfficialDiscoveryFallback = useOfficialDiscoveryFallback,
        freshPageCandidates = urls.mapIndexed { index, url ->
            PlayerEmbedCandidate(
                url = url,
                label = if (index == 0) "Cinemar" else "Collaps",
                providerId = when {
                    "cinemar" in url -> "cinemar"
                    "ortified" in url -> "collaps"
                    else -> null
                },
            )
        },
    )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/playback/$name")).readText()
}
