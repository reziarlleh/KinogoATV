package com.kinogo.atv.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPreloadFailurePolicyTest {
    @Test
    fun `only armed immediately following window can be marked degraded`() {
        assertNotNull(
            PlaybackPreloadFailurePolicy.futureWindowOrNull(
                playlistGeneration = 4L,
                armedForWindowIndex = 2,
                currentWindowIndex = 2,
                eventWindowIndex = 3,
                eventVariantId = "s2e1-adaptive",
            ),
        )
        assertNull(
            PlaybackPreloadFailurePolicy.futureWindowOrNull(4L, 2, 2, 2, "current"),
        )
        assertNull(
            PlaybackPreloadFailurePolicy.futureWindowOrNull(4L, null, 2, 3, "future"),
        )
        assertNull(
            PlaybackPreloadFailurePolicy.futureWindowOrNull(4L, 2, 2, 4, "too-far"),
        )
    }

    @Test
    fun `terminal recovery requires exact window tag and playlist generation`() {
        val failure = PlaybackPreloadFailure(
            window = PlaybackWindowIdentity(7L, 3, "s2e1-adaptive"),
            terminal = true,
        )

        assertTrue(PlaybackPreloadFailurePolicy.matches(failure, 7L, 3, "s2e1-adaptive"))
        assertFalse(PlaybackPreloadFailurePolicy.matches(failure, 8L, 3, "s2e1-adaptive"))
        assertFalse(PlaybackPreloadFailurePolicy.matches(failure, 7L, 2, "s2e1-adaptive"))
        assertFalse(PlaybackPreloadFailurePolicy.matches(failure, 7L, 3, "other-variant"))
    }
}
