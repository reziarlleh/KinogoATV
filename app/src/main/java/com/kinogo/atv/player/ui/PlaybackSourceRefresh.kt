package com.kinogo.atv.player.ui

import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import kotlin.math.abs

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

/** Media3 state reduced to the values needed by the pure stall watchdog. */
internal enum class PlaybackStallState {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}

internal data class PlaybackStallObservation(
    val nowMs: Long,
    val playbackState: PlaybackStallState,
    val playWhenReady: Boolean,
    val playbackSuppressed: Boolean,
    val positionMs: Long,
    val durationMs: Long,
) {
    init {
        require(nowMs >= 0L)
        require(positionMs >= 0L)
        require(durationMs >= 0L)
    }
}

internal enum class PlaybackStallDecision {
    WAIT,
    REFRESH_SOURCES,
}

/**
 * Detects a player that still intends to play but does not make wall-clock progress.
 *
 * Media3 has its own stuck-player errors, but its buffering detector deliberately waits while
 * loading itself still advances. A provider can therefore keep the player in BUFFERING without
 * ever producing media. This watchdog adds a wall-clock ceiling while allowing a longer initial
 * startup than a rebuffer after playback has already advanced.
 *
 * The watchdog emits once. The separate attempted-unit set remains the cross-session retry budget.
 */
internal class PlaybackStallWatchdog(
    private val initialBufferingTimeoutMs: Long = INITIAL_BUFFERING_TIMEOUT_MS,
    private val rebufferingTimeoutMs: Long = REBUFFERING_TIMEOUT_MS,
    private val readyNoProgressTimeoutMs: Long = READY_NO_PROGRESS_TIMEOUT_MS,
    private val minimumProgressMs: Long = MINIMUM_PROGRESS_MS,
) {
    private var lastObservedAtMs: Long? = null
    private var lastPositionMs: Long? = null
    private var noProgressSinceMs: Long? = null
    private var hasObservedProgress = false
    private var recoveryEmitted = false

    init {
        require(initialBufferingTimeoutMs > 0L)
        require(rebufferingTimeoutMs > 0L)
        require(readyNoProgressTimeoutMs > 0L)
        require(minimumProgressMs > 0L)
    }

    fun observe(observation: PlaybackStallObservation): PlaybackStallDecision {
        val previousTime = lastObservedAtMs
        if (previousTime != null && observation.nowMs < previousTime) {
            reset()
        }

        val previousPosition = lastPositionMs
        val madeProgress = previousPosition != null &&
            abs(observation.positionMs - previousPosition) >= minimumProgressMs
        lastObservedAtMs = observation.nowMs
        lastPositionMs = observation.positionMs

        if (madeProgress) {
            hasObservedProgress = true
            noProgressSinceMs = observation.nowMs
        }

        if (recoveryEmitted) return PlaybackStallDecision.WAIT

        val expectsProgress = observation.playWhenReady &&
            !observation.playbackSuppressed &&
            (observation.playbackState == PlaybackStallState.BUFFERING ||
                observation.playbackState == PlaybackStallState.READY)
        if (!expectsProgress) {
            noProgressSinceMs = null
            return PlaybackStallDecision.WAIT
        }

        if (madeProgress) return PlaybackStallDecision.WAIT
        val stalledSince = noProgressSinceMs ?: observation.nowMs.also {
            noProgressSinceMs = it
        }
        val timeoutMs = when (observation.playbackState) {
            PlaybackStallState.READY -> readyNoProgressTimeoutMs
            PlaybackStallState.BUFFERING -> if (hasObservedProgress) {
                rebufferingTimeoutMs
            } else {
                initialBufferingTimeoutMs
            }
            PlaybackStallState.IDLE,
            PlaybackStallState.ENDED,
            -> return PlaybackStallDecision.WAIT
        }
        if (observation.nowMs - stalledSince < timeoutMs) {
            return PlaybackStallDecision.WAIT
        }

        recoveryEmitted = true
        return PlaybackStallDecision.REFRESH_SOURCES
    }

    /** A deliberate source/voice/season/episode replacement begins a new observation window. */
    fun reset() {
        lastObservedAtMs = null
        lastPositionMs = null
        noProgressSinceMs = null
        hasObservedProgress = false
        recoveryEmitted = false
    }

    private companion object {
        const val INITIAL_BUFFERING_TIMEOUT_MS = 45_000L
        const val REBUFFERING_TIMEOUT_MS = 25_000L
        const val READY_NO_PROGRESS_TIMEOUT_MS = 15_000L
        const val MINIMUM_PROGRESS_MS = 250L
    }
}
