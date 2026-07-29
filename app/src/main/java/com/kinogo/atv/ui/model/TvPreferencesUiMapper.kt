package com.kinogo.atv.ui.model

import com.kinogo.atv.domain.SubtitlePreference
import com.kinogo.atv.domain.TvPreferences
import com.kinogo.atv.domain.TvSettingIds
import com.kinogo.atv.domain.VideoQualityPreference

fun List<SettingSectionUiModel>.withPreferences(
    preferences: TvPreferences,
): List<SettingSectionUiModel> = map { section ->
    section.copy(
        items = section.items.map { item ->
            item.copy(value = preferences.labelFor(item.id) ?: item.value)
        },
    )
}

internal fun TvPreferences.labelFor(settingId: String): String? = when (settingId) {
    TvSettingIds.QUALITY -> when (defaultQuality) {
        VideoQualityPreference.AUTO -> "Авто"
        VideoQualityPreference.UHD -> "4K"
        VideoQualityPreference.FULL_HD -> "1080p"
        VideoQualityPreference.HD -> "720p"
        VideoQualityPreference.SD -> "480p"
    }
    TvSettingIds.AUTO_NEXT_EPISODE -> autoNextEpisode.enabledLabel()
    TvSettingIds.SEEK_STEP -> "$seekStepSeconds сек"
    TvSettingIds.HIGH_CONTRAST -> highContrast.enabledLabel()
    TvSettingIds.REDUCE_MOTION -> reduceMotion.enabledLabel()
    TvSettingIds.SUBTITLES -> when (subtitles) {
        SubtitlePreference.SYSTEM -> "Системные"
        SubtitlePreference.ENABLED -> "Вкл."
        SubtitlePreference.DISABLED -> "Выкл."
    }
    else -> null
}

private fun Boolean.enabledLabel(): String = if (this) "Вкл." else "Выкл."
