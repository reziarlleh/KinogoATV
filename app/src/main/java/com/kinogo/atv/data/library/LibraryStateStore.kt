package com.kinogo.atv.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kinogo.atv.data.favorites.FavoriteEntry
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentRatings
import com.kinogo.atv.domain.ContentType
import com.kinogo.atv.domain.LibraryRecord
import com.kinogo.atv.domain.WatchStatus
import java.util.Base64
import kotlinx.coroutines.flow.first

enum class LibraryMutationKind {
    FAVORITE,
    STATUS,
}

data class PendingLibraryMutation(
    val contentId: String,
    val kind: LibraryMutationKind,
    val value: String?,
    val updatedAtEpochMs: Long,
) {
    val dedupeKey: String
        get() = "$contentId:${kind.name}"
}

class LibraryStateStore(
    private val dataStore: DataStore<Preferences>,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun list(): List<LibraryRecord> =
        LibraryStateCodec.decodeRecords(dataStore.data.first()[RECORDS_KEY])

    suspend fun pending(): List<PendingLibraryMutation> =
        LibraryStateCodec.decodeMutations(dataStore.data.first()[PENDING_KEY])

    suspend fun importLegacyFavorites(entries: Collection<FavoriteEntry>): List<LibraryRecord> {
        var result = emptyList<LibraryRecord>()
        dataStore.edit { preferences ->
            val current = LibraryStateCodec.decodeRecords(preferences[RECORDS_KEY])
                .associateByTo(linkedMapOf()) { it.item.id }
            entries.forEach { favorite ->
                val previous = current[favorite.item.id]
                current[favorite.item.id] = LibraryRecord(
                    item = favorite.item,
                    status = previous?.status,
                    favorite = true,
                    updatedAtEpochMs = maxOf(
                        previous?.updatedAtEpochMs ?: 0L,
                        favorite.addedAtEpochMs,
                    ),
                )
            }
            result = normalize(current.values)
            preferences.writeRecords(result)
        }
        return result
    }

    suspend fun setFavorite(item: CatalogItem, enabled: Boolean): List<LibraryRecord> =
        mutate(item, LibraryMutationKind.FAVORITE, enabled.toString()) { current, timestamp ->
            LibraryRecord(
                item = item,
                status = current?.status,
                favorite = enabled,
                updatedAtEpochMs = timestamp,
            )
        }

    suspend fun setStatus(item: CatalogItem, status: WatchStatus?): List<LibraryRecord> =
        mutate(item, LibraryMutationKind.STATUS, status?.folder) { current, timestamp ->
            LibraryRecord(
                item = item,
                status = status,
                favorite = current?.favorite == true,
                updatedAtEpochMs = timestamp,
            )
        }

    /**
     * First account merge is a favorite union. Afterwards the rendered server lists are
     * authoritative except for locally pending dimensions.
     */
    suspend fun mergeRemote(login: String, snapshot: RemoteLibrarySnapshot): List<LibraryRecord> {
        var result = emptyList<LibraryRecord>()
        dataStore.edit { preferences ->
            val local = LibraryStateCodec.decodeRecords(preferences[RECORDS_KEY])
            val remote = snapshot.records.associateBy { it.item.id }
            val pending = LibraryStateCodec.decodeMutations(preferences[PENDING_KEY])
                .associateBy(PendingLibraryMutation::dedupeKey)
                .toMutableMap()
            val syncedAccounts = LibraryStateCodec.decodeStringSet(preferences[SYNCED_ACCOUNTS_KEY])
                .toMutableSet()
            val firstMerge = login !in syncedAccounts
            val merged = linkedMapOf<String, LibraryRecord>()

            (local.map { it.item.id } + remote.keys).distinct().forEach { id ->
                val localRecord = local.firstOrNull { it.item.id == id }
                val remoteRecord = remote[id]
                val favoritePending = pending["$id:${LibraryMutationKind.FAVORITE.name}"] != null
                val statusPending = pending["$id:${LibraryMutationKind.STATUS.name}"] != null
                val favorite = when {
                    favoritePending -> localRecord?.favorite == true
                    firstMerge -> localRecord?.favorite == true || remoteRecord?.favorite == true
                    else -> remoteRecord?.favorite == true
                }
                val status = if (statusPending) localRecord?.status else remoteRecord?.status
                val item = remoteRecord?.item ?: localRecord?.item ?: return@forEach
                if (favorite || status != null) {
                    merged[id] = LibraryRecord(
                        item = item,
                        status = status,
                        favorite = favorite,
                        updatedAtEpochMs = maxOf(
                            localRecord?.updatedAtEpochMs ?: 0L,
                            remoteRecord?.updatedAtEpochMs ?: 0L,
                        ),
                    )
                }

                if (firstMerge && localRecord?.favorite == true && remoteRecord?.favorite != true) {
                    val mutation = PendingLibraryMutation(
                        contentId = id,
                        kind = LibraryMutationKind.FAVORITE,
                        value = true.toString(),
                        updatedAtEpochMs = nowEpochMs(),
                    )
                    pending[mutation.dedupeKey] = mutation
                }
            }

            syncedAccounts += login
            result = normalize(merged.values)
            preferences.writeRecords(result)
            preferences.writeMutations(pending.values)
            preferences[SYNCED_ACCOUNTS_KEY] = LibraryStateCodec.encodeStringSet(syncedAccounts)
        }
        return result
    }

    suspend fun acknowledge(mutation: PendingLibraryMutation) {
        dataStore.edit { preferences ->
            val remaining = LibraryStateCodec.decodeMutations(preferences[PENDING_KEY])
                .filterNot { it.dedupeKey == mutation.dedupeKey && it == mutation }
            preferences.writeMutations(remaining)
        }
    }

    suspend fun clearAccountData() {
        dataStore.edit { preferences ->
            preferences.remove(RECORDS_KEY)
            preferences.remove(PENDING_KEY)
            preferences.remove(SYNCED_ACCOUNTS_KEY)
        }
    }

    private suspend fun mutate(
        item: CatalogItem,
        kind: LibraryMutationKind,
        value: String?,
        update: (LibraryRecord?, Long) -> LibraryRecord,
    ): List<LibraryRecord> {
        var result = emptyList<LibraryRecord>()
        dataStore.edit { preferences ->
            val records = LibraryStateCodec.decodeRecords(preferences[RECORDS_KEY])
                .associateByTo(linkedMapOf()) { it.item.id }
            val timestamp = nowEpochMs()
            val updated = update(records[item.id], timestamp)
            if (!updated.favorite && updated.status == null) records.remove(item.id) else records[item.id] = updated

            val mutations = LibraryStateCodec.decodeMutations(preferences[PENDING_KEY])
                .associateByTo(linkedMapOf(), PendingLibraryMutation::dedupeKey)
            val mutation = PendingLibraryMutation(item.id, kind, value, timestamp)
            mutations[mutation.dedupeKey] = mutation

            result = normalize(records.values)
            preferences.writeRecords(result)
            preferences.writeMutations(mutations.values)
        }
        return result
    }

    private fun normalize(records: Collection<LibraryRecord>): List<LibraryRecord> = records
        .filter { it.favorite || it.status != null }
        .associateBy { it.item.id }
        .values
        .sortedWith(compareByDescending<LibraryRecord> { it.updatedAtEpochMs }.thenBy { it.item.title })

    private fun MutablePreferences.writeRecords(records: Collection<LibraryRecord>) {
        val encoded = LibraryStateCodec.encodeRecords(records)
        if (encoded.isBlank()) remove(RECORDS_KEY) else this[RECORDS_KEY] = encoded
    }

    private fun MutablePreferences.writeMutations(mutations: Collection<PendingLibraryMutation>) {
        val encoded = LibraryStateCodec.encodeMutations(mutations)
        if (encoded.isBlank()) remove(PENDING_KEY) else this[PENDING_KEY] = encoded
    }

    private companion object {
        val RECORDS_KEY = stringPreferencesKey("server_library_records_v1")
        val PENDING_KEY = stringPreferencesKey("server_library_pending_v1")
        val SYNCED_ACCOUNTS_KEY = stringPreferencesKey("server_library_synced_accounts_v1")
    }
}

object LibraryStateCodec {
    private const val VERSION = "1"
    private const val NULL = "~"
    private const val RECORD_FIELDS = 15
    private const val MUTATION_FIELDS = 5
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encodeRecords(records: Collection<LibraryRecord>): String = records.joinToString("\n") { record ->
        val item = record.item
        listOf(
            VERSION,
            record.updatedAtEpochMs.toString(),
            text(item.id),
            text(item.relativePath),
            text(item.title),
            nullable(item.originalTitle),
            nullable(item.posterUrl),
            item.year?.toString() ?: NULL,
            item.type.name,
            item.ratings.kinopoisk?.toString() ?: NULL,
            item.ratings.imdb?.toString() ?: NULL,
            nullable(item.qualityBadge),
            nullable(item.episodeBadge),
            record.status?.name ?: NULL,
            record.favorite.toString(),
        ).joinToString("\t")
    }

    fun decodeRecords(payload: String?): List<LibraryRecord> = payload.orEmpty().lineSequence()
        .filter(String::isNotBlank)
        .mapNotNull { line -> runCatching { decodeRecord(line) }.getOrNull() }
        .filter { it.favorite || it.status != null }
        .associateBy { it.item.id }
        .values
        .toList()

    fun encodeMutations(mutations: Collection<PendingLibraryMutation>): String = mutations
        .associateBy(PendingLibraryMutation::dedupeKey)
        .values
        .joinToString("\n") { mutation ->
            listOf(
                VERSION,
                mutation.updatedAtEpochMs.toString(),
                text(mutation.contentId),
                mutation.kind.name,
                mutation.value?.let(::text) ?: NULL,
            ).joinToString("\t")
        }

    fun decodeMutations(payload: String?): List<PendingLibraryMutation> = payload.orEmpty().lineSequence()
        .filter(String::isNotBlank)
        .mapNotNull { line -> runCatching { decodeMutation(line) }.getOrNull() }
        .associateBy(PendingLibraryMutation::dedupeKey)
        .values
        .toList()

    fun encodeStringSet(values: Collection<String>): String = values.distinct().joinToString("\n", transform = ::text)

    fun decodeStringSet(payload: String?): Set<String> = payload.orEmpty().lineSequence()
        .filter(String::isNotBlank)
        .mapNotNull { runCatching { plain(it) }.getOrNull() }
        .toSet()

    private fun decodeRecord(line: String): LibraryRecord {
        val f = line.split('\t', limit = RECORD_FIELDS)
        require(f.size == RECORD_FIELDS && f[0] == VERSION)
        return LibraryRecord(
            item = CatalogItem(
                id = plain(f[2]),
                relativePath = plain(f[3]),
                title = plain(f[4]),
                originalTitle = nullablePlain(f[5]),
                posterUrl = nullablePlain(f[6]),
                year = f[7].takeUnless { it == NULL }?.toInt(),
                type = ContentType.valueOf(f[8]),
                ratings = ContentRatings(
                    kinopoisk = f[9].takeUnless { it == NULL }?.toDouble(),
                    imdb = f[10].takeUnless { it == NULL }?.toDouble(),
                ),
                qualityBadge = nullablePlain(f[11]),
                episodeBadge = nullablePlain(f[12]),
            ),
            status = f[13].takeUnless { it == NULL }?.let(WatchStatus::valueOf),
            favorite = f[14].toBooleanStrict(),
            updatedAtEpochMs = f[1].toLong(),
        )
    }

    private fun decodeMutation(line: String): PendingLibraryMutation {
        val f = line.split('\t', limit = MUTATION_FIELDS)
        require(f.size == MUTATION_FIELDS && f[0] == VERSION)
        return PendingLibraryMutation(
            contentId = plain(f[2]),
            kind = LibraryMutationKind.valueOf(f[3]),
            value = f[4].takeUnless { it == NULL }?.let(::plain),
            updatedAtEpochMs = f[1].toLong(),
        )
    }

    private fun nullable(value: String?): String = value?.let(::text) ?: NULL
    private fun nullablePlain(value: String): String? = value.takeUnless { it == NULL }?.let(::plain)
    private fun text(value: String): String = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun plain(value: String): String = String(decoder.decode(value), Charsets.UTF_8)
}
