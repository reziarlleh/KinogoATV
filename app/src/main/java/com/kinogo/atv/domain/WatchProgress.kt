package com.kinogo.atv.domain

import kotlin.math.min

/**
 * Persistable playback checkpoint. It contains stable option ids, never an expiring stream URL.
 */
data class WatchProgress(
    val selection: PlaybackSelection,
    val positionMs: Long,
    val durationMs: Long? = null,
    val updatedAtEpochMs: Long,
    val playbackEnded: Boolean = false,
    /**
     * Mirror-independent card snapshot used to restore history after a process restart.
     *
     * Search results are intentionally short-lived UI state. Persisting their relative path and
     * presentation data next to the checkpoint keeps a history row identifiable and playable
     * without retaining a mirror hostname or an expiring media URL.
     */
    val contentSnapshot: CatalogItem? = null,
) {
    init {
        require(positionMs >= 0)
        require(durationMs == null || durationMs > 0)
        require(updatedAtEpochMs >= 0)
        require(contentSnapshot == null || contentSnapshot.id == selection.contentId) {
            "History snapshot must describe the checkpoint content"
        }
    }

    val boundedPositionMs: Long
        get() = durationMs?.let { positionMs.coerceAtMost(it) } ?: positionMs

    val remainingMs: Long?
        get() = durationMs?.let { (it - boundedPositionMs).coerceAtLeast(0) }

    val progressFraction: Double?
        get() = durationMs?.let { boundedPositionMs.toDouble() / it.toDouble() }

    fun isCompleted(rules: WatchProgressRules = WatchProgressRules.DEFAULT): Boolean =
        rules.isCompleted(this)

    /**
     * Exact player exit position for Details/player resume.
     *
     * The percentage/remaining-time completion heuristic is intentionally not used here. A Back
     * or lifecycle checkpoint near the credits still represents an explicit user exit and must
     * remain resumable. Only a real Media3 end signal suppresses the old position.
     */
    fun resumePositionMs(rules: WatchProgressRules = WatchProgressRules.DEFAULT): Long? {
        if (positionMs == 0L || playbackEnded) return null
        return (boundedPositionMs - rules.resumeRewindMs).coerceAtLeast(0)
    }

    fun qualifiesForContinueWatching(
        rules: WatchProgressRules = WatchProgressRules.DEFAULT,
    ): Boolean = rules.qualifiesForContinueWatching(this)
}

/**
 * TV-oriented history rules. Defaults mirror Android TV's conservative "Watch Next" guidance:
 * movies start after the earlier of 3% or two minutes, episodes after two minutes. Without real
 * credit markers, completion is approximated by both a percentage and a remaining-time window.
 */
data class WatchProgressRules(
    val movieStartFraction: Double = 0.03,
    val movieStartMaxMs: Long = 2 * MINUTE_MS,
    val episodeStartMs: Long = 2 * MINUTE_MS,
    val completionFraction: Double = 0.90,
    val movieCompletionRemainingMs: Long = 10 * MINUTE_MS,
    val episodeCompletionRemainingMs: Long = 3 * MINUTE_MS,
    val resumeRewindMs: Long = 5_000,
) {
    init {
        require(movieStartFraction in 0.0..1.0)
        require(completionFraction in 0.0..1.0)
        require(movieStartMaxMs >= 0)
        require(episodeStartMs >= 0)
        require(movieCompletionRemainingMs >= 0)
        require(episodeCompletionRemainingMs >= 0)
        require(resumeRewindMs >= 0)
    }

    fun qualifiesForContinueWatching(progress: WatchProgress): Boolean {
        if (progress.positionMs == 0L || isCompleted(progress)) return false
        return progress.boundedPositionMs >= startThresholdMs(progress)
    }

    fun isCompleted(progress: WatchProgress): Boolean {
        if (progress.playbackEnded) return true

        val fraction = progress.progressFraction ?: return false
        val remaining = progress.remainingMs ?: return false
        val remainingThreshold =
            if (progress.selection.isEpisode) {
                episodeCompletionRemainingMs
            } else {
                movieCompletionRemainingMs
            }

        return fraction >= completionFraction && remaining <= remainingThreshold
    }

    fun startThresholdMs(progress: WatchProgress): Long {
        if (progress.selection.isEpisode) return episodeStartMs

        val duration = progress.durationMs ?: return movieStartMaxMs
        val fractionalThreshold = (duration * movieStartFraction).toLong().coerceAtLeast(1)
        return min(movieStartMaxMs, fractionalThreshold)
    }

    companion object {
        const val MINUTE_MS: Long = 60_000
        val DEFAULT: WatchProgressRules = WatchProgressRules()
    }
}
