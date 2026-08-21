package com.kinogo.atv.ui.components

import com.kinogo.atv.ui.model.TvDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class KinogoNavigationRailTest {
    @Test
    fun `brand exposes about action to dpad semantics`() {
        assertEquals("О программе", RAIL_ABOUT_CONTENT_DESCRIPTION)
    }

    @Test
    fun `every selected destination is its preferred rail focus target`() {
        TvDestination.entries.forEachIndexed { expectedIndex, selected ->
            assertEquals(
                expectedIndex,
                preferredRailFocusIndex(selected),
            )
        }
    }

    @Test
    fun `missing selected destination safely falls back to first rail item`() {
        assertEquals(
            0,
            preferredRailFocusIndex(
                selected = TvDestination.Settings,
                destinations = listOf(TvDestination.Home, TvDestination.Catalog),
            ),
        )
    }
}
