package com.kinogo.atv.ui

import com.kinogo.atv.ui.model.HistoryUiModel
import com.kinogo.atv.ui.model.BookmarkUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingDetailsPosterTest {
    @Test
    fun `history poster provides immediate pending details fallback`() {
        val restoredPoster = PosterUiModel(
            id = "35182",
            title = "Иерихон",
            subtitle = "2006 • Сериал",
            posterUrl = "https://cdn.example.org/35182.webp",
        )
        val history = listOf(
            HistoryUiModel(
                id = "history-35182",
                poster = restoredPoster,
                episodeLabel = "Сезон 1, серия 21",
                positionLabel = "21 мин из 44 мин",
                lastWatchedLabel = "Сегодня",
                progress = 0.48f,
            ),
        )

        val result = pendingDetailsPoster(
            id = "35182",
            catalog = emptyList(),
            searchResults = emptyList(),
            favorites = emptyList(),
            bookmarks = emptyList(),
            history = history,
        )

        assertEquals(restoredPoster, result)
    }

    @Test
    fun `status-only bookmark provides immediate pending details fallback`() {
        val bookmarkPoster = PosterUiModel(
            id = "47001",
            title = "Сериал из закладок",
            subtitle = "2025 • Сериал",
        )

        val result = pendingDetailsPoster(
            id = bookmarkPoster.id,
            catalog = emptyList(),
            searchResults = emptyList(),
            favorites = emptyList(),
            bookmarks = listOf(BookmarkUiModel(poster = bookmarkPoster)),
            history = emptyList(),
        )

        assertEquals(bookmarkPoster, result)
    }
}
