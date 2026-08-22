package com.kinogo.atv.player

import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant

/** A provider-order-stable quality candidate without a media URL or Media3 dependency. */
internal data class PlaybackQualityCandidate<T>(
    val value: T,
    val label: String,
)

/**
 * Result of resolving the user's persistent quality intent against one playback unit.
 *
 * [exceedsRequestedLimit] is true only for the deliberate last-resort rule: every numeric
 * candidate is above the requested cap, so the lowest available stream is selected to preserve
 * playability. The requested label itself must not be replaced by [selectedLabel].
 */
internal data class PlaybackQualityDecision<T>(
    val selected: T,
    val selectedHeight: Int?,
    val exceedsRequestedLimit: Boolean,
)

/** Shared policy for separate provider variants and concrete tracks inside an adaptive manifest. */
internal object PlaybackQualityPolicy {
    private val heightPattern =
        Regex("""(?<!\d)([1-9]\d{2,3})\s*p?(?!\d)""", RegexOption.IGNORE_CASE)

    fun height(label: String): Int? {
        if (Regex("""(?<!\w)4\s*[kк](?!\w)""", RegexOption.IGNORE_CASE).containsMatchIn(label)) {
            return 2160
        }
        return heightPattern
            .find(label)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    fun isAutomatic(label: String): Boolean = label.trim().let { normalized ->
        normalized.equals("Авто", ignoreCase = true) ||
            normalized.startsWith("Авто ·", ignoreCase = true) ||
            normalized.equals("Auto", ignoreCase = true) ||
            normalized.startsWith("Auto ·", ignoreCase = true)
    }

    /**
     * Resolves [desiredQuality] without mutating it.
     *
     * For a fixed cap, an exact separate variant wins. If an adaptive master exists, it is kept so
     * the same policy can select a concrete manifest track later. Otherwise the highest numeric
     * candidate not above the cap is used. If all numeric candidates are above it, the lowest one
     * is the only playable fallback, as explicitly required by the product contract.
     */
    fun <T> select(
        desiredQuality: String,
        candidates: List<PlaybackQualityCandidate<T>>,
        preferAutomaticForMissingFixed: Boolean,
    ): PlaybackQualityDecision<T>? {
        if (candidates.isEmpty()) return null

        val desiredHeight = height(desiredQuality)
        if (isAutomatic(desiredQuality)) {
            val selected = candidates.firstOrNull { isAutomatic(it.label) }
                ?: candidates.withHeight().maxByOrNull { it.height }?.candidate
                ?: candidates.first()
            return selected.decision(exceedsRequestedLimit = false)
        }

        if (desiredHeight == null) {
            val selected = candidates.firstOrNull {
                it.label.equals(desiredQuality, ignoreCase = true)
            } ?: candidates.firstOrNull { isAutomatic(it.label) }
                ?: candidates.first()
            return selected.decision(exceedsRequestedLimit = false)
        }

        candidates.firstOrNull {
            !isAutomatic(it.label) && height(it.label) == desiredHeight
        }?.let { exact ->
            return exact.decision(exceedsRequestedLimit = false)
        }

        if (preferAutomaticForMissingFixed) {
            candidates.firstOrNull { isAutomatic(it.label) }?.let { automatic ->
                return automatic.decision(exceedsRequestedLimit = false)
            }
        }

        val numeric = candidates.withHeight()
        numeric
            .filter { it.height <= desiredHeight }
            .maxByOrNull { it.height }
            ?.let { bounded ->
                return bounded.candidate.decision(exceedsRequestedLimit = false)
            }
        numeric
            .filter { it.height > desiredHeight }
            .minByOrNull { it.height }
            ?.let { lowestAbove ->
                return lowestAbove.candidate.decision(exceedsRequestedLimit = true)
            }

        val selected = candidates.firstOrNull { isAutomatic(it.label) } ?: candidates.first()
        return selected.decision(exceedsRequestedLimit = false)
    }

    private fun <T> List<PlaybackQualityCandidate<T>>.withHeight(): List<NumericCandidate<T>> =
        mapNotNull { candidate ->
            height(candidate.label)?.let { NumericCandidate(candidate, it) }
        }

    private fun <T> PlaybackQualityCandidate<T>.decision(
        exceedsRequestedLimit: Boolean,
    ): PlaybackQualityDecision<T> = PlaybackQualityDecision(
        selected = value,
        selectedHeight = height(label),
        exceedsRequestedLimit = exceedsRequestedLimit,
    )

    private data class NumericCandidate<T>(
        val candidate: PlaybackQualityCandidate<T>,
        val height: Int,
    )
}

/** Resolves one unit while keeping the user's desired cap separate from the concrete variant. */
internal fun PlaybackMediaPlan.preferredForQuality(
    sourceId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    voiceover: String,
    quality: String,
): PlaybackMediaVariant {
    val candidates = variantsFor(sourceId, seasonNumber, episodeNumber)
        .filter { it.voiceover == voiceover }
    return requireNotNull(
        PlaybackQualityPolicy.select(
            desiredQuality = quality,
            candidates = candidates.map { variant ->
                PlaybackQualityCandidate(value = variant, label = variant.quality)
            },
            preferAutomaticForMissingFixed = true,
        ),
    ) { "Playback voice is absent from the selected playback unit" }.selected
}
