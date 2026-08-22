package com.kinogo.atv.player

import com.kinogo.atv.domain.TvPreferences

/** Pure time-based Media3 buffer and recovery thresholds derived from one TV preference. */
internal data class PlaybackBufferConfiguration(
    val targetBufferMs: Int,
    val playbackStartBufferMs: Int,
    val rebufferStartBufferMs: Int,
    val nextEpisodePreloadMs: Int,
    val initialBufferingRecoveryMs: Long,
    val rebufferingRecoveryMs: Long,
    val readyNoProgressRecoveryMs: Long,
)

internal object PlaybackBufferPolicy {
    /**
     * Playlist preloading is armed only once the end of the current item is already buffered.
     *
     * Keeping this decision pure makes the important negative cases explicit: films, disabled
     * auto-next, an unknown duration and the final playlist item must never open the following
     * provider grant early.
     */
    fun shouldArmNextEpisodePreload(
        isEpisodic: Boolean,
        autoNextEpisode: Boolean,
        currentMediaItemIndex: Int,
        mediaItemCount: Int,
        durationMs: Long,
        currentPositionMs: Long,
        bufferedPositionMs: Long,
        preloadHorizonMs: Long,
        playWhenReady: Boolean,
        playbackSuppressed: Boolean,
        minimumRearmPositionMs: Long = 0L,
    ): Boolean {
        if (
            !isEpisodic ||
            !autoNextEpisode ||
            !playWhenReady ||
            playbackSuppressed ||
            durationMs <= 0L ||
            preloadHorizonMs <= 0L
        ) return false
        if (currentMediaItemIndex < 0 || currentMediaItemIndex + 1 >= mediaItemCount) return false
        val currentPosition = currentPositionMs.coerceAtLeast(0L)
        if (currentPosition < minimumRearmPositionMs.coerceAtLeast(0L)) return false
        val remainingMs = (durationMs - currentPosition).coerceAtLeast(0L)
        if (remainingMs > preloadHorizonMs + END_OF_ITEM_BUFFER_TOLERANCE_MS) return false
        val bufferedEndThresholdMs =
            (durationMs - END_OF_ITEM_BUFFER_TOLERANCE_MS).coerceAtLeast(0L)
        return bufferedPositionMs.coerceAtLeast(0L) >= bufferedEndThresholdMs
    }

    fun forSeconds(requestedSeconds: Int): PlaybackBufferConfiguration {
        val seconds = requestedSeconds.takeIf {
            it in TvPreferences.PLAYBACK_BUFFER_SECONDS
        } ?: TvPreferences.DEFAULT_PLAYBACK_BUFFER_SECONDS
        val targetMs = seconds * 1_000
        return PlaybackBufferConfiguration(
            targetBufferMs = targetMs,
            playbackStartBufferMs = (targetMs / 3).coerceIn(1_000, 2_500),
            rebufferStartBufferMs = (targetMs / 2).coerceIn(2_000, 5_000),
            // Media3 playlist preloading keeps only the immediately following episode warm.
            nextEpisodePreloadMs = (targetMs / 2).coerceIn(2_000, 5_000),
            // Initial provider startup is allowed more time than a stream that has already played.
            initialBufferingRecoveryMs = maxOf(20, seconds) * 1_000L,
            // Once the configured reserve is exhausted, recover promptly instead of waiting for
            // Media3/provider timeouts that may never arrive.
            rebufferingRecoveryMs = seconds.coerceIn(5, 10) * 1_000L,
            readyNoProgressRecoveryMs = 15_000L,
        )
    }

    internal const val END_OF_ITEM_BUFFER_TOLERANCE_MS = 500L
}
