package com.kinogo.atv.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPreloadTest {
    @Test
    fun `preload distance follows runtime adaptive column count`() {
        assertFalse(
            shouldPreloadCatalog(
                focusedIndex = 40,
                lastIndex = 49,
                columnCount = 4,
            ),
        )
        assertTrue(
            shouldPreloadCatalog(
                focusedIndex = 41,
                lastIndex = 49,
                columnCount = 4,
            ),
        )
        assertFalse(
            shouldPreloadCatalog(
                focusedIndex = 42,
                lastIndex = 49,
                columnCount = 3,
            ),
        )
        assertTrue(
            shouldPreloadCatalog(
                focusedIndex = 43,
                lastIndex = 49,
                columnCount = 3,
            ),
        )
    }

    @Test
    fun `short catalog preloads safely without negative threshold`() {
        assertTrue(
            shouldPreloadCatalog(
                focusedIndex = 0,
                lastIndex = 4,
                columnCount = 4,
            ),
        )
    }

    @Test
    fun `invalid indices never preload`() {
        assertFalse(shouldPreloadCatalog(-1, lastIndex = 9, columnCount = 4))
        assertFalse(shouldPreloadCatalog(0, lastIndex = -1, columnCount = 4))
    }
}
