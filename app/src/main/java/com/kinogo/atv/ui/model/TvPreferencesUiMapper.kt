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
            item.copy(
                value = preferences.labelFor(item.id) ?: item.value,
                control = controlFor(item.id),
                selectedOptionId = preferences.optionIdFor(item.id),
                options = optionsFor(item.id),
            )
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
    TvSettingIds.AUTO_CHECK_UPDATES -> autoCheckUpdates.enabledLabel()
    else -> null
}

internal fun TvPreferences.optionIdFor(settingId: String): String? = when (settingId) {
    TvSettingIds.QUALITY -> defaultQuality.storageValue
    TvSettingIds.AUTO_NEXT_EPISODE -> autoNextEpisode.toString()
    TvSettingIds.SEEK_STEP -> seekStepSeconds.toString()
    TvSettingIds.HIGH_CONTRAST -> highContrast.toString()
    TvSettingIds.REDUCE_MOTION -> reduceMotion.toString()
    TvSettingIds.SUBTITLES -> subtitles.storageValue
    TvSettingIds.AUTO_CHECK_UPDATES -> autoCheckUpdates.toString()
    else -> null
}

private fun controlFor(settingId: String): SettingControlUi = when (settingId) {
    TvSettingIds.AUTO_NEXT_EPISODE,
    TvSettingIds.HIGH_CONTRAST,
    TvSettingIds.REDUCE_MOTION,
    TvSettingIds.AUTO_CHECK_UPDATES,
    -> SettingControlUi.SWITCH
    TvSettingIds.QUALITY,
    TvSettingIds.SEEK_STEP,
    TvSettingIds.SUBTITLES,
    -> SettingControlUi.DROPDOWN
    else -> SettingControlUi.VALUE
}

private fun optionsFor(settingId: String): List<SettingOptionUiModel> = when (settingId) {
    TvSettingIds.QUALITY -> listOf(
        SettingOptionUiModel(VideoQualityPreference.AUTO.storageValue, "Авто"),
        SettingOptionUiModel(VideoQualityPreference.UHD.storageValue, "4K"),
        SettingOptionUiModel(VideoQualityPreference.FULL_HD.storageValue, "1080p"),
        SettingOptionUiModel(VideoQualityPreference.HD.storageValue, "720p"),
        SettingOptionUiModel(VideoQualityPreference.SD.storageValue, "480p"),
    )
    TvSettingIds.SEEK_STEP -> TvPreferences.SEEK_STEP_SECONDS.map { seconds ->
        SettingOptionUiModel(seconds.toString(), "$seconds сек")
    }
    TvSettingIds.SUBTITLES -> listOf(
        SettingOptionUiModel(SubtitlePreference.SYSTEM.storageValue, "Системные"),
        SettingOptionUiModel(SubtitlePreference.ENABLED.storageValue, "Вкл."),
        SettingOptionUiModel(SubtitlePreference.DISABLED.storageValue, "Выкл."),
    )
    TvSettingIds.AUTO_NEXT_EPISODE,
    TvSettingIds.HIGH_CONTRAST,
    TvSettingIds.REDUCE_MOTION,
    TvSettingIds.AUTO_CHECK_UPDATES,
    -> listOf(
        SettingOptionUiModel("false", "Выкл."),
        SettingOptionUiModel("true", "Вкл."),
    )
    else -> emptyList()
}

private fun Boolean.enabledLabel(): String = if (this) "Вкл." else "Выкл."
