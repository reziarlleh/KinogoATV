package com.kinogo.atv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlayerReducerTest {
    private val config = PlayerReducerConfig(
        seekStepMs = 10_000L,
        digitTimeoutMs = 1_500L,
        hudTimeoutMs = 4_000L,
        seekFeedbackTimeoutMs = 800L,
        maxEpisodeDigits = 4,
    )
    private val reducer = TvPlayerReducer(config)

    @Test
    fun `first center reveals hidden HUD without changing playback`() {
        val playing = reducer.reduce(
            TvPlayerState(
                playback = PlayerPlaybackState.PLAYING,
                hudFocusTarget = PlayerHudFocusTarget.TIMELINE,
            ),
            PlayerIntent.PrimaryAction(100L),
        )

        assertEquals(PlayerPlaybackState.PLAYING, playing.state.playback)
        assertEquals(PlayerHudState.VISIBLE, playing.state.hud)
        assertEquals(PlayerHudFocusTarget.PLAY_PAUSE, playing.state.hudFocusTarget)
        assertEquals(4_100L, playing.state.hudHideDeadlineMs)
        assertEquals(
            listOf(PlayerEffect.ScheduleTimeout(PlayerTimeoutKind.HUD, 4_100L)),
            playing.effects,
        )

        val paused = reducer.reduce(
            TvPlayerState(playback = PlayerPlaybackState.PAUSED),
            PlayerIntent.PrimaryAction(200L),
        )
        assertEquals(PlayerPlaybackState.PAUSED, paused.state.playback)
        assertEquals(PlayerHudState.VISIBLE, paused.state.hud)
        assertNull(paused.state.hudHideDeadlineMs)
        assertTrue(paused.effects.isEmpty())
    }

    @Test
    fun `second center on visible HUD toggles central play pause action`() {
        val playing = reducer.reduce(
            TvPlayerState(
                playback = PlayerPlaybackState.PAUSED,
                hud = PlayerHudState.VISIBLE,
                hudHideDeadlineMs = 4_000L,
            ),
            PlayerIntent.PrimaryAction(100L),
        )

        assertEquals(PlayerPlaybackState.PLAYING, playing.state.playback)
        assertEquals(PlayerEffect.Play, playing.effects.first())

        val paused = reducer.reduce(playing.state, PlayerIntent.PrimaryAction(200L))
        assertEquals(PlayerPlaybackState.PAUSED, paused.state.playback)
        assertEquals(PlayerEffect.Pause, paused.effects.first())
        assertNull(paused.state.hudHideDeadlineMs)
    }

    @Test
    fun `paused playback keeps HUD visible and invalidates an old hide timeout`() {
        val reportedPaused = reducer.reduce(
            TvPlayerState(
                playback = PlayerPlaybackState.PLAYING,
                hud = PlayerHudState.VISIBLE,
                hudHideDeadlineMs = 4_000L,
            ),
            PlayerIntent.PlaybackReported(isPlaying = false),
        )

        assertEquals(PlayerPlaybackState.PAUSED, reportedPaused.state.playback)
        assertNull(reportedPaused.state.hudHideDeadlineMs)

        val staleTimeout = reducer.reduce(
            reportedPaused.state,
            PlayerIntent.Timeout(PlayerTimeoutKind.HUD, 4_000L),
        )
        assertEquals(PlayerHudState.VISIBLE, staleTimeout.state.hud)
        assertTrue(staleTimeout.effects.isEmpty())
    }

    @Test
    fun `center is left to focused drawer while dedicated media key still toggles`() {
        val drawerState = TvPlayerState(
            playback = PlayerPlaybackState.PLAYING,
            hud = PlayerHudState.VISIBLE,
            drawer = PlayerDrawer.EPISODES,
        )

        val center = reducer.reduce(drawerState, PlayerIntent.PrimaryAction(100L))
        assertEquals(drawerState, center.state)
        assertTrue(center.effects.isEmpty())

        val mediaKey = reducer.reduce(drawerState, PlayerIntent.TogglePlayback(200L))
        assertEquals(PlayerPlaybackState.PAUSED, mediaKey.state.playback)
        assertEquals(PlayerDrawer.EPISODES, mediaKey.state.drawer)
        assertEquals(PlayerEffect.Pause, mediaKey.effects.first())
    }

    @Test
    fun `left and right seek by ten seconds and accumulate HUD feedback`() {
        val backward = reducer.reduce(
            TvPlayerState(playback = PlayerPlaybackState.PLAYING),
            PlayerIntent.Seek(SeekDirection.BACKWARD, RemoteKeySource.DPAD, 100L),
        )

        assertEquals(PlayerEffect.SeekRelative(-10_000L), backward.effects.first())
        assertEquals(PlayerHudState.VISIBLE, backward.state.hud)
        assertEquals(PlayerHudFocusTarget.TIMELINE, backward.state.hudFocusTarget)
        assertEquals(-10_000L, backward.state.seekFeedback?.accumulatedDeltaMs)
        assertEquals(900L, backward.state.seekFeedback?.deadlineMs)

        val forward = reducer.reduce(
            backward.state,
            PlayerIntent.Seek(SeekDirection.FORWARD, RemoteKeySource.DPAD, 200L),
        )
        assertEquals(PlayerEffect.SeekRelative(10_000L), forward.effects.first())
        assertEquals(0L, forward.state.seekFeedback?.accumulatedDeltaMs)

        val expired = reducer.reduce(
            forward.state,
            PlayerIntent.Timeout(PlayerTimeoutKind.SEEK_FEEDBACK, 1_000L),
        )
        assertNull(expired.state.seekFeedback)
    }

    @Test
    fun `D-pad seek selects timeline while media seek preserves current HUD focus`() {
        val playFocused = TvPlayerState(
            playback = PlayerPlaybackState.PLAYING,
            hud = PlayerHudState.VISIBLE,
            hudFocusTarget = PlayerHudFocusTarget.PLAY_PAUSE,
        )

        val mediaSeek = reducer.reduce(
            playFocused,
            PlayerIntent.Seek(SeekDirection.FORWARD, RemoteKeySource.MEDIA_KEY, 100L),
        )
        assertEquals(PlayerHudFocusTarget.PLAY_PAUSE, mediaSeek.state.hudFocusTarget)

        val dpadSeek = reducer.reduce(
            mediaSeek.state,
            PlayerIntent.Seek(SeekDirection.BACKWARD, RemoteKeySource.DPAD, 200L),
        )
        assertEquals(PlayerHudFocusTarget.TIMELINE, dpadSeek.state.hudFocusTarget)
    }

    @Test
    fun `HUD keepalive does not steal focus from active timeline`() {
        val timelineFocused = TvPlayerState(
            playback = PlayerPlaybackState.PLAYING,
            hud = PlayerHudState.VISIBLE,
            hudFocusTarget = PlayerHudFocusTarget.TIMELINE,
        )

        val keptAlive = reducer.reduce(timelineFocused, PlayerIntent.ShowHud(300L))

        assertEquals(PlayerHudFocusTarget.TIMELINE, keptAlive.state.hudFocusTarget)
        assertEquals(PlayerHudState.VISIBLE, keptAlive.state.hud)
    }

    @Test
    fun `drawer owns D-pad arrows while media seek remains global`() {
        val state = TvPlayerState(drawer = PlayerDrawer.QUALITY, hud = PlayerHudState.VISIBLE)

        val dpad = reducer.reduce(
            state,
            PlayerIntent.Seek(SeekDirection.FORWARD, RemoteKeySource.DPAD, 10L),
        )
        assertEquals(state, dpad.state)
        assertTrue(dpad.effects.isEmpty())

        val media = reducer.reduce(
            state,
            PlayerIntent.Seek(SeekDirection.FORWARD, RemoteKeySource.MEDIA_KEY, 10L),
        )
        assertEquals(PlayerEffect.SeekRelative(10_000L), media.effects.first())
    }

    @Test
    fun `closing selector restores focus to its invoking HUD control`() {
        val expectedTargets = mapOf(
            PlayerDrawer.SOURCE to PlayerHudFocusTarget.SOURCE,
            PlayerDrawer.SEASONS to PlayerHudFocusTarget.SEASON,
            PlayerDrawer.EPISODES to PlayerHudFocusTarget.SEASON,
            PlayerDrawer.VOICEOVER to PlayerHudFocusTarget.VOICEOVER,
            PlayerDrawer.QUALITY to PlayerHudFocusTarget.QUALITY,
            PlayerDrawer.SUBTITLES to PlayerHudFocusTarget.SUBTITLES,
        )

        expectedTargets.forEach { (drawer, expectedTarget) ->
            val opened = reducer.reduce(
                TvPlayerState(
                    playback = PlayerPlaybackState.PLAYING,
                    hud = PlayerHudState.VISIBLE,
                ),
                PlayerIntent.OpenDrawer(drawer),
            )

            val selected = reducer.reduce(
                opened.state,
                PlayerIntent.CloseDrawer(eventTimeMs = 100L),
            )
            assertEquals(expectedTarget, selected.state.hudFocusTarget)
            assertNull(selected.state.drawer)

            val backedOut = reducer.reduce(opened.state, PlayerIntent.Back)
            assertEquals(expectedTarget, backedOut.state.hudFocusTarget)
            assertNull(backedOut.state.drawer)
        }
    }

    @Test
    fun `digit timeout selects accumulated episode and ignores stale timer`() {
        val one = reducer.reduce(TvPlayerState(), PlayerIntent.EpisodeDigit(1, 100L))
        val twelve = reducer.reduce(one.state, PlayerIntent.EpisodeDigit(2, 500L))

        assertEquals("12", twelve.state.episodeNumberInput.digits)
        assertEquals(2_000L, twelve.state.episodeNumberInput.deadlineMs)

        val stale = reducer.reduce(
            twelve.state,
            PlayerIntent.Timeout(PlayerTimeoutKind.EPISODE_DIGITS, 1_600L),
        )
        assertEquals(twelve.state, stale.state)
        assertTrue(stale.effects.isEmpty())

        val committed = reducer.reduce(
            stale.state,
            PlayerIntent.Timeout(PlayerTimeoutKind.EPISODE_DIGITS, 2_000L),
        )
        assertFalse(committed.state.episodeNumberInput.isActive)
        assertEquals(EpisodeTransition.DIRECT, committed.state.episodeTransition)
        assertEquals(
            listOf(PlayerEffect.SaveProgress, PlayerEffect.SelectEpisodeNumber(12)),
            committed.effects,
        )
    }

    @Test
    fun `digit after timeout starts a fresh episode number`() {
        val old = reducer.reduce(TvPlayerState(), PlayerIntent.EpisodeDigit(1, 100L))
        val fresh = reducer.reduce(old.state, PlayerIntent.EpisodeDigit(7, 1_700L))

        assertEquals("7", fresh.state.episodeNumberInput.digits)
        assertEquals(3_200L, fresh.state.episodeNumberInput.deadlineMs)
    }

    @Test
    fun `center commits active episode number instead of toggling playback`() {
        val digits = TvPlayerState(
            playback = PlayerPlaybackState.PLAYING,
            episodeNumberInput = EpisodeNumberInput("24", 2_000L),
        )

        val result = reducer.reduce(digits, PlayerIntent.PrimaryAction(800L))

        assertEquals(PlayerPlaybackState.PLAYING, result.state.playback)
        assertEquals(EpisodeTransition.DIRECT, result.state.episodeTransition)
        assertEquals(PlayerEffect.SelectEpisodeNumber(24), result.effects.last())
    }

    @Test
    fun `back unwinds digits drawer HUD then exits`() {
        val initial = TvPlayerState(
            hud = PlayerHudState.VISIBLE,
            drawer = PlayerDrawer.EPISODES,
            episodeNumberInput = EpisodeNumberInput("3", 1_500L),
        )

        val noDigits = reducer.reduce(initial, PlayerIntent.Back)
        assertFalse(noDigits.state.episodeNumberInput.isActive)
        assertEquals(PlayerDrawer.EPISODES, noDigits.state.drawer)

        val noDrawer = reducer.reduce(noDigits.state, PlayerIntent.Back)
        assertNull(noDrawer.state.drawer)
        assertEquals(PlayerHudState.VISIBLE, noDrawer.state.hud)

        val noHud = reducer.reduce(noDrawer.state, PlayerIntent.Back)
        assertEquals(PlayerHudState.HIDDEN, noHud.state.hud)

        val exit = reducer.reduce(noHud.state, PlayerIntent.Back)
        assertTrue(exit.state.exitRequested)
        assertEquals(
            listOf(PlayerEffect.SaveProgress, PlayerEffect.RequestExit),
            exit.effects,
        )
    }

    @Test
    fun `stop saves stops and requests exit`() {
        val result = reducer.reduce(
            TvPlayerState(
                playback = PlayerPlaybackState.PLAYING,
                hud = PlayerHudState.VISIBLE,
                drawer = PlayerDrawer.VOICEOVER,
            ),
            PlayerIntent.Stop,
        )

        assertEquals(PlayerPlaybackState.STOPPED, result.state.playback)
        assertEquals(PlayerHudState.HIDDEN, result.state.hud)
        assertNull(result.state.drawer)
        assertTrue(result.state.exitRequested)
        assertEquals(
            listOf(
                PlayerEffect.SaveProgress,
                PlayerEffect.StopPlayback,
                PlayerEffect.RequestExit,
            ),
            result.effects,
        )
    }

    @Test
    fun `previous and next transitions are serialized until completion`() {
        val next = reducer.reduce(TvPlayerState(), PlayerIntent.NextEpisode)
        assertEquals(EpisodeTransition.NEXT, next.state.episodeTransition)
        assertEquals(
            listOf(PlayerEffect.SaveProgress, PlayerEffect.NextEpisode),
            next.effects,
        )

        val ignored = reducer.reduce(next.state, PlayerIntent.PreviousEpisode)
        assertEquals(next.state, ignored.state)
        assertTrue(ignored.effects.isEmpty())

        val ready = reducer.reduce(ignored.state, PlayerIntent.EpisodeTransitionFinished)
        assertNull(ready.state.episodeTransition)
    }
}
