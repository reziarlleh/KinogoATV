package com.kinogo.atv

import com.kinogo.atv.data.catalog.ParsedContentPage
import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import com.kinogo.atv.data.history.PlaybackProgressCollection
import com.kinogo.atv.data.playback.DirectMediaResolver
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentType
import com.kinogo.atv.domain.PlaybackSelection
import com.kinogo.atv.domain.WatchProgress
import com.kinogo.atv.player.ui.PlaybackSourceRefreshRequest
import com.kinogo.atv.player.ui.PlaybackSourceRefreshUnitKey
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import com.kinogo.atv.ui.screens.DetailsFocusTarget
import com.kinogo.atv.ui.screens.detailsFocusTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KinogoAppRootResumeTest {
    @Test
    fun `direct plan persists resolver id instead of untrusted provider metadata`() = runTest {
        val resolver = DirectMediaResolver { }
        val plan = resolveFreshDirectPlan(
            resolver = resolver,
            contentId = CONTENT_ID,
            documentOrigin = "https://mirror.example",
            documentUrl = "https://mirror.example/film/content-42.html",
            candidates = listOf(
                PlayerEmbedCandidate(
                    url = "https://mirror.example/media/master.m3u8?token=short-lived",
                    label = "Прямой источник",
                    providerId = "mirror.example/session-token",
                ),
            ),
            voiceover = "По умолчанию",
            quality = "Авто",
        )

        assertEquals(resolver.id, requireNotNull(plan).defaultSourceId)
        assertEquals("direct-media", plan.defaultSourceId)
    }

    @Test
    fun `checkpoint writes retain callback order before immediate continue`() = runTest {
        val queue = PlaybackCheckpointWriteQueue()
        val firstMayFinish = CompletableDeferred<Unit>()
        val writes = mutableListOf<Int>()

        queue.enqueue(this) {
            firstMayFinish.await()
            writes += 1
        }
        queue.enqueue(this) { writes += 2 }
        firstMayFinish.complete(Unit)

        queue.awaitIdle()

        assertEquals(listOf(1, 2), writes)
    }

    @Test
    fun `history deletion queued after checkpoint cannot be resurrected`() = runTest {
        val queue = PlaybackCheckpointWriteQueue()
        var entries = emptyList<WatchProgress>()
        val checkpoint = progress(
            season = 1,
            episode = 2,
            positionMs = 42_000L,
            durationMs = 2_700_000L,
            updatedAt = 10L,
        )

        queue.enqueue(this) {
            entries = PlaybackProgressCollection.upsert(entries, checkpoint)
        }
        queue.enqueue(this) {
            entries = PlaybackProgressCollection.deleteContent(entries, CONTENT_ID)
        }
        queue.awaitIdle()

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `disposed player generation cannot publish checkpoint or exit again`() {
        assertTrue(acceptsPlaybackCheckpoint(activeGeneration = 7L, callbackGeneration = 7L))
        assertFalse(acceptsPlaybackCheckpoint(activeGeneration = 8L, callbackGeneration = 7L))
        assertFalse(acceptsPlaybackCheckpoint(activeGeneration = null, callbackGeneration = 7L))
    }

    @Test
    fun `new checkpoint remains newest after wall clock rollback`() {
        val futureStored = progress(
            season = 1,
            episode = 4,
            positionMs = 120_000L,
            durationMs = 2_700_000L,
            updatedAt = 10_000L,
        )

        assertEquals(
            10_001L,
            monotonicPlaybackCheckpointTimestamp(
                nowMs = 5_000L,
                previousTimestampMs = 9_000L,
                entries = listOf(futureStored),
            ),
        )
    }

    @Test
    fun `fresh playback page keeps returned details action enabled and focused`() {
        val details = ParsedContentPage(
            catalogItem = CatalogItem(
                id = CONTENT_ID,
                relativePath = "/serialy/content-42.html",
                title = "Series",
                year = 2025,
                type = ContentType.SERIES,
            ),
            description = "Description",
            countries = emptyList(),
            genres = listOf("Drama"),
            directors = emptyList(),
            cast = emptyList(),
            durationMinutes = null,
            metadata = mapOf("Перевод" to "Dub", "Качество" to "1080p"),
            playerEmbeds = listOf(
                PlayerEmbedCandidate(
                    url = "https://cinemar.cc/embed/42",
                    label = "Смотреть онлайн",
                ),
            ),
        ).toPlaybackDetailsUiModel()

        assertTrue(details.playbackAvailable)
        assertEquals(DetailsFocusTarget.PLAYBACK, detailsFocusTarget(details.playbackAvailable))
    }

    @Test
    fun `successful preparation enables returned details even for conservative source card`() {
        val conservative = ParsedContentPage(
            catalogItem = CatalogItem(
                id = CONTENT_ID,
                relativePath = "/serialy/content-42.html",
                title = "Series",
                year = null,
                type = ContentType.SERIES,
            ),
            description = "Description",
            countries = emptyList(),
            genres = emptyList(),
            directors = emptyList(),
            cast = emptyList(),
            durationMinutes = null,
            metadata = emptyMap(),
            playerEmbeds = emptyList(),
        ).toPlaybackDetailsUiModel()

        assertFalse(conservative.playbackAvailable)

        val prepared = conservative.withPreparedPlaybackAvailability(
            nativePlanReady = true,
            webFallbackReady = false,
        )

        assertTrue(prepared.playbackAvailable)
        assertEquals("Нативный источник готов к воспроизведению", prepared.statusMessage)
        assertEquals(DetailsFocusTarget.PLAYBACK, detailsFocusTarget(prepared.playbackAvailable))
    }

    @Test
    fun `failed refresh preserves previously confirmed playback retry action`() {
        val conservative = ParsedContentPage(
            catalogItem = CatalogItem(
                id = CONTENT_ID,
                relativePath = "/serialy/content-42.html",
                title = "Series",
                year = null,
                type = ContentType.SERIES,
            ),
            description = "Description",
            countries = emptyList(),
            genres = emptyList(),
            directors = emptyList(),
            cast = emptyList(),
            durationMinutes = null,
            metadata = emptyMap(),
            playerEmbeds = emptyList(),
        ).toPlaybackDetailsUiModel()
        val previouslyPrepared = conservative.withPreparedPlaybackAvailability(
            nativePlanReady = true,
            webFallbackReady = false,
        )

        val failedRefresh = conservative
            .preserveConfirmedPlaybackAvailability(previouslyPrepared)
            .withPlaybackPreparationFailure()

        assertTrue(failedRefresh.playbackAvailable)
        assertEquals(
            "Источник временно недоступен. Нажмите «Смотреть» для повторного поиска",
            failedRefresh.statusMessage,
        )
        assertEquals(
            DetailsFocusTarget.PLAYBACK,
            detailsFocusTarget(failedRefresh.playbackAvailable),
        )
    }

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
    fun `resume mapping preserves the non-default playback source`() {
        val selected = PlaybackSelectionUiModel(
            contentId = CONTENT_ID,
            season = 3,
            episode = 7,
            voiceover = "voice",
            quality = "1080p",
            resume = true,
            sourceId = "collaps",
        )

        val restored = selected.toDomainSelection().toUiSelection(resume = true)

        assertEquals(selected, restored)
    }

    @Test
    fun `catalog and search resume newest active unfinished unit`() {
        val completedDefault = progress(
            season = 1,
            episode = 1,
            positionMs = 2_700_000L,
            durationMs = 3_000_000L,
            updatedAt = 150L,
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
    fun `newest completed unit never falls back to an older unfinished episode`() {
        val olderUnfinished = progress(
            season = 1,
            episode = 2,
            positionMs = 420_000L,
            durationMs = 2_700_000L,
            updatedAt = 100L,
        )
        val latestCompleted = progress(
            season = 2,
            episode = 8,
            positionMs = 2_700_000L,
            durationMs = 2_700_000L,
            updatedAt = 200L,
            ended = true,
        )

        assertNull(
            preferredResumeProgress(
                entries = listOf(olderUnfinished, latestCompleted),
                contentId = CONTENT_ID,
            ),
        )
    }

    @Test
    fun `exit checkpoint selects latest unfinished episode across many stored units`() {
        val oldFirst = progress(
            season = 1,
            episode = 1,
            positionMs = 720_000L,
            durationMs = 2_700_000L,
            updatedAt = 100L,
        )
        val completedPrevious = progress(
            season = 2,
            episode = 4,
            positionMs = 2_700_000L,
            durationMs = 2_700_000L,
            updatedAt = 300L,
            ended = true,
        )
        val finalExit = progress(
            season = 2,
            episode = 5,
            positionMs = 648_000L,
            durationMs = 2_700_000L,
            updatedAt = 400L,
        )
        val unrelatedNewer = finalExit.copy(
            selection = finalExit.selection.copy(contentId = "another-content"),
            updatedAtEpochMs = 500L,
        )

        val selected = preferredResumeProgress(
            listOf(completedPrevious, unrelatedNewer, oldFirst, finalExit),
            CONTENT_ID,
        )

        assertEquals(finalExit, selected)
        assertEquals("episode-5", requireNotNull(selected).selection.episodeId)
    }

    @Test
    fun `newly activated zero-position episode wins over older unfinished episode`() {
        val older = progress(
            season = 2,
            episode = 5,
            positionMs = 648_000L,
            durationMs = 2_700_000L,
            updatedAt = 400L,
        )
        val activated = progress(
            season = 2,
            episode = 6,
            positionMs = 0L,
            durationMs = 2_700_000L,
            updatedAt = 401L,
        )

        val selected = preferredResumeProgress(listOf(older, activated), CONTENT_ID)

        assertEquals(activated, selected)
        assertEquals("Продолжить S02E06", resumeActionLabel(requireNotNull(selected)))
        assertEquals(0L, selected.resumePositionMs() ?: 0L)
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
