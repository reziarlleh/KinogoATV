package com.kinogo.atv.data.playback.cinemar

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import org.jsoup.Jsoup

internal sealed interface CinemarConfigParseResult {
    data class Parsed(
        val embedUri: URI,
        val catalog: CinemarParsedCatalog,
    ) : CinemarConfigParseResult

    data class Rejected(
        val code: CinemarNativeFailureCode,
    ) : CinemarConfigParseResult
}

/**
 * Parses the JSON argument passed to `Cinemar(...)` without evaluating provider JavaScript.
 *
 * The current public player decodes `file` as:
 * `#2` -> two decimal delimiter digits -> provider slice permutation -> Base64 -> UTF-8 -> JSON.
 * The implementation below mirrors only that data transformation and applies strict size/depth
 * limits before Gson sees the decoded value.
 */
internal class CinemarPublicConfigParser {
    fun parse(
        rawEmbedUrl: String,
        html: String,
    ): CinemarConfigParseResult {
        val embedUri = parseEmbedUri(rawEmbedUrl)
            ?: return CinemarConfigParseResult.Rejected(
                CinemarNativeFailureCode.INVALID_EMBED_ADDRESS,
            )
        if (html.length > MAX_HTML_CHARS) {
            return CinemarConfigParseResult.Rejected(
                CinemarNativeFailureCode.DOCUMENT_TOO_LARGE,
            )
        }

        return try {
            val optionsJson = extractOptionsJson(html)
                ?: return CinemarConfigParseResult.Rejected(
                    CinemarNativeFailureCode.CONFIG_NOT_FOUND,
                )
            val options = JsonParser.parseString(optionsJson).asJsonObject
            val videoId = options.positiveLong("vid")
                ?: return CinemarConfigParseResult.Rejected(
                    CinemarNativeFailureCode.MALFORMED_CONFIG,
                )
            if (!embedVideoIdMatches(embedUri, videoId)) {
                return CinemarConfigParseResult.Rejected(
                    CinemarNativeFailureCode.MALFORMED_CONFIG,
                )
            }

            val playlist = decodePlaylist(options.get("file"))
                ?: return CinemarConfigParseResult.Rejected(
                    CinemarNativeFailureCode.MALFORMED_CONFIG,
                )
            val state = ParseState(embedUri)
            val roots = state.parseNodes(
                values = playlist,
                folderPath = emptyList(),
                indexPath = emptyList(),
                depth = 0,
            )
            if (state.streams.isEmpty() || roots.isEmpty()) {
                CinemarConfigParseResult.Rejected(
                    CinemarNativeFailureCode.NO_PLAYABLE_STREAMS,
                )
            } else {
                CinemarConfigParseResult.Parsed(
                    embedUri = embedUri,
                    catalog = CinemarParsedCatalog(
                        videoId = videoId,
                        roots = roots,
                        streams = state.streams.toList(),
                    ),
                )
            }
        } catch (_: CinemarConfigException) {
            CinemarConfigParseResult.Rejected(CinemarNativeFailureCode.MALFORMED_CONFIG)
        } catch (_: IllegalStateException) {
            CinemarConfigParseResult.Rejected(CinemarNativeFailureCode.MALFORMED_CONFIG)
        } catch (_: RuntimeException) {
            CinemarConfigParseResult.Rejected(CinemarNativeFailureCode.MALFORMED_CONFIG)
        }
    }

    private fun extractOptionsJson(html: String): String? {
        val document = Jsoup.parse(html)
        document.select("script:not([src])").forEach { script ->
            val source = script.data().ifBlank { script.html() }
            findCinemarOptionsObject(source)?.let { return it }
        }
        return null
    }

    /** Locates a call in JavaScript code, never inside a string, template, or comment. */
    private fun findCinemarOptionsObject(source: String): String? {
        var cursor = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        while (cursor < source.length) {
            val char = source[cursor]
            val next = source.getOrNull(cursor + 1)
            when {
                lineComment -> {
                    if (char == '\n' || char == '\r') lineComment = false
                    cursor += 1
                }
                blockComment -> {
                    if (char == '*' && next == '/') {
                        blockComment = false
                        cursor += 2
                    } else {
                        cursor += 1
                    }
                }
                quote != null -> {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == quote -> quote = null
                    }
                    cursor += 1
                }
                char == '/' && next == '/' -> {
                    lineComment = true
                    cursor += 2
                }
                char == '/' && next == '*' -> {
                    blockComment = true
                    cursor += 2
                }
                char == '"' || char == '\'' || char == '`' -> {
                    quote = char
                    cursor += 1
                }
                source.regionMatches(cursor, CINEMAR_FUNCTION, 0, CINEMAR_FUNCTION.length) -> {
                    val nameEnd = cursor + CINEMAR_FUNCTION.length
                    if (hasIdentifierBoundary(source, cursor, nameEnd)) {
                        var argument = skipWhitespace(source, nameEnd)
                        if (argument < source.length && source[argument] == '(') {
                            argument = skipWhitespace(source, argument + 1)
                            if (argument < source.length && source[argument] == '{') {
                                return balancedJsonObject(source, argument)
                            }
                        }
                    }
                    cursor = nameEnd
                }
                else -> cursor += 1
            }
        }
        return null
    }

    private fun hasIdentifierBoundary(
        source: String,
        start: Int,
        endExclusive: Int,
    ): Boolean {
        fun Char.isJavaScriptIdentifierPart(): Boolean =
            isLetterOrDigit() || this == '_' || this == '$'

        val before = source.getOrNull(start - 1)
        val after = source.getOrNull(endExclusive)
        return before?.isJavaScriptIdentifierPart() != true &&
            after?.isJavaScriptIdentifierPart() != true
    }

    private fun balancedJsonObject(
        source: String,
        objectStart: Int,
    ): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in objectStart until source.length) {
            val char = source[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> {
                    depth += 1
                    if (depth > MAX_JSON_DEPTH) return null
                }
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(objectStart, index + 1)
                    if (depth < 0) return null
                }
            }
        }
        return null
    }

    private fun decodePlaylist(file: JsonElement?): JsonArray? {
        if (file == null || file.isJsonNull) return null
        if (file.isJsonArray) return file.asJsonArray
        if (!file.isJsonPrimitive || !file.asJsonPrimitive.isString) return null
        val packed = file.asString
        if (!packed.startsWith(V2_PREFIX) || packed.length > MAX_PACKED_CHARS) return null

        val envelope = packed.substring(V2_PREFIX.length)
        if (envelope.length < 3) return null
        val delimiterCode = envelope.substring(0, 2).toIntOrNull()
            ?.takeIf { it in PRINTABLE_ASCII_RANGE }
            ?: return null
        val delimiter = delimiterCode.toChar()
        val encoded = buildString(envelope.length) {
            splitPreservingEmpty(envelope.substring(2), delimiter).forEach { segment ->
                if (segment.isEmpty()) throw CinemarConfigException()
                if (segment.length <= PROVIDER_SLICE_THRESHOLD) {
                    append(segment)
                } else {
                    val rotation = segment.last().digitToIntOrNull()
                        ?.takeIf { it in 0..MAX_ROTATION }
                        ?: throw CinemarConfigException()
                    val bodyStart = rotation * 2
                    val bodyEnd = segment.length - rotation - 1
                    if (bodyStart >= bodyEnd || rotation > segment.length) {
                        throw CinemarConfigException()
                    }
                    append(segment, bodyStart, bodyEnd)
                    append(segment, 0, rotation)
                }
            }
        }
        if (encoded.length > MAX_BASE64_CHARS) return null
        val padding = (4 - encoded.length % 4) % 4
        val padded = encoded + "=".repeat(padding)
        val bytes = runCatching { Base64.getDecoder().decode(padded) }.getOrNull() ?: return null
        if (bytes.size > MAX_DECODED_BYTES) return null
        val json = strictUtf8(bytes) ?: return null
        if (!hasBoundedJsonDepth(json)) return null
        val parsed = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        return parsed.takeIf(JsonElement::isJsonArray)?.asJsonArray
    }

    private fun splitPreservingEmpty(
        value: String,
        delimiter: Char,
    ): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        value.forEachIndexed { index, char ->
            if (char == delimiter) {
                result += value.substring(start, index)
                start = index + 1
            }
        }
        result += value.substring(start)
        return result
    }

    private fun strictUtf8(bytes: ByteArray): String? =
        runCatching {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()

    private fun hasBoundedJsonDepth(value: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        value.forEach { char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth += 1
                        if (depth > MAX_JSON_DEPTH) return false
                    }
                    '}', ']' -> {
                        depth -= 1
                        if (depth < 0) return false
                    }
                }
            }
        }
        return depth == 0 && !inString && !escaped
    }

    private fun parseEmbedUri(rawUrl: String): URI? {
        val uri = structurallySafeHttpsUri(rawUrl) ?: return null
        val path = uri.rawPath ?: return null
        if (!path.startsWith(EMBED_PATH_PREFIX) || path.length == EMBED_PATH_PREFIX.length) {
            return null
        }
        return uri
    }

    private fun embedVideoIdMatches(
        embedUri: URI,
        videoId: Long,
    ): Boolean {
        val pathId = embedUri.rawPath
            ?.removePrefix(EMBED_PATH_PREFIX)
            ?.substringBefore('/')
            ?.toLongOrNull()
        return pathId == null || pathId == videoId
    }

    private inner class ParseState(
        private val embedUri: URI,
    ) {
        val streams = mutableListOf<CinemarStream>()
        private val nodeIds = linkedSetOf<String>()
        private var nodeCount = 0

        fun parseNodes(
            values: JsonArray,
            folderPath: List<CinemarFolderPathEntry>,
            indexPath: List<Int>,
            depth: Int,
        ): List<CinemarPlaylistNode> {
            if (depth > MAX_PLAYLIST_DEPTH || values.size() > MAX_NODES) {
                throw CinemarConfigException()
            }
            return buildList {
                values.forEachIndexed { index, element ->
                    if (++nodeCount > MAX_NODES || !element.isJsonObject) {
                        throw CinemarConfigException()
                    }
                    val node = element.asJsonObject
                    val currentIndexPath = indexPath + index
                    val providerNodeId = node.string("pjs_id") ?: node.string("id")
                    val nodeId = uniqueNodeId(providerNodeId, currentIndexPath)
                    val folder = node.get("folder")
                    if (folder?.isJsonArray == true) {
                        val title = cleanLabel(node.string("title"), "Раздел ${index + 1}")
                        val pathEntry = CinemarFolderPathEntry(nodeId, title)
                        val children = parseNodes(
                            values = folder.asJsonArray,
                            folderPath = folderPath + pathEntry,
                            indexPath = currentIndexPath,
                            depth = depth + 1,
                        )
                        if (children.isNotEmpty()) {
                            add(CinemarFolder(nodeId, title, children))
                        }
                    } else {
                        parseStream(
                            node = node,
                            nodeId = nodeId,
                            providerNodeId = providerNodeId,
                            folderPath = folderPath,
                        )?.let { stream ->
                            streams += stream
                            add(stream)
                        }
                    }
                }
            }
        }

        private fun parseStream(
            node: JsonObject,
            nodeId: String,
            providerNodeId: String?,
            folderPath: List<CinemarFolderPathEntry>,
        ): CinemarStream? {
            val mediaVariants = parseMediaVariants(node.string("file"), embedUri, nodeId)
            if (mediaVariants.isEmpty()) return null
            val title = cleanLabel(node.string("title"), "Перевод")
            val contextTitle = node.string("title2")
                ?.let { cleanLabel(it, "") }
                ?.takeIf(String::isNotBlank)
            val durationMs = node.positiveLong("duration")
                ?.takeIf { it <= MAX_DURATION_SECONDS }
                ?.times(1_000L)
            return CinemarStream(
                id = nodeId,
                title = title,
                contextTitle = contextTitle,
                providerNodeId = providerNodeId,
                sourceId = node.scalarString("src_id"),
                voiceId = node.scalarString("voice_id") ?: nodeId,
                durationMs = durationMs,
                folderPath = folderPath,
                mediaVariants = mediaVariants,
                subtitles = parseSubtitles(node.string("subtitle"), embedUri, nodeId),
            )
        }

        private fun uniqueNodeId(
            providerId: String?,
            indexPath: List<Int>,
        ): String {
            val candidate = providerId
                ?.takeIf(String::isNotBlank)
                ?.take(MAX_ID_CHARS)
                ?: "node-${indexPath.joinToString("-")}"
            if (!nodeIds.add(candidate)) throw CinemarConfigException()
            return candidate
        }
    }

    private fun parseMediaVariants(
        rawFile: String?,
        embedUri: URI,
        streamId: String,
    ): List<CinemarMediaVariant> {
        val file = rawFile?.takeIf { it.length <= MAX_URL_LIST_CHARS } ?: return emptyList()
        val labelled = parseLabelledValues(file)
        val entries = labelled ?: listOf(null to file)
        return entries.mapIndexedNotNull { index, (rawLabel, rawUrl) ->
            val endpoint = parseMediaEndpoint(rawUrl, embedUri) ?: return@mapIndexedNotNull null
            CinemarMediaVariant(
                id = "$streamId:quality:$index",
                label = cleanLabel(rawLabel, if (labelled == null) "Авто" else "Вариант ${index + 1}"),
                kind = endpoint.second,
                url = CinemarTransientUrl(endpoint.first),
            )
        }
    }

    private fun parseSubtitles(
        rawSubtitles: String?,
        embedUri: URI,
        streamId: String,
    ): List<CinemarSubtitle> {
        val value = rawSubtitles
            ?.takeIf(String::isNotBlank)
            ?.takeIf { it.length <= MAX_URL_LIST_CHARS }
            ?: return emptyList()
        val entries = parseLabelledValues(value) ?: listOf(null to value)
        return entries.mapIndexedNotNull { index, (rawLabel, rawUrl) ->
            val endpoint = parseSubtitleEndpoint(rawUrl, embedUri) ?: return@mapIndexedNotNull null
            CinemarSubtitle(
                id = "$streamId:subtitle:$index",
                label = cleanLabel(rawLabel, "Субтитры ${index + 1}"),
                kind = endpoint.second,
                url = CinemarTransientUrl(endpoint.first),
            )
        }
    }

    /**
     * PlayerJS/Cinemar uses `[label]url,[label]url`. Unlabelled values return null so a normal URL
     * containing a comma is never split accidentally.
     */
    private fun parseLabelledValues(value: String): List<Pair<String?, String>>? {
        if (!value.startsWith('[')) return null
        val result = mutableListOf<Pair<String?, String>>()
        var cursor = 0
        while (cursor < value.length) {
            if (value[cursor] != '[') return emptyList()
            val labelEnd = value.indexOf(']', cursor + 1)
            if (labelEnd < 0 || labelEnd - cursor > MAX_LABEL_CHARS) return emptyList()
            val next = findNextLabel(value, labelEnd + 1)
            val rawUrl = value.substring(labelEnd + 1, next).trim()
            if (rawUrl.isEmpty()) return emptyList()
            result += value.substring(cursor + 1, labelEnd) to rawUrl
            cursor = if (next == value.length) value.length else {
                var nextLabel = next + 1
                while (nextLabel < value.length && value[nextLabel].isWhitespace()) nextLabel++
                nextLabel
            }
        }
        return result
    }

    private fun findNextLabel(
        value: String,
        from: Int,
    ): Int {
        var cursor = from
        while (cursor < value.length) {
            if (value[cursor] == ',') {
                var lookAhead = cursor + 1
                while (lookAhead < value.length && value[lookAhead].isWhitespace()) lookAhead++
                if (lookAhead < value.length && value[lookAhead] == '[') return cursor
            }
            cursor++
        }
        return value.length
    }

    private fun parseMediaEndpoint(
        rawUrl: String,
        embedUri: URI,
    ): Pair<URI, CinemarMediaKind>? {
        val uri = resolveHttpsEndpoint(rawUrl, embedUri) ?: return null
        val path = uri.path?.lowercase(Locale.ROOT) ?: return null
        val kind = when {
            path.endsWith(".m3u8") -> CinemarMediaKind.HLS
            path.endsWith(".mpd") -> CinemarMediaKind.DASH
            path.endsWith(".mp4") -> CinemarMediaKind.MP4
            else -> return null
        }
        return uri to kind
    }

    private fun parseSubtitleEndpoint(
        rawUrl: String,
        embedUri: URI,
    ): Pair<URI, CinemarSubtitleKind>? {
        val uri = resolveHttpsEndpoint(rawUrl, embedUri) ?: return null
        val path = uri.path?.lowercase(Locale.ROOT) ?: return null
        val kind = when {
            path.endsWith(".vtt") -> CinemarSubtitleKind.WEBVTT
            path.endsWith(".srt") -> CinemarSubtitleKind.SUBRIP
            path.endsWith(".ass") || path.endsWith(".ssa") -> CinemarSubtitleKind.SSA
            else -> return null
        }
        return uri to kind
    }

    private fun resolveHttpsEndpoint(
        rawUrl: String,
        embedUri: URI,
    ): URI? {
        if (
            rawUrl.isBlank() ||
            rawUrl.length > MAX_URL_CHARS ||
            rawUrl.any(Char::isISOControl) ||
            '\\' in rawUrl ||
            rawUrl.any(Char::isWhitespace)
        ) {
            return null
        }
        val absolute = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") -> {
                val origin = URI("https", null, embedUri.host, embedUri.port, "/", null, null)
                origin.resolve(rawUrl).toASCIIString()
            }
            else -> rawUrl
        }
        return structurallySafeHttpsUri(absolute)
    }

    private fun structurallySafeHttpsUri(rawUrl: String): URI? {
        if (
            rawUrl.isBlank() ||
            rawUrl != rawUrl.trim() ||
            rawUrl.length > MAX_URL_CHARS ||
            rawUrl.any(Char::isISOControl) ||
            rawUrl.any(Char::isWhitespace) ||
            '\\' in rawUrl
        ) {
            return null
        }
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.isOpaque ||
            uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null ||
            (uri.port != -1 && uri.port != 443)
        ) {
            return null
        }
        return uri
    }

    private fun cleanLabel(
        raw: String?,
        fallback: String,
    ): String {
        val plain = raw
            ?.take(MAX_RAW_LABEL_CHARS)
            ?.let { Jsoup.parseBodyFragment(it).text() }
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.take(MAX_LABEL_CHARS)
            .orEmpty()
        return plain.ifBlank { fallback }
    }

    private fun skipWhitespace(
        source: String,
        start: Int,
    ): Int {
        var cursor = start
        while (cursor < source.length && source[cursor].isWhitespace()) cursor++
        return cursor
    }

    private fun JsonObject.string(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString
    }

    private fun JsonObject.scalarString(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) return null
        return runCatching { value.asString }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun JsonObject.positiveLong(name: String): Long? =
        scalarString(name)?.toLongOrNull()?.takeIf { it > 0L }

    private class CinemarConfigException : RuntimeException()

    private companion object {
        const val CINEMAR_FUNCTION = "Cinemar"
        const val V2_PREFIX = "#2"
        const val EMBED_PATH_PREFIX = "/embed/"
        const val MAX_HTML_CHARS = 2 * 1_024 * 1_024
        const val MAX_PACKED_CHARS = 1 * 1_024 * 1_024
        const val MAX_BASE64_CHARS = 1 * 1_024 * 1_024
        const val MAX_DECODED_BYTES = 2 * 1_024 * 1_024
        const val MAX_URL_LIST_CHARS = 128 * 1_024
        const val MAX_URL_CHARS = 8 * 1_024
        const val MAX_JSON_DEPTH = 24
        const val MAX_PLAYLIST_DEPTH = 8
        const val MAX_NODES = 2_000
        const val MAX_ROTATION = 9
        const val PROVIDER_SLICE_THRESHOLD = 32
        const val MAX_DURATION_SECONDS = 7 * 24 * 60 * 60L
        const val MAX_ID_CHARS = 160
        const val MAX_LABEL_CHARS = 160
        const val MAX_RAW_LABEL_CHARS = 2_048
        val PRINTABLE_ASCII_RANGE = 33..126
        val WHITESPACE = Regex("\\s+")
    }
}
