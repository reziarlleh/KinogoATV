package com.kinogo.atv.player.ui

/** Exact immutable playback context captured before a deferred quality-variant switch. */
internal data class PlaybackQualitySwitchContext(
    val sourceId: String,
    val voiceover: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val currentMediaItemVariantId: String?,
    val desiredQuality: String,
    val playlistGeneration: Long,
    val qualityGeneration: Long,
)

internal data class PlaybackQualitySwitchRequest(
    val targetVariantId: String,
    val context: PlaybackQualitySwitchContext,
) {
    fun isApplicableTo(current: PlaybackQualitySwitchContext): Boolean =
        context == current && targetVariantId != current.currentMediaItemVariantId
}
