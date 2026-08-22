package com.kinogo.atv.player.ui

/** Stable identity of one item in one prepared Media3 playlist generation. */
internal data class PlaybackWindowIdentity(
    val playlistGeneration: Long,
    val windowIndex: Int,
    val variantId: String,
)

internal data class PlaybackPreloadFailure(
    val window: PlaybackWindowIdentity,
    val terminal: Boolean,
)

internal object PlaybackPreloadFailurePolicy {
    /** Accepts only the immediately following item while preload is armed for current. */
    fun futureWindowOrNull(
        playlistGeneration: Long,
        armedForWindowIndex: Int?,
        currentWindowIndex: Int,
        eventWindowIndex: Int,
        eventVariantId: String?,
    ): PlaybackWindowIdentity? {
        if (armedForWindowIndex != currentWindowIndex) return null
        if (eventWindowIndex != currentWindowIndex + 1) return null
        val variantId = eventVariantId?.takeIf(String::isNotBlank) ?: return null
        return PlaybackWindowIdentity(playlistGeneration, eventWindowIndex, variantId)
    }

    fun matches(
        failure: PlaybackPreloadFailure,
        playlistGeneration: Long,
        windowIndex: Int,
        variantId: String?,
    ): Boolean = failure.window == PlaybackWindowIdentity(
        playlistGeneration = playlistGeneration,
        windowIndex = windowIndex,
        variantId = variantId.orEmpty(),
    )
}
