package com.kinogo.atv.data.playback.collaps

import java.net.URI
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsNativePlaybackAdapterTest {
    @Test
    fun validatesEveryDistinctMediaDestinationBeforeReturningReady() = runTest {
        val validated = mutableListOf<URI>()
        val adapter = CollapsNativePlaybackAdapter { validated += it }

        val result = adapter.resolve(
            embedUrl = "https://api.ortified.ws/embed/movie/42",
            html = fixture("collaps_movie_public_config.html"),
        )

        assertTrue(result is CollapsNativePlaybackResult.Ready)
        assertEquals(6, validated.distinct().size)
        assertEquals("api.ortified.ws", validated.first().host)
        assertTrue(validated.all { it.scheme == "https" })
        val ready = result as CollapsNativePlaybackResult.Ready
        assertFalse(ready.toString().contains("session=redacted"))
    }

    @Test
    fun rejectsInvalidEmbedOriginBeforeParsing() = runTest {
        val result = CollapsNativePlaybackAdapter { error("must not validate streams") }.resolve(
            embedUrl = "https://api.ortified.ws.evil.test/embed/movie/42",
            html = fixture("collaps_movie_public_config.html"),
        )

        assertEquals(
            CollapsNativePlaybackResult.Rejected(
                CollapsNativeRejection.INVALID_EMBED_URL,
            ),
            result,
        )
    }

    @Test
    fun rejectsRemotePlaylistInsteadOfFetchingOrExecutingIt() = runTest {
        val html = """
            <script>
              makePlayer({
                title: 'Remote',
                playlist: 'https://media.example.invalid/playlist.json'
              });
            </script>
        """.trimIndent()

        val result = CollapsNativePlaybackAdapter { }.resolve(
            embedUrl = "https://api.ortified.ws/embed/serial/42",
            html = html,
        )

        assertEquals(
            CollapsNativePlaybackResult.Rejected(
                CollapsNativeRejection.REMOTE_PLAYLIST_UNSUPPORTED,
            ),
            result,
        )
    }

    @Test
    fun rejectsHttpPrivateAndDnsRejectedDestinations() = runTest {
        val unsafeUrls = listOf(
            "http://media.example.invalid/video.m3u8",
            "https://127.0.0.1/video.m3u8",
            "https://user@media.example.invalid/video.m3u8",
            "https://media.example.invalid:444/video.m3u8",
            "https://media.example.invalid/video.m3u8#fragment",
        )
        unsafeUrls.forEach { unsafeUrl ->
            val result = CollapsNativePlaybackAdapter { }.resolve(
                embedUrl = "https://api.ortified.ws/embed/movie/42",
                html = movieHtml(unsafeUrl),
            )
            assertEquals(
                "Expected rejection for a syntactically unsafe destination",
                CollapsNativePlaybackResult.Rejected(
                    CollapsNativeRejection.UNSAFE_DESTINATION,
                ),
                result,
            )
        }

        val dnsRejected = CollapsNativePlaybackAdapter {
            throw SecurityException("private DNS answer")
        }.resolve(
            embedUrl = "https://api.ortified.ws/embed/movie/42",
            html = movieHtml("https://media.example.invalid/video.m3u8"),
        )
        assertEquals(
            CollapsNativePlaybackResult.Rejected(
                CollapsNativeRejection.UNSAFE_DESTINATION,
            ),
            dnsRejected,
        )
    }

    @Test
    fun rejectsExecutableSourceValue() = runTest {
        val result = CollapsNativePlaybackAdapter { }.resolve(
            embedUrl = "https://api.ortified.ws/embed/movie/42",
            html = """
                <script>
                  makePlayer({
                    title: 'Unsafe expression',
                    source: getSourceFromWindow()
                  });
                </script>
            """.trimIndent(),
        )

        assertEquals(
            CollapsNativePlaybackResult.Rejected(
                CollapsNativeRejection.MALFORMED_CONFIG,
            ),
            result,
        )
    }

    private fun movieHtml(url: String): String = """
        <script>
          makePlayer({
            title: 'Movie',
            id: 42,
            source: { hls: "$url" }
          });
        </script>
    """.trimIndent()

    private fun fixture(name: String): String {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/playback/$name"),
        )
        return stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }
}
