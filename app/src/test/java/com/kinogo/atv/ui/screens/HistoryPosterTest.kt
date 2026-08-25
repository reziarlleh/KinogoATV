package com.kinogo.atv.ui.screens

import com.kinogo.atv.ui.model.HistoryUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPosterTest {
    @Test
    fun `history grid preserves stable content id and progress metadata`() {
        val history = HistoryUiModel(
            id = "history-35182",
            poster = PosterUiModel("35182", "Сериал", "2026 • Сериал"),
            episodeLabel = "Сезон 2, серия 5",
            positionLabel = "17 из 42 мин",
            lastWatchedLabel = "Сегодня",
            progress = 0.4f,
        )

        val poster = history.toHistoryPoster()

        assertEquals("35182", poster.id)
        assertEquals(0.4f, poster.progress)
        assertEquals(
            "Сезон 2, серия 5 • 17 из 42 мин • Сегодня",
            poster.subtitle,
        )
    }

    @Test
    fun `removal keeps focus at the same visual slot when possible`() {
        val ids = listOf("first", "selected", "next")

        assertEquals("next", preferredHistoryFocusAfterRemoval(ids, "selected"))
        assertEquals("selected", preferredHistoryFocusAfterRemoval(ids, "next"))
        assertEquals(null, preferredHistoryFocusAfterRemoval(listOf("selected"), "selected"))
    }
}
