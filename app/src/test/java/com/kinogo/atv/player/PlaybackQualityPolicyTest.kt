package com.kinogo.atv.player

import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQualityPolicyTest {
    @Test
    fun `fixed cap selects highest available quality not above it`() {
        val decision = select("1080p", "2160p", "1440p", "720p")

        assertEquals("720p", decision.selected)
        assertEquals(720, decision.selectedHeight)
        assertFalse(decision.exceedsRequestedLimit)
    }

    @Test
    fun `equal distance never selects the quality above the cap`() {
        val decision = select("1080p", "1440p", "720p")

        assertEquals("720p", decision.selected)
    }

    @Test
    fun `all qualities above cap select the lowest playable fallback`() {
        val decision = select("480p", "1080p", "720p")

        assertEquals("720p", decision.selected)
        assertTrue(decision.exceedsRequestedLimit)
    }

    @Test
    fun `exact separate variant wins over adaptive master`() {
        val decision = select(
            desired = "1080p",
            preferAutomaticForMissingFixed = true,
            labels = arrayOf("Авто · HLS", "720p", "1080p"),
        )

        assertEquals("1080p", decision.selected)
    }

    @Test
    fun `adaptive master is retained when requested fixed variant is absent`() {
        val decision = select(
            desired = "1080p",
            preferAutomaticForMissingFixed = true,
            labels = arrayOf("Авто · HLS", "720p"),
        )

        assertEquals("Авто · HLS", decision.selected)
        assertFalse(decision.exceedsRequestedLimit)
    }

    @Test
    fun `automatic intent prefers master then highest separate quality`() {
        assertEquals(
            "Авто · HLS",
            select("Авто", "720p", "Авто · HLS", "1080p").selected,
        )
        assertEquals(
            "1080p",
            select("Auto", "720p", "1080p").selected,
        )
    }

    @Test
    fun `4k provider label participates as 2160p`() {
        val decision = select("2160p", "720p", "WEB-DL 4K")

        assertEquals("WEB-DL 4K", decision.selected)
        assertEquals(2160, decision.selectedHeight)
    }

    @Test
    fun `provider order is stable for duplicate heights and unknown labels`() {
        assertEquals(
            "1080p first",
            select("1080p", "1080p first", "1080p second").selected,
        )
        assertEquals(
            "provider-default",
            select("cinema", "provider-default", "provider-backup").selected,
        )
    }

    @Test
    fun `desired cap is resolved independently for every episode`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant("e1-1080", episode = 1, quality = "1080p"),
                variant("e1-720", episode = 1, quality = "720p"),
                variant("e2-720", episode = 2, quality = "720p"),
                variant("e2-480", episode = 2, quality = "480p"),
                variant("e3-2160", episode = 3, quality = "2160p"),
                variant("e4-1080", episode = 4, quality = "1080p"),
            ),
        )

        val selected = (1..4).map { episode ->
            plan.preferredForQuality(
                sourceId = "provider",
                seasonNumber = 1,
                episodeNumber = episode,
                voiceover = "Dub",
                quality = "1080p",
            ).quality
        }

        assertEquals(listOf("1080p", "720p", "2160p", "1080p"), selected)
    }

    @Test
    fun `missing fixed variant retains adaptive master for concrete track resolution`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant("adaptive", episode = 1, quality = "Авто · HLS"),
                variant("low", episode = 1, quality = "480p"),
            ),
        )

        assertEquals(
            "adaptive",
            plan.preferredForQuality("provider", 1, 1, "Dub", "1080p").id,
        )
    }

    @Test
    fun `resolved adaptive tracks and fixed variants share one cap decision`() {
        val fallback = requireNotNull(
            PlaybackQualityPolicy.select(
                desiredQuality = "1080p",
                candidates = listOf(
                    PlaybackQualityCandidate("adaptive-2160", "2160p"),
                    PlaybackQualityCandidate("fixed-720", "720p"),
                ),
                preferAutomaticForMissingFixed = false,
            ),
        )
        val adaptive = requireNotNull(
            PlaybackQualityPolicy.select(
                desiredQuality = "1080p",
                candidates = listOf(
                    PlaybackQualityCandidate("adaptive-1080", "1080p"),
                    PlaybackQualityCandidate("fixed-720", "720p"),
                ),
                preferAutomaticForMissingFixed = false,
            ),
        )

        assertEquals("fixed-720", fallback.selected)
        assertEquals("adaptive-1080", adaptive.selected)
    }

    private fun select(
        desired: String,
        vararg labels: String,
    ): PlaybackQualityDecision<String> = select(
        desired = desired,
        preferAutomaticForMissingFixed = false,
        labels = labels,
    )

    private fun select(
        desired: String,
        preferAutomaticForMissingFixed: Boolean,
        labels: Array<out String>,
    ): PlaybackQualityDecision<String> = requireNotNull(
        PlaybackQualityPolicy.select(
            desiredQuality = desired,
            candidates = labels.map { PlaybackQualityCandidate(value = it, label = it) },
            preferAutomaticForMissingFixed = preferAutomaticForMissingFixed,
        ),
    )

    private fun variant(id: String, episode: Int, quality: String): PlaybackMediaVariant =
        PlaybackMediaVariant(
            id = id,
            sourceId = "provider",
            sourceLabel = "Provider",
            seasonNumber = 1,
            episodeNumber = episode,
            voiceover = "Dub",
            quality = quality,
            mediaUrl = "https://media.example/$id.m3u8",
        )
}
