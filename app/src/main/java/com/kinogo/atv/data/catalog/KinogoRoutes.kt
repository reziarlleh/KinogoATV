package com.kinogo.atv.data.catalog

import com.kinogo.atv.domain.CatalogQuery
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Deterministic, origin-independent GET routes understood by the current DLE frontend. */
object KinogoRoutes {
    fun catalog(query: CatalogQuery): String {
        val base = query.normalizedSearchTerm
            ?.let { "/search/${encodePathSegment(it)}/" }
            ?: query.category?.relativePath
            ?: "/"

        if (query.page == 1) return base
        return if (base == "/") {
            "/page/${query.page}/"
        } else {
            "${base}page/${query.page}/"
        }
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
}
