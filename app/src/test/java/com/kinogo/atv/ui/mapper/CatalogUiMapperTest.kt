package com.kinogo.atv.ui.mapper

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogUiMapperTest {
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
