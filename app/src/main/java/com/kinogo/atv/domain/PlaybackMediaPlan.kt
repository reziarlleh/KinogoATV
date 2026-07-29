package com.kinogo.atv.domain

const val DEFAULT_PLAYBACK_SOURCE_ID = "primary"
const val DEFAULT_PLAYBACK_SOURCE_LABEL = "Основной"

/** One concrete media document for a selectable source/season/episode/voice/quality tuple. */
data class PlaybackMediaVariant(
    val id: String,
    val episodeNumber: Int?,
    val voiceover: String,
    val quality: String,
    val mediaUrl: String,
    val mimeType: String? = null,
    /**
     * Stable adapter-owned identifier. The default keeps legacy one-link plans source-compatible.
     * It is never derived from or replaced by a temporary media URL.
     */
    val sourceId: String = DEFAULT_PLAYBACK_SOURCE_ID,
    val sourceLabel: String = DEFAULT_PLAYBACK_SOURCE_LABEL,
    /**
     * Null remains valid for legacy episodic plans and is interpreted as season 1. Films must not
     * declare a season.
     */
    val seasonNumber: Int? = null,
    /** Optional zero-based adaptive-manifest audio track requested by a provider adapter. */
    val preferredAudioTrackIndex: Int? = null,
    val subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
) {
    init {
        require(id.isNotBlank())
        require(episodeNumber == null || episodeNumber > 0)
        require(voiceover.isNotBlank())
        require(quality.isNotBlank())
        require(mediaUrl.isNotBlank())
        require(mimeType == null || mimeType.isNotBlank())
        require(sourceId.isNotBlank())
        require(sourceLabel.isNotBlank())
        require(seasonNumber == null || seasonNumber > 0)
        require(preferredAudioTrackIndex == null || preferredAudioTrackIndex >= 0)
        require(subtitleTracks.map(PlaybackSubtitleTrack::id).distinct().size == subtitleTracks.size) {
            "Subtitle ids must be unique within a playback variant"
        }
        require(episodeNumber != null || seasonNumber == null) {
            "A film variant cannot declare a season"
        }
    }

    val effectiveSeasonNumber: Int?
        get() = if (episodeNumber == null) null else seasonNumber ?: 1

    override fun toString(): String =
        "PlaybackMediaVariant(" +
            "id=$id, episodeNumber=$episodeNumber, voiceover=$voiceover, quality=$quality, " +
            "mediaUrl=<redacted>, mimeType=$mimeType, sourceId=$sourceId, " +
            "sourceLabel=$sourceLabel, seasonNumber=$seasonNumber, " +
            "preferredAudioTrackIndex=$preferredAudioTrackIndex, " +
            "subtitleTracks=$subtitleTracks)"
}

data class PlaybackMediaSourceOption(
    val id: String,
    val label: String,
)

/** One playable episode coordinate inside a source/translation-specific sparse matrix. */
data class PlaybackEpisodeCoordinate(
    val seasonNumber: Int,
    val episodeNumber: Int,
)

/**
 * One external text track attached to a [PlaybackMediaVariant].
 *
 * Subtitle addresses are as short-lived as the media manifest. This wrapper is intentionally not a
 * data class and redacts the address from [toString].
 */
class PlaybackSubtitleTrack(
    val id: String,
    val label: String,
    val mediaUrl: String,
    val mimeType: String,
    val languageTag: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(mediaUrl.isNotBlank())
        require(mimeType.isNotBlank())
        require(languageTag == null || languageTag.isNotBlank())
    }

    override fun toString(): String =
        "PlaybackSubtitleTrack(id=$id, label=$label, mediaUrl=<redacted>)"
}

/**
 * Immutable media matrix handed to the player. Resolvers may build it from a documented provider
 * API; the player only consumes already resolved variants and never parses provider pages.
 *
 * A plan may contain several native sources and seasons. Existing single-source constructors and
 * selectors remain valid through defaults and delegate to the first source/season.
 */
data class PlaybackMediaPlan(
    val variants: List<PlaybackMediaVariant>,
) {
    init {
        require(variants.isNotEmpty()) { "Playback plan must contain at least one variant" }
        require(variants.map { it.id }.distinct().size == variants.size) {
            "Playback variant ids must be unique"
        }
        require(
            variants.distinctBy {
                listOf(
                    it.sourceId,
                    it.effectiveSeasonNumber,
                    it.episodeNumber,
                    it.voiceover,
                    it.quality,
                )
            }.size == variants.size,
        ) { "Source, season, episode, voiceover and quality combinations must be unique" }

        val episodeKinds = variants.map { it.episodeNumber == null }.distinct()
        require(episodeKinds.size == 1) {
            "A playback plan cannot mix a film with episodic variants"
        }

        variants.groupBy(PlaybackMediaVariant::sourceId).forEach { (sourceId, sourceVariants) ->
            require(sourceVariants.map(PlaybackMediaVariant::sourceLabel).distinct().size == 1) {
                "Playback source $sourceId must have one stable label"
            }
        }
    }

    val isEpisodic: Boolean
        get() = variants.first().episodeNumber != null

    val sourceOptions: List<PlaybackMediaSourceOption>
        get() = variants
            .distinctBy(PlaybackMediaVariant::sourceId)
            .map { PlaybackMediaSourceOption(id = it.sourceId, label = it.sourceLabel) }

    val defaultSourceId: String
        get() = variants.first().sourceId

    /** Legacy view: episodes in the first source and its first season. */
    val episodeNumbers: List<Int>
        get() = episodeNumbersFor(
            sourceId = defaultSourceId,
            seasonNumber = defaultSeasonNumber(defaultSourceId),
        )

    fun sourceLabel(sourceId: String): String? =
        sourceOptions.firstOrNull { it.id == sourceId }?.label

    fun seasonNumbersFor(sourceId: String): List<Int> {
        if (!isEpisodic) return emptyList()
        return variants
            .asSequence()
            .filter { it.sourceId == sourceId }
            .mapNotNull(PlaybackMediaVariant::effectiveSeasonNumber)
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Seasons exposed by one translation of a source.
     *
     * Providers commonly publish a sparse matrix where a translation is absent from some seasons.
     * Keeping this query on the immutable plan lets every selector use the same dependency rules.
     */
    fun seasonNumbersFor(
        sourceId: String,
        voiceover: String,
    ): List<Int> {
        if (!isEpisodic) return emptyList()
        return variants
            .asSequence()
            .filter {
                it.sourceId == sourceId &&
                    it.voiceover == voiceover
            }
            .mapNotNull(PlaybackMediaVariant::effectiveSeasonNumber)
            .distinct()
            .sorted()
            .toList()
    }

    fun defaultSeasonNumber(sourceId: String): Int? =
        seasonNumbersFor(sourceId).firstOrNull()

    fun defaultSeasonNumber(
        sourceId: String,
        voiceover: String,
    ): Int? = seasonNumbersFor(sourceId, voiceover).firstOrNull()

    fun episodeNumbersFor(
        sourceId: String,
        seasonNumber: Int?,
    ): List<Int> = variants
        .asSequence()
        .filter {
            it.sourceId == sourceId &&
                it.effectiveSeasonNumber == seasonNumber
        }
        .mapNotNull(PlaybackMediaVariant::episodeNumber)
        .distinct()
        .sorted()
        .toList()

    /** Episodes exposed by one translation within the selected source and season. */
    fun episodeNumbersFor(
        sourceId: String,
        seasonNumber: Int?,
        voiceover: String,
    ): List<Int> = variants
        .asSequence()
        .filter {
            it.sourceId == sourceId &&
                it.effectiveSeasonNumber == seasonNumber &&
                it.voiceover == voiceover
        }
        .mapNotNull(PlaybackMediaVariant::episodeNumber)
        .distinct()
        .sorted()
        .toList()

    /**
     * Previous playable episode for the selected source and translation.
     *
     * The flattened order is season then episode, so moving back from the first available episode
     * of a season lands on the last available episode of the preceding compatible season.
     */
    fun previousEpisodeCoordinate(
        sourceId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        voiceover: String,
    ): PlaybackEpisodeCoordinate? = adjacentEpisodeCoordinate(
        sourceId = sourceId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        voiceover = voiceover,
        offset = -1,
    )

    /**
     * Next playable episode for the selected source and translation.
     *
     * Missing seasons and episode numbers are skipped rather than synthesized. At a season
     * boundary this returns the first episode that actually exists in the next compatible season.
     */
    fun nextEpisodeCoordinate(
        sourceId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        voiceover: String,
    ): PlaybackEpisodeCoordinate? = adjacentEpisodeCoordinate(
        sourceId = sourceId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        voiceover = voiceover,
        offset = 1,
    )

    /** Legacy selector for the first source and its first season. */
    fun variantsForEpisode(episodeNumber: Int?): List<PlaybackMediaVariant> =
        variantsFor(
            sourceId = defaultSourceId,
            seasonNumber = defaultSeasonNumber(defaultSourceId),
            episodeNumber = episodeNumber,
        )

    fun variantsFor(
        sourceId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): List<PlaybackMediaVariant> = variants.filter {
        it.sourceId == sourceId &&
            it.effectiveSeasonNumber == seasonNumber &&
            it.episodeNumber == episodeNumber
    }

    /** Legacy selector for the first source and its first season. */
    fun voiceoversFor(episodeNumber: Int?): List<String> =
        voiceoversFor(
            sourceId = defaultSourceId,
            seasonNumber = defaultSeasonNumber(defaultSourceId),
            episodeNumber = episodeNumber,
        )

    /** All translations available anywhere within one source, in provider declaration order. */
    fun voiceoversFor(sourceId: String): List<String> = variants
        .asSequence()
        .filter { it.sourceId == sourceId }
        .map(PlaybackMediaVariant::voiceover)
        .distinct()
        .toList()

    fun voiceoversFor(
        sourceId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): List<String> = variantsFor(sourceId, seasonNumber, episodeNumber)
        .map(PlaybackMediaVariant::voiceover)
        .distinct()

    /** Legacy selector for the first source and its first season. */
    fun qualitiesFor(episodeNumber: Int?, voiceover: String): List<String> =
        qualitiesFor(
            sourceId = defaultSourceId,
            seasonNumber = defaultSeasonNumber(defaultSourceId),
            episodeNumber = episodeNumber,
            voiceover = voiceover,
        )

    fun qualitiesFor(
        sourceId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
        voiceover: String,
    ): List<String> = variantsFor(sourceId, seasonNumber, episodeNumber)
        .asSequence()
        .filter { it.voiceover == voiceover }
        .map(PlaybackMediaVariant::quality)
        .distinct()
        .toList()

    /** Legacy selector for the first source and its first season. */
    fun find(
        episodeNumber: Int?,
        voiceover: String,
        quality: String,
    ): PlaybackMediaVariant? = find(
        sourceId = defaultSourceId,
        seasonNumber = defaultSeasonNumber(defaultSourceId),
        episodeNumber = episodeNumber,
        voiceover = voiceover,
        quality = quality,
    )

    fun find(
        sourceId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
        voiceover: String,
        quality: String,
    ): PlaybackMediaVariant? = variants.firstOrNull {
        it.sourceId == sourceId &&
            it.effectiveSeasonNumber == seasonNumber &&
            it.episodeNumber == episodeNumber &&
            it.voiceover == voiceover &&
            it.quality == quality
    }

    fun findById(id: String): PlaybackMediaVariant? = variants.firstOrNull { it.id == id }

    /** Legacy selector for the first source and its first season. */
    fun preferred(
        episodeNumber: Int?,
        voiceover: String,
        quality: String,
    ): PlaybackMediaVariant = preferred(
        sourceId = defaultSourceId,
        seasonNumber = defaultSeasonNumber(defaultSourceId),
        episodeNumber = episodeNumber,
        voiceover = voiceover,
        quality = quality,
    )

    fun preferred(
        sourceId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
        voiceover: String,
        quality: String,
    ): PlaybackMediaVariant {
        val episodeVariants = variantsFor(sourceId, seasonNumber, episodeNumber)
        require(episodeVariants.isNotEmpty()) {
            "Playback unit is absent from the playback plan"
        }
        return find(sourceId, seasonNumber, episodeNumber, voiceover, quality)
            ?: episodeVariants.firstOrNull { it.voiceover == voiceover }
            ?: episodeVariants.firstOrNull { it.quality == quality }
            ?: episodeVariants.first()
    }

    private fun adjacentEpisodeCoordinate(
        sourceId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        voiceover: String,
        offset: Int,
    ): PlaybackEpisodeCoordinate? {
        if (!isEpisodic) return null
        val coordinates = variants
            .asSequence()
            .filter {
                it.sourceId == sourceId &&
                    it.voiceover == voiceover
            }
            .mapNotNull { variant ->
                val season = variant.effectiveSeasonNumber ?: return@mapNotNull null
                val episode = variant.episodeNumber ?: return@mapNotNull null
                PlaybackEpisodeCoordinate(season, episode)
            }
            .distinct()
            .sortedWith(
                compareBy(
                    PlaybackEpisodeCoordinate::seasonNumber,
                    PlaybackEpisodeCoordinate::episodeNumber,
                ),
            )
            .toList()
        val currentIndex = coordinates.indexOf(
            PlaybackEpisodeCoordinate(seasonNumber, episodeNumber),
        )
        if (currentIndex < 0) return null
        return coordinates.getOrNull(currentIndex + offset)
    }
}
