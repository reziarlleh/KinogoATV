package com.kinogo.atv.player.ui

import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackSourceRefreshTest {
    @Test
    fun `unit key resets for a new episode but not a remapped source`() {
        val original = selection(sourceId = "provider-a", quality = "1080p", episode = 2)
        val remapped = selection(sourceId = "provider-b", quality = "720p", episode = 2)
        val nextEpisode = selection(sourceId = "provider-b", quality = "720p", episode = 3)

        assertEquals(original.sourceRefreshUnitKey(), remapped.sourceRefreshUnitKey())
        assertNotEquals(original.sourceRefreshUnitKey(), nextEpisode.sourceRefreshUnitKey())
    }

    @Test
    fun `refresh request rejects a negative playback position`() {
        try {
            PlaybackSourceRefreshRequest(
                selection = selection(),
                positionMs = -1L,
                attemptedUnits = emptySet(),
            )
            throw AssertionError("Expected invalid refresh position")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `normal initial buffering does not refresh before its longer startup deadline`() {
        val watchdog = watchdog()

        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 0L, positionMs = 0L)),
        )
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 4_999L, positionMs = 0L)),
        )
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(buffering(nowMs = 5_000L, positionMs = 0L)),
        )
    }

    @Test
    fun `rebuffer after actual progress uses the shorter recovery deadline`() {
        val watchdog = watchdog()

        watchdog.observe(ready(nowMs = 0L, positionMs = 1_000L))
        watchdog.observe(ready(nowMs = 500L, positionMs = 1_500L))
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 2_499L, positionMs = 1_500L)),
        )
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(buffering(nowMs = 2_500L, positionMs = 1_500L)),
        )
    }

    @Test
    fun `ready state without position progress is treated as a stall`() {
        val watchdog = watchdog()

        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(ready(nowMs = 0L, positionMs = 10_000L)),
        )
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(ready(nowMs = 1_500L, positionMs = 10_000L)),
        )
    }

    @Test
    fun `pause and playback suppression never consume a recovery`() {
        val watchdog = watchdog()

        watchdog.observe(buffering(nowMs = 0L, positionMs = 2_000L))
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(
                buffering(nowMs = 6_000L, positionMs = 2_000L, playWhenReady = false),
            ),
        )
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(
                buffering(nowMs = 12_000L, positionMs = 2_000L, suppressed = true),
            ),
        )
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 12_001L, positionMs = 2_000L)),
        )
    }

    @Test
    fun `seek or playback movement restarts the no-progress window`() {
        val watchdog = watchdog()

        watchdog.observe(buffering(nowMs = 0L, positionMs = 2_000L))
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 4_900L, positionMs = 12_000L)),
        )
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 6_899L, positionMs = 12_000L)),
        )
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(buffering(nowMs = 6_900L, positionMs = 12_000L)),
        )
    }

    @Test
    fun `ended state does not look like a stalled player`() {
        val watchdog = watchdog()

        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(
                ended(nowMs = 0L, positionMs = 60_000L, durationMs = 60_000L),
            ),
        )
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(
                ended(nowMs = 10_000L, positionMs = 60_000L, durationMs = 60_000L),
            ),
        )
    }

    @Test
    fun `ready stall near known end still refreshes sources`() {
        val watchdog = watchdog()

        watchdog.observe(ready(nowMs = 0L, positionMs = 59_000L, durationMs = 60_000L))
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(
                ready(nowMs = 1_500L, positionMs = 59_000L, durationMs = 60_000L),
            ),
        )
    }

    @Test
    fun `rebuffer near known end still refreshes sources`() {
        val watchdog = watchdog()

        watchdog.observe(ready(nowMs = 0L, positionMs = 58_000L, durationMs = 60_000L))
        watchdog.observe(buffering(nowMs = 500L, positionMs = 59_000L, durationMs = 60_000L))
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(
                buffering(nowMs = 2_500L, positionMs = 59_000L, durationMs = 60_000L),
            ),
        )
    }

    @Test
    fun `watchdog emits once until a deliberate playback context reset`() {
        val watchdog = watchdog()

        watchdog.observe(buffering(nowMs = 0L, positionMs = 0L))
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(buffering(nowMs = 5_000L, positionMs = 0L)),
        )
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 10_000L, positionMs = 0L)),
        )

        watchdog.reset()
        watchdog.observe(buffering(nowMs = 10_001L, positionMs = 0L))
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(buffering(nowMs = 15_001L, positionMs = 0L)),
        )
    }

    @Test
    fun `clock regression starts a fresh observation window`() {
        val watchdog = watchdog()

        watchdog.observe(buffering(nowMs = 4_000L, positionMs = 0L))
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 1_000L, positionMs = 0L)),
        )
        assertEquals(
            PlaybackStallDecision.WAIT,
            watchdog.observe(buffering(nowMs = 5_999L, positionMs = 0L)),
        )
        assertEquals(
            PlaybackStallDecision.REFRESH_SOURCES,
            watchdog.observe(buffering(nowMs = 6_000L, positionMs = 0L)),
        )
    }

    private fun selection(
        sourceId: String = "provider-a",
        quality: String = "Авто",
        episode: Int = 2,
    ): PlaybackSelectionUiModel = PlaybackSelectionUiModel(
        contentId = "series",
        season = 1,
        episode = episode,
        voiceover = "Дубляж",
        quality = quality,
        resume = true,
        sourceId = sourceId,
    )

    private fun watchdog(): PlaybackStallWatchdog = PlaybackStallWatchdog(
        initialBufferingTimeoutMs = 5_000L,
        rebufferingTimeoutMs = 2_000L,
        readyNoProgressTimeoutMs = 1_500L,
        minimumProgressMs = 250L,
    )

    private fun buffering(
        nowMs: Long,
        positionMs: Long,
        playWhenReady: Boolean = true,
        suppressed: Boolean = false,
        durationMs: Long = 0L,
    ): PlaybackStallObservation = PlaybackStallObservation(
        nowMs = nowMs,
        playbackState = PlaybackStallState.BUFFERING,
        playWhenReady = playWhenReady,
        playbackSuppressed = suppressed,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    private fun ready(
        nowMs: Long,
        positionMs: Long,
        durationMs: Long = 0L,
    ): PlaybackStallObservation = PlaybackStallObservation(
        nowMs = nowMs,
        playbackState = PlaybackStallState.READY,
        playWhenReady = true,
        playbackSuppressed = false,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    private fun ended(
        nowMs: Long,
        positionMs: Long,
        durationMs: Long,
    ): PlaybackStallObservation = PlaybackStallObservation(
        nowMs = nowMs,
        playbackState = PlaybackStallState.ENDED,
        playWhenReady = true,
        playbackSuppressed = false,
        positionMs = positionMs,
        durationMs = durationMs,
    )
}
