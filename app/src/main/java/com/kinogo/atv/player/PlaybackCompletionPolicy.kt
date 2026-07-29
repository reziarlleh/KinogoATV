package com.kinogo.atv.player

import com.kinogo.atv.domain.PlaybackEpisodeCoordinate
import com.kinogo.atv.domain.PlaybackMediaPlan

internal sealed interface PlaybackCompletionDecision {
    data class Advance(
        val coordinate: PlaybackEpisodeCoordinate,
    ) : PlaybackCompletionDecision

    data object Exit : PlaybackCompletionDecision
}

/**
 * Media3 normally advances between items of the current playlist without emitting STATE_ENDED.
 * The runtime must therefore treat an automatic media-item transition as a completed episode.
 */
internal enum class PlaybackItemTransitionCompletion {
    IGNORE,
    CHECKPOINT_AND_ADVANCE,
    CHECKPOINT_AND_EXIT,
}

internal fun playbackItemTransitionCompletion(
    automaticTransition: Boolean,
    autoNextEpisode: Boolean,
): PlaybackItemTransitionCompletion = when {
    !automaticTransition -> PlaybackItemTransitionCompletion.IGNORE
    autoNextEpisode -> PlaybackItemTransitionCompletion.CHECKPOINT_AND_ADVANCE
    else -> PlaybackItemTransitionCompletion.CHECKPOINT_AND_EXIT
}

internal enum class PlaybackPauseCompletion {
    IGNORE,
    CHECKPOINT,
    CHECKPOINT_AND_EXIT,
}

/**
 * pauseAtEndOfMediaItems stops on the completed item and emits a play-when-ready callback instead
 * of changing the current playlist item. This is the primary natural-end signal when auto-next is
 * disabled.
 */
internal fun playbackPauseCompletion(
    playWhenReady: Boolean,
    mediaItemEnded: Boolean,
): PlaybackPauseCompletion = when {
    playWhenReady -> PlaybackPauseCompletion.IGNORE
    mediaItemEnded -> PlaybackPauseCompletion.CHECKPOINT_AND_EXIT
    else -> PlaybackPauseCompletion.CHECKPOINT
}

internal data class CompletedPlaybackCheckpoint(
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * Produces a final, non-zero checkpoint from the last values observed before Media3 changes the
 * current playlist item. For live/unknown-duration media the last position becomes the duration.
 */
internal fun completedPlaybackCheckpoint(
    lastPositionMs: Long,
    lastDurationMs: Long,
): CompletedPlaybackCheckpoint {
    val completedPosition = maxOf(lastPositionMs, lastDurationMs).coerceAtLeast(1L)
    return CompletedPlaybackCheckpoint(
        positionMs = completedPosition,
        durationMs = lastDurationMs.takeIf { it > 0L } ?: completedPosition,
    )
}

/**
 * Resolves an actual Media3 end event without depending on UI or player state.
 *
 * Films, disabled auto-next and the last compatible episode always return to details. Episodic
 * playback advances only through coordinates available for the currently selected source and
 * translation, including a sparse next season.
 */
internal fun playbackCompletionDecision(
    mediaPlan: PlaybackMediaPlan,
    sourceId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    voiceover: String,
    autoNextEpisode: Boolean,
): PlaybackCompletionDecision {
    if (
        !autoNextEpisode ||
        !mediaPlan.isEpisodic ||
        seasonNumber == null ||
        episodeNumber == null
    ) {
        return PlaybackCompletionDecision.Exit
    }
    val next = mediaPlan.nextEpisodeCoordinate(
        sourceId = sourceId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        voiceover = voiceover,
    )
    return next?.let(PlaybackCompletionDecision::Advance)
        ?: PlaybackCompletionDecision.Exit
}
