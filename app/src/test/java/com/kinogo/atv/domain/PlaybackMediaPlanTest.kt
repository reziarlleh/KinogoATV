package com.kinogo.atv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlaybackMediaPlanTest {
    @Test
    fun `film plan exposes voices and qualities for a single playback unit`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant("dub-auto", null, "Дубляж", "Авто"),
                variant("dub-1080", null, "Дубляж", "1080p"),
                variant("original-auto", null, "Оригинал", "Авто"),
            ),
        )

        assertTrue(!plan.isEpisodic)
        assertTrue(plan.episodeNumbers.isEmpty())
        assertEquals(listOf("Дубляж", "Оригинал"), plan.voiceoversFor(null))
        assertEquals(listOf("Авто", "1080p"), plan.qualitiesFor(null, "Дубляж"))
        assertEquals("dub-1080", plan.find(null, "Дубляж", "1080p")?.id)
        assertNull(plan.find(null, "Оригинал", "1080p"))
    }

    @Test
    fun `preferred variant falls back inside the requested episode only`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant("e1", 1, "Дубляж", "Авто"),
                variant("e2-original", 2, "Оригинал", "Авто"),
                variant("e2-dub", 2, "Дубляж", "720p"),
            ),
        )

        assertEquals("e2-dub", plan.preferred(2, "Дубляж", "1080p").id)
        assertEquals("e2-original", plan.preferred(2, "Неизвестно", "Авто").id)
    }

    @Test
    fun `plan rejects mixed film and episode layouts`() {
        expectInvalid {
            PlaybackMediaPlan(
                listOf(
                    variant("film", null, "Дубляж", "Авто"),
                    variant("episode", 1, "Дубляж", "Авто"),
                ),
            )
        }
    }

    @Test
    fun `multi-source multi-season plan exposes only compatible descendants`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant(
                    id = "a-s1-e1",
                    episode = 1,
                    voiceover = "Дубляж",
                    quality = "1080p",
                    sourceId = "provider-a",
                    sourceLabel = "Источник A",
                    season = 1,
                ),
                variant(
                    id = "a-s2-e1",
                    episode = 1,
                    voiceover = "Оригинал",
                    quality = "720p",
                    sourceId = "provider-a",
                    sourceLabel = "Источник A",
                    season = 2,
                ),
                variant(
                    id = "b-s2-e3",
                    episode = 3,
                    voiceover = "Дубляж",
                    quality = "Авто",
                    sourceId = "provider-b",
                    sourceLabel = "Источник B",
                    season = 2,
                ),
            ),
        )

        assertEquals(listOf("provider-a", "provider-b"), plan.sourceOptions.map { it.id })
        assertEquals(listOf(1, 2), plan.seasonNumbersFor("provider-a"))
        assertEquals(listOf(3), plan.episodeNumbersFor("provider-b", 2))
        assertEquals(
            listOf("Оригинал"),
            plan.voiceoversFor("provider-a", 2, 1),
        )
        assertEquals(
            "b-s2-e3",
            plan.preferred("provider-b", 2, 3, "Дубляж", "1080p").id,
        )
    }

    @Test
    fun `seasons and episodes are filtered by translation`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant(
                    id = "dub-s1-e1",
                    episode = 1,
                    voiceover = "Дубляж",
                    quality = "1080p",
                    sourceId = "provider",
                    sourceLabel = "Источник",
                    season = 1,
                ),
                variant(
                    id = "dub-s1-e3",
                    episode = 3,
                    voiceover = "Дубляж",
                    quality = "1080p",
                    sourceId = "provider",
                    sourceLabel = "Источник",
                    season = 1,
                ),
                variant(
                    id = "original-s1-e3",
                    episode = 3,
                    voiceover = "Оригинал",
                    quality = "720p",
                    sourceId = "provider",
                    sourceLabel = "Источник",
                    season = 1,
                ),
                variant(
                    id = "original-s2-e2",
                    episode = 2,
                    voiceover = "Оригинал",
                    quality = "720p",
                    sourceId = "provider",
                    sourceLabel = "Источник",
                    season = 2,
                ),
            ),
        )

        assertEquals(listOf("Дубляж", "Оригинал"), plan.voiceoversFor("provider"))
        assertEquals(listOf(1), plan.seasonNumbersFor("provider", "Дубляж"))
        assertEquals(listOf(1, 2), plan.seasonNumbersFor("provider", "Оригинал"))
        assertEquals(1, plan.defaultSeasonNumber("provider", "Оригинал"))
        assertEquals(listOf(1, 3), plan.episodeNumbersFor("provider", 1, "Дубляж"))
        assertEquals(listOf(3), plan.episodeNumbersFor("provider", 1, "Оригинал"))
        assertEquals(listOf(2), plan.episodeNumbersFor("provider", 2, "Оригинал"))
        assertTrue(plan.episodeNumbersFor("provider", 2, "Дубляж").isEmpty())

        // Compatibility methods still expose the unfiltered source matrix.
        assertEquals(listOf(1, 2), plan.seasonNumbersFor("provider"))
        assertEquals(listOf(2), plan.episodeNumbersFor("provider", 2))
    }

    @Test
    fun `episode numbers may be sparse when that is what a provider exposes`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant("episode-1", 1, "Дубляж", "Авто"),
                variant("episode-3", 3, "Дубляж", "Авто"),
            ),
        )

        assertEquals(listOf(1, 3), plan.episodeNumbers)
    }

    @Test
    fun `episode navigation crosses sparse season boundaries within selected translation`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant(
                    id = "dub-s1-e2",
                    episode = 2,
                    voiceover = "Дубляж",
                    quality = "1080p",
                    sourceId = "provider",
                    sourceLabel = "Источник",
                    season = 1,
                ),
                variant(
                    id = "original-s2-e1",
                    episode = 1,
                    voiceover = "Оригинал",
                    quality = "720p",
                    sourceId = "provider",
                    sourceLabel = "Источник",
                    season = 2,
                ),
                variant(
                    id = "dub-s3-e4",
                    episode = 4,
                    voiceover = "Дубляж",
                    quality = "720p",
                    sourceId = "provider",
                    sourceLabel = "Источник",
                    season = 3,
                ),
                variant(
                    id = "dub-s3-e7",
                    episode = 7,
                    voiceover = "Дубляж",
                    quality = "720p",
                    sourceId = "provider",
                    sourceLabel = "Источник",
                    season = 3,
                ),
                variant(
                    id = "other-source-s2-e9",
                    episode = 9,
                    voiceover = "Дубляж",
                    quality = "Авто",
                    sourceId = "other",
                    sourceLabel = "Другой",
                    season = 2,
                ),
            ),
        )

        assertEquals(
            PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 4),
            plan.nextEpisodeCoordinate("provider", 1, 2, "Дубляж"),
        )
        assertEquals(
            PlaybackEpisodeCoordinate(seasonNumber = 1, episodeNumber = 2),
            plan.previousEpisodeCoordinate("provider", 3, 4, "Дубляж"),
        )
        assertEquals(
            PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 7),
            plan.nextEpisodeCoordinate("provider", 3, 4, "Дубляж"),
        )
        assertNull(plan.previousEpisodeCoordinate("provider", 1, 2, "Дубляж"))
        assertNull(plan.nextEpisodeCoordinate("provider", 3, 7, "Дубляж"))
        assertEquals(
            listOf(
                PlaybackEpisodeCoordinate(seasonNumber = 1, episodeNumber = 2),
                PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 4),
                PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 7),
            ),
            plan.episodeCoordinatesFor("provider", "Дубляж"),
        )
    }

    @Test
    fun `plan rejects duplicate ids and duplicate selection coordinates`() {
        expectInvalid {
            PlaybackMediaPlan(
                listOf(
                    variant("same", null, "Дубляж", "Авто"),
                    variant("same", null, "Оригинал", "Авто"),
                ),
            )
        }
        expectInvalid {
            PlaybackMediaPlan(
                listOf(
                    variant("one", null, "Дубляж", "Авто"),
                    variant("two", null, "Дубляж", "Авто"),
                ),
            )
        }
    }

    @Test
    fun `subtitle wrapper redacts its transient address`() {
        val secret = "https://subtitle.test/track.srt?token=secret"
        val track = PlaybackSubtitleTrack(
            id = "ru",
            label = "Русские",
            mediaUrl = secret,
            mimeType = "application/x-subrip",
            languageTag = "ru",
        )

        assertTrue(!track.toString().contains(secret))
        assertTrue(!track.toString().contains("token=secret"))
    }

    @Test
    fun `variant diagnostics redact transient media address`() {
        val secret = "https://media.test/master.m3u8?token=secret"
        val variant = PlaybackMediaVariant(
            id = "redacted",
            episodeNumber = null,
            voiceover = "Дубляж",
            quality = "Авто",
            mediaUrl = secret,
        )

        assertTrue(!variant.toString().contains(secret))
        assertTrue(!variant.toString().contains("token=secret"))
    }

    private fun variant(
        id: String,
        episode: Int?,
        voiceover: String,
        quality: String,
        sourceId: String = DEFAULT_PLAYBACK_SOURCE_ID,
        sourceLabel: String = DEFAULT_PLAYBACK_SOURCE_LABEL,
        season: Int? = null,
    ) = PlaybackMediaVariant(
        id = id,
        episodeNumber = episode,
        voiceover = voiceover,
        quality = quality,
        mediaUrl = "https://cdn.test/$id.m3u8",
        mimeType = "application/x-mpegURL",
        sourceId = sourceId,
        sourceLabel = sourceLabel,
        seasonNumber = season,
    )

    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid playback plan")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
