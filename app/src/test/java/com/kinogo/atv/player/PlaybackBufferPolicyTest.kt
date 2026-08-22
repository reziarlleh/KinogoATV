package com.kinogo.atv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBufferPolicyTest {
    @Test
    fun `all exposed TV buffer options map to exact Media3 target durations`() {
        val targets = listOf(5, 10, 15, 20, 30).map { seconds ->
            PlaybackBufferPolicy.forSeconds(seconds).targetBufferMs
        }

        assertEquals(listOf(5_000, 10_000, 15_000, 20_000, 30_000), targets)
    }

    @Test
    fun `invalid stored buffer value falls back to fifteen seconds`() {
        assertEquals(15_000, PlaybackBufferPolicy.forSeconds(-1).targetBufferMs)
        assertEquals(15_000, PlaybackBufferPolicy.forSeconds(60).targetBufferMs)
    }

    @Test
    fun `playback thresholds never exceed selected target buffer`() {
        listOf(5, 10, 15, 20, 30).forEach { seconds ->
            val config = PlaybackBufferPolicy.forSeconds(seconds)

            assertTrue(config.playbackStartBufferMs <= config.targetBufferMs)
            assertTrue(config.rebufferStartBufferMs <= config.targetBufferMs)
            assertEquals(config.rebufferStartBufferMs, config.nextEpisodePreloadMs)
        }
    }

    @Test
    fun `rebuffer recovery is bounded while initial fill remains more tolerant`() {
        assertEquals(20_000L, PlaybackBufferPolicy.forSeconds(5).initialBufferingRecoveryMs)
        assertEquals(30_000L, PlaybackBufferPolicy.forSeconds(30).initialBufferingRecoveryMs)
        assertEquals(5_000L, PlaybackBufferPolicy.forSeconds(5).rebufferingRecoveryMs)
        assertEquals(10_000L, PlaybackBufferPolicy.forSeconds(30).rebufferingRecoveryMs)
    }

    @Test
    fun `next episode preload stays disabled until current end is buffered`() {
        assertTrue(
            !PlaybackBufferPolicy.shouldArmNextEpisodePreload(
                isEpisodic = true,
                autoNextEpisode = true,
                currentMediaItemIndex = 0,
                mediaItemCount = 2,
                durationMs = 40 * 60_000L,
                currentPositionMs = 39 * 60_000L,
                bufferedPositionMs = 30_000L,
                preloadHorizonMs = 30_000L,
                playWhenReady = true,
                playbackSuppressed = false,
            ),
        )
        assertTrue(
            PlaybackBufferPolicy.shouldArmNextEpisodePreload(
                isEpisodic = true,
                autoNextEpisode = true,
                currentMediaItemIndex = 0,
                mediaItemCount = 2,
                durationMs = 40 * 60_000L,
                currentPositionMs = 40 * 60_000L - 30_000L,
                bufferedPositionMs = 40 * 60_000L -
                    PlaybackBufferPolicy.END_OF_ITEM_BUFFER_TOLERANCE_MS,
                preloadHorizonMs = 30_000L,
                playWhenReady = true,
                playbackSuppressed = false,
            ),
        )
    }

    @Test
    fun `next episode preload rejects unsafe or irrelevant contexts`() {
        assertTrue(
            !PlaybackBufferPolicy.shouldArmNextEpisodePreload(
                isEpisodic = false,
                autoNextEpisode = true,
                currentMediaItemIndex = 0,
                mediaItemCount = 2,
                durationMs = 60_000L,
                currentPositionMs = 55_000L,
                bufferedPositionMs = 60_000L,
                preloadHorizonMs = 10_000L,
                playWhenReady = true,
                playbackSuppressed = false,
            ),
        )
        assertTrue(
            !PlaybackBufferPolicy.shouldArmNextEpisodePreload(
                isEpisodic = true,
                autoNextEpisode = false,
                currentMediaItemIndex = 0,
                mediaItemCount = 2,
                durationMs = 60_000L,
                currentPositionMs = 55_000L,
                bufferedPositionMs = 60_000L,
                preloadHorizonMs = 10_000L,
                playWhenReady = true,
                playbackSuppressed = false,
            ),
        )
        assertTrue(
            !PlaybackBufferPolicy.shouldArmNextEpisodePreload(
                isEpisodic = true,
                autoNextEpisode = true,
                currentMediaItemIndex = 0,
                mediaItemCount = 2,
                durationMs = 0L,
                currentPositionMs = 0L,
                bufferedPositionMs = 60_000L,
                preloadHorizonMs = 10_000L,
                playWhenReady = true,
                playbackSuppressed = false,
            ),
        )
        assertTrue(
            !PlaybackBufferPolicy.shouldArmNextEpisodePreload(
                isEpisodic = true,
                autoNextEpisode = true,
                currentMediaItemIndex = 1,
                mediaItemCount = 2,
                durationMs = 60_000L,
                currentPositionMs = 55_000L,
                bufferedPositionMs = 60_000L,
                preloadHorizonMs = 10_000L,
                playWhenReady = true,
                playbackSuppressed = false,
            ),
        )
    }

    @Test
    fun `fully buffered item at start does not open next source early`() {
        assertTrue(
            !PlaybackBufferPolicy.shouldArmNextEpisodePreload(
                isEpisodic = true,
                autoNextEpisode = true,
                currentMediaItemIndex = 0,
                mediaItemCount = 2,
                durationMs = 40 * 60_000L,
                currentPositionMs = 0L,
                bufferedPositionMs = 40 * 60_000L,
                preloadHorizonMs = 30_000L,
                playWhenReady = true,
                playbackSuppressed = false,
            ),
        )
    }

    @Test
    fun `pause suppression and backward seek rearm barrier keep preload disabled`() {
        fun shouldArm(
            playWhenReady: Boolean = true,
            suppressed: Boolean = false,
            currentPositionMs: Long = 55_000L,
            minimumRearmPositionMs: Long = 0L,
        ) = PlaybackBufferPolicy.shouldArmNextEpisodePreload(
            isEpisodic = true,
            autoNextEpisode = true,
            currentMediaItemIndex = 0,
            mediaItemCount = 2,
            durationMs = 60_000L,
            currentPositionMs = currentPositionMs,
            bufferedPositionMs = 60_000L,
            preloadHorizonMs = 10_000L,
            playWhenReady = playWhenReady,
            playbackSuppressed = suppressed,
            minimumRearmPositionMs = minimumRearmPositionMs,
        )

        assertTrue(!shouldArm(playWhenReady = false))
        assertTrue(!shouldArm(suppressed = true))
        assertTrue(!shouldArm(currentPositionMs = 51_000L, minimumRearmPositionMs = 55_000L))
        assertTrue(shouldArm(currentPositionMs = 55_000L, minimumRearmPositionMs = 55_000L))
    }
}
