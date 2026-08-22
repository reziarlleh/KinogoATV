package com.kinogo.atv.data.playback

import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemarEmbedResolverTest {
    @Test
    fun `resolver returns a validated embed without exposing a media source`() = runTest {
        val validated = mutableListOf<URI>()
        val resolver = CinemarEmbedResolver { validated += it }
        val result = resolver.resolve(
            request("https://cinemar.cc/embed/101067/+opaque-token=="),
        )

        assertTrue(result is PlaybackSourceResolution.Embedded)
        val embed = (result as PlaybackSourceResolution.Embedded).embed
        assertEquals("cinemar", embed.providerId)
        assertEquals("https://cinemar.cc/embed/101067/+opaque-token==", embed.embedUrl)
        assertEquals(
            "https://w.kinogo.solar/serialy/34905-vse-nenavidjat-krisa.html",
            embed.refererUrl,
        )
        assertEquals(listOf(URI(embed.embedUrl)), validated)
    }

    @Test
    fun `registry returns Cinemar embed before later direct candidates`() = runTest {
        val registry = PlaybackSourceResolverRegistry(
            listOf(CinemarEmbedResolver { }, DirectMediaResolver { }),
        )
        val result = registry.resolveFirst(
            contentId = "34905",
            documentOrigin = "https://w.kinogo.solar",
            documentUrl = "https://w.kinogo.solar/serialy/34905-vse-nenavidjat-krisa.html",
            candidates = listOf(
                candidate("https://cinemar.cc/embed/101067/+opaque"),
                candidate("https://w.kinogo.solar/video/master.m3u8"),
            ),
        )

        assertTrue(result is PlaybackSourceResolution.Embedded)
    }

    @Test
    fun `embed policy requires exact HTTPS provider origin and embed path`() {
        val rejected = listOf(
            "http://cinemar.cc/embed/101067/token",
            "https://www.cinemar.cc/embed/101067/token",
            "https://cinemar.cc.evil.test/embed/101067/token",
            "https://user:pass@cinemar.cc/embed/101067/token",
            "https://cinemar.cc:444/embed/101067/token",
            "https://cinemar.cc/player/101067/token",
            "https://cinemar.cc/embed/",
            "https://cinemar.cc/embed/101067/token#fragment",
            " https://cinemar.cc/embed/101067/token",
        )

        rejected.forEach { url ->
            assertNull("Expected rejection for $url", CinemarEmbedUrlPolicy.validatedEmbedUri(url))
        }
        assertTrue(
            CinemarEmbedUrlPolicy.validatedEmbedUri(
                "https://CINEMAR.cc:443/embed/101067/+opaque==",
            ) != null,
        )
    }

    @Test
    fun `main frame policy blocks navigation outside exact provider host`() {
        assertTrue(CinemarEmbedUrlPolicy.isAllowedMainFrameUrl("https://cinemar.cc/help?q=1"))
        assertFalse(CinemarEmbedUrlPolicy.isAllowedMainFrameUrl("https://ads.cinemar.cc/landing"))
        assertFalse(CinemarEmbedUrlPolicy.isAllowedMainFrameUrl("https://example.test/landing"))
        assertFalse(CinemarEmbedUrlPolicy.isAllowedMainFrameUrl("intent://external"))
    }

    @Test
    fun `player document policy admits exact origin runtime route but not root or api`() {
        val runtime = "https://cinemar.cc/runtime/player/session"

        assertNotNull(CinemarEmbedUrlPolicy.validatedPlayerDocumentUri(runtime))
        assertNull(CinemarEmbedUrlPolicy.validatedEmbedUri(runtime))
        listOf(
            "https://cinemar.cc/",
            "https://cinemar.cc/api/playlist/load",
            "https://cinemar.cc/API/playlist/load",
            "https://www.cinemar.cc/runtime/player/session",
            "https://cinemar.cc/runtime/player/session?token=opaque",
            "https://cinemar.cc/runtime/player/session#fragment",
            "https://user:pass@cinemar.cc/runtime/player/session",
            "https://cinemar.cc:444/runtime/player/session",
            "http://cinemar.cc/runtime/player/session",
        ).forEach { url ->
            assertNull(CinemarEmbedUrlPolicy.validatedPlayerDocumentUri(url))
        }
    }

    @Test
    fun `referer must remain on selected Kinogo document origin`() {
        val origin = "https://w.kinogo.solar"
        assertEquals(
            "https://w.kinogo.solar/serialy/34905-test.html?tab=1",
            CinemarEmbedUrlPolicy.validatedRefererUrl(
                "https://w.kinogo.solar/serialy/34905-test.html?tab=1",
                origin,
            ),
        )
        assertNull(
            CinemarEmbedUrlPolicy.validatedRefererUrl(
                "https://kinogo.solar/serialy/34905-test.html",
                origin,
            ),
        )
        assertNull(
            CinemarEmbedUrlPolicy.validatedRefererUrl(
                "https://w.kinogo.solar/serialy/34905-test.html#secret",
                origin,
            ),
        )
    }

    private fun request(url: String): PlaybackSourceRequest = PlaybackSourceRequest(
        contentId = "34905",
        documentOrigin = "https://w.kinogo.solar",
        documentUrl = "https://w.kinogo.solar/serialy/34905-vse-nenavidjat-krisa.html",
        candidate = candidate(url),
    )

    private fun candidate(url: String): PlayerEmbedCandidate = PlayerEmbedCandidate(
        url = url,
        label = "Основной плеер",
        providerId = "0",
    )
}
