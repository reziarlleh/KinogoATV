package com.kinogo.atv.data.catalog

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentRatings
import com.kinogo.atv.domain.ContentType
import com.kinogo.atv.data.library.HtmlLibraryParser
import com.kinogo.atv.data.library.ParsedServerLibraryState
import java.net.URI
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ParsedCatalogPage(
    val items: List<CatalogItem>,
    val page: Int,
    val nextPage: Int?,
) {
    init {
        require(page > 0)
        require(nextPage == null || nextPage > page)
    }

    val currentPage: Int
        get() = page
}

data class PlayerEmbedCandidate(
    val url: String,
    val label: String,
    val providerId: String? = null,
) {
    init {
        require(url.isNotBlank())
        require(label.isNotBlank())
    }

    val embedUrl: String
        get() = url

    override fun toString(): String =
        "PlayerEmbedCandidate(url=<redacted>, label=$label, providerId=$providerId)"
}

data class ParsedContentPage(
    val catalogItem: CatalogItem,
    val description: String,
    val countries: List<String>,
    val genres: List<String>,
    val directors: List<String>,
    val cast: List<String>,
    val durationMinutes: Int?,
    val metadata: Map<String, String>,
    val playerEmbeds: List<PlayerEmbedCandidate>,
    val playerNotice: String? = null,
    val serverLibraryState: ParsedServerLibraryState = ParsedServerLibraryState(null, null),
) {
    val embeds: List<PlayerEmbedCandidate>
        get() = playerEmbeds
}

/**
 * Pure parser for the server-rendered DLE pages currently exposed by kinogo.parts.
 *
 * It intentionally does not execute scripts or resolve media URLs. Player iframe addresses are
 * returned as untrusted candidates for the separate source-resolver layer.
 */
class KinogoHtmlParser {
    fun parseCatalog(
        html: String,
        origin: String,
        page: Int,
    ): ParsedCatalogPage {
        require(page > 0) { "Catalog page must be positive" }
        val normalizedOrigin = normalizeOrigin(origin)
        val document = Jsoup.parse(html, "$normalizedOrigin/")
        val container = document.selectFirst("#dle-content")
            ?: throw CatalogParseException("Catalog container #dle-content is missing")
        val articles = container.select("article.shortStory")
        val items = articles.mapNotNull { parseCatalogItem(it, normalizedOrigin) }
        if (articles.isNotEmpty() && items.isEmpty()) {
            throw CatalogParseException("Catalog cards use an unsupported structure")
        }

        return ParsedCatalogPage(
            items = items,
            page = page,
            nextPage = findNextPage(document, page),
        )
    }

    fun parseDetails(
        html: String,
        origin: String,
        relativePath: String,
    ): ParsedContentPage {
        val normalizedOrigin = normalizeOrigin(origin)
        val normalizedPath = normalizeRelativePath(relativePath)
        val document = Jsoup.parse(html, "$normalizedOrigin$normalizedPath")
        val article = document.selectFirst("#dle-content article.fullStory, article.fullStory")
            ?: throw CatalogParseException("Content card article.fullStory is missing")
        val title = article.selectFirst("h1")?.normalizedText()
            ?: throw CatalogParseException("Content title is missing")

        val metadata = extractMetadata(article)
        val posterUrl = article.selectFirst(".sPoster img")
            ?.imageSource()
            ?.let { absoluteHttpsUrl(it, "$normalizedOrigin$normalizedPath") }
        val description = article.select(".filmDescription p")
            .mapNotNull { it.normalizedText() }
            .joinToString("\n\n")
        val item = CatalogItem(
            id = extractContentId(article, normalizedPath),
            relativePath = normalizedPath,
            title = title,
            originalTitle = metadata.find("Зарубежное название", "Оригинальное название")
                .meaningfulValue(),
            posterUrl = posterUrl,
            year = metadata.find("Год выпуска", "Год").toYear(),
            type = inferContentType(normalizedPath),
            ratings = ContentRatings(
                kinopoisk = parseRating(article.selectFirst(".fullKP")?.text(), "КиноПоиск")
                    ?: parseRatingValue(metadata.find("КиноПоиск", "Кинопоиск", "КП")),
                imdb = parseRating(article.selectFirst(".fQuality")?.text(), "IMDB")
                    ?: parseRatingValue(metadata.find("IMDB", "IMDb")),
            ),
            qualityBadge = article.selectFirst(".quAl")?.normalizedText()
                ?: metadata.find("Качество").meaningfulValue(),
            episodeBadge = metadata.find("Последняя серия онлайн", "Сезон").meaningfulValue(),
        )

        return ParsedContentPage(
            catalogItem = item,
            description = description,
            countries = splitValues(metadata.find("Страна")),
            genres = splitValues(metadata.findPrefix("Жанр")),
            directors = splitValues(metadata.find("Режиссер", "Режиссёр")),
            cast = splitValues(metadata.find("Актеры", "Актёры", "В ролях")),
            durationMinutes = parseDurationMinutes(
                metadata.find("Продолжительность", "Длительность"),
            ),
            metadata = metadata.values.toMap(),
            playerEmbeds = parsePlayerEmbeds(article, "$normalizedOrigin$normalizedPath"),
            playerNotice = article.selectFirst(".sectionPlayer .player-empty, .player-empty")
                ?.normalizedText()
                ?.take(MAX_PLAYER_NOTICE_LENGTH),
            serverLibraryState = HtmlLibraryParser.parseDetails(html),
        )
    }

    private fun parseCatalogItem(article: Element, origin: String): CatalogItem? {
        val titleLink = article.selectFirst("h2 a") ?: return null
        val title = titleLink.normalizedText() ?: return null
        val rawHref = titleLink.attr("href")
        val relativePath = sameOriginRelativePath(rawHref, origin) ?: return null
        val metadata = extractMetadata(article)
        val posterUrl = article.selectFirst(".sPoster img")
            ?.imageSource()
            ?.let { absoluteHttpsUrl(it, "$origin/") }

        return CatalogItem(
            id = extractContentId(article, rawHref),
            relativePath = relativePath,
            title = title,
            originalTitle = metadata.find("Зарубежное название", "Оригинальное название")
                .meaningfulValue(),
            posterUrl = posterUrl,
            year = metadata.find("Год выпуска", "Год").toYear(),
            type = inferContentType(relativePath),
            ratings = ContentRatings(
                kinopoisk = parseRating(article.selectFirst(".mRatings .kp")?.text(), "КП"),
                imdb = parseRating(article.selectFirst(".mRatings .imdb")?.text(), "IMDB"),
            ),
            qualityBadge = article.selectFirst(".quAl")?.normalizedText()
                ?: metadata.find("Качество").meaningfulValue(),
            episodeBadge = article.selectFirst(".sPoster .lenta .cont, .sPoster .lenta")
                ?.normalizedText()
                ?: metadata.find("Добавлено").meaningfulValue(),
        )
    }

    private fun extractMetadata(root: Element): LabeledMetadata {
        val result = LabeledMetadata()
        root.select(".sInfo > span, .cast .sInfo span, .castInfo span").forEach { row ->
            val label = row.selectFirst("b")?.normalizedText() ?: return@forEach
            val fullText = row.normalizedText() ?: return@forEach
            val value = fullText.removePrefix(label).trimStart(':', ' ')
            result.put(label, value)
        }
        root.select(
            ".fullRatings > *, .filmDop > .fDop, .filmDescription .fDop > div",
        ).forEach { row ->
            val labelElement = row.selectFirst("b, .fDop-l") ?: return@forEach
            val label = labelElement.normalizedText() ?: return@forEach
            val explicitValue = row.selectFirst(".fDop-r")?.normalizedText()
            val value = explicitValue
                ?: row.normalizedText()?.removePrefix(label)?.trimStart(':', ' ')
                ?: return@forEach
            result.put(label, value)
        }
        return result
    }

    private fun parsePlayerEmbeds(article: Element, baseUrl: String): List<PlayerEmbedCandidate> {
        val result = linkedMapOf<String, PlayerEmbedCandidate>()
        article.select(".js-player-tabs [data-src]").forEach { tab ->
            val url = absoluteHttpsUrl(tab.attr("data-src"), baseUrl) ?: return@forEach
            val label = tab.normalizedText() ?: tab.attr("title").trim().ifEmpty { "Плеер" }
            result.putIfAbsent(
                url,
                PlayerEmbedCandidate(
                    url = url,
                    label = label,
                    providerId = tab.attr("data-provider").trim().ifEmpty { null },
                ),
            )
        }
        article.select(".player-container iframe[src], .player-container iframe[data-src]")
            .forEach { iframe ->
                val rawUrl = iframe.attr("src").takeUnless { it.isBlank() }
                    ?: iframe.attr("data-src")
                val url = absoluteHttpsUrl(rawUrl, baseUrl) ?: return@forEach
                result.putIfAbsent(
                    url,
                    PlayerEmbedCandidate(
                        url = url,
                        label = iframe.attr("title").trim().ifEmpty { "Плеер" },
                    ),
                )
            }
        return result.values.toList()
    }

    private fun findNextPage(document: Document, page: Int): Int? {
        val links = document.select(".pagiNation a")
        val linkedPages = links.mapNotNull { link ->
            PAGE_PATTERN.find(link.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        if (page + 1 in linkedPages) return page + 1
        val hasLaterLink = links.any {
            it.text().trim().lowercase(RUSSIAN_LOCALE) in NEXT_PAGE_LABELS
        }
        return (page + 1).takeIf { hasLaterLink }
    }

    private fun sameOriginRelativePath(rawUrl: String, origin: String): String? = runCatching {
        val originUri = URI.create("$origin/")
        val resolved = originUri.resolve(rawUrl.trim())
        if (!resolved.scheme.equals("https", ignoreCase = true) ||
            !resolved.host.equals(originUri.host, ignoreCase = true) ||
            effectivePort(resolved) != effectivePort(originUri)
        ) {
            return null
        }
        buildString {
            append(resolved.rawPath?.takeIf { it.startsWith('/') } ?: return null)
            resolved.rawQuery?.let { append('?').append(it) }
        }
    }.getOrNull()

    private fun absoluteHttpsUrl(rawUrl: String, baseUrl: String): String? = runCatching {
        if (rawUrl.isBlank() || rawUrl.startsWith("data:", ignoreCase = true)) return null
        val resolved = URI.create(baseUrl).resolve(rawUrl.trim())
        if (!resolved.scheme.equals("https", ignoreCase = true) || resolved.host.isNullOrBlank()) {
            return null
        }
        resolved.toString()
    }.getOrNull()

    private fun normalizeOrigin(rawOrigin: String): String {
        val uri = runCatching { URI.create(rawOrigin.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid catalog origin", it) }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Catalog origin must use HTTPS" }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null) { "Invalid catalog origin" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "Catalog origin must not have query" }
        require(uri.path.isNullOrBlank() || uri.path == "/") { "Catalog origin must not have a path" }
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://${uri.host.lowercase(Locale.ROOT)}$port"
    }

    private fun normalizeRelativePath(rawPath: String): String {
        require(rawPath.startsWith('/') && !rawPath.startsWith("//")) {
            "Content path must be relative to the selected mirror"
        }
        val uri = URI.create(rawPath)
        require(uri.scheme == null && uri.rawAuthority == null && uri.rawUserInfo == null) {
            "Absolute content URL is not allowed"
        }
        return buildString {
            append(uri.rawPath ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
        }
    }

    private fun extractContentId(article: Element, pathOrUrl: String): String {
        val candidates = sequenceOf(
            article.id(),
            article.attr("data-id"),
            pathOrUrl,
        )
        candidates.forEach { candidate ->
            CONTENT_ID_PATTERN.find(candidate)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return runCatching { URI.create(pathOrUrl).path }
            .getOrNull()
            ?.substringAfterLast('/')
            ?.substringBefore('.')
            ?.takeIf(String::isNotBlank)
            ?: pathOrUrl
    }

    private fun inferContentType(relativePath: String): ContentType {
        val path = relativePath.lowercase(Locale.ROOT)
        return when {
            "/serialy/" in path -> ContentType.SERIES
            "/multfilmy/" in path || "/multserialy/" in path -> ContentType.ANIMATION
            "/anime/" in path || "/anime-serialy/" in path -> ContentType.ANIME
            "/filmy/" in path -> ContentType.MOVIE
            else -> ContentType.UNKNOWN
        }
    }

    private fun parseRating(raw: String?, label: String): Double? {
        if (raw.isNullOrBlank() || !raw.contains(label, ignoreCase = true)) return null
        return parseRatingValue(raw)
    }

    private fun parseRatingValue(raw: String?): Double? = raw
        ?.let { DECIMAL_PATTERN.find(it) }
            ?.value
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.takeIf { it in 0.0..10.0 }

    private fun parseDurationMinutes(raw: String?): Int? =
        raw?.let { DURATION_PATTERN.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

    private fun splitValues(raw: String?): List<String> = raw
        ?.split(LIST_SEPARATOR_PATTERN)
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.distinct()
        .orEmpty()

    private fun String?.toYear(): Int? = this
        ?.let { YEAR_PATTERN.find(it)?.value?.toIntOrNull() }
        ?.takeIf { it > 1800 }

    private fun String?.meaningfulValue(): String? = this
        ?.trim()
        ?.takeIf { value ->
            value.isNotEmpty() && value.lowercase(RUSSIAN_LOCALE) !in EMPTY_VALUES
        }

    private fun Element.normalizedText(): String? = text()
        .replace(WHITESPACE_PATTERN, " ")
        .trim()
        .takeIf(String::isNotEmpty)

    private fun Element.imageSource(): String? = attr("data-src")
        .trim()
        .takeIf { it.isNotEmpty() && !it.startsWith("data:", ignoreCase = true) }
        ?: attr("src").trim().takeIf {
            it.isNotEmpty() && !it.startsWith("data:", ignoreCase = true)
        }

    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }

    private class LabeledMetadata {
        val values = linkedMapOf<String, String>()

        fun put(rawLabel: String, rawValue: String) {
            val label = rawLabel.trim().trimEnd(':').trim()
            val value = rawValue.trim()
            if (label.isNotEmpty() && value.isNotEmpty()) values.putIfAbsent(label, value)
        }

        fun find(vararg labels: String): String? {
            val normalizedLabels = labels.map(::normalizeLabel).toSet()
            return values.entries.firstOrNull { normalizeLabel(it.key) in normalizedLabels }?.value
        }

        fun findPrefix(label: String): String? {
            val normalizedPrefix = normalizeLabel(label)
            return values.entries.firstOrNull {
                normalizeLabel(it.key).startsWith(normalizedPrefix)
            }?.value
        }

        private fun normalizeLabel(value: String): String = value
            .trim()
            .trimEnd(':')
            .lowercase(RUSSIAN_LOCALE)
            .replace('ё', 'е')
    }

    private companion object {
        val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")
        val WHITESPACE_PATTERN = Regex("\\s+")
        val CONTENT_ID_PATTERN = Regex("(?:post-|[/#])(\\d+)(?=[-_.#/?]|$)")
        val PAGE_PATTERN = Regex("/page/(\\d+)(?:/|$)", RegexOption.IGNORE_CASE)
        val YEAR_PATTERN = Regex("(?<!\\d)(?:18|19|20|21)\\d{2}(?!\\d)")
        val DECIMAL_PATTERN = Regex("\\d+(?:[.,]\\d+)?")
        val DURATION_PATTERN = Regex("(\\d{1,4})\\s*(?:мин|minute)", RegexOption.IGNORE_CASE)
        val LIST_SEPARATOR_PATTERN = Regex("\\s*(?:,|/|;|\\|)\\s*")
        val NEXT_PAGE_LABELS = setOf("позже", "следующая", "далее", "next", ">", "»")
        val EMPTY_VALUES = setOf("неизвестно", "отсутствует", "нет", "-")
        const val MAX_PLAYER_NOTICE_LENGTH = 500
    }
}
