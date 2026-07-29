package com.kinogo.atv.data.catalog

import com.kinogo.atv.data.mirror.KinogoHtmlFingerprint
import com.kinogo.atv.data.mirror.MirrorUrlNormalizer
import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class HtmlResponse(
    val requestedOrigin: String,
    val resolvedOrigin: String,
    val relativePath: String,
    val statusCode: Int,
    val body: String,
)

fun interface HtmlTransport {
    suspend fun get(rawOrigin: String, rawRelativePath: String): HtmlResponse
}

/**
 * Small HTTPS-only transport for untrusted mirror HTML.
 *
 * Redirects are checked one by one and a catalog generation stays pinned to the selected origin.
 * Cross-origin redirects are reported to the mirror manager instead of being followed silently.
 */
class SafeHtmlClient internal constructor(
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int,
    private val maxRedirects: Int,
    private val maxBodyBytes: Int,
    private val destinationValidator: (URI) -> Unit,
    private val connectionFactory: (URI) -> HttpsURLConnection,
) : HtmlTransport {
    constructor(
        connectTimeoutMs: Int = 7_000,
        readTimeoutMs: Int = 12_000,
        maxRedirects: Int = 4,
        maxBodyBytes: Int = 2 * 1_024 * 1_024,
    ) : this(
        connectTimeoutMs = connectTimeoutMs,
        readTimeoutMs = readTimeoutMs,
        maxRedirects = maxRedirects,
        maxBodyBytes = maxBodyBytes,
        destinationValidator = { uri -> NetworkDestinationValidator.validateHttpsPublic(uri) },
        connectionFactory = { uri -> uri.toURL().openConnection() as HttpsURLConnection },
    )

    init {
        require(connectTimeoutMs > 0)
        require(readTimeoutMs > 0)
        require(maxRedirects >= 0)
        require(maxBodyBytes > 0)
    }

    override suspend fun get(rawOrigin: String, rawRelativePath: String): HtmlResponse =
        withContext(Dispatchers.IO) {
            val origin = MirrorUrlNormalizer.normalize(rawOrigin)
            val relativePath = CatalogRouteNormalizer.normalize(rawRelativePath)
            var currentUri = URI.create(origin).resolve(relativePath)
            var redirects = 0

            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    destinationValidator(currentUri)
                    val connection = connectionFactory(currentUri).apply {
                        instanceFollowRedirects = false
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        requestMethod = "GET"
                        setRequestProperty("Accept", "text/html,application/xhtml+xml")
                        setRequestProperty("Accept-Language", "ru,en;q=0.7")
                        setRequestProperty("User-Agent", USER_AGENT)
                    }

                    try {
                        val statusCode = connection.responseCode
                        if (statusCode in 300..399) {
                            if (redirects >= maxRedirects) {
                                throw CatalogNetworkException(IllegalStateException("Too many redirects"))
                            }
                            val location = connection.getHeaderField("Location")
                                ?: throw CatalogNetworkException(
                                    IllegalStateException("Redirect without Location"),
                                )
                            val redirected = currentUri.resolve(location)
                            destinationValidator(redirected)
                            val redirectedOrigin = originOf(redirected)
                            if (redirectedOrigin != origin) {
                                throw CatalogRedirectException(redirectedOrigin)
                            }
                            currentUri = redirected
                            redirects++
                            continue
                        }

                        if (statusCode !in 200..299) throw CatalogHttpStatusException(statusCode)
                        val body = readBody(connection, statusCode)
                        CatalogHtmlDocumentPolicy.validate(body)
                        return@withContext HtmlResponse(
                            requestedOrigin = origin,
                            resolvedOrigin = originOf(currentUri),
                            relativePath = relativePath,
                            statusCode = statusCode,
                            body = body,
                        )
                    } finally {
                        // Cleanup must never replace cancellation or a more useful request error.
                        runCatching(connection::disconnect)
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("Unreachable")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (known: CatalogException) {
                throw known
            } catch (error: Exception) {
                throw CatalogNetworkException(error)
            }
        }

    private fun originOf(uri: URI): String {
        val authority = requireNotNull(uri.rawAuthority) { "HTTPS origin is missing" }
        return MirrorUrlNormalizer.normalize("https://$authority")
    }

    private suspend fun readBody(connection: HttpsURLConnection, statusCode: Int): String {
        val stream = if (statusCode in 200..399) connection.inputStream else connection.errorStream
        return CatalogHtmlBodyDecoder(maxBodyBytes).read(
            input = stream,
            contentType = connection.contentType,
            declaredLength = connection.contentLengthLong,
        )
    }

    private companion object {
        const val USER_AGENT = "KinogoTV/0.2 (Android TV; native catalog)"
    }
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
