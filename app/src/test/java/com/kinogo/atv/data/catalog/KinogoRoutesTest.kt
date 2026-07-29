package com.kinogo.atv.data.catalog

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
    fun rejectsInvalidQueries() {
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery(page = 0) }
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery.search("   ") }
        assertThrows(IllegalArgumentException::class.java) { CatalogQuery.search("bad\u0000term") }
    }
}
