package com.kinogo.atv.ui.screens

import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceSelectionModelTest {
    @Test
    fun `resume season episode voice and quality are preselected`() {
        val state = PlaybackSourceSelectionModel.initial(
            plan = episodicPlan(),
            requested = selection(
                season = 2,
                episode = 3,
                voiceover = "Dub B",
                quality = "1080p",
            ),
        )

        assertEquals(
            PlaybackSourceSelectionState(
                sourceId = "cinemar",
                season = 2,
                episode = 3,
                voiceover = "Dub B",
                quality = "1080p",
            ),
            state,
        )
    }

    @Test
    fun `changing source normalizes unavailable dependent choices`() {
        val plan = episodicPlan()
        val initial = PlaybackSourceSelectionModel.initial(
            plan = plan,
            requested = selection(
                season = 2,
                episode = 3,
                voiceover = "Dub B",
                quality = "1080p",
            ),
        )

        val collaps = PlaybackSourceSelectionModel.selectSource(
            plan = plan,
            state = initial,
            sourceId = "collaps",
        )

        assertEquals(
            PlaybackSourceSelectionState(
                sourceId = "collaps",
                season = 1,
                episode = 1,
                voiceover = "Original",
                quality = "720p",
            ),
            collaps,
        )
    }

    @Test
    fun `season unavailable for selected translation cannot be selected`() {
        val plan = episodicPlan()
        val seasonOne = PlaybackSourceSelectionModel.initial(
            plan = plan,
            requested = selection(
                season = 1,
                episode = 3,
                voiceover = "Dub A",
                quality = "720p",
            ),
        )

        val seasonTwo = PlaybackSourceSelectionModel.selectSeason(
            plan = plan,
            state = seasonOne,
            season = 2,
        )

        assertEquals(1, seasonTwo.season)
        assertEquals(3, seasonTwo.episode)
        assertEquals("Dub A", seasonTwo.voiceover)
        assertEquals("720p", seasonTwo.quality)
        assertEquals(
            listOf(1),
            PlaybackSourceSelectionModel.seasonOptions(plan, seasonOne),
        )
    }

    @Test
    fun `changing translation filters seasons and episodes then normalizes descendants`() {
        val plan = episodicPlan()
        val dubA = PlaybackSourceSelectionModel.initial(
            plan = plan,
            requested = selection(
                season = 1,
                episode = 1,
                voiceover = "Dub A",
                quality = "720p",
            ),
        )

        val dubB = PlaybackSourceSelectionModel.selectVoiceover(
            plan = plan,
            state = dubA,
            voiceover = "Dub B",
        )

        assertEquals(
            PlaybackSourceSelectionState(
                sourceId = "cinemar",
                season = 2,
                episode = 3,
                voiceover = "Dub B",
                quality = "1080p",
            ),
            dubB,
        )
        assertEquals(
            listOf("Dub A", "Dub B"),
            PlaybackSourceSelectionModel.voiceoverOptions(plan, dubA),
        )
        assertEquals(
            listOf(2),
            PlaybackSourceSelectionModel.seasonOptions(plan, dubB),
        )
        assertEquals(
            listOf(3),
            PlaybackSourceSelectionModel.episodeOptions(plan, dubB),
        )
    }

    @Test
    fun `changing translation preserves a compatible unit and falls back only when needed`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant("a-s1e1", "cinemar", "Cinemar", 1, 1, "Dub A", "720p"),
                variant("a-s2e3", "cinemar", "Cinemar", 2, 3, "Dub A", "720p"),
                variant("b-s1e1", "cinemar", "Cinemar", 1, 1, "Dub B", "1080p"),
                variant("b-s3e4", "cinemar", "Cinemar", 3, 4, "Dub B", "1080p"),
            ),
        )
        val compatibleDubA = PlaybackSourceSelectionModel.initial(
            plan = plan,
            requested = selection(
                season = 1,
                episode = 1,
                voiceover = "Dub A",
                quality = "720p",
            ),
        )

        val compatibleDubB = PlaybackSourceSelectionModel.selectVoiceover(
            plan = plan,
            state = compatibleDubA,
            voiceover = "Dub B",
        )

        assertEquals(1, compatibleDubB.season)
        assertEquals(1, compatibleDubB.episode)
        assertEquals("Dub B", compatibleDubB.voiceover)

        val incompatibleDubA = PlaybackSourceSelectionModel.initial(
            plan = plan,
            requested = selection(
                season = 2,
                episode = 3,
                voiceover = "Dub A",
                quality = "720p",
            ),
        )
        val normalizedDubB = PlaybackSourceSelectionModel.selectVoiceover(
            plan = plan,
            state = incompatibleDubA,
            voiceover = "Dub B",
        )

        assertEquals(1, normalizedDubB.season)
        assertEquals(1, normalizedDubB.episode)
        assertEquals("Dub B", normalizedDubB.voiceover)
    }

    @Test
    fun `fixed adaptive preference remains selectable beside Auto`() {
        val plan = PlaybackMediaPlan(
            listOf(
                variant(
                    id = "adaptive",
                    sourceId = "cinemar",
                    sourceLabel = "Cinemar",
                    season = null,
                    episode = null,
                    voiceover = "Dub",
                    quality = "Авто",
                ),
            ),
        )
        val state = PlaybackSourceSelectionModel.initial(
            plan = plan,
            requested = selection(
                season = null,
                episode = null,
                voiceover = "Dub",
                quality = "1080p",
            ),
        )

        assertEquals("1080p", state.quality)
        assertEquals(
            listOf("1080p", "Авто"),
            PlaybackSourceSelectionModel.qualityOptions(plan, state),
        )
    }

    @Test
    fun `preferred source is moved first without losing variants`() {
        val plan = episodicPlan()

        val reordered = PlaybackSourceSelectionModel.preferSource(plan, "collaps")

        assertEquals("collaps", reordered.defaultSourceId)
        assertEquals(plan.variants.toSet(), reordered.variants.toSet())
        assertEquals(plan.variants.size, reordered.variants.size)
        assertFalse(reordered.toString().contains("media.example"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown preferred source is rejected`() {
        PlaybackSourceSelectionModel.preferSource(
            plan = episodicPlan(),
            sourceId = "missing",
        )
    }

    @Test
    fun `playback unit comparison ignores voice quality and resume`() {
        val first = selection(
            season = 2,
            episode = 3,
            voiceover = "Dub A",
            quality = "720p",
        )
        val alternateTrack = first.copy(
            voiceover = "Dub B",
            quality = "1080p",
            resume = false,
        )

        assertTrue(first.isSamePlaybackUnitAs(alternateTrack))
        assertFalse(first.isSamePlaybackUnitAs(first.copy(episode = 4)))
    }

    @Test
    fun `resume position label is deterministic`() {
        assertEquals("0:00", formatPlaybackPosition(-5L))
        assertEquals("1:05", formatPlaybackPosition(65_999L))
        assertEquals("1:01:01", formatPlaybackPosition(3_661_999L))
    }

    private fun episodicPlan(): PlaybackMediaPlan = PlaybackMediaPlan(
        listOf(
            variant("c-s1e1", "cinemar", "Cinemar", 1, 1, "Dub A", "720p"),
            variant("c-s1e3", "cinemar", "Cinemar", 1, 3, "Dub A", "720p"),
            variant("c-s2e3", "cinemar", "Cinemar", 2, 3, "Dub B", "1080p"),
            variant("v-s1e1", "collaps", "Collaps", 1, 1, "Original", "720p"),
        ),
    )

    private fun variant(
        id: String,
        sourceId: String,
        sourceLabel: String,
        season: Int?,
        episode: Int?,
        voiceover: String,
        quality: String,
    ): PlaybackMediaVariant = PlaybackMediaVariant(
        id = id,
        sourceId = sourceId,
        sourceLabel = sourceLabel,
        seasonNumber = season,
        episodeNumber = episode,
        voiceover = voiceover,
        quality = quality,
        mediaUrl = "https://media.example/$id.m3u8?token=$id",
        mimeType = "application/x-mpegURL",
    )

    private fun selection(
        season: Int?,
        episode: Int?,
        voiceover: String,
        quality: String,
    ): PlaybackSelectionUiModel = PlaybackSelectionUiModel(
        contentId = "night-business",
        season = season,
        episode = episode,
        voiceover = voiceover,
        quality = quality,
        resume = true,
    )
}
