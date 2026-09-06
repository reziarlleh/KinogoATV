package com.kinogo.atv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchProgressTest {
    @Test
    fun `every positive unfinished timestamp remains resumable`() {
        val progress = episodeProgress(37 * MINUTE, 40 * MINUTE)

        assertEquals(37 * MINUTE - 5_000L, progress.resumePositionMs())
    }

    @Test
    fun `explicit player end suppresses its terminal timestamp`() {
        val progress =
            movieProgress(positionMs = 1_000, durationMs = null).copy(playbackEnded = true)

        assertNull(progress.resumePositionMs())
    }

    @Test
    fun `resume rewinds five seconds and preserves playback choice`() {
        val progress = episodeProgress(positionMs = 15_000, durationMs = 40 * MINUTE)

        assertEquals(10_000L, progress.resumePositionMs())
        assertEquals("voice-2", progress.selection.voiceId)
        assertEquals("1080p", progress.selection.qualityId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative rewind is rejected`() {
        episodeProgress(positionMs = 15_000, durationMs = 40 * MINUTE)
            .resumePositionMs(resumeRewindMs = -1L)
    }

    private fun movieProgress(positionMs: Long, durationMs: Long?): WatchProgress =
        WatchProgress(
            selection =
                PlaybackSelection(
                    contentId = "movie-1",
                    voiceId = "voice-1",
                    qualityId = "auto",
                ),
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = 1,
        )

    private fun episodeProgress(positionMs: Long, durationMs: Long?): WatchProgress =
        WatchProgress(
            selection =
                PlaybackSelection(
                    contentId = "series-1",
                    seasonId = "season-1",
                    episodeId = "episode-3",
                    voiceId = "voice-2",
                    qualityId = "1080p",
                ),
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = 1,
        )

    private companion object {
        const val MINUTE = 60_000L
    }
}
