package com.kinogo.atv.ui.screens

import com.kinogo.atv.ui.model.DetailsPlaybackChoiceUiModel
import com.kinogo.atv.ui.model.EpisodeUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsPlaybackChoiceModelTest {
    @Test
    fun `translation filters seasons episodes and qualities`() {
        val seasonOneEpisodeOne = episode(id = "shared", season = 1, number = 1)
        val seasonOneEpisodeTwo = episode(id = "s1e2", season = 1, number = 2)
        val seasonTwoEpisodeOne = episode(id = "shared", season = 2, number = 1)
        val choices = listOf(
            choice("Dub", seasonOneEpisodeOne, "720p"),
            choice("Dub", seasonOneEpisodeTwo, "720p"),
            choice("Original", seasonOneEpisodeOne, "1080p"),
            choice("Original", seasonTwoEpisodeOne, "4K"),
        )

        assertEquals(
            listOf("Dub", "Original"),
            DetailsPlaybackChoiceModel.voiceovers(choices),
        )
        assertEquals(
            listOf(1),
            DetailsPlaybackChoiceModel.seasons(choices, voiceover = "Dub"),
        )
        assertEquals(
            listOf(1, 2),
            DetailsPlaybackChoiceModel.seasons(choices, voiceover = "Original"),
        )
        assertEquals(
            listOf(seasonTwoEpisodeOne),
            DetailsPlaybackChoiceModel.episodes(
                choices = choices,
                voiceover = "Original",
                season = 2,
            ),
        )
        assertEquals(
            listOf("4K"),
            DetailsPlaybackChoiceModel.qualities(
                choices = choices,
                voiceover = "Original",
                season = 2,
                episode = 1,
            ),
        )
    }

    @Test
    fun `movie quality is selected without season or episode`() {
        val choices = listOf(
            DetailsPlaybackChoiceUiModel(
                voiceover = "Dub",
                qualities = listOf("Авто", "1080p"),
            ),
            DetailsPlaybackChoiceUiModel(
                voiceover = "Original",
                qualities = listOf("720p"),
            ),
        )

        assertEquals(
            emptyList<Int>(),
            DetailsPlaybackChoiceModel.seasons(choices, voiceover = "Dub"),
        )
        assertEquals(
            listOf("Авто", "1080p"),
            DetailsPlaybackChoiceModel.qualities(
                choices = choices,
                voiceover = "Dub",
                season = null,
                episode = null,
            ),
        )
    }

    private fun episode(id: String, season: Int, number: Int) =
        EpisodeUiModel(
            id = id,
            season = season,
            number = number,
            title = "Episode $number",
            duration = "",
        )

    private fun choice(
        voiceover: String,
        episode: EpisodeUiModel,
        vararg qualities: String,
    ) = DetailsPlaybackChoiceUiModel(
        voiceover = voiceover,
        episode = episode,
        qualities = qualities.toList(),
    )
}
