package com.kinogo.atv.data.history

import com.kinogo.atv.domain.PlaybackSelection
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentRatings
import com.kinogo.atv.domain.ContentType
import com.kinogo.atv.domain.WatchProgress
import java.io.ByteArrayOutputStream

data class PlaybackProgressKey(
    val contentId: String,
    val seasonId: String? = null,
    val episodeId: String? = null,
) {
    init {
        require(contentId.isNotBlank())
        require((seasonId == null) == (episodeId == null))
        require(seasonId == null || seasonId.isNotBlank())
        require(episodeId == null || episodeId.isNotBlank())
    }
}

fun WatchProgress.progressKey(): PlaybackProgressKey =
    PlaybackProgressKey(
        contentId = selection.contentId,
        seasonId = selection.seasonId,
        episodeId = selection.episodeId,
    )

/** Pure collection rules shared by DataStore code and local unit tests. */
object PlaybackProgressCollection {
    fun normalize(entries: Collection<WatchProgress>): List<WatchProgress> {
        val newestByKey = linkedMapOf<PlaybackProgressKey, WatchProgress>()
        entries.forEach { progress ->
            val key = progress.progressKey()
            val current = newestByKey[key]
            if (current == null || progress.updatedAtEpochMs >= current.updatedAtEpochMs) {
                newestByKey[key] = progress
            }
        }
        return newestByKey.values.sortedWith(historyOrder())
    }

    fun upsert(entries: Collection<WatchProgress>, progress: WatchProgress): List<WatchProgress> {
        val normalized = normalize(entries)
        val key = progress.progressKey()
        val current = normalized.firstOrNull { it.progressKey() == key }
        val winner = when {
            current == null -> progress
            progress.updatedAtEpochMs >= current.updatedAtEpochMs -> progress.copy(
                contentSnapshot = progress.contentSnapshot ?: current.contentSnapshot,
            )
            else -> current.copy(
                contentSnapshot = current.contentSnapshot ?: progress.contentSnapshot,
            )
        }
        return normalize(normalized.filterNot { it.progressKey() == key } + winner)
    }

    fun attachContentSnapshot(
        entries: Collection<WatchProgress>,
        item: CatalogItem,
    ): List<WatchProgress> =
        normalize(
            entries.map { progress ->
                if (
                    progress.selection.contentId == item.id &&
                    progress.contentSnapshot != item
                ) {
                    progress.copy(contentSnapshot = item)
                } else {
                    progress
                }
            },
        )

    fun find(entries: Collection<WatchProgress>, key: PlaybackProgressKey): WatchProgress? =
        normalize(entries).firstOrNull { it.progressKey() == key }

    fun delete(entries: Collection<WatchProgress>, key: PlaybackProgressKey): List<WatchProgress> =
        normalize(entries.filterNot { it.progressKey() == key })

    /** Removes every movie/episode checkpoint represented by one History poster. */
    fun deleteContent(entries: Collection<WatchProgress>, contentId: String): List<WatchProgress> {
        require(contentId.isNotBlank())
        return normalize(entries.filterNot { it.selection.contentId == contentId })
    }

    private fun historyOrder(): Comparator<WatchProgress> =
        compareByDescending<WatchProgress> { it.updatedAtEpochMs }
            .thenBy { it.selection.contentId }
            .thenBy { it.selection.seasonId ?: "" }
            .thenBy { it.selection.episodeId ?: "" }
}

/**
 * Small deterministic codec for Preferences DataStore. All text fields are UTF-8 percent-encoded;
 * malformed records are skipped independently so one interrupted write cannot erase all history.
 */
object PlaybackProgressCodec {
    private const val VERSION_1 = "1"
    private const val VERSION_2 = "2"
    private const val VERSION_3 = "3"
    private const val NULL_TOKEN = "~"
    private const val V1_FIELD_COUNT = 10
    private const val V2_FIELD_COUNT = 21
    private const val V3_FIELD_COUNT = 22
    private val hex = "0123456789ABCDEF".toCharArray()

    fun encode(entries: Collection<WatchProgress>): String =
        PlaybackProgressCollection.normalize(entries).joinToString(separator = "\n") { progress ->
            val snapshot = progress.contentSnapshot
            listOf(
                VERSION_3,
                encodeText(progress.selection.contentId),
                encodeNullableText(progress.selection.seasonId),
                encodeNullableText(progress.selection.episodeId),
                encodeText(progress.selection.voiceId),
                encodeText(progress.selection.qualityId),
                progress.positionMs.toString(),
                progress.durationMs?.toString() ?: NULL_TOKEN,
                progress.updatedAtEpochMs.toString(),
                if (progress.playbackEnded) "1" else "0",
                if (snapshot != null) "1" else "0",
                snapshot?.relativePath?.let(::encodeText) ?: NULL_TOKEN,
                snapshot?.title?.let(::encodeText) ?: NULL_TOKEN,
                encodeNullableText(snapshot?.originalTitle),
                encodeNullableText(snapshot?.posterUrl),
                snapshot?.year?.toString() ?: NULL_TOKEN,
                snapshot?.type?.name ?: NULL_TOKEN,
                snapshot?.ratings?.kinopoisk?.toString() ?: NULL_TOKEN,
                snapshot?.ratings?.imdb?.toString() ?: NULL_TOKEN,
                encodeNullableText(snapshot?.qualityBadge),
                encodeNullableText(snapshot?.episodeBadge),
                encodeNullableText(progress.selection.sourceId),
            ).joinToString(separator = "\t")
        }

    fun decode(payload: String?): List<WatchProgress> {
        if (payload.isNullOrBlank()) return emptyList()
        val decoded =
            payload.lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> runCatching { decodeLine(line) }.getOrNull() }
                .toList()
        return PlaybackProgressCollection.normalize(decoded)
    }

    private fun decodeLine(line: String): WatchProgress {
        val fields = line.split('\t')
        require(
            (fields.size == V1_FIELD_COUNT && fields[0] == VERSION_1) ||
                (fields.size == V2_FIELD_COUNT && fields[0] == VERSION_2) ||
                (fields.size == V3_FIELD_COUNT && fields[0] == VERSION_3),
        )

        val selection =
            PlaybackSelection(
                contentId = decodeText(fields[1]),
                seasonId = decodeNullableText(fields[2]),
                episodeId = decodeNullableText(fields[3]),
                voiceId = decodeText(fields[4]),
                qualityId = decodeText(fields[5]),
                sourceId = if (fields[0] == VERSION_3) {
                    decodeNullableText(fields[21])
                } else {
                    null
                },
            )
        val ended =
            when (fields[9]) {
                "0" -> false
                "1" -> true
                else -> error("Invalid ended flag")
            }
        val snapshot = if (fields[0] != VERSION_1 && fields[10] == "1") {
            CatalogItem(
                id = selection.contentId,
                relativePath = decodeText(fields[11]),
                title = decodeText(fields[12]),
                originalTitle = decodeNullableText(fields[13]),
                posterUrl = decodeNullableText(fields[14]),
                year = fields[15].takeUnless { it == NULL_TOKEN }?.toInt(),
                type = ContentType.valueOf(fields[16]),
                ratings = ContentRatings(
                    kinopoisk = fields[17].takeUnless { it == NULL_TOKEN }?.toDouble(),
                    imdb = fields[18].takeUnless { it == NULL_TOKEN }?.toDouble(),
                ),
                qualityBadge = decodeNullableText(fields[19]),
                episodeBadge = decodeNullableText(fields[20]),
            )
        } else {
            require(fields[0] == VERSION_1 || fields[10] == "0")
            null
        }

        return WatchProgress(
            selection = selection,
            positionMs = fields[6].toLong(),
            durationMs = fields[7].takeUnless { it == NULL_TOKEN }?.toLong(),
            updatedAtEpochMs = fields[8].toLong(),
            playbackEnded = ended,
            contentSnapshot = snapshot,
        )
    }

    private fun encodeNullableText(value: String?): String =
        value?.let(::encodeText) ?: NULL_TOKEN

    private fun decodeNullableText(value: String): String? =
        value.takeUnless { it == NULL_TOKEN }?.let(::decodeText)

    private fun encodeText(value: String): String =
        buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val unsigned = byte.toInt() and 0xFF
                if (isUnreservedAscii(unsigned)) {
                    append(unsigned.toChar())
                } else {
                    append('%')
                    append(hex[unsigned ushr 4])
                    append(hex[unsigned and 0x0F])
                }
            }
        }

    private fun decodeText(value: String): String {
        val bytes = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%') {
                require(index + 2 < value.length)
                val high = value[index + 1].digitToIntOrNull(16) ?: error("Invalid escape")
                val low = value[index + 2].digitToIntOrNull(16) ?: error("Invalid escape")
                bytes.write((high shl 4) or low)
                index += 3
            } else {
                require(char.code in 0..127 && isUnreservedAscii(char.code))
                bytes.write(char.code)
                index++
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun isUnreservedAscii(value: Int): Boolean =
        value in 'a'.code..'z'.code ||
            value in 'A'.code..'Z'.code ||
            value in '0'.code..'9'.code ||
            value == '-'.code ||
            value == '_'.code ||
            value == '.'.code
}
