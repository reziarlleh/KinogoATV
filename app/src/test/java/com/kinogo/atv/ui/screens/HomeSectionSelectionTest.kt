package com.kinogo.atv.ui.screens

import com.kinogo.atv.ui.model.HomeSectionUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionSelectionTest {
    private val item = PosterUiModel("1", "Фильм", "2026 • Фильм")

    @Test
    fun `home selects persisted history and new releases without relying on hero`() {
        val sections = listOf(
            HomeSectionUiModel("continue-persisted", "Продолжить просмотр", listOf(item)),
            HomeSectionUiModel("live-new", "Новинки", listOf(item.copy(id = "2"))),
            HomeSectionUiModel("live-series", "Сериалы", listOf(item.copy(id = "3"))),
        )

        assertEquals("continue-persisted", sections.historySectionOrNull()?.id)
        assertEquals("live-new", sections.newSectionOrNull("continue-persisted")?.id)
    }

    @Test
    fun `new section falls back to first non-history section`() {
        val sections = listOf(
            HomeSectionUiModel("history", "История просмотра", listOf(item)),
            HomeSectionUiModel("catalog", "Последние добавления", listOf(item.copy(id = "2"))),
        )

        assertEquals("catalog", sections.newSectionOrNull("history")?.id)
    }
}
