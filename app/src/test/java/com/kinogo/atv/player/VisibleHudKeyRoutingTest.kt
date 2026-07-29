package com.kinogo.atv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleHudKeyRoutingTest {
    @Test
    fun `rapid seek is routed by root until timeline receives focus`() {
        assertTrue(
            shouldDispatchVisibleHudKeyAtRoot(
                rootFocused = true,
                focusTarget = PlayerHudFocusTarget.TIMELINE,
                episodeNumberInputActive = false,
                keyKind = VisibleHudKeyKind.SEEK,
            ),
        )

        assertFalse(
            shouldDispatchVisibleHudKeyAtRoot(
                rootFocused = false,
                focusTarget = PlayerHudFocusTarget.TIMELINE,
                episodeNumberInputActive = false,
                keyKind = VisibleHudKeyKind.SEEK,
            ),
        )
    }

    @Test
    fun `seek stays with Compose when play pause is the requested target`() {
        assertFalse(
            shouldDispatchVisibleHudKeyAtRoot(
                rootFocused = true,
                focusTarget = PlayerHudFocusTarget.PLAY_PAUSE,
                episodeNumberInputActive = false,
                keyKind = VisibleHudKeyKind.SEEK,
            ),
        )
    }

    @Test
    fun `primary keeps fast second OK and numeric episode commit behavior`() {
        assertTrue(
            shouldDispatchVisibleHudKeyAtRoot(
                rootFocused = true,
                focusTarget = PlayerHudFocusTarget.PLAY_PAUSE,
                episodeNumberInputActive = false,
                keyKind = VisibleHudKeyKind.PRIMARY,
            ),
        )
        assertTrue(
            shouldDispatchVisibleHudKeyAtRoot(
                rootFocused = false,
                focusTarget = PlayerHudFocusTarget.PLAY_PAUSE,
                episodeNumberInputActive = true,
                keyKind = VisibleHudKeyKind.PRIMARY,
            ),
        )
        assertFalse(
            shouldDispatchVisibleHudKeyAtRoot(
                rootFocused = true,
                focusTarget = PlayerHudFocusTarget.SOURCE,
                episodeNumberInputActive = false,
                keyKind = VisibleHudKeyKind.PRIMARY,
            ),
        )
    }
}
