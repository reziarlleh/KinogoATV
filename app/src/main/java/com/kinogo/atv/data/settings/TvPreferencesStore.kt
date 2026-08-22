package com.kinogo.atv.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kinogo.atv.domain.SubtitlePreference
import com.kinogo.atv.domain.TvPreferences
import com.kinogo.atv.domain.VideoQualityPreference
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TvPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<TvPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(TvPreferencesCodec::decode)
        .distinctUntilChanged()

    suspend fun snapshot(): TvPreferences = TvPreferencesCodec.decode(dataStore.data.first())

    suspend fun set(settingId: String, optionId: String) {
        dataStore.edit { stored ->
            TvPreferencesCodec.encode(
                stored = stored,
                value = TvPreferencesCodec.decode(stored).withSetting(settingId, optionId),
            )
        }
    }
}

internal object TvPreferencesCodec {
    private val qualityKey = stringPreferencesKey("setting_quality_v1")
    private val autoNextKey = booleanPreferencesKey("setting_auto_next_v1")
    private val seekStepKey = intPreferencesKey("setting_seek_seconds_v1")
    private val playbackBufferKey = intPreferencesKey("setting_playback_buffer_seconds_v1")
    private val highContrastKey = booleanPreferencesKey("setting_high_contrast_v1")
    private val reduceMotionKey = booleanPreferencesKey("setting_reduce_motion_v1")
    private val subtitlesKey = stringPreferencesKey("setting_subtitles_v1")
    private val autoCheckUpdatesKey = booleanPreferencesKey("setting_auto_check_updates_v1")

    fun decode(stored: Preferences): TvPreferences = TvPreferences(
        defaultQuality = VideoQualityPreference.fromStorage(stored[qualityKey]),
        autoNextEpisode = stored[autoNextKey] ?: true,
        seekStepSeconds = stored[seekStepKey] ?: TvPreferences.DEFAULT_SEEK_STEP_SECONDS,
        playbackBufferSeconds = stored[playbackBufferKey]
            ?: TvPreferences.DEFAULT_PLAYBACK_BUFFER_SECONDS,
        highContrast = stored[highContrastKey] ?: false,
        reduceMotion = stored[reduceMotionKey] ?: false,
        subtitles = SubtitlePreference.fromStorage(stored[subtitlesKey]),
        autoCheckUpdates = stored[autoCheckUpdatesKey] ?: true,
    ).normalized()

    fun encode(stored: androidx.datastore.preferences.core.MutablePreferences, value: TvPreferences) {
        val normalized = value.normalized()
        stored[qualityKey] = normalized.defaultQuality.storageValue
        stored[autoNextKey] = normalized.autoNextEpisode
        stored[seekStepKey] = normalized.seekStepSeconds
        stored[playbackBufferKey] = normalized.playbackBufferSeconds
        stored[highContrastKey] = normalized.highContrast
        stored[reduceMotionKey] = normalized.reduceMotion
        stored[subtitlesKey] = normalized.subtitles.storageValue
        stored[autoCheckUpdatesKey] = normalized.autoCheckUpdates
    }
}
