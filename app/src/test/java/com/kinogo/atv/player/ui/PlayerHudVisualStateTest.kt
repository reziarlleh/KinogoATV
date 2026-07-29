package com.kinogo.atv.player.ui

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerHudVisualStateTest {
    @Test
    fun `focused timeline marker stays fully inside the available width`() {
        assertEquals(
            6f,
            timelineMarkerCenterX(widthPx = 100f, radiusPx = 6f, progress = 0f),
            0.001f,
        )
        assertEquals(
            50f,
            timelineMarkerCenterX(widthPx = 100f, radiusPx = 6f, progress = 0.5f),
            0.001f,
        )
        assertEquals(
            94f,
            timelineMarkerCenterX(widthPx = 100f, radiusPx = 6f, progress = 1f),
            0.001f,
        )
    }

    @Test
    fun `timeline marker clamps invalid progress to the track ends`() {
        assertEquals(
            6f,
            timelineMarkerCenterX(widthPx = 100f, radiusPx = 6f, progress = -1f),
            0.001f,
        )
        assertEquals(
            94f,
            timelineMarkerCenterX(widthPx = 100f, radiusPx = 6f, progress = 2f),
            0.001f,
        )
    }

    @Test
    fun `only Media3 buffering state displays the buffering indicator`() {
        assertTrue(isPlayerBuffering(Player.STATE_BUFFERING))
        assertFalse(isPlayerBuffering(Player.STATE_IDLE))
        assertFalse(isPlayerBuffering(Player.STATE_READY))
        assertFalse(isPlayerBuffering(Player.STATE_ENDED))
    }
}
