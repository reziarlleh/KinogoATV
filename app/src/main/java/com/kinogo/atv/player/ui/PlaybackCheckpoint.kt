package com.kinogo.atv.player.ui

import com.kinogo.atv.ui.model.PlaybackSelectionUiModel

/**
 * A URL-free progress handoff from the Media3 runtime to persistent history.
 *
 * [playbackEnded] is explicit because a completed episode can have the same last reported
 * position as an unfinished one. [unitActivated] is explicit for the inverse reason: a regular
 * zero-position lifecycle callback must not erase an already persisted timestamp, while a
 * deliberate transition to another episode must remain visible before its first progress tick.
 */
data class PlaybackCheckpoint(
    val selection: PlaybackSelectionUiModel,
    val positionMs: Long,
    val durationMs: Long,
    val playbackEnded: Boolean,
    val unitActivated: Boolean = false,
) {
    init {
        require(positionMs >= 0L)
        require(durationMs >= 0L)
        require(
            !unitActivated ||
                (positionMs == 0L && !playbackEnded && selection.season != null &&
                    selection.episode != null),
        ) {
            "A unit activation must be a zero-position unfinished episode checkpoint"
        }
    }
}
