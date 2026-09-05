package com.kinogo.atv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressTest {
    @Test
    fun `long movie enters continue row after two minutes`() {
        val before = movieProgress(positionMs = 119_999, durationMs = 100 * MINUTE)
        val atThreshold = movieProgress(positionMs = 2 * MINUTE, durationMs = 100 * MINUTE)

        assertFalse(before.qualifiesForContinueWatching())
        assertTrue(atThreshold.qualifiesForContinueWatching())
    }

    @Test
    fun `short movie uses three percent when it is earlier`() {
        val duration = 30 * MINUTE
        assertFalse(movieProgress(53_999, duration).qualifiesForContinueWatching())
        assertTrue(movieProgress(54_000, duration).qualifiesForContinueWatching())
    }

    @Test
    fun `episode enters continue row after two minutes`() {
        assertFalse(episodeProgress(119_999, 40 * MINUTE).qualifiesForContinueWatching())
        assertTrue(episodeProgress(2 * MINUTE, 40 * MINUTE).qualifiesForContinueWatching())
    }

    @Test
    fun `episode completion needs percentage and three minute window`() {
        assertFalse(episodeProgress(36 * MINUTE, 40 * MINUTE).isCompleted())
        assertTrue(episodeProgress(37 * MINUTE, 40 * MINUTE).isCompleted())
    }

    @Test
    fun `near end exit remains an exact resume point without a player end signal`() {
        val progress = episodeProgress(37 * MINUTE, 40 * MINUTE)

        assertTrue(progress.isCompleted())
        assertEquals(37 * MINUTE - 5_000L, progress.resumePositionMs())
    }

    @Test
    fun `movie completion needs percentage and ten minute window`() {
        assertFalse(movieProgress(89 * MINUTE, 100 * MINUTE).isCompleted())
        assertTrue(movieProgress(90 * MINUTE, 100 * MINUTE).isCompleted())
    }

    @Test
    fun `explicit player end completes media with unknown duration`() {
        val progress =
            movieProgress(positionMs = 1_000, durationMs = null).copy(playbackEnded = true)

        assertTrue(progress.isCompleted())
        assertNull(progress.resumePositionMs())
    }

    @Test
    fun `resume rewinds five seconds and preserves playback choice`() {
        val progress = episodeProgress(positionMs = 15_000, durationMs = 40 * MINUTE)

        assertEquals(10_000L, progress.resumePositionMs())
        assertEquals("voice-2", progress.selection.voiceId)
        assertEquals("1080p", progress.selection.qualityId)
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
