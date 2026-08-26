package com.kinogo.atv.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.kinogo.atv.ui.model.HistoryUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `dialog consumes the release that opened it by remote long OK`() {
        val repeat = historyDialogActivationDecision(
            pendingRelease = true,
            key = Key.DirectionCenter,
            type = KeyEventType.KeyDown,
        )
        val release = historyDialogActivationDecision(
            pendingRelease = true,
            key = Key.DirectionCenter,
            type = KeyEventType.KeyUp,
        )

        assertTrue(repeat.consume)
        assertFalse(repeat.releaseConsumed)
        assertTrue(release.consume)
        assertTrue(release.releaseConsumed)
    }

    @Test
    fun `pointer long action leaves the next remote click untouched`() {
        val release = historyDialogActivationDecision(
            pendingRelease = false,
            key = Key.Enter,
            type = KeyEventType.KeyUp,
        )

        assertFalse(release.consume)
        assertFalse(release.releaseConsumed)
    }
}
