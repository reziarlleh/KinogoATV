package com.kinogo.atv.data.catalog

import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogCategory
import com.kinogo.atv.domain.CatalogDefaultSort
import com.kinogo.atv.domain.CatalogFilterOption
import com.kinogo.atv.domain.CatalogQuery
import com.kinogo.atv.domain.CatalogSortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KinogoRoutesTest {
    @Test
    fun buildsHomeCategoryAndPageRoutes() {
        assertEquals("/", KinogoRoutes.catalog(CatalogQuery()))
        assertEquals("/page/3/", KinogoRoutes.catalog(CatalogQuery(page = 3)))
        assertEquals(
            "/filmy/",
            KinogoRoutes.catalog(CatalogQuery(category = CatalogCategory.ALL_MOVIES)),
        )
        assertEquals(
            "/serialy/page/2/",
            KinogoRoutes.catalog(
                CatalogQuery(category = CatalogCategory.ALL_SERIES, page = 2),
            ),
        )
    }

    @Test
    fun everyObservedCategoryUsesItsExactAllowlistedRoute() {
        val expected = mapOf(
            CatalogCategory.ALL_MOVIES to "/filmy/",
            CatalogCategory.CARTOONS to "/multfilmy/",
            CatalogCategory.NEW_RELEASES to "/novinki/",
            CatalogCategory.SCIENCE_FICTION to "/fantastika/",
            CatalogCategory.FANTASY to "/fjentezi/",
            CatalogCategory.NOIR to "/nuar/",
            CatalogCategory.HORROR to "/uzhasy/",
            CatalogCategory.THRILLER to "/triller/",
            CatalogCategory.SPORT to "/sport/",
            CatalogCategory.ADVENTURE to "/prikljuchenija/",
            CatalogCategory.HISTORICAL to "/istoricheskie/",
            CatalogCategory.MUSICAL to "/mjuzikl/",
            CatalogCategory.MELODRAMA to "/melodrama/",
            CatalogCategory.SHORT_FILM to "/korotkometrazhka/",
            CatalogCategory.CRIME to "/kriminal/",
            CatalogCategory.DRAMA to "/drama/",
            CatalogCategory.COMEDY to "/komedija/",
            CatalogCategory.DOCUMENTARY to "/dokumentalnye/",
            CatalogCategory.DETECTIVE to "/detektiv/",
            CatalogCategory.CHILDREN to "/detskij/",
            CatalogCategory.WAR to "/voennyj/",
            CatalogCategory.WESTERN to "/vestern/",
            CatalogCategory.ALL_SERIES to "/serialy/",
            CatalogCategory.FOREIGN_SERIES to "/zarubezhnye-serialy/",
            CatalogCategory.RUSSIAN_SERIES to "/russkie-serialy/",
            CatalogCategory.ANIMATED_SERIES to "/multserialy/",
            CatalogCategory.ANIME_SERIES to "/anime-serialy/",
            CatalogCategory.ANIME to "/anime/",
        )

        assertEquals(CatalogCategory.entries.size, expected.size)
        expected.forEach { (category, path) ->
            assertEquals(path, category.relativePath)
            assertEquals(path, KinogoRoutes.catalog(CatalogQuery(category = category)))
            assertEquals(
                "${path}page/4/",
                KinogoRoutes.catalog(CatalogQuery(category = category, page = 4)),
            )
            assertEquals(category, CatalogCategory.fromRelativePath(path))
        }
    }

    @Test
    fun browseFiltersDoNotChangeTheDeterministicListingRoute() {
        val query = CatalogQuery(
            category = CatalogCategory.DRAMA,
            filters = CatalogBrowseFilters(
                defaultSort = CatalogDefaultSort.RATING,
                sortDirection = CatalogSortDirection.ASC,
                collection = CatalogFilterOption("Netflix", "Netflix"),
                year = 2026,
                country = CatalogFilterOption("США", "США"),
            ),
            page = 2,
        )

        assertEquals("/drama/page/2/", KinogoRoutes.catalog(query))
        assertEquals(query.copy(page = 1), query.identity)
    }

    @Test
    fun searchUsesUtf8PathEncodingAndNormalizesWhitespace() {
        assertEquals(
            "/search/%D0%98%D0%B3%D1%80%D0%B0%20%D0%BF%D1%80%D0%B5%D1%81%D1%82%D0%BE%D0%BB%D0%BE%D0%B2/",
            KinogoRoutes.catalog(CatalogQuery.search("  Игра   престолов ")),
        )
        assertEquals(
            "/search/a%2Fb%3Fc/page/4/",
            KinogoRoutes.catalog(CatalogQuery.search("a/b?c", page = 4)),
        )
    }

    @Test
    fun rejectsInvalidQueriesAndFilterValues() {
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery(page = 0) }
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery.search("   ") }
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery.search("bad\u0000term") }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogQuery(
                category = CatalogCategory.ALL_MOVIES,
                searchTerm = "test",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogQuery(
                filters = CatalogBrowseFilters(year = 2026),
                searchTerm = "test",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogBrowseFilters(sortDirection = CatalogSortDirection.ASC)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogFilterOption("bad\u0000value", "Плохо")
        }
    }
}
