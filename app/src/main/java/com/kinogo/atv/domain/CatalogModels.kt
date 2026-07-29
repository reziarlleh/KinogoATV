package com.kinogo.atv.domain

/** Broad content kind used by catalog filters and playback-history rules. */
enum class ContentType {
    MOVIE,
    SERIES,
    ANIMATION,
    ANIME,
    UNKNOWN,
}

data class ContentRatings(
    val kinopoisk: Double? = null,
    val imdb: Double? = null,
) {
    init {
        require(kinopoisk == null || kinopoisk in 0.0..10.0)
        require(imdb == null || imdb in 0.0..10.0)
    }
}

/**
 * A mirror-independent catalog record.
 *
 * [relativePath] deliberately does not contain a host. The active mirror is selected only when a
 * repository resolves this reference, so changing mirrors does not invalidate history/favorites.
 */
data class CatalogItem(
    val id: String,
    val relativePath: String,
    val title: String,
    val originalTitle: String? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val type: ContentType = ContentType.UNKNOWN,
    val ratings: ContentRatings = ContentRatings(),
    val qualityBadge: String? = null,
    val episodeBadge: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(relativePath.startsWith("/") && !relativePath.startsWith("//")) {
            "Catalog paths must be relative to the active mirror"
        }
        require(title.isNotBlank())
        require(year == null || year > 1800)
    }

    /** Numeric DLE post id used by bookmark/status forms; never substitute a player id here. */
    val serverPostId: Long?
        get() = id.toLongOrNull()?.takeIf { it > 0L }
}

data class ContentDetails(
    val catalogItem: CatalogItem,
    val description: String,
    val countries: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val durationMinutes: Int? = null,
    val moviePlaybackOptions: PlaybackOptions? = null,
    val seasons: List<Season> = emptyList(),
) {
    init {
        require(durationMinutes == null || durationMinutes > 0)
    }

    val hasEpisodes: Boolean
        get() = seasons.any { it.episodes.isNotEmpty() }
}
