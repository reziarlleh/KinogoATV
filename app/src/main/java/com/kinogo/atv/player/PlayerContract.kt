package com.kinogo.atv.player

/** Tunable timings and navigation increments for a TV remote. */
data class PlayerReducerConfig(
    val seekStepMs: Long = 10_000L,
    val digitTimeoutMs: Long = 1_500L,
    val hudTimeoutMs: Long = 4_000L,
    val seekFeedbackTimeoutMs: Long = 800L,
    val maxEpisodeDigits: Int = 4,
) {
    init {
        require(seekStepMs > 0L)
        require(digitTimeoutMs > 0L)
        require(hudTimeoutMs > 0L)
        require(seekFeedbackTimeoutMs > 0L)
        require(maxEpisodeDigits > 0)
    }
}

enum class PlayerPlaybackState {
    PLAYING,
    PAUSED,
    STOPPED,
}

enum class PlayerHudState {
    HIDDEN,
    VISIBLE,
}

/** Control that should receive focus when the HUD becomes visible. */
enum class PlayerHudFocusTarget {
    PLAY_PAUSE,
    TIMELINE,
    SEASON,
    VOICEOVER,
    QUALITY,
    SUBTITLES,
    SOURCE,
}

enum class PlayerDrawer {
    SOURCE,
    SEASONS,
    EPISODES,
    VOICEOVER,
    QUALITY,
    SUBTITLES,
    SETTINGS,
}

enum class SeekDirection(val sign: Long) {
    BACKWARD(-1L),
    FORWARD(1L),
}

enum class RemoteKeySource {
    DPAD,
    MEDIA_KEY,
}

enum class EpisodeDirection {
    PREVIOUS,
    NEXT,
}

enum class EpisodeTransition {
    PREVIOUS,
    NEXT,
    DIRECT,
}

enum class PlayerTimeoutKind {
    HUD,
    SEEK_FEEDBACK,
    EPISODE_DIGITS,
}

data class SeekFeedback(
    val accumulatedDeltaMs: Long,
    val deadlineMs: Long,
)

data class EpisodeNumberInput(
    val digits: String = "",
    val deadlineMs: Long? = null,
) {
    val isActive: Boolean get() = digits.isNotEmpty()

    companion object {
        val Empty = EpisodeNumberInput()
    }
}

data class TvPlayerState(
    val playback: PlayerPlaybackState = PlayerPlaybackState.PAUSED,
    val hud: PlayerHudState = PlayerHudState.HIDDEN,
    val hudFocusTarget: PlayerHudFocusTarget = PlayerHudFocusTarget.PLAY_PAUSE,
    val hudHideDeadlineMs: Long? = null,
    val drawer: PlayerDrawer? = null,
    val seekFeedback: SeekFeedback? = null,
    val episodeNumberInput: EpisodeNumberInput = EpisodeNumberInput.Empty,
    val episodeTransition: EpisodeTransition? = null,
    val exitRequested: Boolean = false,
)

sealed interface PlayerIntent {
    /**
     * D-pad center/enter. Commits numeric episode input, reveals a hidden HUD, or invokes the
     * focused central play/pause action when the HUD is already visible.
     */
    data class PrimaryAction(val eventTimeMs: Long) : PlayerIntent

    /** Dedicated play/pause media key; it is never consumed by focused drawers. */
    data class TogglePlayback(val eventTimeMs: Long) : PlayerIntent

    data class Play(val eventTimeMs: Long) : PlayerIntent

    data class Pause(val eventTimeMs: Long) : PlayerIntent

    data class Seek(
        val direction: SeekDirection,
        val source: RemoteKeySource,
        val eventTimeMs: Long,
    ) : PlayerIntent

    data class ShowHud(val eventTimeMs: Long) : PlayerIntent

    data class OpenDrawer(val drawer: PlayerDrawer) : PlayerIntent

    data class CloseDrawer(val eventTimeMs: Long) : PlayerIntent

    data class EpisodeDigit(val digit: Int, val eventTimeMs: Long) : PlayerIntent {
        init {
            require(digit in 0..9)
        }
    }

    data object PreviousEpisode : PlayerIntent

    data object NextEpisode : PlayerIntent

    data object Stop : PlayerIntent

    data object Back : PlayerIntent

    data class Timeout(
        val kind: PlayerTimeoutKind,
        val deadlineMs: Long,
    ) : PlayerIntent

    /** Actual playback state reported by Media3. */
    data class PlaybackReported(val isPlaying: Boolean) : PlayerIntent

    /** Resolver/player reports completion or failure of an episode transition. */
    data object EpisodeTransitionFinished : PlayerIntent
}

sealed interface PlayerEffect {
    data object Play : PlayerEffect

    data object Pause : PlayerEffect

    data class SeekRelative(val deltaMs: Long) : PlayerEffect

    data object PreviousEpisode : PlayerEffect

    data object NextEpisode : PlayerEffect

    data object StopPlayback : PlayerEffect

    /** Persist the current episode, variant and position before a destructive action. */
    data object SaveProgress : PlayerEffect

    data object RequestExit : PlayerEffect

    data class SelectEpisodeNumber(val episodeNumber: Int) : PlayerEffect

    data class ScheduleTimeout(
        val kind: PlayerTimeoutKind,
        val deadlineMs: Long,
    ) : PlayerEffect
}

data class PlayerReduction(
    val state: TvPlayerState,
    val effects: List<PlayerEffect> = emptyList(),
)
