package com.kinogo.atv.domain

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

    /**
     * Exact player exit position for Details/player resume.
     *
     * Every positive checkpoint remains resumable. Only a real Media3 end signal suppresses the
     * old position; elapsed percentage is deliberately not part of this exact persistence model.
     */
    fun resumePositionMs(resumeRewindMs: Long = DEFAULT_RESUME_REWIND_MS): Long? {
        require(resumeRewindMs >= 0L)
        if (positionMs == 0L || playbackEnded) return null
        return (boundedPositionMs - resumeRewindMs).coerceAtLeast(0)
    }

    companion object {
        private const val DEFAULT_RESUME_REWIND_MS = 5_000L
    }
}
