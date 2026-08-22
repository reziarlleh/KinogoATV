package com.kinogo.atv.data.history

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentType
import com.kinogo.atv.domain.PlaybackSelection
import com.kinogo.atv.domain.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressCodecTest {
    @Test
    fun `codec round trips exact movie and episode checkpoints`() {
        val movie = progress(content = "movie-1", updatedAt = 10, duration = null)
        val episode =
            progress(
                content = "сериал\t1",
                season = "season/2",
                episode = "episode\n5",
                voice = "озвучка 2",
                quality = "1080p+",
                position = 1_234_567,
                duration = 2_345_678,
                updatedAt = 20,
                snapshot = CatalogItem(
                    id = "сериал\t1",
                    relativePath = "/serialy/serial-1.html",
                    title = "Название сериала",
                    posterUrl = "https://cdn.example.org/poster 1.webp",
                    year = 2025,
                    type = ContentType.SERIES,
                    qualityBadge = "WEB-DL 1080p",
                ),
            )

        val decoded = PlaybackProgressCodec.decode(PlaybackProgressCodec.encode(listOf(movie, episode)))

        assertEquals(listOf(episode, movie), decoded)
        assertNull(decoded.last().durationMs)
    }

    @Test
    fun `version one checkpoint remains readable without a snapshot`() {
        val legacyPayload =
            "1\t35182\tseason-1\tepisode-2\tvoice\tauto\t120000\t2400000\t42\t0"

        val decoded = PlaybackProgressCodec.decode(legacyPayload).single()

        assertEquals("35182", decoded.selection.contentId)
        assertEquals("season-1", decoded.selection.seasonId)
        assertEquals(120_000L, decoded.positionMs)
        assertNull(decoded.contentSnapshot)
    }

    @Test
    fun `malformed line is skipped without losing valid history`() {
        val valid = progress(content = "movie-1", updatedAt = 10)
        val payload = PlaybackProgressCodec.encode(listOf(valid)) + "\nnot-a-valid-record"

        assertEquals(listOf(valid), PlaybackProgressCodec.decode(payload))
    }

    @Test
    fun `upsert replaces same playback unit including selected variant`() {
        val snapshot = CatalogItem(
            id = "series",
            relativePath = "/serialy/series.html",
            title = "Series",
        )
        val old = progress(
            content = "series",
            season = "s1",
            episode = "e2",
            updatedAt = 10,
            snapshot = snapshot,
        )
        val replacement =
            progress(
                content = "series",
                season = "s1",
                episode = "e2",
                voice = "new-voice",
                quality = "4k",
                position = 90_000,
                updatedAt = 20,
            )

        val result = PlaybackProgressCollection.upsert(listOf(old), replacement)

        assertEquals(1, result.size)
        assertEquals("new-voice", result.single().selection.voiceId)
        assertEquals("4k", result.single().selection.qualityId)
        assertEquals(90_000L, result.single().positionMs)
        assertEquals(snapshot, result.single().contentSnapshot)
    }

    @Test
    fun `late older checkpoint cannot roll the same episode back`() {
        val newer = progress(
            content = "series",
            season = "s2",
            episode = "e5",
            voice = "new-voice",
            quality = "1080p",
            position = 420_000,
            updatedAt = 200,
        )
        val olderFinishingLater = progress(
            content = "series",
            season = "s2",
            episode = "e5",
            voice = "old-voice",
            quality = "720p",
            position = 180_000,
            updatedAt = 100,
            snapshot = CatalogItem(
                id = "series",
                relativePath = "/serialy/series.html",
                title = "Series",
            ),
        )

        val result = PlaybackProgressCollection.upsert(listOf(newer), olderFinishingLater)

        assertEquals(420_000L, result.single().positionMs)
        assertEquals(200L, result.single().updatedAtEpochMs)
        assertEquals("new-voice", result.single().selection.voiceId)
        assertEquals(olderFinishingLater.contentSnapshot, result.single().contentSnapshot)
    }

    @Test
    fun `snapshot enrichment preserves the newest checkpoint and enriches every episode`() {
        val snapshot = CatalogItem(
            id = "series",
            relativePath = "/serialy/series.html",
            title = "Series",
        )
        val newestEpisode = progress(
            content = "series",
            season = "s1",
            episode = "e1",
            position = 95_000,
            updatedAt = 30,
        )
        val otherEpisode = progress(
            content = "series",
            season = "s1",
            episode = "e2",
            position = 40_000,
            updatedAt = 20,
        )
        val unrelated = progress(content = "movie", updatedAt = 10)

        val result = PlaybackProgressCollection.attachContentSnapshot(
            entries = listOf(newestEpisode, otherEpisode, unrelated),
            item = snapshot,
        )

        val enrichedNewest = result.first { it.progressKey() == newestEpisode.progressKey() }
        assertEquals(95_000L, enrichedNewest.positionMs)
        assertEquals(30L, enrichedNewest.updatedAtEpochMs)
        assertEquals(snapshot, enrichedNewest.contentSnapshot)
        assertEquals(snapshot, result.first { it.progressKey() == otherEpisode.progressKey() }.contentSnapshot)
        assertNull(result.first { it.progressKey() == unrelated.progressKey() }.contentSnapshot)
    }

    @Test
    fun `different episodes remain separate and history is newest first`() {
        val first = progress("series", "s1", "e1", updatedAt = 10)
        val second = progress("series", "s1", "e2", updatedAt = 20)

        val result = PlaybackProgressCollection.normalize(listOf(first, second))

        assertEquals(listOf(second, first), result)
        assertFalse(first.progressKey() == second.progressKey())
    }

    @Test
    fun `delete removes only matching content unit`() {
        val movie = progress(content = "movie", updatedAt = 30)
        val episode = progress("series", "s1", "e1", updatedAt = 20)

        val result = PlaybackProgressCollection.delete(listOf(movie, episode), movie.progressKey())

        assertEquals(listOf(episode), result)
        assertTrue(result.none { it.selection.contentId == "movie" })
    }

    private fun progress(
        content: String,
        season: String? = null,
        episode: String? = null,
        voice: String = "voice",
        quality: String = "auto",
        position: Long = 10_000,
        duration: Long? = 100_000,
        updatedAt: Long,
        snapshot: CatalogItem? = null,
        ended: Boolean = false,
    ): WatchProgress =
        WatchProgress(
            selection =
                PlaybackSelection(
                    contentId = content,
                    seasonId = season,
                    episodeId = episode,
                    voiceId = voice,
                    qualityId = quality,
                ),
            positionMs = position,
            durationMs = duration,
            updatedAtEpochMs = updatedAt,
            playbackEnded = ended,
            contentSnapshot = snapshot,
        )
}
