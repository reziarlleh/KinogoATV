package com.kinogo.atv.ui.mapper

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentDetails
import com.kinogo.atv.domain.ContentRatings
import com.kinogo.atv.domain.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogUiMapperTest {
    private val catalogItem = CatalogItem("42", "/filmy/42-film.html", "Фильм")

    @Test
    fun `poster displays both site ratings without replacing quality`() {
        val poster = catalogItem.copy(
            ratings = ContentRatings(kinopoisk = 8.1, imdb = 7.7),
            qualityBadge = "Качество: WEB-DL 720",
            episodeBadge = "1 сезон 1-19 серия",
        ).toPosterUiModel()

        assertEquals("КП 8.1\nIMDb 7.7", poster.rating)
        assertEquals("WEB-DL 720", poster.badge)
        assertEquals("1 сезон 1-19 серия", poster.episodeBadge)
    }

    @Test
    fun `poster handles single missing and zero ratings without invented values`() {
        assertNull(catalogItem.toPosterUiModel().rating)
        assertEquals(
            "IMDb 7",
            catalogItem.copy(ratings = ContentRatings(imdb = 7.0)).toPosterUiModel().rating,
        )
        assertEquals(
            "КП 0",
            catalogItem.copy(ratings = ContentRatings(kinopoisk = 0.0)).toPosterUiModel().rating,
        )
    }

    @Test
    fun `details displays the site duration for movies and series`() {
        listOf(ContentType.MOVIE, ContentType.SERIES, ContentType.ANIME).forEach { type ->
            val details = ContentDetails(
                catalogItem = catalogItem.copy(type = type),
                description = "Описание",
                durationMinutes = 127,
            ).toDetailsUiModel(playbackAvailable = false, statusMessage = null)

            assertEquals("127 мин", details.duration)
        }
    }

    @Test
    fun `details without site metadata keeps ratings and duration absent`() {
        val details = ContentDetails(catalogItem, "Описание")
            .toDetailsUiModel(playbackAvailable = true, statusMessage = null)

        assertNull(details.duration)
        assertNull(details.episodeInfo)
        assertEquals("", details.rating)
    }

    @Test
    fun `season and episode label comes only from Kinogo metadata`() {
        val siteLabel = "1-4 сезон 1-20 серия"
        val item = catalogItem.copy(type = ContentType.SERIES, episodeBadge = siteLabel)
        val details = ContentDetails(item, "Описание")
            .toDetailsUiModel(playbackAvailable = true, statusMessage = null)

        assertEquals(siteLabel, item.toPosterUiModel().episodeBadge)
        assertNull(item.toPosterUiModel().badge)
        assertEquals(siteLabel, details.episodeInfo)
        assertNull(catalogItem.copy(episodeBadge = " ").toPosterUiModel().episodeBadge)
    }

    @Test
    fun `details and poster preserve the same site ratings`() {
        val details = ContentDetails(
            catalogItem.copy(ratings = ContentRatings(kinopoisk = 8.0, imdb = 7.7)),
            "Описание",
        ).toDetailsUiModel(playbackAvailable = true, statusMessage = null)

        assertEquals("КП 8   IMDb 7.7", details.rating)
    }

    @Test
    fun `keeps poster URL when mapping a catalog card`() {
        val item = CatalogItem(
            id = "film-42",
            relativePath = "/film/42",
            title = "Film 42",
            posterUrl = "https://cdn.example.org/film-42.webp",
            year = 2026,
            type = ContentType.MOVIE,
        )

        assertEquals(item.posterUrl, item.toPosterUiModel().posterUrl)
    }

    @Test
    fun `keeps poster URL when mapping content details`() {
        val item = CatalogItem(
            id = "film-42",
            relativePath = "/film/42",
            title = "Film 42",
            posterUrl = "https://cdn.example.org/film-42.webp",
        )

        val details = ContentDetails(
            catalogItem = item,
            description = "Description",
        ).toDetailsUiModel(
            playbackAvailable = true,
            statusMessage = null,
        )

        assertEquals(item.posterUrl, details.posterUrl)
    }

    @Test
    fun `removes Russian and Ukrainian quality labels from poster badges`() {
        assertEquals("WEB-DL 720", "Качество: WEB-DL 720".normalizedQualityBadge())
        assertEquals("BD-Rip", "  Якість  BD-Rip ".normalizedQualityBadge())
        assertEquals("WEBRip 1080p", "якість:  WEBRip 1080p".normalizedQualityBadge())
    }

    @Test
    fun `keeps a bare quality value and drops an empty label`() {
        assertEquals("WEB-DL 1080p", "WEB-DL 1080p".normalizedQualityBadge())
        assertNull("Качество: ".normalizedQualityBadge())
        assertNull(null.normalizedQualityBadge())
    }
}
