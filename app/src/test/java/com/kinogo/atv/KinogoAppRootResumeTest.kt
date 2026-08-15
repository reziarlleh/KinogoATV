package com.kinogo.atv

import com.kinogo.atv.domain.PlaybackSelection
import com.kinogo.atv.domain.WatchProgress
import com.kinogo.atv.player.ui.PlaybackSourceRefreshRequest
import com.kinogo.atv.player.ui.PlaybackSourceRefreshUnitKey
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KinogoAppRootResumeTest {
    @Test
    fun `automatic recovery launch persists consumed attempts and discards failed player`() {
        val previousUnit = refreshUnit(episode = 1)
        val failedUnit = refreshUnit(episode = 2)
        val recovery = PlaybackSourceRefreshRequest(
            selection = selection(season = 1, episode = 2),
            positionMs = 42_000L,
            attemptedUnits = setOf(failedUnit),
        )

        val safety = playbackLaunchSafety(
            currentAttempts = setOf(previousUnit),
            recovery = recovery,
        )

        assertEquals(setOf(previousUnit, failedUnit), safety.automaticSourceRefreshAttempts)
        assertTrue(safety.discardActivePlaybackOnExit)
    }

    @Test
    fun `ordinary preparation may return to player without changing refresh budget`() {
        val previousUnit = refreshUnit(episode = 1)

        val safety = playbackLaunchSafety(
            currentAttempts = setOf(previousUnit),
            recovery = null,
        )

        assertEquals(setOf(previousUnit), safety.automaticSourceRefreshAttempts)
        assertFalse(safety.discardActivePlaybackOnExit)
    }

    @Test
    fun `recovery prerequisite holes become explicit errors while normal launch stays unchanged`() {
        val recoverySafety = playbackLaunchSafety(
            currentAttempts = emptySet(),
            recovery = PlaybackSourceRefreshRequest(
                selection = selection(season = 1, episode = 2),
                positionMs = 42_000L,
                attemptedUnits = setOf(refreshUnit(episode = 2)),
            ),
        )
        val normalSafety = playbackLaunchSafety(
            currentAttempts = emptySet(),
            recovery = null,
        )

        assertEquals(
            "Не удалось обновить источник: карточка больше недоступна",
            recoverySafety.recoveryErrorFor(
                PlaybackRecoveryEarlyFailure.CONTENT_UNAVAILABLE,
            ),
        )
        assertEquals(
            "Не удалось обновить источник: нет активного проверенного зеркала",
            recoverySafety.recoveryErrorFor(
                PlaybackRecoveryEarlyFailure.MIRROR_UNAVAILABLE,
            ),
        )
        assertNull(
            normalSafety.recoveryErrorFor(
                PlaybackRecoveryEarlyFailure.CONTENT_UNAVAILABLE,
            ),
        )
        assertNull(
            normalSafety.recoveryErrorFor(
                PlaybackRecoveryEarlyFailure.MIRROR_UNAVAILABLE,
            ),
        )
    }

    @Test
    fun `automatic source refresh never applies old position to another episode`() {
        val requested = selection(season = 2, episode = 8)

        assertTrue(isSamePlaybackUnit(requested, selection(season = 2, episode = 8)))
        assertFalse(isSamePlaybackUnit(requested, selection(season = 3, episode = 1)))
        assertFalse(isSamePlaybackUnit(requested, selection(season = 2, episode = 9)))
    }

    @Test
    fun `catalog and search resume newest unfinished unit instead of completed default episode`() {
        val completedDefault = progress(
            season = 1,
            episode = 1,
            positionMs = 2_700_000L,
            durationMs = 3_000_000L,
            updatedAt = 300L,
            ended = true,
        )
        val latestUnfinished = progress(
            season = 3,
            episode = 7,
            positionMs = 1_062_000L,
            durationMs = 2_700_000L,
            updatedAt = 200L,
        )
        val olderUnfinished = progress(
            season = 2,
            episode = 4,
            positionMs = 600_000L,
            durationMs = 2_700_000L,
            updatedAt = 100L,
        )

        val selected = preferredResumeProgress(
            listOf(completedDefault, olderUnfinished, latestUnfinished),
            CONTENT_ID,
        )

        assertEquals(latestUnfinished, selected)
        assertEquals("Продолжить S03E07 с 17:42", resumeActionLabel(requireNotNull(selected)))
    }

    @Test
    fun `content with only completed checkpoints has no continue action`() {
        assertNull(
            preferredResumeProgress(
                listOf(
                    progress(
                        season = 1,
                        episode = 1,
                        positionMs = 100L,
                        durationMs = 1_000L,
                        updatedAt = 1L,
                        ended = true,
                    ),
                ),
                CONTENT_ID,
            ),
        )
    }

    private fun progress(
        season: Int,
        episode: Int,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long,
        ended: Boolean = false,
    ) = WatchProgress(
        selection = PlaybackSelection(
            contentId = CONTENT_ID,
            seasonId = "season-$season",
            episodeId = "episode-$episode",
            voiceId = "voice",
            qualityId = "720p",
        ),
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtEpochMs = updatedAt,
        playbackEnded = ended,
    )

    private fun selection(season: Int, episode: Int) = PlaybackSelectionUiModel(
        contentId = CONTENT_ID,
        season = season,
        episode = episode,
        voiceover = "voice",
        quality = "720p",
        resume = true,
    )

    private fun refreshUnit(episode: Int) = PlaybackSourceRefreshUnitKey(
        contentId = CONTENT_ID,
        season = 1,
        episode = episode,
    )

    private companion object {
        const val CONTENT_ID = "content-42"
    }
}
