package com.kinogo.atv.data.playback.cinemar

import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemarGrantClientTest {
    @Test
    fun `posts one JSON string token to the exact same-origin grant endpoint`() = runTest {
        val calls = mutableListOf<GrantCall>()
        val validated = mutableListOf<URI>()
        val client = CinemarGrantClient(
            transport = CinemarGrantTransport { endpoint, embedUri, jsonBody ->
                calls += GrantCall(endpoint, embedUri, jsonBody.decodeToString())
                CinemarGrantHttpResponse(
                    statusCode = 200,
                    contentType = "application/json; charset=utf-8",
                    body = """
                        {
                          "file":"[1080p]https://media.example.test/movie/1080.m3u8,[720p]https://media.example.test/movie/720.m3u8",
                          "duration":3600,
                          "subtitle":"[ru]https://media.example.test/movie/ru.vtt"
                        }
                    """.trimIndent().encodeToByteArray(),
                )
            },
            destinationValidator = { validated += it },
        )

        val result = client.load(
            embedUrl = "https://cinemar.cc/embed/9010/fixture-offer",
            stream = deferredStream(),
        )

        assertTrue(result is CinemarGrantResolution.Ready)
        val hydrated = (result as CinemarGrantResolution.Ready).stream
        assertEquals(listOf("1080p", "720p"), hydrated.mediaVariants.map { it.label })
        assertEquals(3_600_000L, hydrated.durationMs)
        assertEquals(CinemarSubtitleKind.WEBVTT, hydrated.subtitles.single().kind)
        assertNull(hydrated.grantToken)
        assertEquals(1, calls.size)
        assertEquals("https://cinemar.cc/api/playlist/load", calls.single().endpoint.toString())
        assertEquals(
            "https://cinemar.cc/embed/9010/fixture-offer",
            calls.single().embedUri.toString(),
        )
        assertEquals("\"fixture-opaque-grant-token-do-not-log\"", calls.single().jsonBody)
        assertEquals(
            setOf("cinemar.cc", "media.example.test"),
            validated.mapNotNull(URI::getHost).toSet(),
        )
        assertFalse(result.toString().contains("fixture-opaque-grant-token"))
        assertFalse(result.toString().contains("media.example.test"))
    }

    @Test
    fun `accepts current exact-origin runtime document as grant referer`() = runTest {
        var call: GrantCall? = null
        val client = CinemarGrantClient(
            transport = CinemarGrantTransport { endpoint, embedUri, jsonBody ->
                call = GrantCall(endpoint, embedUri, jsonBody.decodeToString())
                CinemarGrantHttpResponse(
                    statusCode = 200,
                    contentType = "application/json",
                    body = """{"file":"https://media.example.test/movie/master.m3u8"}"""
                        .encodeToByteArray(),
                )
            },
            destinationValidator = { },
        )

        val result = client.load(
            embedUrl = "https://cinemar.cc/runtime/player/session",
            stream = deferredStream(),
        )

        assertTrue(result is CinemarGrantResolution.Ready)
        assertEquals("https://cinemar.cc/api/playlist/load", call?.endpoint.toString())
        assertEquals("https://cinemar.cc/runtime/player/session", call?.embedUri.toString())
    }

    @Test
    fun `rejects another origin before transport receives the token`() = runTest {
        var calls = 0
        val client = CinemarGrantClient(
            transport = CinemarGrantTransport { _, _, _ ->
                calls++
                error("must not run")
            },
            destinationValidator = { },
        )

        val result = client.load(
            embedUrl = "https://provider.example/embed/9010",
            stream = deferredStream(),
        )

        assertEquals(
            CinemarGrantFailureCode.INVALID_EMBED_ADDRESS,
            (result as CinemarGrantResolution.Rejected).code,
        )
        assertEquals(0, calls)
    }

    @Test
    fun `does not follow a grant redirect`() = runTest {
        val client = CinemarGrantClient(
            transport = CinemarGrantTransport { _, _, _ ->
                CinemarGrantHttpResponse(
                    statusCode = 302,
                    contentType = "text/html",
                    body = ByteArray(0),
                )
            },
            destinationValidator = { },
        )

        val result = client.load(
            embedUrl = "https://cinemar.cc/embed/9010/fixture-offer",
            stream = deferredStream(),
        )

        assertEquals(
            CinemarGrantFailureCode.HTTP_ERROR,
            (result as CinemarGrantResolution.Rejected).code,
        )
    }

    @Test
    fun `rejects an oversized response even from an injected transport`() = runTest {
        val client = CinemarGrantClient(
            transport = CinemarGrantTransport { _, _, _ ->
                CinemarGrantHttpResponse(
                    statusCode = 200,
                    contentType = "application/json",
                    body = ByteArray(129),
                )
            },
            destinationValidator = { },
            maxResponseBytes = 128,
        )

        val result = client.load(
            embedUrl = "https://cinemar.cc/embed/9010/fixture-offer",
            stream = deferredStream(),
        )

        assertEquals(
            CinemarGrantFailureCode.RESPONSE_TOO_LARGE,
            (result as CinemarGrantResolution.Rejected).code,
        )
    }

    @Test
    fun `rejects a media destination without exposing it`() = runTest {
        val client = CinemarGrantClient(
            transport = CinemarGrantTransport { _, _, _ ->
                CinemarGrantHttpResponse(
                    statusCode = 200,
                    contentType = "application/json",
                    body = """{"file":"https://blocked.example/video/master.m3u8"}"""
                        .encodeToByteArray(),
                )
            },
            destinationValidator = { uri ->
                if (uri.host == "blocked.example") throw SecurityException("blocked fixture")
            },
        )

        val result = client.load(
            embedUrl = "https://cinemar.cc/embed/9010/fixture-offer",
            stream = deferredStream(),
        )

        assertEquals(
            CinemarGrantFailureCode.UNSAFE_NETWORK_DESTINATION,
            (result as CinemarGrantResolution.Rejected).code,
        )
        assertFalse(result.toString().contains("blocked.example"))
        assertFalse(result.userMessage.contains("blocked.example"))
    }

    private fun deferredStream() = CinemarStream(
        id = "voice-deferred",
        title = "Fixture Voice",
        contextTitle = null,
        providerNodeId = "voice-deferred",
        sourceId = null,
        voiceId = "77",
        durationMs = null,
        folderPath = emptyList(),
        mediaVariants = emptyList(),
        subtitles = emptyList(),
        grantToken = CinemarGrantToken("fixture-opaque-grant-token-do-not-log"),
    )

    private data class GrantCall(
        val endpoint: URI,
        val embedUri: URI,
        val jsonBody: String,
    )
}
