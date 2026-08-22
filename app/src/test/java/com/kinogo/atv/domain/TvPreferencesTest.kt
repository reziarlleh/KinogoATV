package com.kinogo.atv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPreferencesTest {
    @Test
    fun `stable option ids set dropdowns and switches without cycling`() {
        val selected = TvPreferences()
            .withSetting(TvSettingIds.QUALITY, "1080p")
            .withSetting(TvSettingIds.SEEK_STEP, "30")
            .withSetting(TvSettingIds.PLAYBACK_BUFFER, "20")
            .withSetting(TvSettingIds.SUBTITLES, "disabled")
            .withSetting(TvSettingIds.AUTO_NEXT_EPISODE, "false")
            .withSetting(TvSettingIds.AUTO_CHECK_UPDATES, "false")

        assertEquals(VideoQualityPreference.FULL_HD, selected.defaultQuality)
        assertEquals(30, selected.seekStepSeconds)
        assertEquals(20, selected.playbackBufferSeconds)
        assertEquals(SubtitlePreference.DISABLED, selected.subtitles)
        assertFalse(selected.autoNextEpisode)
        assertFalse(selected.autoCheckUpdates)
        assertEquals(selected, selected.withSetting(TvSettingIds.SEEK_STEP, "invalid"))
    }

    @Test
    fun `quality preference keeps canonical fixed intent until playback plan is known`() {
        assertEquals(
            "2160p",
            VideoQualityPreference.UHD.requestedLabel("Авто"),
        )
        assertEquals(
            "1080p",
            VideoQualityPreference.FULL_HD.requestedLabel("FullHD 1080"),
        )
        assertEquals(
            "480p",
            VideoQualityPreference.SD.requestedLabel("Авто"),
        )
        assertEquals(
            "720p",
            VideoQualityPreference.AUTO.requestedLabel("720p"),
        )
        assertEquals(
            "1080p",
            VideoQualityPreference.FULL_HD.requestedLabel("Авто"),
        )
        assertEquals(
            "1080p",
            VideoQualityPreference.FULL_HD.requestedLabel("2160p"),
        )
    }

    @Test
    fun `subtitle mode follows system only in system mode`() {
        assertFalse(SubtitlePreference.SYSTEM.textTrackEnabled(false))
        assertTrue(SubtitlePreference.SYSTEM.textTrackEnabled(true))
        assertTrue(SubtitlePreference.ENABLED.textTrackEnabled(false))
        assertFalse(SubtitlePreference.DISABLED.textTrackEnabled(true))
    }

    @Test
    fun `invalid seek value normalizes to safe default`() {
        val normalized = TvPreferences(
            seekStepSeconds = -1,
            playbackBufferSeconds = 60,
        ).normalized()

        assertEquals(10, normalized.seekStepSeconds)
        assertEquals(10_000L, normalized.seekStepMs)
        assertEquals(15, normalized.playbackBufferSeconds)
    }
}
