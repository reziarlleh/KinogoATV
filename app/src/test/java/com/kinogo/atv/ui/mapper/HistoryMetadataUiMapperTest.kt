package com.kinogo.atv.ui.mapper

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMetadataUiMapperTest {
    private val cached = CatalogItem(
        "42", "/serialy/42-series.html", "Сериал", type = ContentType.SERIES,
        episodeBadge = "1 сезон 2 серия",
    )

    @Test
    fun `pending and failed reads never display cached episode count`() {
        assertEquals("Проверяем серии…", historyEpisodeBadge(cached, null, false, true))
        assertEquals("Серии не проверены", historyEpisodeBadge(cached, null, true, true))
    }

    @Test
    fun `fresh site label replaces the persisted snapshot without deriving counts`() {
        assertEquals(
            "1-4 сезон 1-20 серия",
            historyEpisodeBadge(cached, cached.copy(episodeBadge = " 1-4 сезон 1-20 серия "), true, true),
        )
    }

    @Test
    fun `fresh missing metadata does not fall back to outdated label`() {
        assertNull(historyEpisodeBadge(cached, cached.copy(episodeBadge = null), true, true))
        assertNull(historyEpisodeBadge(cached, cached.copy(episodeBadge = " "), true, true))
    }

    @Test
    fun `films do not gain episode labels and unknown episodic checkpoints show status`() {
        assertNull(historyEpisodeBadge(cached.copy(type = ContentType.MOVIE, episodeBadge = null), null, false, false))
        assertEquals("Проверяем серии…", historyEpisodeBadge(null, null, false, true))
        assertEquals("Серии не проверены", historyEpisodeBadge(null, null, true, true))
    }
}
