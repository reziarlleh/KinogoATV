package com.kinogo.atv.player.ui

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudFocusRequestTest {
    @Test
    fun `focus request retries after a newly composed node is not ready`() = runTest {
        var attempts = 0
        var awaitedFrames = 0

        val focused = requestHudFocusWithRetry(
            requestFocus = {
                attempts += 1
                attempts == 2
            },
            awaitNextAttempt = { awaitedFrames += 1 },
        )

        assertTrue(focused)
        assertEquals(2, attempts)
        assertEquals(1, awaitedFrames)
    }
}
