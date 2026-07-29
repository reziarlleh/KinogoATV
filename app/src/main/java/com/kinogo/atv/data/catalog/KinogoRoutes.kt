package com.kinogo.atv.data.catalog

import com.kinogo.atv.domain.CatalogQuery
import com.kinogo.atv.domain.CatalogFilter
import com.kinogo.atv.domain.CatalogSection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Deterministic, origin-independent GET routes understood by the current DLE frontend. */
object KinogoRoutes {
    fun catalog(query: CatalogQuery): String {
        val base = query.normalizedSearchTerm
            ?.let { "/search/${encodePathSegment(it)}/" }
            ?: query.filter?.basePath
            ?: query.section.basePath

        if (query.page == 1) return base
        return if (base == "/") {
            "/page/${query.page}/"
        } else {
            "${base}page/${query.page}/"
        }
    }

    private val CatalogSection.basePath: String
        get() = when (this) {
            CatalogSection.ROOT -> "/"
            CatalogSection.MOVIES -> "/filmy/"
            CatalogSection.SERIES -> "/serialy/"
            CatalogSection.CARTOONS -> "/multfilmy/"
            CatalogSection.ANIME -> "/anime/"
        }

    private val CatalogFilter.basePath: String
        get() = when (this) {
            CatalogFilter.NewReleases -> "/novinki/"
            is CatalogFilter.Year -> "/xfsearch/god/$value/"
            is CatalogFilter.Country -> "/xfsearch/country/${encodePathSegment(title)}/"
            is CatalogFilter.Genre -> "/${value.routeSegment}/"
        }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
}
