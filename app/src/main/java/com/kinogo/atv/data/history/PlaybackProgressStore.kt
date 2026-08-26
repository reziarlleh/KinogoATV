package com.kinogo.atv.data.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.WatchProgress
import kotlinx.coroutines.flow.first

/** Atomic Preferences DataStore persistence for the MVP history/resume list. */
class PlaybackProgressStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun upsert(progress: WatchProgress) {
        dataStore.edit { preferences ->
            val current = PlaybackProgressCodec.decode(preferences[RECORDS_KEY])
            preferences[RECORDS_KEY] = PlaybackProgressCodec.encode(
                PlaybackProgressCollection.upsert(current, progress),
            )
        }
    }

    /**
     * Enriches every stored episode/movie checkpoint for [item] in one DataStore transaction.
     *
     * The transform starts from the latest value inside [DataStore.edit], so a background card
     * refresh can never write an older position over a newer player checkpoint.
     */
    suspend fun attachContentSnapshot(item: CatalogItem) {
        dataStore.edit { preferences ->
            val current = PlaybackProgressCodec.decode(preferences[RECORDS_KEY])
            val enriched = PlaybackProgressCollection.attachContentSnapshot(current, item)
            if (enriched != current) {
                preferences[RECORDS_KEY] = PlaybackProgressCodec.encode(enriched)
            }
        }
    }

    suspend fun get(
        contentId: String,
        seasonId: String? = null,
        episodeId: String? = null,
    ): WatchProgress? {
        val key = PlaybackProgressKey(contentId, seasonId, episodeId)
        return PlaybackProgressCollection.find(list(), key)
    }

    suspend fun list(): List<WatchProgress> {
        val preferences = dataStore.data.first()
        return PlaybackProgressCodec.decode(preferences[RECORDS_KEY])
    }

    suspend fun delete(
        contentId: String,
        seasonId: String? = null,
        episodeId: String? = null,
    ) {
        val key = PlaybackProgressKey(contentId, seasonId, episodeId)
        dataStore.edit { preferences ->
            val current = PlaybackProgressCodec.decode(preferences[RECORDS_KEY])
            preferences[RECORDS_KEY] = PlaybackProgressCodec.encode(
                PlaybackProgressCollection.delete(current, key),
            )
        }
    }

    /** Removes all stored seasons/episodes represented by one History card. */
    suspend fun deleteContent(contentId: String) {
        require(contentId.isNotBlank())
        dataStore.edit { preferences ->
            val current = PlaybackProgressCodec.decode(preferences[RECORDS_KEY])
            preferences[RECORDS_KEY] = PlaybackProgressCodec.encode(
                PlaybackProgressCollection.deleteContent(current, contentId),
            )
        }
    }

    /** Removes only playback history, preserving unrelated application preferences. */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(RECORDS_KEY) }
    }

    private companion object {
        val RECORDS_KEY = stringPreferencesKey("playback_progress_records_v1")
    }
}
