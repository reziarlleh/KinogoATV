package com.kinogo.atv.player.ui

import com.kinogo.atv.domain.PlaybackEpisodeCoordinate
import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant
import com.kinogo.atv.player.preferredForQuality

/**
 * Finds a target only when the Media3 playlist still represents the complete current context.
 *
 * A mismatched count means source/voice/quality replacement has changed one side of the contract;
 * callers must rebuild instead of seeking to a potentially unrelated index.
 */
internal fun preparedEpisodePlaylistIndex(
    coordinates: List<PlaybackEpisodeCoordinate>,
    preparedCoordinates: List<PlaybackEpisodeCoordinate?>,
    target: PlaybackEpisodeCoordinate,
): Int? {
    if (preparedCoordinates != coordinates) return null
    return coordinates.indexOf(target).takeIf { it >= 0 }
}

/**
 * Re-resolves every episodic playlist leaf for a new persistent quality intent.
 *
 * [currentVariantOverride] is deliberately retained for its exact current coordinate. Rebuilding
 * a playlist must not replace the already chosen/current opaque Cinemar reference merely because
 * a later episode needs a different fixed variant; the player can therefore restore the same
 * index and position without requesting a second current-item grant.
 */
internal fun playlistVariantsForQuality(
    mediaPlan: PlaybackMediaPlan,
    coordinates: List<PlaybackEpisodeCoordinate>,
    sourceId: String,
    voiceover: String,
    desiredQuality: String,
    currentVariantOverride: PlaybackMediaVariant? = null,
): List<PlaybackMediaVariant> = coordinates.map { coordinate ->
    currentVariantOverride?.takeIf { variant ->
        variant.sourceId == sourceId &&
            variant.voiceover == voiceover &&
            variant.effectiveSeasonNumber == coordinate.seasonNumber &&
            variant.episodeNumber == coordinate.episodeNumber
    } ?: mediaPlan.preferredForQuality(
        sourceId = sourceId,
        seasonNumber = coordinate.seasonNumber,
        episodeNumber = coordinate.episodeNumber,
        voiceover = voiceover,
        quality = desiredQuality,
    )
}
