package com.kinogo.atv.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupViewsTest {
    @Test
    fun `native startup title uses current application name`() {
        assertEquals("KinogoATV", STARTUP_APP_TITLE)
    }
}
