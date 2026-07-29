package com.kinogo.atv.data.mirror

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Persists manual origins and the user's preference; health is always revalidated on launch. */
class MirrorPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun manualOrigins(): List<String> {
        val payload = dataStore.data.first()[MANUAL_ORIGINS_KEY]
        return MirrorOriginsCodec.decode(payload)
    }

    suspend fun addManual(rawOrigin: String): String {
        val origin = MirrorUrlNormalizer.normalize(rawOrigin)
        dataStore.edit { preferences ->
            val current = MirrorOriginsCodec.decode(preferences[MANUAL_ORIGINS_KEY])
            preferences[MANUAL_ORIGINS_KEY] = MirrorOriginsCodec.encode(current + origin)
        }
        return origin
    }

    suspend fun selectedOrigin(): String? =
        dataStore.data.first()[SELECTED_ORIGIN_KEY]?.let { stored ->
            runCatching { MirrorUrlNormalizer.normalize(stored) }.getOrNull()
        }

    suspend fun setSelectedOrigin(rawOrigin: String?) {
        dataStore.edit { preferences ->
            if (rawOrigin == null) {
                preferences.remove(SELECTED_ORIGIN_KEY)
            } else {
                preferences[SELECTED_ORIGIN_KEY] = MirrorUrlNormalizer.normalize(rawOrigin)
            }
        }
    }

    private companion object {
        val MANUAL_ORIGINS_KEY = stringPreferencesKey("mirror_manual_origins_v1")
        val SELECTED_ORIGIN_KEY = stringPreferencesKey("mirror_selected_origin_v1")
    }
}
object MirrorOriginsCodec {
    fun encode(origins: Collection<String>): String =
        origins
            .map(MirrorUrlNormalizer::normalize)
            .distinct()
            .sorted()
            .joinToString("\n")

    fun decode(payload: String?): List<String> =
        payload
            ?.lineSequence()
            ?.mapNotNull { value ->
                runCatching { MirrorUrlNormalizer.normalize(value) }.getOrNull()
            }
            ?.distinct()
            ?.sorted()
            ?.toList()
            .orEmpty()
}
