package com.kinogo.atv.data.playback.cinemar

import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemarNativeSourceAdapterTest {
    @Test
    fun `decodes current v2 envelope into translations qualities and subtitles`() = runTest {
        val validated = mutableListOf<URI>()
        val adapter = CinemarNativeSourceAdapter { validated += it }

        val result = adapter.resolve(
            embedUrl = "https://cinemar-fixture.example/embed/9001",
            html = fixture("movie_v2.html"),
        )

        assertTrue(result is CinemarNativeResolution.Ready)
        val catalog = (result as CinemarNativeResolution.Ready).catalog
        assertEquals(9001L, catalog.videoId)
        assertEquals(2, catalog.streams.size)

        val dub = catalog.streams[0]
        assertEquals("Дубляж (Studio)", dub.title)
        assertEquals("2963", dub.voiceId)
        assertEquals(6_115_000L, dub.durationMs)
        assertEquals(CinemarMediaKind.HLS, dub.mediaVariants.single().kind)
        assertEquals("Авто", dub.mediaVariants.single().label)
        assertEquals(
            "https://media.example.test/movie/master.m3u8",
            dub.mediaVariants.single().url.valueForPlayback(),
        )
        assertEquals(
            listOf(CinemarSubtitleKind.SUBRIP, CinemarSubtitleKind.WEBVTT),
            dub.subtitles.map { it.kind },
        )

        val original = catalog.streams[1]
        assertEquals("English (Original)", original.title)
        assertEquals(listOf("1080p", "720p"), original.mediaVariants.map { it.label })
        assertTrue(original.mediaVariants.all { it.kind == CinemarMediaKind.MP4 })

        assertEquals(6, validated.distinct().size)
        assertTrue(validated.first().toASCIIString().endsWith("/embed/9001"))
    }

    @Test
    fun `retains folder hierarchy for season and episode selection`() = runTest {
        val adapter = CinemarNativeSourceAdapter { }

        val result = adapter.resolve(
            embedUrl = "https://cinemar-fixture.example/embed/9002",
            html = fixture("series_v2.html"),
        )

        assertTrue(result is CinemarNativeResolution.Ready)
        val catalog = (result as CinemarNativeResolution.Ready).catalog
        assertEquals(2, catalog.streams.size)
        assertEquals(
            listOf("1 сезон", "1 серия"),
            catalog.streams[0].folderPath.map { it.title },
        )
        assertEquals(
            listOf("1 сезон", "2 серия"),
            catalog.streams[1].folderPath.map { it.title },
        )
        val season = catalog.roots.single() as CinemarFolder
        assertEquals("1 сезон", season.title)
        assertEquals(2, season.children.size)
    }

    @Test
    fun `retains current deferred leaf without inventing a media URL`() = runTest {
        val validated = mutableListOf<URI>()
        val adapter = CinemarNativeSourceAdapter { validated += it }

        val result = adapter.resolve(
            embedUrl = "https://cinemar-fixture.example/embed/9010",
            html = fixture("movie_deferred_grant.html"),
        )

        assertTrue(result is CinemarNativeResolution.Ready)
        val stream = (result as CinemarNativeResolution.Ready).catalog.streams.single()
        assertTrue(stream.mediaVariants.isEmpty())
        assertTrue(stream.grantToken != null)
        assertEquals(listOf("https://cinemar-fixture.example/embed/9010"), validated.map(URI::toString))
        assertFalse(result.toString().contains("fixture-opaque-grant-token"))
        assertEquals("CinemarGrantToken(<redacted>)", stream.grantToken.toString())
    }

    @Test
    fun `accepts current exact-origin Cinemar runtime player document`() = runTest {
        val validated = mutableListOf<URI>()
        val adapter = CinemarNativeSourceAdapter { validated += it }

        val result = adapter.resolve(
            embedUrl = "https://cinemar.cc/runtime/player/session",
            html = fixture("movie_deferred_grant.html"),
        )

        assertTrue(result is CinemarNativeResolution.Ready)
        val stream = (result as CinemarNativeResolution.Ready).catalog.streams.single()
        assertTrue(stream.grantToken != null)
        assertEquals(
            listOf("https://cinemar.cc/runtime/player/session"),
            validated.map(URI::toString),
        )
    }

    @Test
    fun `validates every returned endpoint and rejects a failed public boundary`() = runTest {
        val adapter = CinemarNativeSourceAdapter { uri ->
            if (uri.host == "media.example.test") {
                throw SecurityException("fixture destination is blocked")
            }
        }

        val result = adapter.resolve(
            embedUrl = "https://cinemar-fixture.example/embed/9001",
            html = fixture("movie_v2.html"),
        )

        assertEquals(
            CinemarNativeFailureCode.UNSAFE_NETWORK_DESTINATION,
            (result as CinemarNativeResolution.Rejected).code,
        )
        assertFalse(result.toString().contains("media.example.test"))
        assertFalse(result.userMessage.contains("media.example.test"))
    }

    @Test
    fun `rejects non-https media before network validation`() = runTest {
        var validationCalls = 0
        val adapter = CinemarNativeSourceAdapter { validationCalls++ }

        val result = adapter.resolve(
            embedUrl = "https://cinemar-fixture.example/embed/9003",
            html = fixture("unsafe_direct_playlist.html"),
        )

        assertEquals(
            CinemarNativeFailureCode.NO_PLAYABLE_STREAMS,
            (result as CinemarNativeResolution.Rejected).code,
        )
        assertEquals(0, validationCalls)
    }

    @Test
    fun `transient URL redacts itself from diagnostics`() = runTest {
        val result = CinemarNativeSourceAdapter { }.resolve(
            embedUrl = "https://cinemar-fixture.example/embed/9001",
            html = fixture("movie_v2.html"),
        ) as CinemarNativeResolution.Ready

        val endpoint = result.catalog.streams.first().mediaVariants.first().url

        assertEquals("CinemarTransientUrl(<redacted>)", endpoint.toString())
        assertFalse(result.catalog.toString().contains("media.example.test"))
        assertFalse(result.catalog.toString().contains(".m3u8"))
    }

    @Test
    fun `embed document must be a matching https embed`() = runTest {
        val adapter = CinemarNativeSourceAdapter { }
        val invalidAddresses = listOf(
            "http://cinemar-fixture.example/embed/9001",
            "https://user:pass@cinemar-fixture.example/embed/9001",
            "https://cinemar-fixture.example/player/9001",
            "https://cinemar-fixture.example/embed/9001#fragment",
            "https://cinemar-fixture.example/embed/9002",
        )

        invalidAddresses.forEach { address ->
            val result = adapter.resolve(address, fixture("movie_v2.html"))
            assertTrue("Expected rejection for address shape", result is CinemarNativeResolution.Rejected)
        }
    }

    private fun fixture(name: String): String =
        requireNotNull(
            javaClass.getResource("/fixtures/playback/cinemar/$name"),
        ).readText()
}
