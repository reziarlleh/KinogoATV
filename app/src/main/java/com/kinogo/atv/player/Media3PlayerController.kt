package com.kinogo.atv.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** Host-owned operations deliberately kept outside the Media3 adapter. */
interface Media3PlayerHost {
    fun onPlayerIntent(intent: PlayerIntent)

    fun onSaveProgressRequested()

    fun onEpisodeNumberRequested(episodeNumber: Int)

    fun onEpisodeBoundary(direction: EpisodeDirection)

    fun onExitRequested()

    /** Schedule delivery of PlayerIntent.Timeout(kind, deadlineMs) on the main thread. */
    fun scheduleTimeout(kind: PlayerTimeoutKind, deadlineMs: Long)
}

/**
 * Thin, Activity-free executor for reducer effects. Episode resolution, persistence and timeout
 * scheduling remain explicit integration responsibilities of [Media3PlayerHost].
 */
class Media3PlayerController(
    private val player: Player,
    private val host: Media3PlayerHost,
) {
    private var attached = false

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Logical play/pause follows user intent and must not flip to PAUSED while buffering.
            host.onPlayerIntent(PlayerIntent.PlaybackReported(playWhenReady))
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            host.onPlayerIntent(PlayerIntent.EpisodeTransitionFinished)
        }
    }

    fun attach() {
        if (attached) return
        attached = true
        player.addListener(listener)
        host.onPlayerIntent(PlayerIntent.PlaybackReported(player.playWhenReady))
    }

    fun detach() {
        if (!attached) return
        attached = false
        player.removeListener(listener)
    }

    fun execute(reduction: PlayerReduction) {
        reduction.effects.forEach(::execute)
    }

    fun execute(effect: PlayerEffect) {
        when (effect) {
            PlayerEffect.Play -> player.play()
            PlayerEffect.Pause -> player.pause()
            is PlayerEffect.SeekRelative -> seekRelative(effect.deltaMs)
            PlayerEffect.PreviousEpisode -> previousEpisode()
            PlayerEffect.NextEpisode -> nextEpisode()
            PlayerEffect.StopPlayback -> player.stop()
            PlayerEffect.SaveProgress -> host.onSaveProgressRequested()
            PlayerEffect.RequestExit -> host.onExitRequested()
            is PlayerEffect.SelectEpisodeNumber -> {
                host.onEpisodeNumberRequested(effect.episodeNumber)
            }
            is PlayerEffect.ScheduleTimeout -> {
                host.scheduleTimeout(effect.kind, effect.deadlineMs)
            }
        }
    }

    private fun seekRelative(deltaMs: Long) {
        val current = player.currentPosition.coerceAtLeast(0L)
        val unboundedTarget = saturatedAdd(current, deltaMs).coerceAtLeast(0L)
        val duration = player.duration
        val target = if (duration != C.TIME_UNSET && duration >= 0L) {
            unboundedTarget.coerceAtMost(duration)
        } else {
            unboundedTarget
        }
        player.seekTo(target)
    }

    private fun previousEpisode() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            host.onEpisodeBoundary(EpisodeDirection.PREVIOUS)
        }
    }

    private fun nextEpisode() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            host.onEpisodeBoundary(EpisodeDirection.NEXT)
        }
    }

    private fun saturatedAdd(value: Long, delta: Long): Long = when {
        delta > 0L && value > Long.MAX_VALUE - delta -> Long.MAX_VALUE
        delta < 0L && value < Long.MIN_VALUE - delta -> Long.MIN_VALUE
        else -> value + delta
    }
}
