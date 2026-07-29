package com.kinogo.atv.ui

import com.kinogo.atv.ui.model.TvDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoredTvDestinationTest {
    @Test
    fun `restores a destination that still exists`() {
        assertEquals(
            TvDestination.Catalog,
            restoredTvDestination("Catalog"),
        )
    }

    @Test
    fun `falls back when a saved destination no longer exists`() {
        assertEquals(
            TvDestination.Home,
            restoredTvDestination("RemovedDestination"),
        )
    }

    @Test
    fun `uses the supplied fallback for an invalid destination`() {
        assertEquals(
            TvDestination.Settings,
            restoredTvDestination("", TvDestination.Settings),
        )
    }
}
