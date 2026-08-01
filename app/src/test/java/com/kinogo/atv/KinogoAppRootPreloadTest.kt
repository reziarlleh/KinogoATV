package com.kinogo.atv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KinogoAppRootPreloadTest {
    @Test
    fun `home preloads until three poster rows are ready`() {
        assertTrue(shouldContinueHomeInitialPreload(10, loadedPage = 1, nextPage = 2))
        assertTrue(shouldContinueHomeInitialPreload(17, loadedPage = 2, nextPage = 3))
        assertFalse(shouldContinueHomeInitialPreload(18, loadedPage = 2, nextPage = 3))
        assertFalse(shouldContinueHomeInitialPreload(24, loadedPage = 2, nextPage = 3))
    }

    @Test
    fun `home preload stops without a strictly advancing next page`() {
        assertFalse(shouldContinueHomeInitialPreload(10, loadedPage = 1, nextPage = null))
        assertFalse(shouldContinueHomeInitialPreload(10, loadedPage = 2, nextPage = 2))
        assertFalse(shouldContinueHomeInitialPreload(10, loadedPage = 2, nextPage = 1))
        assertFalse(
            shouldContinueHomeInitialPreload(
                itemCount = 10,
                loadedPage = 1,
                nextPage = 2,
                columns = 0,
            ),
        )
    }

    @Test
    fun `catalog warmup waits until the home row reserve is complete`() {
        assertFalse(
            shouldStartDeferredCatalogPreload(
                homeItemCount = 10,
                loadedHomePage = 1,
                nextHomePage = 2,
                catalogItemCount = 0,
                catalogLoading = false,
            ),
        )
        assertTrue(
            shouldStartDeferredCatalogPreload(
                homeItemCount = 18,
                loadedHomePage = 2,
                nextHomePage = 3,
                catalogItemCount = 0,
                catalogLoading = false,
            ),
        )
        assertFalse(
            shouldStartDeferredCatalogPreload(
                homeItemCount = 18,
                loadedHomePage = 2,
                nextHomePage = 3,
                catalogItemCount = 1,
                catalogLoading = false,
            ),
        )
        assertFalse(
            shouldStartDeferredCatalogPreload(
                homeItemCount = 18,
                loadedHomePage = 2,
                nextHomePage = 3,
                catalogItemCount = 0,
                catalogLoading = true,
            ),
        )
    }
}
