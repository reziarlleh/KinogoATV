package com.kinogo.atv.player.ui

import com.kinogo.atv.domain.PlaybackEpisodeCoordinate
import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackPlaylistNavigationTest {
    private val coordinates = listOf(
        PlaybackEpisodeCoordinate(seasonNumber = 1, episodeNumber = 8),
        PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 2),
        PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 5),
    )

    @Test
    fun `prepared playlist resolves sparse cross season target without rebuild`() {
        assertEquals(
            1,
            preparedEpisodePlaylistIndex(
                coordinates = coordinates,
                preparedCoordinates = coordinates,
                target = PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 2),
            ),
        )
    }

    @Test
    fun `prepared playlist refuses stale count or absent coordinate`() {
        assertNull(
            preparedEpisodePlaylistIndex(
                coordinates = coordinates,
                preparedCoordinates = coordinates.dropLast(1),
                target = PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 2),
            ),
        )
        assertNull(
            preparedEpisodePlaylistIndex(
                coordinates = coordinates,
                preparedCoordinates = coordinates,
                target = PlaybackEpisodeCoordinate(seasonNumber = 2, episodeNumber = 1),
            ),
        )
        assertNull(
            preparedEpisodePlaylistIndex(
                coordinates = coordinates,
                preparedCoordinates = coordinates.reversed(),
                target = PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 2),
            ),
        )
    }

    @Test
    fun `quality change keeps current variant and recalculates future fixed episode`() {
        val e1Only720 = variant(id = "e1-720", episode = 1, quality = "720p")
        val e2At720 = variant(id = "e2-720", episode = 2, quality = "720p")
        val e2At1080 = variant(id = "e2-1080", episode = 2, quality = "1080p")
        val plan = PlaybackMediaPlan(listOf(e1Only720, e2At720, e2At1080))
        val coordinates = plan.episodeCoordinatesFor(SOURCE_ID, VOICEOVER)

        val before = playlistVariantsForQuality(
            mediaPlan = plan,
            coordinates = coordinates,
            sourceId = SOURCE_ID,
            voiceover = VOICEOVER,
            desiredQuality = "1080p",
            currentVariantOverride = e1Only720,
        )
        val after = playlistVariantsForQuality(
            mediaPlan = plan,
            coordinates = coordinates,
            sourceId = SOURCE_ID,
            voiceover = VOICEOVER,
            desiredQuality = "720p",
            currentVariantOverride = e1Only720,
        )

        assertEquals(listOf("e1-720", "e2-1080"), before.map { it.id })
        assertEquals(listOf("e1-720", "e2-720"), after.map { it.id })
        assertSame(e1Only720, after.first())
        assertEquals("https://media.example/e2-720.m3u8", after.last().mediaUrl)
    }

    private fun variant(
        id: String,
        episode: Int,
        quality: String,
    ): PlaybackMediaVariant = PlaybackMediaVariant(
        id = id,
        sourceId = SOURCE_ID,
        sourceLabel = "Источник",
        seasonNumber = 1,
        episodeNumber = episode,
        voiceover = VOICEOVER,
        quality = quality,
        mediaUrl = "https://media.example/$id.m3u8",
    )

    private companion object {
        const val SOURCE_ID = "source"
        const val VOICEOVER = "Перевод"
    }
}
