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
 * A single page of either a catalog section or a text search.
 *
 * Filtering and sorting are deliberately not represented yet: the current DLE implementation
 * applies them through mutable POST/session state, so pretending they are stable GET parameters
 * would make requests non-deterministic.
 */
data class CatalogQuery(
    val section: CatalogSection = CatalogSection.ROOT,
    val searchTerm: String? = null,
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
    }

    val normalizedSearchTerm: String?
        get() = searchTerm?.trim()?.replace(WHITESPACE, " ")

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun search(term: String, page: Int = 1): CatalogQuery =
            CatalogQuery(searchTerm = term, page = page)
    }
}
