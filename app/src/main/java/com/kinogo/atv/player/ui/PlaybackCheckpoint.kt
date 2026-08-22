package com.kinogo.atv.player.ui

import com.kinogo.atv.ui.model.PlaybackSelectionUiModel

/**
 * A URL-free progress handoff from the Media3 runtime to persistent history.
 *
 * [playbackEnded] is explicit because a completed episode can have the same last reported
 * position as an unfinished one. Inferring completion again in the composition root loses the
 * Media3 end signal and can make resume select an already watched episode.
 */
data class PlaybackCheckpoint(
    val selection: PlaybackSelectionUiModel,
    val positionMs: Long,
    val durationMs: Long,
    val playbackEnded: Boolean,
) {
    init {
        require(positionMs >= 0L)
        require(durationMs >= 0L)
    }
}
