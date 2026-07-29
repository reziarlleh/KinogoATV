package com.kinogo.atv.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPreferencesTest {
    @Test
    fun `quality seek and subtitles cycle in both D-pad directions`() {
        val initial = TvPreferences()

        assertEquals(
            VideoQualityPreference.UHD,
            initial.cycle(TvSettingIds.QUALITY, SettingCycleDirection.NEXT).defaultQuality,
        )
        assertEquals(
            VideoQualityPreference.SD,
            initial.cycle(TvSettingIds.QUALITY, SettingCycleDirection.PREVIOUS).defaultQuality,
        )
        assertEquals(
            15,
            initial.cycle(TvSettingIds.SEEK_STEP, SettingCycleDirection.NEXT).seekStepSeconds,
        )
        assertEquals(
            5,
            initial.cycle(TvSettingIds.SEEK_STEP, SettingCycleDirection.PREVIOUS).seekStepSeconds,
        )
        assertEquals(
            SubtitlePreference.ENABLED,
            initial.cycle(TvSettingIds.SUBTITLES, SettingCycleDirection.NEXT).subtitles,
        )
        assertEquals(
            SubtitlePreference.DISABLED,
            initial.cycle(TvSettingIds.SUBTITLES, SettingCycleDirection.PREVIOUS).subtitles,
        )
    }

    @Test
    fun `center-style next cycles wrap and booleans toggle`() {
        val wrapped = TvPreferences(
            defaultQuality = VideoQualityPreference.SD,
            seekStepSeconds = 60,
            subtitles = SubtitlePreference.DISABLED,
        )
            .cycle(TvSettingIds.QUALITY)
            .cycle(TvSettingIds.SEEK_STEP)
            .cycle(TvSettingIds.SUBTITLES)
            .cycle(TvSettingIds.AUTO_NEXT_EPISODE)
            .cycle(TvSettingIds.HIGH_CONTRAST)
            .cycle(TvSettingIds.REDUCE_MOTION)

        assertEquals(VideoQualityPreference.AUTO, wrapped.defaultQuality)
        assertEquals(5, wrapped.seekStepSeconds)
        assertEquals(SubtitlePreference.SYSTEM, wrapped.subtitles)
        assertFalse(wrapped.autoNextEpisode)
        assertTrue(wrapped.highContrast)
        assertTrue(wrapped.reduceMotion)
    }

    @Test
    fun `quality preference resolves provider labels and preserves fallback`() {
        val available = listOf("Авто", "WEB 2160p", "FullHD 1080", "720p")

        assertEquals(
            "WEB 2160p",
            VideoQualityPreference.UHD.resolve("Авто", available),
        )
        assertEquals(
            "FullHD 1080",
            VideoQualityPreference.FULL_HD.resolve("Авто", available),
        )
        assertEquals(
            "Авто",
            VideoQualityPreference.SD.resolve("Авто", available),
        )
        assertEquals(
            "720p",
            VideoQualityPreference.AUTO.resolve("720p", available),
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
        val normalized = TvPreferences(seekStepSeconds = -1).normalized()

        assertEquals(10, normalized.seekStepSeconds)
        assertEquals(10_000L, normalized.seekStepMs)
    }
}
