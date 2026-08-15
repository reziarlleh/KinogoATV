package com.kinogo.atv.player.ui

import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackSourceRefreshTest {
    @Test
    fun `unit key resets for a new episode but not a remapped source`() {
        val original = selection(sourceId = "provider-a", quality = "1080p", episode = 2)
        val remapped = selection(sourceId = "provider-b", quality = "720p", episode = 2)
        val nextEpisode = selection(sourceId = "provider-b", quality = "720p", episode = 3)

        assertEquals(original.sourceRefreshUnitKey(), remapped.sourceRefreshUnitKey())
        assertNotEquals(original.sourceRefreshUnitKey(), nextEpisode.sourceRefreshUnitKey())
    }

    @Test
    fun `refresh request rejects a negative playback position`() {
        try {
            PlaybackSourceRefreshRequest(
                selection = selection(),
                positionMs = -1L,
                attemptedUnits = emptySet(),
            )
            throw AssertionError("Expected invalid refresh position")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun selection(
        sourceId: String = "provider-a",
        quality: String = "Авто",
        episode: Int = 2,
    ): PlaybackSelectionUiModel = PlaybackSelectionUiModel(
        contentId = "series",
        season = 1,
        episode = episode,
        voiceover = "Дубляж",
        quality = quality,
        resume = true,
        sourceId = sourceId,
    )
}
