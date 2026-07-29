package com.kinogo.atv.data.playback

import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlaybackSourceResolverTest {
    @Test
    fun `direct resolver accepts explicit public HTTPS media formats`() = runTest {
        val validated = mutableListOf<URI>()
        val resolver = DirectMediaResolver { validated += it }

        val cases = listOf(
            "https://cdn.test/video/master.M3U8?token=short-lived" to PlaybackMediaKind.HLS,
            "https://cdn.test/video/manifest.mpd" to PlaybackMediaKind.DASH,
            "https://cdn.test/video/movie.mp4" to PlaybackMediaKind.MP4,
        )
        cases.forEach { (url, expectedKind) ->
            val result = resolver.resolve(
                request(
                    url = url,
                    providerId = "documented-cdn",
                ),
            )

            assertTrue(result is PlaybackSourceResolution.Resolved)
            val source = (result as PlaybackSourceResolution.Resolved).source
            assertEquals(expectedKind, source.mediaKind)
            assertEquals("documented-cdn", source.providerId)
            assertEquals(url, source.mediaUrl)
        }
        assertEquals(cases.map { URI(it.first) }, validated)
    }

    @Test
    fun `direct resolver rejects unsafe URLs before returning a source`() = runTest {
        val validated = mutableListOf<URI>()
        val resolver = DirectMediaResolver { uri ->
            validated += uri
            if (uri.host == "private.test") error("private address")
        }
        val invalid = listOf(
            "http://cdn.test/video.mp4",
            "https://user:secret@cdn.test/video.mp4",
            "https://cdn.test/video.mp4#fragment",
            " https://cdn.test/video.mp4",
            "https://private.test/video.m3u8",
        )

        invalid.forEach { url ->
            val result = resolver.resolve(request(url))
            assertTrue("Expected rejection for $url", result is PlaybackSourceResolution.Rejected)
        }
        assertEquals(listOf(URI("https://private.test/video.m3u8")), validated)
    }

    @Test
    fun `registry does not treat provider HTML as a direct stream`() = runTest {
        val registry = PlaybackSourceResolverRegistry(listOf(DirectMediaResolver { }))

        val result = registry.resolve(
            request("https://cinema.example/embed/opaque-player"),
        )

        assertTrue(result is PlaybackSourceResolution.Unsupported)
    }

    @Test
    fun `generic direct resolver requires the verified document origin`() = runTest {
        val resolver = DirectMediaResolver { }

        val result = resolver.resolve(
            request(
                url = "https://cdn.test/video.mp4",
                documentOrigin = "https://catalog.test",
            ),
        )

        assertTrue(result is PlaybackSourceResolution.Unsupported)
    }

    @Test
    fun `registry continues after unsupported candidate and returns a later direct stream`() =
        runTest {
            val registry = PlaybackSourceResolverRegistry(listOf(DirectMediaResolver { }))
            val result = registry.resolveFirst(
                contentId = "42",
                documentOrigin = "https://cdn.test",
                candidates = listOf(
                    candidate("https://cinema.example/embed/42"),
                    candidate("https://cdn.test/42/master.m3u8"),
                ),
            )

            assertTrue(result is PlaybackSourceResolution.Resolved)
            assertEquals(
                PlaybackMediaKind.HLS,
                (result as PlaybackSourceResolution.Resolved).source.mediaKind,
            )
        }

    @Test
    fun `resolver cancellation is not converted to a rejected source`() = runTest {
        val expected = CancellationException("cancel resolver")
        val resolver = DirectMediaResolver { throw expected }

        try {
            resolver.resolve(request("https://cdn.test/video.mp4"))
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertEquals(expected.message, actual.message)
        }
    }

    @Test
    fun `registry requires unique resolver ids`() {
        val duplicate = object : PlaybackSourceResolver {
            override val id: String = "direct-media"
            override fun supports(candidate: PlayerEmbedCandidate): Boolean = false
            override suspend fun resolve(
                request: PlaybackSourceRequest,
            ): PlaybackSourceResolution = PlaybackSourceResolution.Unsupported("test")
        }

        try {
            PlaybackSourceResolverRegistry(listOf(DirectMediaResolver { }, duplicate))
            fail("Expected duplicate ids to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `transient native and web addresses are redacted from diagnostic strings`() {
        val secret = "fixture-token-must-not-appear"
        val source = ResolvedPlaybackSource(
            id = "native",
            mediaUrl = "https://cdn.test/video.m3u8?token=$secret",
            mediaKind = PlaybackMediaKind.HLS,
            providerId = "fixture",
            label = "Нативный",
        )
        val embed = ResolvedPlaybackEmbed(
            id = "web",
            embedUrl = "https://player.test/embed?token=$secret",
            refererUrl = "https://catalog.test/item?session=$secret",
            providerId = "fixture",
            label = "Web",
        )

        assertTrue("<redacted>" in source.toString())
        assertTrue("<redacted>" in embed.toString())
        assertTrue(secret !in source.toString())
        assertTrue(secret !in embed.toString())

        val request = request("https://cdn.test/video.m3u8?token=$secret")
        assertTrue(secret !in request.candidate.toString())
        assertTrue(secret !in request.toString())
    }

    private fun request(
        url: String,
        providerId: String? = null,
        documentOrigin: String = defaultOrigin(url),
    ): PlaybackSourceRequest = PlaybackSourceRequest(
        contentId = "42",
        documentOrigin = documentOrigin,
        candidate = candidate(url, providerId),
    )

    private fun candidate(
        url: String,
        providerId: String? = null,
    ): PlayerEmbedCandidate = PlayerEmbedCandidate(
        url = url,
        label = "Основной плеер",
        providerId = providerId,
    )

    private fun defaultOrigin(url: String): String {
        val host = runCatching { URI(url.trim()).host }.getOrNull() ?: "cdn.test"
        return "https://$host"
    }
}
