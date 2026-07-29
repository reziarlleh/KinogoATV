package com.kinogo.atv.ui.screens

import com.kinogo.atv.domain.CatalogSection
import com.kinogo.atv.ui.model.PosterUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogUiBehaviorTest {
    private val movie = poster("movie", "Бета", "2025 • Фильм", "WEB-DL 720p")
    private val series = poster("series", "Альфа", "2026 • Сериал", "WEB-DL 1080p")
    private val cartoon = poster("cartoon", "Мульт", "2024 • Мультфильм", "4K")

    @Test
    fun `top row keeps the requested broad sections`() {
        val items = listOf(movie, series, cartoon)

        assertEquals(listOf(movie), items.filterForSection(CatalogSection.MOVIES))
        assertEquals(listOf(series), items.filterForSection(CatalogSection.SERIES))
        assertEquals(listOf(cartoon), items.filterForSection(CatalogSection.CARTOONS))
    }

    @Test
    fun `dropdown sorting is deterministic for the loaded set`() {
        val items = listOf(movie, series, cartoon)

        assertEquals(
            listOf("Мульт", "Альфа", "Бета"),
            items.sortedForCatalog(CatalogSortMode.QUALITY).map { it.title },
        )
        assertEquals(
            listOf("Альфа", "Бета", "Мульт"),
            items.sortedForCatalog(CatalogSortMode.TITLE).map { it.title },
        )
        assertEquals(
            listOf(series, movie, cartoon),
            items.sortedForCatalog(CatalogSortMode.NEWEST),
        )
    }

    @Test
    fun `filter years cover site range and include loaded values without duplicates`() {
        val years = catalogFilterYears(
            items = listOf(movie, series, poster("old", "Старый", "2012 • Фильм", null)),
            currentYear = 2026,
        )

        assertEquals(2026, years.first())
        assertEquals(2014, years.last())
        assertFalse(2012 in years)
        assertEquals(years.distinct(), years)
    }

    @Test
    fun `country choices include the main site values`() {
        val countries = catalogCountries()

        assertTrue("США" in countries)
        assertTrue("Россия" in countries)
        assertTrue("Корея" in countries)
        assertTrue("Казахстан" in countries)
    }

    private fun poster(
        id: String,
        title: String,
        subtitle: String,
        badge: String?,
    ) = PosterUiModel(
        id = id,
        title = title,
        subtitle = subtitle,
        badge = badge,
    )
}
