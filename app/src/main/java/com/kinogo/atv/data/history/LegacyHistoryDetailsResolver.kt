package com.kinogo.atv.data.history

import com.kinogo.atv.data.catalog.CatalogHttpStatusException
import com.kinogo.atv.data.catalog.CatalogParseException
import com.kinogo.atv.data.catalog.CatalogRepository
import com.kinogo.atv.data.catalog.CatalogRouteNormalizer
import com.kinogo.atv.data.catalog.KinogoSessionHttpClient
import com.kinogo.atv.data.catalog.ParsedContentPage
import com.kinogo.atv.data.catalog.SessionHttpResponse
import com.kinogo.atv.domain.CatalogItem
import java.net.URI
import java.util.Locale
import org.jsoup.Jsoup

/**
 * Restores a canonical details path for v1 history records that contain only a numeric DLE id.
 *
 * The raw error document is never treated as a content page. It may only nominate a tightly
 * constrained root-relative path; the regular catalog repository then performs the supported
 * document fingerprint, parser and exact post-id checks.
 */
class LegacyHistoryDetailsResolver internal constructor(
    private val rawProbe: suspend (origin: String, path: String) -> SessionHttpResponse,
    private val repository: CatalogRepository,
) {
    constructor(
        client: KinogoSessionHttpClient,
        repository: CatalogRepository,
    ) : this(client::getRaw, repository)

    suspend fun resolve(origin: String, contentId: String): ParsedContentPage {
        val probeItem = probeItem(contentId)
            ?: throw CatalogParseException("Некорректный идентификатор старой записи истории")
        val response = rawProbe(origin, probeItem.relativePath)
        val resolvedPath = when (response.statusCode) {
            200 -> LegacyHistorySuggestionParser.normalizeContentPath(
                rawPath = response.relativePath,
                contentId = contentId,
            ) ?: throw CatalogParseException(
                "Источник перенаправил старую запись истории на неподдерживаемый адрес",
            )
            404, 410 -> LegacyHistorySuggestionParser.findPath(
                html = response.body,
                contentId = contentId,
            ) ?: throw CatalogParseException(
                "Не удалось восстановить адрес старой записи истории",
            )
            else -> throw CatalogHttpStatusException(response.statusCode)
        }
        return repository.loadDetails(
            origin = origin,
            item = probeItem.copy(relativePath = resolvedPath),
        )
    }

    companion object {
        internal fun probeItem(contentId: String): CatalogItem? {
            val postId = canonicalPostId(contentId) ?: return null
            return CatalogItem(
                id = postId,
                relativePath = "/serialy/$postId-history.html",
                title = "Загрузка карточки…",
            )
        }

        internal fun isProbeItem(item: CatalogItem): Boolean =
            probeItem(item.id)?.relativePath == item.relativePath

        private fun canonicalPostId(contentId: String): String? {
            if (!CANONICAL_POSITIVE_ID.matches(contentId)) return null
            return contentId.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.toString()
        }

        private val CANONICAL_POSITIVE_ID = Regex("[1-9][0-9]*")
    }
}

internal object LegacyHistorySuggestionParser {
    fun findPath(html: String, contentId: String): String? {
        if (LegacyHistoryDetailsResolver.probeItem(contentId) == null) return null
        return Jsoup.parse(html)
            .select(".info a[href]")
            .asSequence()
            .map { it.attr("href").trim() }
            .mapNotNull { normalizeContentPath(it, contentId) }
            .firstOrNull()
    }

    fun normalizeContentPath(rawPath: String, contentId: String): String? {
        val postId = LegacyHistoryDetailsResolver.probeItem(contentId)?.id ?: return null
        val expectedBasename = Regex(
            pattern = "^${Regex.escape(postId)}-[\\p{L}\\p{N}_-]+\\.html$",
            option = RegexOption.IGNORE_CASE,
        )
        if (!rawPath.startsWith("/") || rawPath.startsWith("//")) return null
        val normalized = runCatching {
            CatalogRouteNormalizer.normalize(rawPath)
        }.getOrNull() ?: return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.path
        val rootDirectory = path
            .removePrefix("/")
            .substringBefore('/')
            .lowercase(Locale.ROOT)
        if (rootDirectory !in ALLOWED_CONTENT_ROOTS) return null
        val basename = path.substringAfterLast('/')
        return normalized.takeIf { expectedBasename.matches(basename) }
    }

    private val ALLOWED_CONTENT_ROOTS = setOf(
        "filmy",
        "serialy",
        "multfilmy",
        "multserialy",
        "anime",
        "anime-serialy",
    )
}
