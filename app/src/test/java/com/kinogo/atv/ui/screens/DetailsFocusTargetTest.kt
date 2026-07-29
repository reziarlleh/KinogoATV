package com.kinogo.atv.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsFocusTargetTest {
    @Test
    fun unavailablePlaybackStartsOnBack() {
        assertEquals(DetailsFocusTarget.BACK, detailsFocusTarget(playbackAvailable = false))
    }

    @Test
    fun availablePlaybackMovesFocusToPlaybackAction() {
        assertEquals(DetailsFocusTarget.PLAYBACK, detailsFocusTarget(playbackAvailable = true))
    }
}
