package com.kinogo.atv.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kinogo.atv.domain.SettingCycleDirection
import com.kinogo.atv.domain.SubtitlePreference
import com.kinogo.atv.domain.TvSettingIds
import com.kinogo.atv.domain.VideoQualityPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TvPreferencesStoreTest {
    @Test
    fun `new store instance restores all values written through DataStore`() = runTest {
        val persistedDataStore = InMemoryPreferencesDataStore()
        val firstProcess = TvPreferencesStore(persistedDataStore)

        firstProcess.cycle(TvSettingIds.QUALITY, SettingCycleDirection.NEXT)
        firstProcess.cycle(TvSettingIds.SEEK_STEP, SettingCycleDirection.NEXT)
        firstProcess.cycle(TvSettingIds.SUBTITLES, SettingCycleDirection.PREVIOUS)
        firstProcess.cycle(TvSettingIds.AUTO_NEXT_EPISODE, SettingCycleDirection.NEXT)
        firstProcess.cycle(TvSettingIds.HIGH_CONTRAST, SettingCycleDirection.NEXT)
        firstProcess.cycle(TvSettingIds.REDUCE_MOTION, SettingCycleDirection.NEXT)
        firstProcess.set(TvSettingIds.AUTO_CHECK_UPDATES, "false")

        // Recreating the repository models Activity/process recreation: there is no Compose
        // state involved, and values are decoded only from the shared DataStore snapshot.
        val afterRestart = TvPreferencesStore(persistedDataStore).snapshot()

        assertEquals(VideoQualityPreference.UHD, afterRestart.defaultQuality)
        assertEquals(15, afterRestart.seekStepSeconds)
        assertEquals(SubtitlePreference.DISABLED, afterRestart.subtitles)
        assertFalse(afterRestart.autoNextEpisode)
        assertEquals(true, afterRestart.highContrast)
        assertEquals(true, afterRestart.reduceMotion)
        assertFalse(afterRestart.autoCheckUpdates)
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = mutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }
}
