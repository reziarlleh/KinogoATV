package com.kinogo.atv.data.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kinogo.atv.domain.PlaybackSelection
import com.kinogo.atv.domain.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressStoreTest {
    @Test
    fun `delete content removes every episode atomically`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val store = PlaybackProgressStore(dataStore)
        store.upsert(progress("series", "season-1", "episode-1", 10L))
        store.upsert(progress("series", "season-2", "episode-4", 20L))
        store.upsert(progress("movie", updatedAt = 30L))

        store.deleteContent("series")

        assertEquals(listOf("movie"), store.list().map { it.selection.contentId })
    }

    @Test
    fun `clear history preserves unrelated preferences`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val store = PlaybackProgressStore(dataStore)
        val unrelatedKey = stringPreferencesKey("unrelated-setting")
        dataStore.edit { it[unrelatedKey] = "keep" }
        store.upsert(progress("movie", updatedAt = 10L))

        store.clear()

        assertTrue(store.list().isEmpty())
        assertEquals("keep", dataStore.data.first()[unrelatedKey])
    }

    private fun progress(
        contentId: String,
        seasonId: String? = null,
        episodeId: String? = null,
        updatedAt: Long,
    ) = WatchProgress(
        selection = PlaybackSelection(
            contentId = contentId,
            seasonId = seasonId,
            episodeId = episodeId,
            voiceId = "voice",
            qualityId = "720p",
            sourceId = "collaps",
        ),
        positionMs = 42_000L,
        durationMs = 2_400_000L,
        updatedAtEpochMs = updatedAt,
    )

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
