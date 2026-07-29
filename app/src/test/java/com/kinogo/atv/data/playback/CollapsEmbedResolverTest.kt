package com.kinogo.atv.data.playback

import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsEmbedResolverTest {
    @Test
    fun resolvesOnlyValidatedCollapsEmbed() = runTest {
        val validated = mutableListOf<URI>()
        val resolver = CollapsEmbedResolver { validated += it }

        val result = resolver.resolve(
            PlaybackSourceRequest(
                contentId = "117297",
                documentOrigin = "https://w.kinogo.solar",
                documentUrl = "https://w.kinogo.solar/filmy/117297-nochnoj-biznes.html",
                candidate = PlayerEmbedCandidate(
                    url = "https://api.ortified.ws/embed/movie/91392",
                    label = "Плеер 1",
                    providerId = "collaps",
                ),
            ),
        )

        assertTrue(result is PlaybackSourceResolution.Embedded)
        val embedded = (result as PlaybackSourceResolution.Embedded).embed
        assertEquals("collaps", embedded.providerId)
        assertEquals("https://w.kinogo.solar/filmy/117297-nochnoj-biznes.html", embedded.refererUrl)
        assertEquals(listOf(URI("https://api.ortified.ws/embed/movie/91392")), validated)
        assertFalse(embedded.toString().contains("Authorization", ignoreCase = true))
    }

    @Test
    fun exactOriginAndEmbedPathAreRequired() {
        listOf(
            "http://api.ortified.ws/embed/movie/91392",
            "https://evil.api.ortified.ws/embed/movie/91392",
            "https://api.ortified.ws.evil.test/embed/movie/91392",
            "https://api.ortified.ws/landing",
            "https://user@api.ortified.ws/embed/movie/91392",
            "https://api.ortified.ws:444/embed/movie/91392",
            "https://api.ortified.ws/embed/movie/91392#fragment",
        ).forEach { url ->
            assertNull("Expected rejection for $url", CollapsEmbedUrlPolicy.validatedEmbedUri(url))
        }
        assertTrue(
            CollapsEmbedUrlPolicy.validatedEmbedUri(
                "https://api.ortified.ws/embed/movie/91392",
            ) != null,
        )
        assertTrue(
            CollapsEmbedUrlPolicy.isAllowedMainFrameUrl(
                "https://api.ortified.ws/embed/movie/91392?start=1",
            ),
        )
    }

    @Test
    fun trustedPolicyRegistryDoesNotInferUnknownProviders() {
        assertEquals(CinemarEmbedUrlPolicy, TrustedEmbedUrlPolicies.forProvider("CINEMAR"))
        assertEquals(CollapsEmbedUrlPolicy, TrustedEmbedUrlPolicies.forProvider("collaps"))
        assertNull(TrustedEmbedUrlPolicies.forProvider("mirror-2"))
    }
}
