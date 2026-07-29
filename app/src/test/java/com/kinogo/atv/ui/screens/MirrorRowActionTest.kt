package com.kinogo.atv.ui.screens

import com.kinogo.atv.ui.model.MirrorStatusUi
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorRowActionTest {
    @Test
    fun `available mirror is selected directly`() {
        assertEquals(MirrorRowAction.Select, MirrorStatusUi.Available.rowAction())
    }

    @Test
    fun `non-selectable mirror opens details`() {
        listOf(
            MirrorStatusUi.Active,
            MirrorStatusUi.Quarantined,
            MirrorStatusUi.Error,
        ).forEach { status ->
            assertEquals(MirrorRowAction.ShowDetails, status.rowAction())
        }
    }
}
