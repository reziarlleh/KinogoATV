package com.kinogo.atv.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQualitySwitchGuardTest {
    private val current = PlaybackQualitySwitchContext(
        sourceId = "provider",
        voiceover = "Dub",
        seasonNumber = 2,
        episodeNumber = 4,
        currentMediaItemVariantId = "adaptive-s2e4",
        desiredQuality = "1080p",
        playlistGeneration = 7L,
        qualityGeneration = 3L,
    )

    @Test
    fun `deferred switch applies only to exact captured playback context`() {
        val request = PlaybackQualitySwitchRequest("fixed-s2e4-1080", current)

        assertTrue(request.isApplicableTo(current))
    }

    @Test
    fun `episode source voice tag and generations invalidate stale switch`() {
        val request = PlaybackQualitySwitchRequest("fixed-s2e4-1080", current)

        assertFalse(request.isApplicableTo(current.copy(episodeNumber = 5)))
        assertFalse(request.isApplicableTo(current.copy(sourceId = "backup")))
        assertFalse(request.isApplicableTo(current.copy(voiceover = "Original")))
        assertFalse(
            request.isApplicableTo(current.copy(currentMediaItemVariantId = "adaptive-new")),
        )
        assertFalse(request.isApplicableTo(current.copy(playlistGeneration = 8L)))
        assertFalse(request.isApplicableTo(current.copy(qualityGeneration = 4L)))
        assertFalse(request.isApplicableTo(current.copy(desiredQuality = "720p")))
    }

    @Test
    fun `already active target never schedules redundant playlist rebuild`() {
        val request = PlaybackQualitySwitchRequest(
            targetVariantId = current.currentMediaItemVariantId.orEmpty(),
            context = current,
        )

        assertFalse(request.isApplicableTo(current))
    }
}
