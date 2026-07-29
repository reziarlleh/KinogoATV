package com.kinogo.atv.data.playback.collaps

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsPlayerConfigParserTest {
    @Test
    fun parsesMovieStreamsManifestAudioOrderAndSubtitlesWithoutExecutingJavascript() {
        val catalog = CollapsPlayerConfigParser.parse(fixture("collaps_movie_public_config.html"))

        assertEquals("Тестовый фильм", catalog.title)
        assertTrue(catalog.seasons.isEmpty())
        val movie = requireNotNull(catalog.movie)
        assertEquals("42", movie.id)
        assertEquals(
            listOf(
                CollapsStreamType.HLS,
                CollapsStreamType.DASH,
                CollapsStreamType.FILE,
                CollapsStreamType.FILE,
            ),
            movie.streams.map { it.type },
        )
        assertEquals(listOf(null, null, 720, 1080), movie.streams.map { it.qualityHeight })
        assertEquals(
            listOf("Дубляж", "Оригинал"),
            movie.audioTracks.map { it.name },
        )
        assertEquals(listOf(1, 0), movie.audioTracks.map { it.manifestTrackIndex })
        assertEquals(listOf("Русские"), movie.subtitles.map { it.name })
        assertFalse(movie.streams.first().toString().contains("session="))
        assertFalse(movie.subtitles.first().toString().contains("session="))
        assertTrue(movie.streams.first().toString().contains("<redacted>"))
    }

    @Test
    fun parsesNestedSeasonEpisodeTreeAndCurrentSelection() {
        val catalog = CollapsPlayerConfigParser.parse(fixture("collaps_series_public_config.html"))

        assertNull(catalog.movie)
        assertTrue(catalog.flatEpisodes.isEmpty())
        assertEquals(listOf("1", "2"), catalog.seasons.map { it.number })
        assertEquals(listOf("1", "2"), catalog.seasons.first().episodes.map { it.episode })
        assertEquals(listOf(480, 720), catalog.seasons.first().episodes[1].streams.map {
            it.qualityHeight
        })
        assertTrue(catalog.seasons[1].blocked)
        assertTrue(catalog.seasons[1].episodes.single().blocked)
        assertTrue(catalog.seasons[1].episodes.single().streams.isEmpty())
        assertEquals("1", catalog.currentSelection?.season)
        assertEquals("2", catalog.currentSelection?.episode)
    }

    @Test
    fun parsesDocumentedFlatPlaylistShape() {
        val catalog = CollapsPlayerConfigParser.parse(
            """
            <script>
              makePlayer({
                title: 'Подборка',
                playlist: {
                  flat: [
                    {
                      id: 'part-1',
                      title: 'Часть 1',
                      source: { hls: 'https://media.example.invalid/part1.m3u8' }
                    }
                  ],
                  current: { id: 'part-1' }
                }
              });
            </script>
            """.trimIndent(),
        )

        assertEquals("part-1", catalog.flatEpisodes.single().id)
        assertEquals("part-1", catalog.currentSelection?.id)
    }

    private fun fixture(name: String): String {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/playback/$name"),
        )
        return stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }
}
