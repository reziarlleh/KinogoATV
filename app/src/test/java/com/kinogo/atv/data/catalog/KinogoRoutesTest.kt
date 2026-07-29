package com.kinogo.atv.data.catalog

import com.kinogo.atv.domain.CatalogFilter
import com.kinogo.atv.domain.CatalogGenre
import com.kinogo.atv.domain.CatalogQuery
import com.kinogo.atv.domain.CatalogSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KinogoRoutesTest {
    @Test
    fun buildsSectionAndPageRoutes() {
        assertEquals("/", KinogoRoutes.catalog(CatalogQuery()))
        assertEquals("/page/3/", KinogoRoutes.catalog(CatalogQuery(page = 3)))
        assertEquals(
            "/filmy/",
            KinogoRoutes.catalog(CatalogQuery(section = CatalogSection.MOVIES)),
        )
        assertEquals(
            "/serialy/page/2/",
            KinogoRoutes.catalog(CatalogQuery(section = CatalogSection.SERIES, page = 2)),
        )
        assertEquals(
            "/multfilmy/",
            KinogoRoutes.catalog(CatalogQuery(section = CatalogSection.CARTOONS)),
        )
        assertEquals(
            "/anime/",
            KinogoRoutes.catalog(CatalogQuery(section = CatalogSection.ANIME)),
        )
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
    fun buildsOnlyConfirmedDeterministicFilterRoutes() {
        assertEquals(
            "/novinki/",
            KinogoRoutes.catalog(CatalogQuery(filter = CatalogFilter.NewReleases)),
        )
        assertEquals(
            "/xfsearch/god/2026/",
            KinogoRoutes.catalog(CatalogQuery(filter = CatalogFilter.Year(2026))),
        )
        assertEquals(
            "/xfsearch/country/%D0%A1%D0%A8%D0%90/page/2/",
            KinogoRoutes.catalog(
                CatalogQuery(filter = CatalogFilter.Country(" США "), page = 2),
            ),
        )
        assertEquals(
            "/boevik/",
            KinogoRoutes.catalog(
                CatalogQuery(filter = CatalogFilter.Genre(CatalogGenre.ACTION)),
            ),
        )
        val genreRoutes = mapOf(
            CatalogGenre.ACTION to "/boevik/",
            CatalogGenre.COMEDY to "/komedija/",
            CatalogGenre.THRILLER to "/triller/",
            CatalogGenre.HORROR to "/uzhasy/",
            CatalogGenre.SCIENCE_FICTION to "/fantastika/",
            CatalogGenre.ADVENTURE to "/prikljuchenija/",
        )
        genreRoutes.forEach { (genre, route) ->
            assertEquals(
                route,
                KinogoRoutes.catalog(CatalogQuery(filter = CatalogFilter.Genre(genre))),
            )
        }
    }

    @Test
    fun rejectsInvalidQueries() {
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery(page = 0) }
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery.search("   ") }
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery.search("bad\u0000term") }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogQuery(
                section = CatalogSection.MOVIES,
                filter = CatalogFilter.Year(2026),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogQuery(
                searchTerm = "test",
                filter = CatalogFilter.Country("США"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogFilter.Country("bad\u0000country")
        }
    }
}
