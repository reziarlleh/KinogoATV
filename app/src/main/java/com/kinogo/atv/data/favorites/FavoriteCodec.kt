package com.kinogo.atv.data.favorites

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentRatings
import com.kinogo.atv.domain.ContentType
import java.util.Base64

data class FavoriteEntry(
    val item: CatalogItem,
    val addedAtEpochMs: Long,
) {
    init {
        require(addedAtEpochMs >= 0L)
    }
}

object FavoriteCollection {
    fun normalize(entries: Collection<FavoriteEntry>): List<FavoriteEntry> {
        val newestById = linkedMapOf<String, FavoriteEntry>()
        entries.forEach { entry ->
            val current = newestById[entry.item.id]
            if (current == null || entry.addedAtEpochMs >= current.addedAtEpochMs) {
                newestById[entry.item.id] = entry
            }
        }
        return newestById.values.sortedWith(
            compareByDescending<FavoriteEntry> { it.addedAtEpochMs }
                .thenBy { it.item.title }
                .thenBy { it.item.id },
        )
    }

    fun toggle(
        entries: Collection<FavoriteEntry>,
        item: CatalogItem,
        nowEpochMs: Long,
    ): List<FavoriteEntry> = if (entries.any { it.item.id == item.id }) {
        normalize(entries.filterNot { it.item.id == item.id })
    } else {
        normalize(entries + FavoriteEntry(item, nowEpochMs))
    }
}

/** Deterministic line codec; malformed favorites are skipped independently. */
object FavoriteCodec {
    private const val VERSION = "1"
    private const val NULL_TOKEN = "~"
    private const val FIELD_COUNT = 13
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(entries: Collection<FavoriteEntry>): String =
        FavoriteCollection.normalize(entries).joinToString("\n") { entry ->
            val item = entry.item
            listOf(
                VERSION,
                entry.addedAtEpochMs.toString(),
                encodeText(item.id),
                encodeText(item.relativePath),
                encodeText(item.title),
                encodeNullable(item.originalTitle),
                encodeNullable(item.posterUrl),
                item.year?.toString() ?: NULL_TOKEN,
                item.type.name,
                item.ratings.kinopoisk?.toString() ?: NULL_TOKEN,
                item.ratings.imdb?.toString() ?: NULL_TOKEN,
                encodeNullable(item.qualityBadge),
                encodeNullable(item.episodeBadge),
            ).joinToString("\t")
        }

    fun decode(payload: String?): List<FavoriteEntry> {
        if (payload.isNullOrBlank()) return emptyList()
        return FavoriteCollection.normalize(
            payload.lineSequence()
                .filter(String::isNotBlank)
                .mapNotNull { line -> runCatching { decodeLine(line) }.getOrNull() }
                .toList(),
        )
    }

    private fun decodeLine(line: String): FavoriteEntry {
        val fields = line.split('\t', limit = FIELD_COUNT)
        require(fields.size == FIELD_COUNT && fields[0] == VERSION)
        return FavoriteEntry(
            item = CatalogItem(
                id = decodeText(fields[2]),
                relativePath = decodeText(fields[3]),
                title = decodeText(fields[4]),
                originalTitle = decodeNullable(fields[5]),
                posterUrl = decodeNullable(fields[6]),
                year = fields[7].takeUnless { it == NULL_TOKEN }?.toInt(),
                type = ContentType.valueOf(fields[8]),
                ratings = ContentRatings(
                    kinopoisk = fields[9].takeUnless { it == NULL_TOKEN }?.toDouble(),
                    imdb = fields[10].takeUnless { it == NULL_TOKEN }?.toDouble(),
                ),
                qualityBadge = decodeNullable(fields[11]),
                episodeBadge = decodeNullable(fields[12]),
            ),
            addedAtEpochMs = fields[1].toLong(),
        )
    }

    private fun encodeNullable(value: String?): String = value?.let(::encodeText) ?: NULL_TOKEN

    private fun decodeNullable(value: String): String? =
        value.takeUnless { it == NULL_TOKEN }?.let(::decodeText)

    private fun encodeText(value: String): String =
        encoder.encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeText(value: String): String =
        String(decoder.decode(value), Charsets.UTF_8)
}
