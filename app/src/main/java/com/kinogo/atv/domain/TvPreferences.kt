package com.kinogo.atv.domain

object TvSettingIds {
    const val QUALITY = "quality"
    const val AUTO_NEXT_EPISODE = "next"
    const val SEEK_STEP = "seek"
    const val HIGH_CONTRAST = "contrast"
    const val REDUCE_MOTION = "motion"
    const val SUBTITLES = "captions"
}
enum class SettingCycleDirection(val delta: Int) {
    PREVIOUS(-1),
    NEXT(1),
}

enum class VideoQualityPreference(val storageValue: String) {
    AUTO("auto"),
    UHD("2160p"),
    FULL_HD("1080p"),
    HD("720p"),
    SD("480p"),
    ;

    fun resolve(requestedQuality: String, availableQualities: Collection<String>): String {
        if (this == AUTO) return requestedQuality
        return availableQualities.firstOrNull(::matches) ?: requestedQuality
    }

    private fun matches(value: String): Boolean {
        val normalized = value.lowercase()
        return when (this) {
            AUTO -> normalized == "auto" || normalized == "авто"
            UHD -> "2160" in normalized || "4k" in normalized || "4к" in normalized
            FULL_HD -> "1080" in normalized
            HD -> "720" in normalized
            SD -> "480" in normalized
        }
    }

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
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val subtitles: SubtitlePreference = SubtitlePreference.SYSTEM,
) {
    val seekStepMs: Long get() = seekStepSeconds * 1_000L

    fun cycle(
        settingId: String,
        direction: SettingCycleDirection = SettingCycleDirection.NEXT,
    ): TvPreferences = when (settingId) {
        TvSettingIds.QUALITY -> copy(
            defaultQuality = VideoQualityPreference.entries.cycle(defaultQuality, direction),
        )
        TvSettingIds.AUTO_NEXT_EPISODE -> copy(autoNextEpisode = !autoNextEpisode)
        TvSettingIds.SEEK_STEP -> copy(
            seekStepSeconds = SEEK_STEP_SECONDS.cycle(seekStepSeconds, direction),
        )
        TvSettingIds.HIGH_CONTRAST -> copy(highContrast = !highContrast)
        TvSettingIds.REDUCE_MOTION -> copy(reduceMotion = !reduceMotion)
        TvSettingIds.SUBTITLES -> copy(
            subtitles = SubtitlePreference.entries.cycle(subtitles, direction),
        )
        else -> this
    }

    fun normalized(): TvPreferences = copy(
        seekStepSeconds = seekStepSeconds.takeIf { it in SEEK_STEP_SECONDS }
            ?: DEFAULT_SEEK_STEP_SECONDS,
    )

    companion object {
        const val DEFAULT_SEEK_STEP_SECONDS = 10
        val SEEK_STEP_SECONDS = listOf(5, 10, 15, 30, 60)
    }
}

private fun <T> List<T>.cycle(current: T, direction: SettingCycleDirection): T {
    if (isEmpty()) return current
    val currentIndex = indexOf(current).takeIf { it >= 0 } ?: 0
    val nextIndex = Math.floorMod(currentIndex + direction.delta, size)
    return this[nextIndex]
}
