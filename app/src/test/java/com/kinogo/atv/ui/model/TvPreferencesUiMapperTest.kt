package com.kinogo.atv.ui.model

import com.kinogo.atv.domain.SubtitlePreference
import com.kinogo.atv.domain.TvPreferences
import com.kinogo.atv.domain.VideoQualityPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class TvPreferencesUiMapperTest {
    @Test
    fun `mapper renders persisted settings instead of fixture values`() {
        val sections = KinogoFixtures.settings.withPreferences(
            TvPreferences(
                defaultQuality = VideoQualityPreference.FULL_HD,
                autoNextEpisode = false,
                seekStepSeconds = 30,
                playbackBufferSeconds = 20,
                highContrast = true,
                reduceMotion = true,
                subtitles = SubtitlePreference.DISABLED,
                autoCheckUpdates = false,
            ),
        )
        val values = sections.flatMap { it.items }.associate { it.id to it.value }

        assertEquals("1080p", values["quality"])
        assertEquals("Выкл.", values["next"])
        assertEquals("30 сек", values["seek"])
        assertEquals("20 сек", values["playback_buffer"])
        assertEquals("Вкл.", values["contrast"])
        assertEquals("Вкл.", values["motion"])
        assertEquals("Выкл.", values["captions"])
        assertEquals("Выкл.", values["auto_check_updates"])

        val byId = sections.flatMap { it.items }.associateBy { it.id }
        assertEquals(SettingControlUi.SWITCH, byId.getValue("next").control)
        assertEquals(SettingControlUi.DROPDOWN, byId.getValue("quality").control)
        assertEquals(SettingControlUi.DROPDOWN, byId.getValue("playback_buffer").control)
        assertEquals("20", byId.getValue("playback_buffer").selectedOptionId)
        assertEquals(
            listOf("5", "10", "15", "20", "30"),
            byId.getValue("playback_buffer").options.map(SettingOptionUiModel::id),
        )
        assertEquals("1080p", byId.getValue("quality").selectedOptionId)
        assertEquals(
            listOf("auto", "2160p", "1080p", "720p", "480p"),
            byId.getValue("quality").options.map(SettingOptionUiModel::id),
        )
    }
}
