package com.kinogo.atv.data.catalog

import com.kinogo.atv.data.mirror.KinogoHtmlFingerprint
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class HtmlResponse(
    val requestedOrigin: String,
    val resolvedOrigin: String,
    val relativePath: String,
    val statusCode: Int,
    val body: String,
)

/** Stateful HTML transport required by the server-side xSort catalog protocol. */
interface CatalogFilterHtmlTransport {
    suspend fun get(rawOrigin: String, rawRelativePath: String): HtmlResponse

    /**
     * Opaque per-origin version of the cookie session.
     *
     * Implementations must change it whenever the stored cookie name/value set actually changes.
     * No cookie name or value crosses this boundary.
     */
    fun sessionEpoch(rawOrigin: String): Long

    suspend fun postCatalogForm(
        rawOrigin: String,
        rawRelativePath: String,
        form: Map<String, String>,
    ): HtmlResponse
}

/** Canonicalizes a route without ever allowing it to replace the selected mirror origin. */
internal object CatalogRouteNormalizer {
    private val encodedControl = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)
    private val encodedSeparatorOrPercent = Regex("%(?:2f|5c|25)", RegexOption.IGNORE_CASE)
    private val encodedDot = Regex("%2e", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String {
        require(raw.startsWith('/') && !raw.startsWith("//")) {
            "Catalog path must be relative to the selected mirror"
        }
        require(raw.none { it == '\\' || it == '\u0000' || it.isWhitespace() || it.code == 0x7f }) {
            "Invalid catalog path"
        }
        require(!encodedControl.containsMatchIn(raw)) { "Encoded control characters are not allowed" }

        val uri =
            try {
                URI(raw)
            } catch (error: Exception) {
                throw IllegalArgumentException("Malformed catalog path", error)
            }
        require(!uri.isOpaque && uri.scheme == null && uri.rawAuthority == null) {
            "Absolute URL is not allowed"
        }
        require(uri.rawUserInfo == null) { "User info is not allowed" }
        require(uri.rawFragment == null) { "Fragments are not allowed in catalog paths" }

        val path = uri.rawPath ?: "/"
        require(path.startsWith('/') && !path.startsWith("//")) {
            "Catalog path must be absolute within the origin"
        }
        require(!encodedSeparatorOrPercent.containsMatchIn(path)) {
            "Encoded path separators and percent signs are not allowed"
        }
        require(
            path.split('/').none { segment ->
                val dotDecoded = encodedDot.replace(segment, ".")
                dotDecoded == "." || dotDecoded == ".."
            },
        ) { "Dot path segments are not allowed" }

        // URI keeps existing escapes intact and percent-encodes non-ASCII route characters.
        return uri.toASCIIString()
    }
}

/** Bounded byte reader and deterministic HTTP metadata decoder, independent from networking. */
internal class CatalogHtmlBodyDecoder(private val maxBodyBytes: Int) {
    init {
        require(maxBodyBytes > 0)
    }

    suspend fun read(
        input: InputStream?,
        contentType: String?,
        declaredLength: Long = -1L,
    ): String {
        val charset = CatalogHtmlMetadata.charsetFor(contentType)
        if (declaredLength > maxBodyBytes.toLong()) {
            throw CatalogResponseTooLargeException(maxBodyBytes)
        }

        if (input == null) return ""
        val bytes = try {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1_024)
            var total = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > maxBodyBytes) throw CatalogResponseTooLargeException(maxBodyBytes)
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } finally {
            // As with disconnect(), a cleanup failure must not mask cancellation.
            runCatching(input::close)
        }
        return bytes.toString(charset)
    }
}

internal object CatalogHtmlMetadata {
    private val supportedMediaTypes = setOf("text/html", "application/xhtml+xml")
    private val charsetParameter =
        Regex(
            "(?:^|;)\\s*charset\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^;\\s]+))",
            RegexOption.IGNORE_CASE,
        )
    private val charsetAssignment =
        Regex("(?:^|;)\\s*charset\\s*=", RegexOption.IGNORE_CASE)

    fun charsetFor(contentType: String?): Charset {
        if (contentType.isNullOrBlank()) return Charsets.UTF_8

        val mediaType = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mediaType !in supportedMediaTypes) throw CatalogContentTypeException(contentType)

        val charsetNames = charsetParameter.findAll(contentType).mapNotNull { match ->
            match.groupValues.drop(1).firstOrNull(String::isNotEmpty)?.trim()
        }.toList()
        if (charsetAssignment.containsMatchIn(contentType) && charsetNames.isEmpty()) {
            throw CatalogCharsetException("")
        }
        val distinctNames = charsetNames.distinctBy { it.lowercase(Locale.ROOT) }
        if (distinctNames.size > 1) {
            throw CatalogCharsetException(distinctNames.joinToString())
        }
        val charsetName = distinctNames.singleOrNull() ?: return Charsets.UTF_8
        return try {
            Charset.forName(charsetName)
        } catch (_: Exception) {
            throw CatalogCharsetException(charsetName)
        }
    }
}

internal object CatalogHtmlDocumentPolicy {
    fun validate(body: String) {
        if (KinogoHtmlFingerprint.isChallenge(body)) throw CatalogChallengeException()
        if (!KinogoHtmlFingerprint.matches(body)) throw CatalogFingerprintException()
    }
}
