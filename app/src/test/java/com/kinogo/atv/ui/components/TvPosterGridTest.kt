package com.kinogo.atv.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPosterGridTest {
    @Test
    fun `return restores the same poster by stable id`() {
        assertEquals(
            7,
            restoredPosterGridFocusIndex(
                preferredItemId = "selected",
                itemIds = listOf("0", "1", "2", "3", "4", "5", "6", "selected", "8"),
                requested = true,
            ),
        )
        assertEquals(
            null,
            restoredPosterGridFocusIndex(
                preferredItemId = "missing",
                itemIds = listOf("0", "1"),
                requested = true,
            ),
        )
    }

    @Test
    fun `recreated origin does not replace a non-first stable target with first poster`() {
        val itemIds = listOf("first", "1", "2", "3", "4", "5", "6", "selected")

        assertFalse(
            shouldRequestFirstPosterFocus(
                requestInitialFocus = true,
                preferredItemId = "selected",
                itemIds = itemIds,
            ),
        )
        assertTrue(
            shouldRequestFirstPosterFocus(
                requestInitialFocus = true,
                preferredItemId = "removed",
                itemIds = itemIds,
            ),
        )
    }

    @Test
    fun `append keeps an in-flight focus move when target remains present`() {
        assertFalse(
            shouldCancelPosterGridFocusMove(
                previousPagingKey = "novinki-desc",
                pagingKey = "novinki-desc",
                targetId = "42",
                itemIds = listOf("41", "42", "43", "44"),
            ),
        )
    }

    @Test
    fun `focus move is cancelled when feed changes or target disappears`() {
        assertTrue(
            shouldCancelPosterGridFocusMove(
                previousPagingKey = "novinki-desc",
                pagingKey = "drama-desc",
                targetId = "42",
                itemIds = listOf("42"),
            ),
        )
        assertTrue(
            shouldCancelPosterGridFocusMove(
                previousPagingKey = "novinki-desc",
                pagingKey = "novinki-desc",
                targetId = "42",
                itemIds = listOf("41", "43"),
            ),
        )
    }

    @Test
    fun `cancelled repeated move cannot clear a newer job for the same target`() {
        assertFalse(ownsPosterGridFocusMove(activeGeneration = 8L, moveGeneration = 7L))
        assertTrue(ownsPosterGridFocusMove(activeGeneration = 8L, moveGeneration = 8L))
    }

    @Test
    fun `internal directions move to the exact six-column neighbour`() {
        assertEquals(moveTo(6), decision(index = 0, key = Key.DirectionDown))
        assertEquals(moveTo(7), decision(index = 6, key = Key.DirectionRight))
        assertEquals(moveTo(6), decision(index = 7, key = Key.DirectionLeft))
        assertEquals(moveTo(1), decision(index = 7, key = Key.DirectionUp))
        assertEquals(moveTo(13), decision(index = 7, key = Key.DirectionDown))
    }

    @Test
    fun `left from first column and up from first row exit the grid`() {
        assertEquals(PosterGridNavigationDecision.Exit, decision(0, Key.DirectionLeft))
        assertEquals(PosterGridNavigationDecision.Exit, decision(6, Key.DirectionLeft))
        assertEquals(PosterGridNavigationDecision.Exit, decision(0, Key.DirectionUp))
        assertEquals(PosterGridNavigationDecision.Exit, decision(5, Key.DirectionUp))
    }

    @Test
    fun `right never wraps to the next row`() {
        assertEquals(PosterGridNavigationDecision.Stay, decision(5, Key.DirectionRight))
        assertEquals(PosterGridNavigationDecision.Stay, decision(11, Key.DirectionRight))
    }

    @Test
    fun `incomplete final row keeps focus when requested neighbour is absent`() {
        assertEquals(
            PosterGridNavigationDecision.Stay,
            posterGridNavigationDecision(
                index = 8,
                itemCount = 14,
                columns = COLUMNS,
                key = Key.DirectionDown,
            ),
        )
        assertEquals(
            moveTo(13),
            posterGridNavigationDecision(
                index = 7,
                itemCount = 14,
                columns = COLUMNS,
                key = Key.DirectionDown,
            ),
        )
        assertEquals(
            PosterGridNavigationDecision.Stay,
            posterGridNavigationDecision(
                index = 13,
                itemCount = 14,
                columns = COLUMNS,
                key = Key.DirectionRight,
            ),
        )
    }

    @Test
    fun `non-directional and invalid requests stay in place`() {
        assertEquals(PosterGridNavigationDecision.Stay, decision(7, Key.Enter))
        assertEquals(
            PosterGridNavigationDecision.Stay,
            posterGridNavigationDecision(-1, 18, COLUMNS, Key.DirectionRight),
        )
        assertEquals(
            PosterGridNavigationDecision.Stay,
            posterGridNavigationDecision(0, 18, 0, Key.DirectionRight),
        )
    }

    @Test
    fun `repeated center press invokes one long action and suppresses click on key up`() {
        val firstRepeat = posterLongPressDecision(
            key = Key.DirectionCenter,
            type = KeyEventType.KeyDown,
            repeatCount = 1,
            alreadyHandled = false,
        )
        val laterRepeat = posterLongPressDecision(
            key = Key.DirectionCenter,
            type = KeyEventType.KeyDown,
            repeatCount = 2,
            alreadyHandled = firstRepeat.longPressHandled,
        )
        val release = posterLongPressDecision(
            key = Key.DirectionCenter,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            alreadyHandled = laterRepeat.longPressHandled,
        )

        assertTrue(firstRepeat.invokeLongClick)
        assertTrue(firstRepeat.consume)
        assertFalse(laterRepeat.invokeLongClick)
        assertTrue(laterRepeat.consume)
        assertFalse(release.invokeLongClick)
        assertTrue(release.consume)
        assertFalse(release.longPressHandled)
    }

    @Test
    fun `short center press remains available to ordinary poster click`() {
        val press = posterLongPressDecision(
            key = Key.Enter,
            type = KeyEventType.KeyDown,
            repeatCount = 0,
            alreadyHandled = false,
        )
        val release = posterLongPressDecision(
            key = Key.Enter,
            type = KeyEventType.KeyUp,
            repeatCount = 0,
            alreadyHandled = press.longPressHandled,
        )

        assertFalse(press.consume)
        assertFalse(press.invokeLongClick)
        assertFalse(release.consume)
    }

    @Test
    fun `default preload keeps two loaded rows below focus`() {
        assertFalse(shouldPreloadPosterGrid(5, itemCount = 18, columns = COLUMNS))
        assertTrue(shouldPreloadPosterGrid(6, itemCount = 18, columns = COLUMNS))
        assertTrue(shouldPreloadPosterGrid(12, itemCount = 18, columns = COLUMNS))
        assertTrue(shouldPreloadPosterGrid(17, itemCount = 18, columns = COLUMNS))
    }

    @Test
    fun `preload row math handles incomplete final row`() {
        assertFalse(shouldPreloadPosterGrid(5, itemCount = 14, columns = COLUMNS))
        assertTrue(shouldPreloadPosterGrid(7, itemCount = 14, columns = COLUMNS))
        assertTrue(shouldPreloadPosterGrid(12, itemCount = 14, columns = COLUMNS))
        assertTrue(shouldPreloadPosterGrid(13, itemCount = 14, columns = COLUMNS))
        assertFalse(
            shouldPreloadPosterGrid(
                focusedIndex = 6,
                itemCount = 14,
                columns = COLUMNS,
                preloadRows = 1,
            ),
        )
    }

    @Test
    fun `invalid preload inputs never request another page`() {
        assertFalse(shouldPreloadPosterGrid(-1, 18, COLUMNS))
        assertFalse(shouldPreloadPosterGrid(18, 18, COLUMNS))
        assertFalse(shouldPreloadPosterGrid(0, 0, COLUMNS))
        assertFalse(shouldPreloadPosterGrid(0, 18, 0))
        assertFalse(shouldPreloadPosterGrid(0, 18, COLUMNS, preloadRows = 0))
    }

    @Test
    fun `offscreen move scrolls exactly one row instead of pinning target to top`() {
        assertEquals(
            6,
            posterGridScrollAnchor(
                firstVisibleItemIndex = 0,
                currentIndex = 12,
                targetIndex = 18,
                itemCount = 30,
                columns = COLUMNS,
            ),
        )
        assertEquals(
            0,
            posterGridScrollAnchor(
                firstVisibleItemIndex = 6,
                currentIndex = 6,
                targetIndex = 0,
                itemCount = 30,
                columns = COLUMNS,
            ),
        )
    }

    private fun decision(index: Int, key: Key): PosterGridNavigationDecision =
        posterGridNavigationDecision(
            index = index,
            itemCount = 18,
            columns = COLUMNS,
            key = key,
        )

    private fun moveTo(index: Int) = PosterGridNavigationDecision.Move(index)

    private companion object {
        const val COLUMNS = 6
    }
}
