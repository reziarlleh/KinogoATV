package com.kinogo.atv.ui.screens

import com.kinogo.atv.ui.model.AppUpdateUiModel
import com.kinogo.atv.ui.model.AppUpdateUiPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateActionPresentationTest {
    @Test
    fun `checking keeps a stable non actionable update control`() {
        val presentation = AppUpdateUiModel(
            currentVersion = "0.5.4",
            phase = AppUpdateUiPhase.CHECKING,
            actionLabel = null,
            actionEnabled = false,
        ).stableActionPresentation()

        assertEquals("Проверяем…", presentation.label)
        assertFalse(presentation.actionable)
    }

    @Test
    fun `available update keeps the same control actionable`() {
        val presentation = AppUpdateUiModel(
            currentVersion = "0.5.4",
            phase = AppUpdateUiPhase.AVAILABLE,
            actionLabel = "Загрузить",
        ).stableActionPresentation()

        assertEquals("Загрузить", presentation.label)
        assertEquals("↓", presentation.leadingMark)
        assertTrue(presentation.primary)
        assertTrue(presentation.actionable)
    }
}
