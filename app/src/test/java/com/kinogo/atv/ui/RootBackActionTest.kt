package com.kinogo.atv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RootBackActionTest {
    @Test
    fun `back closes details before offering to exit`() {
        assertEquals(
            RootBackAction.CLOSE_DETAILS,
            rootBackAction(
                hasOpenDetails = true,
                exitConfirmationVisible = false,
            ),
        )
    }

    @Test
    fun `back on an application root asks for confirmation`() {
        assertEquals(
            RootBackAction.SHOW_EXIT_CONFIRMATION,
            rootBackAction(
                hasOpenDetails = false,
                exitConfirmationVisible = false,
            ),
        )
    }

    @Test
    fun `back on the confirmation keeps the application open`() {
        assertEquals(
            RootBackAction.DISMISS_EXIT_CONFIRMATION,
            rootBackAction(
                hasOpenDetails = false,
                exitConfirmationVisible = true,
            ),
        )
    }
}
