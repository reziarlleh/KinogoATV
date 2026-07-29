package com.kinogo.atv.player

/**
 * Pure state reducer for the full-screen TV player. It has no Android, coroutine or Media3
 * dependency and can therefore be exhaustively unit-tested.
 */
class TvPlayerReducer(
    private val config: PlayerReducerConfig = PlayerReducerConfig(),
) {
    fun reduce(state: TvPlayerState, intent: PlayerIntent): PlayerReduction =
        when (intent) {
            is PlayerIntent.PrimaryAction -> primaryAction(state, intent.eventTimeMs)
            is PlayerIntent.TogglePlayback -> togglePlayback(state, intent.eventTimeMs)
            is PlayerIntent.Play -> setPlayback(state, playing = true, intent.eventTimeMs)
            is PlayerIntent.Pause -> setPlayback(state, playing = false, intent.eventTimeMs)
            is PlayerIntent.Seek -> seek(state, intent)
            is PlayerIntent.ShowHud -> showHud(state, intent.eventTimeMs)
            is PlayerIntent.OpenDrawer -> PlayerReduction(
                state.copy(
                    hud = PlayerHudState.VISIBLE,
                    hudHideDeadlineMs = null,
                    drawer = intent.drawer,
                    episodeNumberInput = EpisodeNumberInput.Empty,
                ),
            )
            is PlayerIntent.CloseDrawer -> closeDrawer(state, intent.eventTimeMs)
            is PlayerIntent.EpisodeDigit -> episodeDigit(state, intent)
            PlayerIntent.PreviousEpisode -> episodeTransition(state, EpisodeTransition.PREVIOUS)
            PlayerIntent.NextEpisode -> episodeTransition(state, EpisodeTransition.NEXT)
            PlayerIntent.Stop -> stop(state)
            PlayerIntent.Back -> back(state)
            is PlayerIntent.Timeout -> timeout(state, intent)
            is PlayerIntent.PlaybackReported -> PlayerReduction(
                state.copy(
                    playback = if (intent.isPlaying) {
                        PlayerPlaybackState.PLAYING
                    } else if (state.playback != PlayerPlaybackState.STOPPED) {
                        PlayerPlaybackState.PAUSED
                    } else {
                        PlayerPlaybackState.STOPPED
                    },
                    hudHideDeadlineMs = if (
                        !intent.isPlaying && state.hud == PlayerHudState.VISIBLE
                    ) {
                        null
                    } else {
                        state.hudHideDeadlineMs
                    },
                ),
            )
            PlayerIntent.EpisodeTransitionFinished -> PlayerReduction(
                state.copy(episodeTransition = null),
            )
        }

    private fun primaryAction(state: TvPlayerState, eventTimeMs: Long): PlayerReduction {
        if (state.episodeNumberInput.isActive) return commitEpisodeNumber(state)
        // A focused drawer owns D-pad center; media play/pause keys still bypass this guard.
        if (state.drawer != null) return PlayerReduction(state)
        // The first OK press is reserved for revealing the controls. This keeps an invisible
        // central play/pause button from being activated by the same event that opens the HUD.
        if (state.hud == PlayerHudState.HIDDEN) {
            return showHud(
                state.copy(hudFocusTarget = PlayerHudFocusTarget.PLAY_PAUSE),
                eventTimeMs,
            )
        }
        return togglePlayback(state, eventTimeMs)
    }

    private fun togglePlayback(state: TvPlayerState, eventTimeMs: Long): PlayerReduction =
        when (state.playback) {
            PlayerPlaybackState.PLAYING -> setPlayback(state, playing = false, eventTimeMs)
            PlayerPlaybackState.PAUSED,
            PlayerPlaybackState.STOPPED,
            -> setPlayback(state, playing = true, eventTimeMs)
        }

    private fun setPlayback(
        state: TvPlayerState,
        playing: Boolean,
        eventTimeMs: Long,
    ): PlayerReduction {
        val target = if (playing) PlayerPlaybackState.PLAYING else PlayerPlaybackState.PAUSED
        val playbackEffect = if (playing) PlayerEffect.Play else PlayerEffect.Pause
        val (shownState, hudEffects) = withVisibleHud(
            state.copy(
                playback = target,
                episodeNumberInput = EpisodeNumberInput.Empty,
                exitRequested = false,
            ),
            eventTimeMs,
        )
        return PlayerReduction(shownState, listOf(playbackEffect) + hudEffects)
    }

    private fun seek(state: TvPlayerState, intent: PlayerIntent.Seek): PlayerReduction {
        if (state.drawer != null && intent.source == RemoteKeySource.DPAD) {
            return PlayerReduction(state)
        }

        val deltaMs = config.seekStepMs * intent.direction.sign
        val previous = state.seekFeedback
        val accumulated = if (previous != null && intent.eventTimeMs <= previous.deadlineMs) {
            previous.accumulatedDeltaMs + deltaMs
        } else {
            deltaMs
        }
        val seekDeadline = intent.eventTimeMs + config.seekFeedbackTimeoutMs
        val (shownState, hudEffects) = withVisibleHud(
            state.copy(
                hudFocusTarget = if (intent.source == RemoteKeySource.DPAD) {
                    PlayerHudFocusTarget.TIMELINE
                } else {
                    state.hudFocusTarget
                },
                seekFeedback = SeekFeedback(accumulated, seekDeadline),
                episodeNumberInput = EpisodeNumberInput.Empty,
            ),
            intent.eventTimeMs,
        )
        return PlayerReduction(
            shownState,
            buildList {
                add(PlayerEffect.SeekRelative(deltaMs))
                add(PlayerEffect.ScheduleTimeout(PlayerTimeoutKind.SEEK_FEEDBACK, seekDeadline))
                addAll(hudEffects)
            },
        )
    }

    private fun showHud(state: TvPlayerState, eventTimeMs: Long): PlayerReduction {
        val stateWithInitialFocus = if (state.hud == PlayerHudState.HIDDEN) {
            state.copy(hudFocusTarget = PlayerHudFocusTarget.PLAY_PAUSE)
        } else {
            state
        }
        val (newState, effects) = withVisibleHud(stateWithInitialFocus, eventTimeMs)
        return PlayerReduction(newState, effects)
    }

    private fun closeDrawer(state: TvPlayerState, eventTimeMs: Long): PlayerReduction {
        if (state.drawer == null) return PlayerReduction(state)
        val (newState, effects) = withVisibleHud(
            state.copy(
                hudFocusTarget = state.drawer.returnFocusTarget(),
                drawer = null,
            ),
            eventTimeMs,
        )
        return PlayerReduction(newState, effects)
    }

    private fun episodeDigit(
        state: TvPlayerState,
        intent: PlayerIntent.EpisodeDigit,
    ): PlayerReduction {
        val current = state.episodeNumberInput
        val canAppend = current.isActive &&
            current.deadlineMs != null &&
            intent.eventTimeMs <= current.deadlineMs &&
            current.digits.length < config.maxEpisodeDigits
        val digits = if (canAppend) current.digits + intent.digit else intent.digit.toString()
        val deadline = intent.eventTimeMs + config.digitTimeoutMs
        return PlayerReduction(
            state.copy(
                hud = PlayerHudState.VISIBLE,
                hudHideDeadlineMs = null,
                episodeNumberInput = EpisodeNumberInput(digits, deadline),
            ),
            listOf(PlayerEffect.ScheduleTimeout(PlayerTimeoutKind.EPISODE_DIGITS, deadline)),
        )
    }

    private fun commitEpisodeNumber(state: TvPlayerState): PlayerReduction {
        val number = state.episodeNumberInput.digits.toIntOrNull()
        val cleared = state.copy(episodeNumberInput = EpisodeNumberInput.Empty)
        if (number == null || number <= 0) return PlayerReduction(cleared)
        return PlayerReduction(
            cleared.copy(episodeTransition = EpisodeTransition.DIRECT),
            listOf(
                PlayerEffect.SaveProgress,
                PlayerEffect.SelectEpisodeNumber(number),
            ),
        )
    }

    private fun episodeTransition(
        state: TvPlayerState,
        transition: EpisodeTransition,
    ): PlayerReduction {
        if (state.episodeTransition != null) return PlayerReduction(state)
        val effect = when (transition) {
            EpisodeTransition.PREVIOUS -> PlayerEffect.PreviousEpisode
            EpisodeTransition.NEXT -> PlayerEffect.NextEpisode
            EpisodeTransition.DIRECT -> error("Direct episode transitions use SelectEpisodeNumber")
        }
        return PlayerReduction(
            state.copy(
                episodeTransition = transition,
                episodeNumberInput = EpisodeNumberInput.Empty,
            ),
            listOf(PlayerEffect.SaveProgress, effect),
        )
    }

    private fun stop(state: TvPlayerState): PlayerReduction = PlayerReduction(
        state.copy(
            playback = PlayerPlaybackState.STOPPED,
            hud = PlayerHudState.HIDDEN,
            hudHideDeadlineMs = null,
            drawer = null,
            seekFeedback = null,
            episodeNumberInput = EpisodeNumberInput.Empty,
            episodeTransition = null,
            exitRequested = true,
        ),
        listOf(
            PlayerEffect.SaveProgress,
            PlayerEffect.StopPlayback,
            PlayerEffect.RequestExit,
        ),
    )

    private fun back(state: TvPlayerState): PlayerReduction = when {
        state.episodeNumberInput.isActive -> PlayerReduction(
            state.copy(episodeNumberInput = EpisodeNumberInput.Empty),
        )
        state.drawer != null -> PlayerReduction(
            state.copy(
                hudFocusTarget = state.drawer.returnFocusTarget(),
                drawer = null,
            ),
        )
        state.hud == PlayerHudState.VISIBLE -> PlayerReduction(
            state.copy(
                hud = PlayerHudState.HIDDEN,
                hudHideDeadlineMs = null,
                seekFeedback = null,
            ),
        )
        else -> PlayerReduction(
            state.copy(exitRequested = true),
            listOf(PlayerEffect.SaveProgress, PlayerEffect.RequestExit),
        )
    }

    private fun timeout(state: TvPlayerState, intent: PlayerIntent.Timeout): PlayerReduction =
        when (intent.kind) {
            PlayerTimeoutKind.HUD -> {
                if (state.hudHideDeadlineMs != intent.deadlineMs || state.drawer != null) {
                    PlayerReduction(state)
                } else {
                    PlayerReduction(
                        state.copy(
                            hud = PlayerHudState.HIDDEN,
                            hudHideDeadlineMs = null,
                        ),
                    )
                }
            }
            PlayerTimeoutKind.SEEK_FEEDBACK -> {
                if (state.seekFeedback?.deadlineMs != intent.deadlineMs) {
                    PlayerReduction(state)
                } else {
                    PlayerReduction(state.copy(seekFeedback = null))
                }
            }
            PlayerTimeoutKind.EPISODE_DIGITS -> {
                if (state.episodeNumberInput.deadlineMs != intent.deadlineMs) {
                    PlayerReduction(state)
                } else {
                    commitEpisodeNumber(state)
                }
            }
        }

    private fun withVisibleHud(
        state: TvPlayerState,
        eventTimeMs: Long,
    ): Pair<TvPlayerState, List<PlayerEffect>> {
        if (state.drawer != null || state.playback != PlayerPlaybackState.PLAYING) {
            return state.copy(hud = PlayerHudState.VISIBLE, hudHideDeadlineMs = null) to emptyList()
        }
        val deadline = eventTimeMs + config.hudTimeoutMs
        return state.copy(
            hud = PlayerHudState.VISIBLE,
            hudHideDeadlineMs = deadline,
        ) to listOf(PlayerEffect.ScheduleTimeout(PlayerTimeoutKind.HUD, deadline))
    }

    private fun PlayerDrawer.returnFocusTarget(): PlayerHudFocusTarget = when (this) {
        PlayerDrawer.SOURCE -> PlayerHudFocusTarget.SOURCE
        PlayerDrawer.SEASONS,
        PlayerDrawer.EPISODES,
        -> PlayerHudFocusTarget.SEASON
        PlayerDrawer.VOICEOVER -> PlayerHudFocusTarget.VOICEOVER
        PlayerDrawer.QUALITY -> PlayerHudFocusTarget.QUALITY
        PlayerDrawer.SUBTITLES -> PlayerHudFocusTarget.SUBTITLES
        PlayerDrawer.SETTINGS -> PlayerHudFocusTarget.PLAY_PAUSE
    }
}
