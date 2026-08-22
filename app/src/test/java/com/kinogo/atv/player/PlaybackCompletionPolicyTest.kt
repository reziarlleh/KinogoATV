package com.kinogo.atv.player

import com.kinogo.atv.domain.PlaybackEpisodeCoordinate
import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCompletionPolicyTest {
    @Test
    fun `automatic playlist transition checkpoints and advances when auto next is enabled`() {
        assertEquals(
            PlaybackItemTransitionCompletion.CHECKPOINT_AND_ADVANCE,
            playbackItemTransitionCompletion(
                automaticTransition = true,
                autoNextEpisode = true,
            ),
        )
    }

    @Test
    fun `automatic playlist transition checkpoints and exits when auto next is disabled`() {
        assertEquals(
            PlaybackItemTransitionCompletion.CHECKPOINT_AND_EXIT,
            playbackItemTransitionCompletion(
                automaticTransition = true,
                autoNextEpisode = false,
            ),
        )
    }

    @Test
    fun `pause at end callback checkpoints and exits without a playlist transition`() {
        assertEquals(
            PlaybackPauseCompletion.CHECKPOINT_AND_EXIT,
            playbackPauseCompletion(
                playWhenReady = false,
                mediaItemEnded = true,
                autoNextEpisode = false,
            ),
        )
        assertEquals(
            PlaybackPauseCompletion.CHECKPOINT,
            playbackPauseCompletion(
                playWhenReady = false,
                mediaItemEnded = false,
                autoNextEpisode = false,
            ),
        )
        assertEquals(
            PlaybackPauseCompletion.IGNORE,
            playbackPauseCompletion(
                playWhenReady = true,
                mediaItemEnded = true,
                autoNextEpisode = false,
            ),
        )
    }

    @Test
    fun `end pause cannot exit while cross season auto next is enabled`() {
        assertEquals(
            PlaybackPauseCompletion.IGNORE,
            playbackPauseCompletion(
                playWhenReady = false,
                mediaItemEnded = true,
                autoNextEpisode = true,
            ),
        )
    }

    @Test
    fun `automatic source refresh is bounded to one request per prepared session`() {
        assertEquals(
            PlaybackErrorRecoveryDecision.REFRESH_SOURCES,
            playbackErrorRecoveryDecision(
                refreshCallbackAvailable = true,
                refreshAlreadyRequested = false,
            ),
        )
        assertEquals(
            PlaybackErrorRecoveryDecision.SHOW_ERROR,
            playbackErrorRecoveryDecision(
                refreshCallbackAvailable = true,
                refreshAlreadyRequested = true,
            ),
        )
        assertEquals(
            PlaybackErrorRecoveryDecision.SHOW_ERROR,
            playbackErrorRecoveryDecision(
                refreshCallbackAvailable = false,
                refreshAlreadyRequested = false,
            ),
        )
    }

    @Test
    fun `manual playlist transition does not look like natural completion`() {
        assertEquals(
            PlaybackItemTransitionCompletion.IGNORE,
            playbackItemTransitionCompletion(
                automaticTransition = false,
                autoNextEpisode = false,
            ),
        )
    }

    @Test
    fun `completed checkpoint uses known duration and never falls back to zero`() {
        assertEquals(
            CompletedPlaybackCheckpoint(positionMs = 90_000L, durationMs = 90_000L),
            completedPlaybackCheckpoint(
                lastPositionMs = 89_750L,
                lastDurationMs = 90_000L,
            ),
        )
        assertEquals(
            CompletedPlaybackCheckpoint(positionMs = 42_000L, durationMs = 42_000L),
            completedPlaybackCheckpoint(
                lastPositionMs = 42_000L,
                lastDurationMs = 0L,
            ),
        )
        assertEquals(
            CompletedPlaybackCheckpoint(positionMs = 1L, durationMs = 1L),
            completedPlaybackCheckpoint(
                lastPositionMs = 0L,
                lastDurationMs = 0L,
            ),
        )
    }

    @Test
    fun `auto next advances to first compatible episode of sparse next season`() {
        val plan = episodicPlan()

        assertEquals(
            PlaybackCompletionDecision.Advance(
                PlaybackEpisodeCoordinate(seasonNumber = 3, episodeNumber = 4),
            ),
            playbackCompletionDecision(
                mediaPlan = plan,
                sourceId = "provider",
                seasonNumber = 1,
                episodeNumber = 2,
                voiceover = "Дубляж",
                autoNextEpisode = true,
            ),
        )
    }

    @Test
    fun `completion exits when auto next is disabled`() {
        assertEquals(
            PlaybackCompletionDecision.Exit,
            playbackCompletionDecision(
                mediaPlan = episodicPlan(),
                sourceId = "provider",
                seasonNumber = 1,
                episodeNumber = 2,
                voiceover = "Дубляж",
                autoNextEpisode = false,
            ),
        )
    }

    @Test
    fun `film and final compatible episode return to details`() {
        val film = PlaybackMediaPlan(
            listOf(
                variant(
                    id = "film",
                    episode = null,
                    voiceover = "Дубляж",
                    season = null,
                ),
            ),
        )

        assertEquals(
            PlaybackCompletionDecision.Exit,
            playbackCompletionDecision(
                mediaPlan = film,
                sourceId = "provider",
                seasonNumber = null,
                episodeNumber = null,
                voiceover = "Дубляж",
                autoNextEpisode = true,
            ),
        )
        assertEquals(
            PlaybackCompletionDecision.Exit,
            playbackCompletionDecision(
                mediaPlan = episodicPlan(),
                sourceId = "provider",
                seasonNumber = 3,
                episodeNumber = 4,
                voiceover = "Дубляж",
                autoNextEpisode = true,
            ),
        )
    }

    private fun episodicPlan(): PlaybackMediaPlan = PlaybackMediaPlan(
        listOf(
            variant(
                id = "dub-s1-e2",
                episode = 2,
                voiceover = "Дубляж",
                season = 1,
            ),
            variant(
                id = "original-s2-e1",
                episode = 1,
                voiceover = "Оригинал",
                season = 2,
            ),
            variant(
                id = "dub-s3-e4",
                episode = 4,
                voiceover = "Дубляж",
                season = 3,
            ),
        ),
    )

    private fun variant(
        id: String,
        episode: Int?,
        voiceover: String,
        season: Int?,
    ): PlaybackMediaVariant = PlaybackMediaVariant(
        id = id,
        episodeNumber = episode,
        voiceover = voiceover,
        quality = "Авто",
        mediaUrl = "https://cdn.test/$id.m3u8",
        mimeType = "application/x-mpegURL",
        sourceId = "provider",
        sourceLabel = "Источник",
        seasonNumber = season,
    )
}
