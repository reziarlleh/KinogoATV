package com.kinogo.atv.ui.screens

import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackEpisodeCoordinate
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
    ): PlaybackSourceSelectionState {
        val resumeBranch = if (requested.resume) {
            compatibleResumeBranch(
                plan = plan,
                desiredSourceId = requested.sourceId,
                desiredSeason = requested.season,
                desiredEpisode = requested.episode,
                desiredVoiceover = requested.voiceover,
            )
        } else {
            null
        }
        return normalize(
            plan = plan,
            desiredSourceId = resumeBranch?.sourceId
                ?: requested.sourceId
                ?: plan.defaultSourceId,
            desiredSeason = requested.season,
            desiredEpisode = requested.episode,
            desiredVoiceover = resumeBranch?.voiceover ?: requested.voiceover,
            desiredQuality = requested.quality,
        )
    }

    /**
     * Resolves the first playable unit after a persisted real end event. Provider refreshes may
     * remove the completed leaf (or its whole source), so the lookup is coordinate-first and only
     * then applies the saved source/translation preference.
     */
    fun continuationAfterCompletedEpisode(
        plan: PlaybackMediaPlan,
        requested: PlaybackSelectionUiModel,
    ): PlaybackSourceSelectionState? {
        val season = requested.season ?: return null
        val episode = requested.episode ?: return null
        if (!plan.isEpisodic) return null
        val completed = PlaybackEpisodeCoordinate(season, episode)
        val candidates = plan.variants
            .asSequence()
            .map { ResumePlaybackBranch(it.sourceId, it.voiceover) }
            .distinct()
            .mapNotNull { branch ->
                val coordinates = plan.episodeCoordinatesFor(branch.sourceId, branch.voiceover)
                val completedIndex = coordinates.indexOf(completed)
                val next = if (completedIndex >= 0) {
                    coordinates.getOrNull(completedIndex + 1)
                } else {
                    coordinates.firstOrNull { it.isAfter(completed) }
                } ?: return@mapNotNull null
                ResumeContinuationCandidate(
                    branch = branch,
                    coordinate = next,
                    containsCompletedUnit = completedIndex >= 0,
                )
            }
            .toList()
        val exactCandidates = candidates.filter(ResumeContinuationCandidate::containsCompletedUnit)
        val chosen = if (exactCandidates.isNotEmpty()) {
            exactCandidates.minByOrNull { it.branch.preferenceRank(requested) }
        } else {
            candidates.minWithOrNull(
                compareBy<ResumeContinuationCandidate>(
                    { it.coordinate.seasonNumber },
                    { it.coordinate.episodeNumber },
                    { it.branch.preferenceRank(requested) },
                ),
            )
        } ?: return null
        return normalize(
            plan = plan,
            desiredSourceId = chosen.branch.sourceId,
            desiredSeason = chosen.coordinate.seasonNumber,
            desiredEpisode = chosen.coordinate.episodeNumber,
            desiredVoiceover = chosen.branch.voiceover,
            desiredQuality = requested.quality,
        )
    }

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

    /**
     * A saved episode belongs to the content, not to a provider branch. During a resume launch,
     * preserve its exact season/episode whenever any fresh source/voiceover still exposes it.
     * Manual source and voice changes remain scoped to the branch explicitly chosen by the user.
     */
    private fun compatibleResumeBranch(
        plan: PlaybackMediaPlan,
        desiredSourceId: String?,
        desiredSeason: Int?,
        desiredEpisode: Int?,
        desiredVoiceover: String,
    ): ResumePlaybackBranch? {
        if (!plan.isEpisodic || desiredSeason == null || desiredEpisode == null) return null
        return plan.variants
            .asSequence()
            .filter {
                it.effectiveSeasonNumber == desiredSeason &&
                    it.episodeNumber == desiredEpisode
            }
            .minByOrNull { variant ->
                ResumePlaybackBranch(variant.sourceId, variant.voiceover).preferenceRank(
                    sourceId = desiredSourceId,
                    voiceover = desiredVoiceover,
                )
            }
            ?.let { ResumePlaybackBranch(it.sourceId, it.voiceover) }
    }
}

private data class ResumePlaybackBranch(
    val sourceId: String,
    val voiceover: String,
) {
    fun preferenceRank(requested: PlaybackSelectionUiModel): Int =
        preferenceRank(requested.sourceId, requested.voiceover)

    fun preferenceRank(sourceId: String?, voiceover: String): Int = when {
        this.sourceId == sourceId && this.voiceover == voiceover -> 0
        this.sourceId == sourceId -> 1
        this.voiceover == voiceover -> 2
        else -> 3
    }
}

private data class ResumeContinuationCandidate(
    val branch: ResumePlaybackBranch,
    val coordinate: PlaybackEpisodeCoordinate,
    val containsCompletedUnit: Boolean,
)

private fun PlaybackEpisodeCoordinate.isAfter(other: PlaybackEpisodeCoordinate): Boolean =
    seasonNumber > other.seasonNumber ||
        (seasonNumber == other.seasonNumber && episodeNumber > other.episodeNumber)

internal fun PlaybackSelectionUiModel.isSamePlaybackUnitAs(
    other: PlaybackSelectionUiModel,
): Boolean =
    contentId == other.contentId &&
        season == other.season &&
        episode == other.episode
