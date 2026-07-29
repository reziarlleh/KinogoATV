package com.kinogo.atv.data.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kinogo.atv.domain.CatalogItem
import kotlinx.coroutines.flow.first

class FavoriteStore(
    private val dataStore: DataStore<Preferences>,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun list(): List<FavoriteEntry> =
        FavoriteCodec.decode(dataStore.data.first()[RECORDS_KEY])

    suspend fun toggle(item: CatalogItem): List<FavoriteEntry> {
        var updated = emptyList<FavoriteEntry>()
        dataStore.edit { preferences ->
            val current = FavoriteCodec.decode(preferences[RECORDS_KEY])
            updated = FavoriteCollection.toggle(current, item, nowEpochMs())
            if (updated.isEmpty()) {
                preferences.remove(RECORDS_KEY)
            } else {
                preferences[RECORDS_KEY] = FavoriteCodec.encode(updated)
            }
        }
        return updated
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(RECORDS_KEY) }
    }

    private companion object {
        val RECORDS_KEY = stringPreferencesKey("favorite_catalog_items_v1")
    }
}
