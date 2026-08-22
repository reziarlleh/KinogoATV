package com.kinogo.atv.domain

object TvSettingIds {
    const val QUALITY = "quality"
    const val AUTO_NEXT_EPISODE = "next"
    const val SEEK_STEP = "seek"
    const val PLAYBACK_BUFFER = "playback_buffer"
    const val HIGH_CONTRAST = "contrast"
    const val REDUCE_MOTION = "motion"
    const val SUBTITLES = "captions"
    const val AUTO_CHECK_UPDATES = "auto_check_updates"
}
enum class VideoQualityPreference(val storageValue: String) {
    AUTO("auto"),
    UHD("2160p"),
    FULL_HD("1080p"),
    HD("720p"),
    SD("480p"),
    ;

    /** Returns the persistent intent; the real playback plan resolves a concrete stream later. */
    fun requestedLabel(providerDefault: String): String =
        if (this == AUTO) providerDefault else storageValue

    companion object {
        fun fromStorage(value: String?): VideoQualityPreference =
            entries.firstOrNull { it.storageValue == value } ?: AUTO
    }
}

enum class SubtitlePreference(val storageValue: String) {
    SYSTEM("system"),
    ENABLED("enabled"),
    DISABLED("disabled"),
    ;

    fun textTrackEnabled(systemCaptionsEnabled: Boolean): Boolean = when (this) {
        SYSTEM -> systemCaptionsEnabled
        ENABLED -> true
        DISABLED -> false
    }

    companion object {
        fun fromStorage(value: String?): SubtitlePreference =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

data class TvPreferences(
    val defaultQuality: VideoQualityPreference = VideoQualityPreference.AUTO,
    val autoNextEpisode: Boolean = true,
    val seekStepSeconds: Int = DEFAULT_SEEK_STEP_SECONDS,
    val playbackBufferSeconds: Int = DEFAULT_PLAYBACK_BUFFER_SECONDS,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val subtitles: SubtitlePreference = SubtitlePreference.SYSTEM,
    val autoCheckUpdates: Boolean = true,
) {
    val seekStepMs: Long get() = seekStepSeconds * 1_000L

    /**
     * Applies the stable option id selected by a TV dropdown or switch.
     *
     * Invalid ids are ignored instead of resetting a stored preference. This keeps the UI and
     * DataStore resilient when a stale dialog survives a configuration change.
     */
    fun withSetting(settingId: String, optionId: String): TvPreferences = when (settingId) {
        TvSettingIds.QUALITY -> VideoQualityPreference.entries
            .firstOrNull { it.storageValue == optionId }
            ?.let { copy(defaultQuality = it) }
            ?: this
        TvSettingIds.AUTO_NEXT_EPISODE -> optionId.strictBooleanOrNull()
            ?.let { copy(autoNextEpisode = it) }
            ?: this
        TvSettingIds.SEEK_STEP -> optionId.toIntOrNull()
            ?.takeIf { it in SEEK_STEP_SECONDS }
            ?.let { copy(seekStepSeconds = it) }
            ?: this
        TvSettingIds.PLAYBACK_BUFFER -> optionId.toIntOrNull()
            ?.takeIf { it in PLAYBACK_BUFFER_SECONDS }
            ?.let { copy(playbackBufferSeconds = it) }
            ?: this
        TvSettingIds.HIGH_CONTRAST -> optionId.strictBooleanOrNull()
            ?.let { copy(highContrast = it) }
            ?: this
        TvSettingIds.REDUCE_MOTION -> optionId.strictBooleanOrNull()
            ?.let { copy(reduceMotion = it) }
            ?: this
        TvSettingIds.SUBTITLES -> SubtitlePreference.entries
            .firstOrNull { it.storageValue == optionId }
            ?.let { copy(subtitles = it) }
            ?: this
        TvSettingIds.AUTO_CHECK_UPDATES -> optionId.strictBooleanOrNull()
            ?.let { copy(autoCheckUpdates = it) }
            ?: this
        else -> this
    }

    fun normalized(): TvPreferences = copy(
        seekStepSeconds = seekStepSeconds.takeIf { it in SEEK_STEP_SECONDS }
            ?: DEFAULT_SEEK_STEP_SECONDS,
        playbackBufferSeconds = playbackBufferSeconds.takeIf { it in PLAYBACK_BUFFER_SECONDS }
            ?: DEFAULT_PLAYBACK_BUFFER_SECONDS,
    )

    companion object {
        const val DEFAULT_SEEK_STEP_SECONDS = 10
        const val DEFAULT_PLAYBACK_BUFFER_SECONDS = 15
        val SEEK_STEP_SECONDS = listOf(5, 10, 15, 30, 60)
        val PLAYBACK_BUFFER_SECONDS = listOf(5, 10, 15, 20, 30)
    }
}

private fun String.strictBooleanOrNull(): Boolean? = when (this) {
    "true" -> true
    "false" -> false
    else -> null
}
