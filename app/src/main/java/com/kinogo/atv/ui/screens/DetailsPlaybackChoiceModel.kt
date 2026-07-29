package com.kinogo.atv.ui.screens

import com.kinogo.atv.ui.model.DetailsPlaybackChoiceUiModel
import com.kinogo.atv.ui.model.EpisodeUiModel

/**
 * Pure cascade used by the details screen. The order is deliberately
 * voiceover -> season -> episode -> quality.
 */
internal object DetailsPlaybackChoiceModel {
    fun voiceovers(choices: List<DetailsPlaybackChoiceUiModel>): List<String> =
        choices.map(DetailsPlaybackChoiceUiModel::voiceover).distinct()

    fun seasons(
        choices: List<DetailsPlaybackChoiceUiModel>,
        voiceover: String,
    ): List<Int> = choices
        .asSequence()
        .filter { it.voiceover == voiceover }
        .mapNotNull { it.episode?.season }
        .distinct()
        .sorted()
        .toList()

    fun episodes(
        choices: List<DetailsPlaybackChoiceUiModel>,
        voiceover: String,
        season: Int?,
    ): List<EpisodeUiModel> = choices
        .asSequence()
        .filter { choice ->
            choice.voiceover == voiceover &&
                choice.episode?.season == season
        }
        .mapNotNull(DetailsPlaybackChoiceUiModel::episode)
        .distinctBy(EpisodeUiModel::id)
        .sortedBy(EpisodeUiModel::number)
        .toList()

    fun qualities(
        choices: List<DetailsPlaybackChoiceUiModel>,
        voiceover: String,
        season: Int?,
        episode: Int?,
    ): List<String> = choices
        .asSequence()
        .filter { choice ->
            choice.voiceover == voiceover &&
                choice.episode?.season == season &&
                choice.episode?.number == episode
        }
        .flatMap { it.qualities.asSequence() }
        .distinct()
        .toList()
}
