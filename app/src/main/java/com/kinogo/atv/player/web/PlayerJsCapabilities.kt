package com.kinogo.atv.player.web

/** A display-only PlayerJS option. Switching is always performed by [index]. */
data class PlayerJsCapabilityOption(
    val index: Int,
    val title: String,
    val id: String?,
)

/**
 * Sanitized playlist metadata. It deliberately has no source, poster, subtitle URL or arbitrary
 * property bag. [path] is stable only for the capability snapshot in which it was returned.
 */
data class PlayerJsPlaylistNode(
    val index: Int,
    val path: List<Int>,
    val title: String,
    val id: String?,
    val children: List<PlayerJsPlaylistNode>,
)

/** A bounded, URL-free snapshot produced by [PlayerJsCapabilitiesRequestBuilder]. */
data class PlayerJsCapabilities(
    val playerAvailable: Boolean,
    val qualities: List<PlayerJsCapabilityOption>,
    val audioTracks: List<PlayerJsCapabilityOption>,
    val subtitles: List<PlayerJsCapabilityOption>,
    val playlistId: String?,
    val playlistFolders: List<PlayerJsPlaylistNode>,
    val timeSeconds: Double?,
    val durationSeconds: Double?,
)

sealed interface PlayerJsCapabilitiesParseResult {
    data class Parsed(val capabilities: PlayerJsCapabilities) : PlayerJsCapabilitiesParseResult

    data class Rejected(val reason: Reason) : PlayerJsCapabilitiesParseResult

    enum class Reason {
        Empty,
        TooLarge,
        MalformedJson,
        UnsupportedSchema,
        InvalidPayload,
    }
}

/**
 * Parses either direct JSON or the JSON-string-wrapped value returned by WebView's
 * evaluateJavascript callback. It never throws for provider-controlled input.
 */
object PlayerJsCapabilitiesJsonParser {
    fun parse(rawResult: String?): PlayerJsCapabilitiesParseResult {
        if (rawResult.isNullOrBlank()) {
            return PlayerJsCapabilitiesParseResult.Rejected(
                PlayerJsCapabilitiesParseResult.Reason.Empty,
            )
        }
        if (rawResult.length > MAX_JSON_CHARS) {
            return PlayerJsCapabilitiesParseResult.Rejected(
                PlayerJsCapabilitiesParseResult.Reason.TooLarge,
            )
        }

        val outer = try {
            BoundedJsonParser(rawResult).parse()
        } catch (_: BoundedJsonException) {
            return PlayerJsCapabilitiesParseResult.Rejected(
                PlayerJsCapabilitiesParseResult.Reason.MalformedJson,
            )
        }
        val payload = when (outer) {
            is JsonText -> {
                if (outer.value.length > MAX_JSON_CHARS) {
                    return PlayerJsCapabilitiesParseResult.Rejected(
                        PlayerJsCapabilitiesParseResult.Reason.TooLarge,
                    )
                }
                try {
                    BoundedJsonParser(outer.value).parse()
                } catch (_: BoundedJsonException) {
                    return PlayerJsCapabilitiesParseResult.Rejected(
                        PlayerJsCapabilitiesParseResult.Reason.MalformedJson,
                    )
                }
            }

            else -> outer
        }

        val root = payload as? JsonMap ?: return PlayerJsCapabilitiesParseResult.Rejected(
            PlayerJsCapabilitiesParseResult.Reason.InvalidPayload,
        )
        val schema = root.values["schema"].exactIntOrNull()
        if (schema != SCHEMA_VERSION) {
            return PlayerJsCapabilitiesParseResult.Rejected(
                PlayerJsCapabilitiesParseResult.Reason.UnsupportedSchema,
            )
        }

        return try {
            PlayerJsCapabilitiesParseResult.Parsed(parseCapabilities(root))
        } catch (_: InvalidCapabilitiesPayload) {
            PlayerJsCapabilitiesParseResult.Rejected(
                PlayerJsCapabilitiesParseResult.Reason.InvalidPayload,
            )
        }
    }

    fun parseOrNull(rawResult: String?): PlayerJsCapabilities? =
        (parse(rawResult) as? PlayerJsCapabilitiesParseResult.Parsed)?.capabilities

    private fun parseCapabilities(root: JsonMap): PlayerJsCapabilities {
        val playerAvailable = (root.values["playerAvailable"] as? JsonBoolean)?.value
            ?: invalidPayload()
        val nodeCounter = NodeCounter()
        return PlayerJsCapabilities(
            playerAvailable = playerAvailable,
            qualities = parseOptions(root.requiredArray("qualities")),
            audioTracks = parseOptions(root.requiredArray("audioTracks")),
            subtitles = parseOptions(root.requiredArray("subtitles")),
            playlistId = root.optionalSafeText("playlistId", MAX_ID_LENGTH),
            playlistFolders = parsePlaylistNodes(
                values = root.requiredArray("playlistFolders"),
                expectedParentPath = emptyList(),
                depth = 0,
                counter = nodeCounter,
            ),
            timeSeconds = root.optionalSeconds("timeSeconds"),
            durationSeconds = root.optionalSeconds("durationSeconds"),
        )
    }

    private fun parseOptions(values: List<JsonValue>): List<PlayerJsCapabilityOption> {
        if (values.size > MAX_OPTIONS) invalidPayload()
        val seenIndexes = mutableSetOf<Int>()
        return values.map { value ->
            val item = value as? JsonMap ?: invalidPayload()
            val index = item.values["index"].exactIntOrNull()
                ?.takeIf { it in 0..MAX_OPTION_INDEX }
                ?: invalidPayload()
            if (!seenIndexes.add(index)) invalidPayload()
            PlayerJsCapabilityOption(
                index = index,
                title = item.requiredSafeText("title", MAX_TITLE_LENGTH),
                id = item.optionalSafeText("id", MAX_ID_LENGTH),
            )
        }
    }

    private fun parsePlaylistNodes(
        values: List<JsonValue>,
        expectedParentPath: List<Int>,
        depth: Int,
        counter: NodeCounter,
    ): List<PlayerJsPlaylistNode> {
        if (depth > MAX_PLAYLIST_DEPTH || values.size > MAX_PLAYLIST_NODES) invalidPayload()
        return values.map { value ->
            counter.value += 1
            if (counter.value > MAX_PLAYLIST_NODES) invalidPayload()

            val item = value as? JsonMap ?: invalidPayload()
            val index = item.values["index"].exactIntOrNull()
                ?.takeIf { it in 0..MAX_PLAYLIST_NODES }
                ?: invalidPayload()
            val path = item.requiredArray("path").map { pathPart ->
                pathPart.exactIntOrNull()
                    ?.takeIf { it in 0..MAX_PLAYLIST_NODES }
                    ?: invalidPayload()
            }
            if (path != expectedParentPath + index) invalidPayload()

            PlayerJsPlaylistNode(
                index = index,
                path = path,
                title = item.requiredSafeText("title", MAX_TITLE_LENGTH),
                id = item.optionalSafeText("id", MAX_ID_LENGTH),
                children = parsePlaylistNodes(
                    values = item.requiredArray("children"),
                    expectedParentPath = path,
                    depth = depth + 1,
                    counter = counter,
                ),
            )
        }
    }

    private fun JsonMap.requiredArray(name: String): List<JsonValue> =
        (values[name] as? JsonArray)?.values ?: invalidPayload()

    private fun JsonMap.requiredSafeText(name: String, maxLength: Int): String =
        (values[name] as? JsonText)?.value
            ?.takeIf { it.isSafeCapabilityText(maxLength) }
            ?: invalidPayload()

    private fun JsonMap.optionalSafeText(name: String, maxLength: Int): String? =
        when (val value = values[name]) {
            JsonNull -> null
            is JsonText -> value.value.takeIf { it.isSafeCapabilityText(maxLength) }
                ?: invalidPayload()
            else -> invalidPayload()
        }

    private fun JsonMap.optionalSeconds(name: String): Double? = when (val value = values[name]) {
        JsonNull -> null
        is JsonNumber -> value.value.takeIf {
            it.isFinite() && it >= 0.0 && it <= MAX_TIME_SECONDS
        } ?: invalidPayload()
        else -> invalidPayload()
    }

    private fun String.isSafeCapabilityText(maxLength: Int): Boolean =
        isNotBlank() &&
            length <= maxLength &&
            none(Char::isISOControl) &&
            !looksLikeMediaLocation()

    private fun JsonValue?.exactIntOrNull(): Int? {
        val number = (this as? JsonNumber)?.value ?: return null
        if (!number.isFinite() || number % 1.0 != 0.0) return null
        return number.takeIf { it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
    }

    private class NodeCounter(var value: Int = 0)

    private class InvalidCapabilitiesPayload : RuntimeException()

    private fun invalidPayload(): Nothing = throw InvalidCapabilitiesPayload()

    private const val SCHEMA_VERSION = 1
    private const val MAX_JSON_CHARS = 256 * 1024
    private const val MAX_OPTIONS = 64
    private const val MAX_OPTION_INDEX = 255
    private const val MAX_PLAYLIST_NODES = 512
    private const val MAX_PLAYLIST_DEPTH = 8
    private const val MAX_TITLE_LENGTH = 160
    private const val MAX_ID_LENGTH = 128
    private const val MAX_TIME_SECONDS = 10.0 * 365 * 24 * 60 * 60
}

private sealed interface JsonValue

private data class JsonMap(val values: Map<String, JsonValue>) : JsonValue

private data class JsonArray(val values: List<JsonValue>) : JsonValue

private data class JsonText(val value: String) : JsonValue

private data class JsonNumber(val value: Double) : JsonValue

private data class JsonBoolean(val value: Boolean) : JsonValue

private data object JsonNull : JsonValue

private class BoundedJsonException : RuntimeException()

/** Purpose-built JSON reader so provider-controlled results never depend on Android's stub JSON. */
private class BoundedJsonParser(private val source: String) {
    private var position = 0

    fun parse(): JsonValue {
        skipWhitespace()
        val value = readValue(depth = 0)
        skipWhitespace()
        if (position != source.length) malformed()
        return value
    }

    private fun readValue(depth: Int): JsonValue {
        if (depth > MAX_DEPTH || position >= source.length) malformed()
        return when (source[position]) {
            '{' -> readObject(depth + 1)
            '[' -> readArray(depth + 1)
            '"' -> JsonText(readString())
            't' -> readLiteral("true", JsonBoolean(true))
            'f' -> readLiteral("false", JsonBoolean(false))
            'n' -> readLiteral("null", JsonNull)
            '-', in '0'..'9' -> readNumber()
            else -> malformed()
        }
    }

    private fun readObject(depth: Int): JsonMap {
        position += 1
        skipWhitespace()
        val result = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonMap(result)
        while (true) {
            if (position >= source.length || source[position] != '"') malformed()
            val key = readString()
            skipWhitespace()
            if (!consume(':')) malformed()
            skipWhitespace()
            if (result.put(key, readValue(depth)) != null) malformed()
            skipWhitespace()
            if (consume('}')) return JsonMap(result)
            if (!consume(',')) malformed()
            skipWhitespace()
        }
    }

    private fun readArray(depth: Int): JsonArray {
        position += 1
        skipWhitespace()
        val result = mutableListOf<JsonValue>()
        if (consume(']')) return JsonArray(result)
        while (true) {
            if (result.size >= MAX_ARRAY_ITEMS) malformed()
            result += readValue(depth)
            skipWhitespace()
            if (consume(']')) return JsonArray(result)
            if (!consume(',')) malformed()
            skipWhitespace()
        }
    }

    private fun readString(): String {
        if (!consume('"')) malformed()
        val result = StringBuilder()
        while (position < source.length) {
            when (val character = source[position++]) {
                '"' -> return result.toString()
                '\\' -> {
                    if (position >= source.length) malformed()
                    when (val escaped = source[position++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> result.append(readUnicodeEscape())
                        else -> malformed()
                    }
                }

                else -> {
                    if (character.code < 0x20) malformed()
                    result.append(character)
                }
            }
            if (result.length > MAX_STRING_CHARS) malformed()
        }
        malformed()
    }

    private fun readUnicodeEscape(): Char {
        if (position + 4 > source.length) malformed()
        var value = 0
        repeat(4) {
            value = value * 16 + source[position++].digitToIntOrNull(16).orMalformed()
        }
        return value.toChar()
    }

    private fun readNumber(): JsonNumber {
        val start = position
        consume('-')
        if (consume('0')) {
            if (position < source.length && source[position].isDigit()) malformed()
        } else {
            readDigits(required = true)
        }
        if (consume('.')) readDigits(required = true)
        if (position < source.length && (source[position] == 'e' || source[position] == 'E')) {
            position += 1
            if (position < source.length && (source[position] == '+' || source[position] == '-')) {
                position += 1
            }
            readDigits(required = true)
        }
        val number = source.substring(start, position).toDoubleOrNull()
            ?.takeIf(Double::isFinite)
            ?: malformed()
        return JsonNumber(number)
    }

    private fun readDigits(required: Boolean) {
        val start = position
        while (position < source.length && source[position].isDigit()) position += 1
        if (required && start == position) malformed()
    }

    private fun <T : JsonValue> readLiteral(text: String, value: T): T {
        if (!source.regionMatches(position, text, 0, text.length)) malformed()
        position += text.length
        return value
    }

    private fun consume(expected: Char): Boolean {
        if (position >= source.length || source[position] != expected) return false
        position += 1
        return true
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position] in " \t\r\n") position += 1
    }

    private fun Int?.orMalformed(): Int = this ?: malformed()

    private fun malformed(): Nothing = throw BoundedJsonException()

    private companion object {
        const val MAX_DEPTH = 16
        const val MAX_ARRAY_ITEMS = 1024
        const val MAX_STRING_CHARS = 256 * 1024
    }
}
