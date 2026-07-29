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
                highContrast = true,
                reduceMotion = true,
                subtitles = SubtitlePreference.DISABLED,
            ),
        )
        val values = sections.flatMap { it.items }.associate { it.id to it.value }

        assertEquals("1080p", values["quality"])
        assertEquals("Выкл.", values["next"])
        assertEquals("30 сек", values["seek"])
        assertEquals("Вкл.", values["contrast"])
        assertEquals("Вкл.", values["motion"])
        assertEquals("Выкл.", values["captions"])
    }
}
