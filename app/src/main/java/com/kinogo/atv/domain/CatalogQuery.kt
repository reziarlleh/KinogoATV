package com.kinogo.atv.domain

/** Top-level catalog sections that have stable GET routes on supported Kinogo mirrors. */
enum class CatalogSection {
    ROOT,
    MOVIES,
    SERIES,
    CARTOONS,
    ANIME,
}

/**
 * One deterministic server-side catalog filter.
 *
 * Kinogo's current HTML frontend exposes these filters as independent GET routes. Their
 * intersection is not represented because no stable combined GET contract has been confirmed.
 */
sealed interface CatalogFilter {
    val title: String

    data object NewReleases : CatalogFilter {
        override val title: String = "Новинки"
    }

    data class Year(val value: Int) : CatalogFilter {
        init {
            require(value in 1900..2100) { "Catalog year is outside the supported range" }
        }

        override val title: String = value.toString()
    }

    data class Country(val value: String) : CatalogFilter {
        init {
            require(value.isNotBlank()) { "Catalog country must not be blank" }
            require(value.length <= 64) { "Catalog country is too long" }
            require(value.none(Char::isISOControl)) {
                "Catalog country must not contain control characters"
            }
        }

        override val title: String = value.trim()
    }

    data class Genre(val value: CatalogGenre) : CatalogFilter {
        override val title: String = value.title
    }
}

/** Allowlisted genre routes observed on the supported HTML frontend. */
enum class CatalogGenre(
    val title: String,
    internal val routeSegment: String,
) {
    ACTION("Боевик", "boevik"),
    COMEDY("Комедия", "komedija"),
    THRILLER("Триллер", "triller"),
    HORROR("Ужасы", "uzhasy"),
    SCIENCE_FICTION("Фантастика", "fantastika"),
    ADVENTURE("Приключения", "prikljuchenija"),
}

/**
 * A single page of either a catalog section or a text search.
 */
data class CatalogQuery(
    val section: CatalogSection = CatalogSection.ROOT,
    val searchTerm: String? = null,
    val filter: CatalogFilter? = null,
    val page: Int = 1,
) {
    init {
        require(page > 0) { "Catalog page must be positive" }
        require(searchTerm == null || searchTerm.trim().isNotEmpty()) {
            "Search term must not be blank"
        }
        require(searchTerm == null || searchTerm.none(Char::isISOControl)) {
            "Search term must not contain control characters"
        }
        require(searchTerm == null || filter == null) {
            "Text search and catalog filter cannot share one deterministic route"
        }
        require(filter == null || section == CatalogSection.ROOT) {
            "A catalog filter cannot be combined with a top-level section"
        }
    }

    val normalizedSearchTerm: String?
        get() = searchTerm?.trim()?.replace(WHITESPACE, " ")

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun search(term: String, page: Int = 1): CatalogQuery =
            CatalogQuery(searchTerm = term, page = page)
    }
}
