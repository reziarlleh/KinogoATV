package com.kinogo.atv.player.ui

import com.kinogo.atv.ui.model.PlaybackSelectionUiModel

/** Stable identity for bounding automatic refreshes without retaining a transient media URL. */
data class PlaybackSourceRefreshUnitKey(
    val contentId: String,
    val season: Int?,
    val episode: Int?,
)

/**
 * Handoff to the composition root when Media3 needs a freshly parsed details/provider plan.
 * [attemptedUnits] must be copied into the replacement player session to prevent retry loops.
 */
data class PlaybackSourceRefreshRequest(
    val selection: PlaybackSelectionUiModel,
    val positionMs: Long,
    val attemptedUnits: Set<PlaybackSourceRefreshUnitKey>,
) {
    init {
        require(positionMs >= 0L)
    }
}

internal fun PlaybackSelectionUiModel.sourceRefreshUnitKey(): PlaybackSourceRefreshUnitKey =
    PlaybackSourceRefreshUnitKey(
        contentId = contentId,
        season = season,
        episode = episode,
    )
