package com.kinogo.atv.data.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Locale
import kotlinx.coroutines.flow.first

/** Small, ordered and device-local history of committed search queries. */
class SearchHistoryStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun list(): List<String> =
        SearchHistoryCodec.decode(dataStore.data.first()[QUERIES_KEY])

    suspend fun record(rawQuery: String): List<String> {
        var updated = emptyList<String>()
        dataStore.edit { preferences ->
            updated = SearchHistoryCollection.record(
                current = SearchHistoryCodec.decode(preferences[QUERIES_KEY]),
                rawQuery = rawQuery,
            )
            if (updated.isEmpty()) {
                preferences.remove(QUERIES_KEY)
            } else {
                preferences[QUERIES_KEY] = SearchHistoryCodec.encode(updated)
            }
        }
        return updated
    }

    private companion object {
        val QUERIES_KEY = stringPreferencesKey("recent_search_queries_v1")
    }
}

internal object SearchHistoryCollection {
    const val MAX_ITEMS = 10
    private const val MAX_QUERY_LENGTH = 120
    private val whitespace = Regex("\\s+")

    fun record(current: List<String>, rawQuery: String): List<String> {
        val normalized = normalize(rawQuery) ?: return current
        return buildList {
            add(normalized)
            current
                .asSequence()
                .mapNotNull(::normalize)
                .filterNot { it.equals(normalized, ignoreCase = true) }
                .take(MAX_ITEMS - 1)
                .forEach(::add)
        }
    }

    fun normalize(rawQuery: String): String? = rawQuery
        .trim()
        .replace(whitespace, " ")
        .take(MAX_QUERY_LENGTH)
        .takeIf(String::isNotEmpty)
}

internal object SearchHistoryCodec {
    private const val SEPARATOR = '\n'

    fun encode(queries: List<String>): String = queries
        .mapNotNull(SearchHistoryCollection::normalize)
        .take(SearchHistoryCollection.MAX_ITEMS)
        .joinToString(SEPARATOR.toString())

    fun decode(raw: String?): List<String> {
        val seen = mutableSetOf<String>()
        return raw
            .orEmpty()
            .split(SEPARATOR)
            .asSequence()
            .mapNotNull(SearchHistoryCollection::normalize)
            .filter { seen.add(it.lowercase(Locale.ROOT)) }
            .take(SearchHistoryCollection.MAX_ITEMS)
            .toList()
    }
}
