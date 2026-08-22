package com.kinogo.atv.ui.screens

import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.player.PlaybackQualityPolicy
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel

/**
 * URL-free state for the pre-play TV selector.
 *
 * The short-lived [PlaybackMediaPlan] is only consulted by reducer-style functions and is never
 * retained in this model. This keeps media and embed addresses out of UI state, persistence and
 * diagnostics.
 */
internal data class PlaybackSourceSelectionState(
    val sourceId: String,
    val season: Int?,
    val episode: Int?,
    val voiceover: String,
    val quality: String,
) {
    fun toPlaybackSelection(base: PlaybackSelectionUiModel): PlaybackSelectionUiModel =
        base.copy(
            season = season,
            episode = episode,
            voiceover = voiceover,
            quality = quality,
            sourceId = sourceId,
        )
}

internal object PlaybackSourceSelectionModel {
    fun initial(
        plan: PlaybackMediaPlan,
        requested: PlaybackSelectionUiModel,
    ): PlaybackSourceSelectionState = normalize(
        plan = plan,
        desiredSourceId = requested.sourceId ?: plan.defaultSourceId,
        desiredSeason = requested.season,
        desiredEpisode = requested.episode,
        desiredVoiceover = requested.voiceover,
        desiredQuality = requested.quality,
    )

    fun selectSource(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
        sourceId: String,
    ): PlaybackSourceSelectionState = normalize(
        plan = plan,
        desiredSourceId = sourceId,
        desiredSeason = state.season,
        desiredEpisode = state.episode,
        desiredVoiceover = state.voiceover,
        desiredQuality = state.quality,
    )

    fun selectSeason(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
        season: Int,
    ): PlaybackSourceSelectionState = normalize(
        plan = plan,
        desiredSourceId = state.sourceId,
        desiredSeason = season,
        desiredEpisode = state.episode,
        desiredVoiceover = state.voiceover,
        desiredQuality = state.quality,
    )

    fun selectEpisode(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
        episode: Int,
    ): PlaybackSourceSelectionState = normalize(
        plan = plan,
        desiredSourceId = state.sourceId,
        desiredSeason = state.season,
        desiredEpisode = episode,
        desiredVoiceover = state.voiceover,
        desiredQuality = state.quality,
    )

    fun selectVoiceover(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
        voiceover: String,
    ): PlaybackSourceSelectionState = normalize(
        plan = plan,
        desiredSourceId = state.sourceId,
        desiredSeason = state.season,
        desiredEpisode = state.episode,
        desiredVoiceover = voiceover,
        desiredQuality = state.quality,
    )

    fun selectQuality(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
        quality: String,
    ): PlaybackSourceSelectionState = normalize(
        plan = plan,
        desiredSourceId = state.sourceId,
        desiredSeason = state.season,
        desiredEpisode = state.episode,
        desiredVoiceover = state.voiceover,
        desiredQuality = quality,
    )

    fun seasonOptions(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
    ): List<Int> = plan.seasonNumbersFor(
        sourceId = state.sourceId,
        voiceover = state.voiceover,
    )

    fun episodeOptions(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
    ): List<Int> = plan.episodeNumbersFor(
        sourceId = state.sourceId,
        seasonNumber = state.season,
        voiceover = state.voiceover,
    )

    fun voiceoverOptions(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
    ): List<String> = plan.voiceoversFor(state.sourceId)

    fun qualityOptions(
        plan: PlaybackMediaPlan,
        state: PlaybackSourceSelectionState,
    ): List<String> {
        val declared = plan.qualitiesFor(
            sourceId = state.sourceId,
            seasonNumber = state.season,
            episodeNumber = state.episode,
            voiceover = state.voiceover,
        )
        return if (
            state.quality !in declared &&
            PlaybackQualityPolicy.height(state.quality) != null
        ) {
            listOf(state.quality) + declared
        } else {
            declared
        }
    }

    /**
     * [TvPlayerScreen] initializes from the plan's first source. Reordering only the in-memory
     * variants makes the explicit pre-play source choice its default without adding a URL-bearing
     * field to persisted [PlaybackSelectionUiModel].
     */
    fun preferSource(
        plan: PlaybackMediaPlan,
        sourceId: String,
    ): PlaybackMediaPlan {
        if (sourceId == plan.defaultSourceId) return plan
        require(plan.sourceOptions.any { it.id == sourceId }) {
            "Unknown playback source"
        }
        return plan.copy(
            variants = plan.variants.filter { it.sourceId == sourceId } +
                plan.variants.filterNot { it.sourceId == sourceId },
        )
    }

    private fun normalize(
        plan: PlaybackMediaPlan,
        desiredSourceId: String,
        desiredSeason: Int?,
        desiredEpisode: Int?,
        desiredVoiceover: String,
        desiredQuality: String,
    ): PlaybackSourceSelectionState {
        val sourceId = desiredSourceId.takeIf { candidate ->
            plan.sourceOptions.any { it.id == candidate }
        } ?: plan.defaultSourceId
        val voiceovers = plan.voiceoversFor(sourceId)
        val voiceover = desiredVoiceover.takeIf { it in voiceovers } ?: voiceovers.first()
        val seasons = plan.seasonNumbersFor(sourceId, voiceover)
        val season = if (plan.isEpisodic) {
            desiredSeason?.takeIf { it in seasons } ?: seasons.first()
        } else {
            null
        }
        val episodes = plan.episodeNumbersFor(sourceId, season, voiceover)
        val episode = if (plan.isEpisodic) {
            desiredEpisode?.takeIf { it in episodes } ?: episodes.first()
        } else {
            null
        }
        val unitVariants = plan.variantsFor(sourceId, season, episode)
        val qualities = unitVariants
            .asSequence()
            .filter { it.voiceover == voiceover }
            .map { it.quality }
            .distinct()
            .toList()
        val quality = when {
            desiredQuality in qualities -> desiredQuality
            PlaybackQualityPolicy.height(desiredQuality) != null -> desiredQuality
            else -> qualities.first()
        }
        return PlaybackSourceSelectionState(
            sourceId = sourceId,
            season = season,
            episode = episode,
            voiceover = voiceover,
            quality = quality,
        )
    }
}

internal fun PlaybackSelectionUiModel.isSamePlaybackUnitAs(
    other: PlaybackSelectionUiModel,
): Boolean =
    contentId == other.contentId &&
        season == other.season &&
        episode == other.episode
