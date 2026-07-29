package com.kinogo.atv.data.playback.collaps

import com.kinogo.atv.data.mirror.NetworkAddressPolicy
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Parses only data literals exposed to `makePlayer({...})`.
 *
 * This is intentionally not a JavaScript interpreter. Functions, getters, computed properties,
 * template expressions and remote playlist URLs are never executed or fetched.
 */
object CollapsPlayerConfigParser {
    fun parse(html: String): CollapsParsedCatalog {
        if (html.length > MAX_HTML_CHARS) throw CollapsConfigTooLargeException()

        val playerObjects = JsEnvelopeScanner.findMakePlayerObjects(html)
        if (playerObjects.isEmpty()) throw CollapsConfigNotFoundException()

        var foundPlayerConfig = false
        for (objectLiteral in playerObjects.asReversed()) {
            val properties = JsEnvelopeScanner.topLevelPropertySlices(objectLiteral)
            val sourceSlice = properties["source"]
            val playlistSlice = properties["playlist"]
            if (sourceSlice == null && playlistSlice == null) continue
            foundPlayerConfig = true

            val blocked = properties["blocked"]
                ?.let(SafeJsLiteralParser::parse)
                .asBooleanOrNull() == true
            if (blocked) throw CollapsBlockedException()

            val title = properties["title"]
                ?.let(SafeJsLiteralParser::parse)
                .scalarTextOrNull()
                .toDisplayText(MAX_TITLE_CHARS)
                .ifBlank { "Collaps" }
            val rootId = properties["id"]
                ?.let(SafeJsLiteralParser::parse)
                .scalarTextOrNull()
                .toStableId("movie")

            if (playlistSlice != null) {
                return parsePlaylist(
                    title = title,
                    value = SafeJsLiteralParser.parse(playlistSlice),
                )
            }

            val source = requireNotNull(sourceSlice)
            val item = parseItem(
                value = JsObject(
                    linkedMapOf(
                        "id" to JsString(rootId),
                        "title" to JsString(title),
                        "source" to SafeJsLiteralParser.parse(source),
                    ),
                ),
                inheritedSeason = null,
                inheritedBlocked = false,
                fallbackIndex = 0,
            )
            return CollapsParsedCatalog(title = title, movie = item).requirePlayable()
        }

        if (foundPlayerConfig) throw CollapsMalformedConfigException()
        throw CollapsConfigNotFoundException()
    }

    private fun parsePlaylist(
        title: String,
        value: JsValue,
    ): CollapsParsedCatalog {
        if (value is JsString) throw CollapsRemotePlaylistException()
        val playlist = value.requireObject()
        val seasonsValue = playlist["seasons"]
        val flatValue = playlist["flat"]
        if ((seasonsValue == null) == (flatValue == null)) {
            throw CollapsMalformedConfigException()
        }

        val current = playlist["current"]?.let(::parseCurrent)
        if (seasonsValue != null) {
            val seasons = seasonsValue.requireArray().values.mapIndexed { seasonIndex, rawSeason ->
                val season = rawSeason.requireObject()
                val number = season["season"]
                    .scalarTextOrNull()
                    .toShortLabel((seasonIndex + 1).toString())
                val blocked = season["blocked"].asBooleanOrNull() == true
                val episodes = season["episodes"].requireArray().values.mapIndexed {
                        episodeIndex,
                        episode,
                    ->
                    parseItem(
                        value = episode,
                        inheritedSeason = number,
                        inheritedBlocked = blocked,
                        fallbackIndex = episodeIndex,
                    )
                }
                CollapsSeason(
                    number = number,
                    blocked = blocked,
                    episodes = episodes,
                )
            }
            if (seasons.isEmpty()) throw CollapsNoPlayableItemsException()
            return CollapsParsedCatalog(
                title = title,
                seasons = seasons,
                currentSelection = current,
            ).requirePlayable()
        }

        val flatEpisodes = requireNotNull(flatValue).requireArray().values.mapIndexed {
                episodeIndex,
                episode,
            ->
            parseItem(
                value = episode,
                inheritedSeason = null,
                inheritedBlocked = false,
                fallbackIndex = episodeIndex,
            )
        }
        if (flatEpisodes.isEmpty()) throw CollapsNoPlayableItemsException()
        return CollapsParsedCatalog(
            title = title,
            flatEpisodes = flatEpisodes,
            currentSelection = current,
        ).requirePlayable()
    }

    private fun parseCurrent(value: JsValue): CollapsCurrentSelection? {
        val current = value.requireObject()
        val id = current["id"].scalarTextOrNull()?.toStableId("current")
        val season = current["season"].scalarTextOrNull()?.toShortLabel("")
        val episode = current["episode"].scalarTextOrNull()?.toShortLabel("")
        if (id == null && season.isNullOrBlank() && episode.isNullOrBlank()) return null
        return CollapsCurrentSelection(
            id = id,
            season = season?.ifBlank { null },
            episode = episode?.ifBlank { null },
        )
    }

    private fun parseItem(
        value: JsValue,
        inheritedSeason: String?,
        inheritedBlocked: Boolean,
        fallbackIndex: Int,
    ): CollapsPlaybackItem {
        val item = value.requireObject()
        val episode = item["episode"]
            .scalarTextOrNull()
            ?.toShortLabel("")
            ?.ifBlank { null }
        val idFallback = buildString {
            append("item")
            inheritedSeason?.let { append("-s").append(it) }
            episode?.let { append("-e").append(it) } ?: append("-").append(fallbackIndex + 1)
        }
        val id = item["id"].scalarTextOrNull().toStableId(idFallback)
        val title = item["title"]
            .scalarTextOrNull()
            .toDisplayText(MAX_ITEM_TITLE_CHARS)
            .ifBlank {
                episode?.let { "Серия $it" } ?: "Видео ${fallbackIndex + 1}"
            }
        val blocked = inheritedBlocked || item["blocked"].asBooleanOrNull() == true
        val parsedSource = item["source"]?.let { parseSource(it, id) }

        if (!blocked && (parsedSource == null || parsedSource.streams.isEmpty())) {
            throw CollapsNoPlayableItemsException()
        }
        return CollapsPlaybackItem(
            id = id,
            season = inheritedSeason,
            episode = episode,
            title = title,
            blocked = blocked,
            streams = parsedSource?.streams.orEmpty(),
            audioTracks = parsedSource?.audioTracks.orEmpty(),
            subtitles = parsedSource?.subtitles.orEmpty(),
        )
    }

    private fun parseSource(
        value: JsValue,
        itemId: String,
    ): ParsedSource {
        val source = value.requireObject()
        val streams = mutableListOf<CollapsStream>()

        source["hls"]?.let { raw ->
            streams += CollapsStream(
                id = "$itemId:hls",
                type = CollapsStreamType.HLS,
                qualityHeight = null,
                uri = parseSafeMediaUri(raw.requireString()),
            )
        }
        source["dash"]?.let { raw ->
            streams += CollapsStream(
                id = "$itemId:dash",
                type = CollapsStreamType.DASH,
                qualityHeight = null,
                uri = parseSafeMediaUri(raw.requireString()),
            )
        }
        source["file"]?.let { rawFiles ->
            val files = rawFiles.requireObject()
            if (files.fields.size > MAX_STREAMS_PER_ITEM) {
                throw CollapsMalformedConfigException()
            }
            files.fields.forEach { (qualityLabel, rawUrl) ->
                val quality = qualityLabel.toIntOrNull()?.takeIf { it in 1..MAX_QUALITY_HEIGHT }
                streams += CollapsStream(
                    id = "$itemId:file:${quality ?: "auto"}",
                    type = CollapsStreamType.FILE,
                    qualityHeight = quality,
                    uri = parseSafeMediaUri(rawUrl.requireString()),
                )
            }
        }
        if (streams.size > MAX_STREAMS_PER_ITEM) throw CollapsMalformedConfigException()

        return ParsedSource(
            streams = streams,
            audioTracks = parseAudioTracks(source["audio"]),
            subtitles = parseSubtitles(source["cc"], itemId),
        )
    }

    private fun parseAudioTracks(value: JsValue?): List<CollapsAudioTrack> {
        if (value == null || value === JsNull) return emptyList()
        val audio = value.requireObject()
        val names = audio["names"]?.requireArray()?.values?.map { rawName ->
            rawName.requireString().toDisplayText(MAX_TRACK_NAME_CHARS)
                .ifBlank { throw CollapsMalformedConfigException() }
        }.orEmpty()
        if (names.size > MAX_AUDIO_TRACKS) throw CollapsMalformedConfigException()
        if (names.isEmpty()) return emptyList()

        val order = audio["order"]?.requireArray()?.values?.map { rawIndex ->
            rawIndex.exactIntOrNull() ?: throw CollapsMalformedConfigException()
        }
        val manifestOrder = if (
            order != null &&
            order.size == names.size &&
            order.toSet().size == names.size &&
            order.all { it in names.indices }
        ) {
            order
        } else {
            names.indices.toList()
        }
        return manifestOrder.map { manifestIndex ->
            CollapsAudioTrack(
                manifestTrackIndex = manifestIndex,
                name = names[manifestIndex],
            )
        }
    }

    private fun parseSubtitles(
        value: JsValue?,
        itemId: String,
    ): List<CollapsSubtitle> {
        if (value == null || value === JsNull) return emptyList()
        val entries = when (value) {
            is JsArray -> value.values
            is JsObject -> value.fields.values.toList()
            else -> throw CollapsMalformedConfigException()
        }
        if (entries.size > MAX_SUBTITLES) throw CollapsMalformedConfigException()
        return entries.mapIndexed { index, rawSubtitle ->
            val subtitle = rawSubtitle.requireObject()
            val name = subtitle["name"]
                .scalarTextOrNull()
                .toDisplayText(MAX_TRACK_NAME_CHARS)
                .ifBlank { "Субтитры ${index + 1}" }
            CollapsSubtitle(
                id = "$itemId:subtitle:${index + 1}",
                name = name,
                uri = parseSafeMediaUri(subtitle["url"].requireString()),
            )
        }
    }

    private fun parseSafeMediaUri(rawUrl: String): URI {
        if (
            rawUrl.isBlank() ||
            rawUrl.length > MAX_URL_CHARS ||
            rawUrl != rawUrl.trim() ||
            rawUrl.any(Char::isISOControl) ||
            '\\' in rawUrl
        ) {
            throw CollapsUnsafeUrlException()
        }
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: throw CollapsUnsafeUrlException()
        val host = uri.host ?: throw CollapsUnsafeUrlException()
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.isOpaque ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null ||
            (uri.port != -1 && uri.port != 443) ||
            uri.rawPath.isNullOrBlank() ||
            host.equals("localhost", ignoreCase = true) ||
            host.lowercase(Locale.ROOT).endsWith(".local")
        ) {
            throw CollapsUnsafeUrlException()
        }
        rejectNonPublicIpLiteral(host)
        return uri
    }

    private fun rejectNonPublicIpLiteral(host: String) {
        val looksLikeIpv4 = host.all { it.isDigit() || it == '.' }
        val looksLikeIpv6 = ':' in host
        if (!looksLikeIpv4 && !looksLikeIpv6) return
        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            ?: throw CollapsUnsafeUrlException()
        if (!NetworkAddressPolicy.isPublic(address)) throw CollapsUnsafeUrlException()
    }

    private fun CollapsParsedCatalog.requirePlayable(): CollapsParsedCatalog {
        if (playableItems.none { !it.blocked && it.streams.isNotEmpty() }) {
            throw CollapsNoPlayableItemsException()
        }
        return this
    }

    private data class ParsedSource(
        val streams: List<CollapsStream>,
        val audioTracks: List<CollapsAudioTrack>,
        val subtitles: List<CollapsSubtitle>,
    )

    private const val MAX_HTML_CHARS = 5 * 1024 * 1024
    private const val MAX_TITLE_CHARS = 240
    private const val MAX_ITEM_TITLE_CHARS = 240
    private const val MAX_TRACK_NAME_CHARS = 120
    private const val MAX_URL_CHARS = 12 * 1024
    private const val MAX_STREAMS_PER_ITEM = 32
    private const val MAX_AUDIO_TRACKS = 64
    private const val MAX_SUBTITLES = 128
    private const val MAX_QUALITY_HEIGHT = 16_384
}

internal class CollapsConfigTooLargeException : IllegalArgumentException()
internal class CollapsConfigNotFoundException : IllegalArgumentException()
internal class CollapsMalformedConfigException : IllegalArgumentException()
internal class CollapsRemotePlaylistException : IllegalArgumentException()
internal class CollapsBlockedException : IllegalArgumentException()
internal class CollapsNoPlayableItemsException : IllegalArgumentException()
internal class CollapsUnsafeUrlException : IllegalArgumentException()

private fun JsValue?.asBooleanOrNull(): Boolean? = (this as? JsBoolean)?.value

private fun JsValue?.scalarTextOrNull(): String? = when (this) {
    is JsString -> value
    is JsNumber -> raw
    else -> null
}

private fun JsValue?.requireString(): String =
    (this as? JsString)?.value ?: throw CollapsMalformedConfigException()

private fun JsValue?.requireObject(): JsObject =
    this as? JsObject ?: throw CollapsMalformedConfigException()

private fun JsValue?.requireArray(): JsArray =
    this as? JsArray ?: throw CollapsMalformedConfigException()

private fun JsValue.exactIntOrNull(): Int? {
    val rawNumber = (this as? JsNumber)?.raw ?: return null
    if (!INTEGER.matches(rawNumber)) return null
    return rawNumber.toIntOrNull()
}

private fun String?.toDisplayText(maxChars: Int): String =
    this.orEmpty()
        .asSequence()
        .map { if (it.isISOControl()) ' ' else it }
        .joinToString("")
        .trim()
        .replace(WHITESPACE, " ")
        .take(maxChars)

private fun String?.toShortLabel(fallback: String): String =
    toDisplayText(32).ifBlank { fallback }

private fun String?.toStableId(fallback: String): String {
    val normalized = this.orEmpty()
        .trim()
        .take(96)
        .map { char ->
            if (char.isLetterOrDigit() || char in "._:-") char else '_'
        }
        .joinToString("")
        .trim('_')
    return normalized.ifBlank { fallback }
}

private val INTEGER = Regex("-?(?:0|[1-9][0-9]*)")
private val WHITESPACE = Regex("\\s+")

private sealed interface JsValue
private data class JsObject(val fields: LinkedHashMap<String, JsValue>) : JsValue {
    operator fun get(key: String): JsValue? = fields[key]
}
private data class JsArray(val values: List<JsValue>) : JsValue
private data class JsString(val value: String) : JsValue
private data class JsNumber(val raw: String) : JsValue
private data class JsBoolean(val value: Boolean) : JsValue
private data object JsNull : JsValue

/** Strict parser for JSON-like JavaScript literals (single quotes and bare object keys included). */
private class SafeJsLiteralParser private constructor(
    private val input: String,
) {
    private var index = 0
    private var nodeCount = 0

    fun parseRoot(): JsValue {
        val result = parseValue(depth = 0)
        skipTrivia()
        if (index != input.length) fail()
        return result
    }

    private fun parseValue(depth: Int): JsValue {
        if (depth > MAX_DEPTH || ++nodeCount > MAX_NODES) fail()
        skipTrivia()
        if (index >= input.length) fail()
        return when (input[index]) {
            '{' -> parseObject(depth + 1)
            '[' -> parseArray(depth + 1)
            '\'', '"' -> JsString(parseString())
            '-', in '0'..'9' -> JsNumber(parseNumber())
            else -> when (val identifier = parseIdentifier()) {
                "true" -> JsBoolean(true)
                "false" -> JsBoolean(false)
                "null" -> JsNull
                else -> {
                    identifier.length // Make the rejected token explicit to static analyzers.
                    fail()
                }
            }
        }
    }

    private fun parseObject(depth: Int): JsObject {
        expect('{')
        val fields = linkedMapOf<String, JsValue>()
        skipTrivia()
        if (consume('}')) return JsObject(fields)
        while (true) {
            skipTrivia()
            val key = when (input.getOrNull(index)) {
                '\'', '"' -> parseString()
                in '0'..'9' -> parseUnsignedKey()
                else -> parseIdentifier()
            }
            if (key.isBlank() || fields.containsKey(key)) fail()
            skipTrivia()
            expect(':')
            fields[key] = parseValue(depth)
            skipTrivia()
            if (consume('}')) return JsObject(fields)
            expect(',')
            skipTrivia()
            if (consume('}')) return JsObject(fields)
        }
    }

    private fun parseArray(depth: Int): JsArray {
        expect('[')
        val values = mutableListOf<JsValue>()
        skipTrivia()
        if (consume(']')) return JsArray(values)
        while (true) {
            values += parseValue(depth)
            if (values.size > MAX_ARRAY_ITEMS) fail()
            skipTrivia()
            if (consume(']')) return JsArray(values)
            expect(',')
            skipTrivia()
            if (consume(']')) return JsArray(values)
        }
    }

    private fun parseString(): String {
        val quote = input[index++]
        val result = StringBuilder()
        while (index < input.length) {
            val char = input[index++]
            if (char == quote) {
                if (result.length > MAX_STRING_CHARS) fail()
                return result.toString()
            }
            if (char == '\r' || char == '\n') fail()
            if (char != '\\') {
                result.append(char)
                continue
            }
            if (index >= input.length) fail()
            when (val escaped = input[index++]) {
                '\\', '/', '\'', '"' -> result.append(escaped)
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> result.append(readHexChar(4))
                'x' -> result.append(readHexChar(2))
                else -> fail()
            }
            if (result.length > MAX_STRING_CHARS) fail()
        }
        fail()
    }

    private fun readHexChar(length: Int): Char {
        if (index + length > input.length) fail()
        val value = input.substring(index, index + length)
        if (value.any { it.digitToIntOrNull(16) == null }) fail()
        index += length
        return value.toInt(16).toChar()
    }

    private fun parseNumber(): String {
        val start = index
        consume('-')
        if (consume('0')) {
            if (input.getOrNull(index)?.isDigit() == true) fail()
        } else {
            if (input.getOrNull(index) !in '1'..'9') fail()
            while (input.getOrNull(index)?.isDigit() == true) index++
        }
        if (consume('.')) {
            if (input.getOrNull(index)?.isDigit() != true) fail()
            while (input.getOrNull(index)?.isDigit() == true) index++
        }
        if (input.getOrNull(index) == 'e' || input.getOrNull(index) == 'E') {
            index++
            if (input.getOrNull(index) == '+' || input.getOrNull(index) == '-') index++
            if (input.getOrNull(index)?.isDigit() != true) fail()
            while (input.getOrNull(index)?.isDigit() == true) index++
        }
        return input.substring(start, index)
    }

    private fun parseIdentifier(): String {
        val start = index
        if (!input.getOrNull(index).isIdentifierStart()) fail()
        index++
        while (input.getOrNull(index).isIdentifierPart()) index++
        return input.substring(start, index)
    }

    private fun parseUnsignedKey(): String {
        val start = index
        while (input.getOrNull(index)?.isDigit() == true) index++
        return input.substring(start, index)
    }

    private fun skipTrivia() {
        while (true) {
            while (input.getOrNull(index)?.isWhitespace() == true) index++
            when {
                input.startsWith("//", index) -> {
                    index += 2
                    while (index < input.length && input[index] != '\n') index++
                }
                input.startsWith("/*", index) -> {
                    val end = input.indexOf("*/", index + 2)
                    if (end < 0) fail()
                    index = end + 2
                }
                else -> return
            }
        }
    }

    private fun expect(char: Char) {
        skipTrivia()
        if (!consume(char)) fail()
    }

    private fun consume(char: Char): Boolean {
        if (input.getOrNull(index) != char) return false
        index++
        return true
    }

    private fun fail(): Nothing = throw CollapsMalformedConfigException()

    companion object {
        fun parse(input: String): JsValue {
            if (input.length > MAX_LITERAL_CHARS) throw CollapsConfigTooLargeException()
            return SafeJsLiteralParser(input).parseRoot()
        }

        private const val MAX_LITERAL_CHARS = 4 * 1024 * 1024
        private const val MAX_DEPTH = 48
        private const val MAX_NODES = 20_000
        private const val MAX_ARRAY_ITEMS = 10_000
        private const val MAX_STRING_CHARS = 64 * 1024
    }
}

/** Locates the public player call and slices root properties without evaluating other expressions. */
private object JsEnvelopeScanner {
    fun findMakePlayerObjects(html: String): List<String> {
        val result = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < html.length) {
            val match = html.indexOf(MAKE_PLAYER, startIndex = searchFrom)
            if (match < 0) break
            searchFrom = match + MAKE_PLAYER.length
            if (
                html.getOrNull(match - 1).isIdentifierPart() ||
                html.getOrNull(searchFrom).isIdentifierPart()
            ) {
                continue
            }
            var cursor = skipWhitespace(html, searchFrom)
            if (html.getOrNull(cursor) != '(') continue
            cursor = skipWhitespace(html, cursor + 1)
            if (html.getOrNull(cursor) != '{') continue
            val end = findMatchingObjectEnd(html, cursor) ?: throw CollapsMalformedConfigException()
            val objectLiteral = html.substring(cursor, end + 1)
            if (objectLiteral.length > MAX_OBJECT_CHARS) throw CollapsConfigTooLargeException()
            result += objectLiteral
            searchFrom = end + 1
        }
        return result
    }

    fun topLevelPropertySlices(objectLiteral: String): Map<String, String> {
        if (objectLiteral.firstOrNull() != '{' || objectLiteral.lastOrNull() != '}') {
            throw CollapsMalformedConfigException()
        }
        val result = linkedMapOf<String, String>()
        var cursor = 1
        while (cursor < objectLiteral.lastIndex) {
            cursor = skipTrivia(objectLiteral, cursor)
            if (cursor >= objectLiteral.lastIndex) break
            val keyRead = readPropertyKey(objectLiteral, cursor)
            val key = keyRead.first
            cursor = skipTrivia(objectLiteral, keyRead.second)
            if (objectLiteral.getOrNull(cursor) != ':') throw CollapsMalformedConfigException()
            cursor = skipTrivia(objectLiteral, cursor + 1)
            val valueStart = cursor
            val valueEnd = findTopLevelValueEnd(objectLiteral, valueStart)
            if (key in WANTED_PROPERTIES) {
                if (result.put(key, objectLiteral.substring(valueStart, valueEnd).trim()) != null) {
                    throw CollapsMalformedConfigException()
                }
            }
            cursor = skipTrivia(objectLiteral, valueEnd)
            when (objectLiteral.getOrNull(cursor)) {
                ',' -> cursor++
                '}' -> break
                else -> throw CollapsMalformedConfigException()
            }
        }
        return result
    }

    private fun findMatchingObjectEnd(
        source: String,
        start: Int,
    ): Int? {
        var cursor = start
        var depth = 0
        while (cursor < source.length) {
            when (source[cursor]) {
                '\'', '"' -> cursor = skipQuoted(source, cursor)
                '`' -> cursor = skipTemplate(source, cursor)
                '/' -> cursor = skipSlashToken(source, cursor)
                '{' -> {
                    depth++
                    cursor++
                }
                '}' -> {
                    depth--
                    if (depth == 0) return cursor
                    if (depth < 0) return null
                    cursor++
                }
                else -> cursor++
            }
        }
        return null
    }

    private fun findTopLevelValueEnd(
        source: String,
        start: Int,
    ): Int {
        var cursor = start
        var braces = 0
        var brackets = 0
        var parentheses = 0
        while (cursor < source.length) {
            when (source[cursor]) {
                '\'', '"' -> cursor = skipQuoted(source, cursor)
                '`' -> cursor = skipTemplate(source, cursor)
                '/' -> cursor = skipSlashToken(source, cursor)
                '{' -> {
                    braces++
                    cursor++
                }
                '}' -> {
                    if (braces == 0 && brackets == 0 && parentheses == 0) return cursor
                    braces--
                    if (braces < 0) throw CollapsMalformedConfigException()
                    cursor++
                }
                '[' -> {
                    brackets++
                    cursor++
                }
                ']' -> {
                    brackets--
                    if (brackets < 0) throw CollapsMalformedConfigException()
                    cursor++
                }
                '(' -> {
                    parentheses++
                    cursor++
                }
                ')' -> {
                    parentheses--
                    if (parentheses < 0) throw CollapsMalformedConfigException()
                    cursor++
                }
                ',' -> {
                    if (braces == 0 && brackets == 0 && parentheses == 0) return cursor
                    cursor++
                }
                else -> cursor++
            }
        }
        throw CollapsMalformedConfigException()
    }

    private fun readPropertyKey(
        source: String,
        start: Int,
    ): Pair<String, Int> {
        val first = source.getOrNull(start) ?: throw CollapsMalformedConfigException()
        if (first == '\'' || first == '"') {
            val end = skipQuoted(source, start)
            val parsed = SafeJsLiteralParser.parse(source.substring(start, end)) as? JsString
                ?: throw CollapsMalformedConfigException()
            return parsed.value to end
        }
        if (!first.isIdentifierStart()) throw CollapsMalformedConfigException()
        var cursor = start + 1
        while (source.getOrNull(cursor).isIdentifierPart()) cursor++
        return source.substring(start, cursor) to cursor
    }

    private fun skipQuoted(
        source: String,
        start: Int,
    ): Int {
        val quote = source[start]
        var cursor = start + 1
        while (cursor < source.length) {
            when (source[cursor]) {
                '\\' -> cursor += 2
                quote -> return cursor + 1
                '\r', '\n' -> throw CollapsMalformedConfigException()
                else -> cursor++
            }
        }
        throw CollapsMalformedConfigException()
    }

    private fun skipTemplate(
        source: String,
        start: Int,
    ): Int {
        var cursor = start + 1
        while (cursor < source.length) {
            when (source[cursor]) {
                '\\' -> cursor += 2
                '`' -> return cursor + 1
                else -> cursor++
            }
        }
        throw CollapsMalformedConfigException()
    }

    private fun skipSlashToken(
        source: String,
        start: Int,
    ): Int {
        if (source.startsWith("//", start)) {
            val newline = source.indexOf('\n', start + 2)
            return if (newline < 0) source.length else newline + 1
        }
        if (source.startsWith("/*", start)) {
            val end = source.indexOf("*/", start + 2)
            if (end < 0) throw CollapsMalformedConfigException()
            return end + 2
        }
        if (!looksLikeRegexStart(source, start)) return start + 1

        var cursor = start + 1
        var inCharacterClass = false
        while (cursor < source.length) {
            when (source[cursor]) {
                '\\' -> cursor += 2
                '[' -> {
                    inCharacterClass = true
                    cursor++
                }
                ']' -> {
                    inCharacterClass = false
                    cursor++
                }
                '/' -> if (!inCharacterClass) {
                    cursor++
                    while (source.getOrNull(cursor)?.isLetter() == true) cursor++
                    return cursor
                } else {
                    cursor++
                }
                '\r', '\n' -> return start + 1
                else -> cursor++
            }
        }
        return start + 1
    }

    private fun looksLikeRegexStart(
        source: String,
        slashIndex: Int,
    ): Boolean {
        var cursor = slashIndex - 1
        while (cursor >= 0 && source[cursor].isWhitespace()) cursor--
        if (cursor < 0) return true
        return source[cursor] in "([{=,:;!&|?+-*%^~<>"
    }

    private fun skipTrivia(
        source: String,
        start: Int,
    ): Int {
        var cursor = start
        while (true) {
            cursor = skipWhitespace(source, cursor)
            when {
                source.startsWith("//", cursor) -> {
                    val newline = source.indexOf('\n', cursor + 2)
                    cursor = if (newline < 0) source.length else newline + 1
                }
                source.startsWith("/*", cursor) -> {
                    val end = source.indexOf("*/", cursor + 2)
                    if (end < 0) throw CollapsMalformedConfigException()
                    cursor = end + 2
                }
                else -> return cursor
            }
        }
    }

    private fun skipWhitespace(
        source: String,
        start: Int,
    ): Int {
        var cursor = start
        while (source.getOrNull(cursor)?.isWhitespace() == true) cursor++
        return cursor
    }

    private val WANTED_PROPERTIES = setOf("blocked", "title", "id", "source", "playlist")
    private const val MAKE_PLAYER = "makePlayer"
    private const val MAX_OBJECT_CHARS = 4 * 1024 * 1024
}

private fun Char?.isIdentifierStart(): Boolean =
    this != null && (isLetter() || this == '_' || this == '$')

private fun Char?.isIdentifierPart(): Boolean =
    this != null && (isLetterOrDigit() || this == '_' || this == '$')
