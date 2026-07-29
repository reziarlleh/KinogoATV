package com.kinogo.atv.data.playback

import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * A short-lived provider document. Both addresses can contain opaque tokens and therefore redact
 * themselves from structured logging.
 */
class ProviderEmbedDocument internal constructor(
    internal val requestedUrl: String,
    internal val resolvedUrl: String,
    val html: String,
) {
    init {
        require(requestedUrl.isNotBlank())
        require(resolvedUrl.isNotBlank())
        require(html.isNotBlank())
    }

    override fun toString(): String =
        "ProviderEmbedDocument(requestedUrl=<redacted>, resolvedUrl=<redacted>, html=<redacted>)"
}

enum class ProviderEmbedDocumentFailure {
    INVALID_ADDRESS,
    HTTP_ERROR,
    REDIRECT_REJECTED,
    UNSUPPORTED_CONTENT,
    RESPONSE_TOO_LARGE,
    NETWORK_ERROR,
}

sealed interface ProviderEmbedDocumentResult {
    data class Ready(val document: ProviderEmbedDocument) : ProviderEmbedDocumentResult

    data class Failed(
        val reason: ProviderEmbedDocumentFailure,
        val userMessage: String,
    ) : ProviderEmbedDocumentResult
}

internal data class ProviderDocumentHttpResponse(
    val statusCode: Int,
    val contentType: String?,
    val location: String?,
    val body: ByteArray,
) {
    override fun toString(): String =
        "ProviderDocumentHttpResponse(" +
            "statusCode=$statusCode, contentType=$contentType, " +
            "location=<redacted>, body=<redacted>)"
}

internal fun interface ProviderDocumentTransport {
    suspend fun get(url: URI, refererUrl: String): ProviderDocumentHttpResponse
}

/**
 * Fetches the public HTML configuration delivered to a normal browser iframe.
 *
 * It never executes provider JavaScript, replays cookies, downloads media or persists the response.
 * Redirects are checked one by one and every destination must resolve exclusively to public IPs.
 */
class ProviderEmbedDocumentClient internal constructor(
    private val transport: ProviderDocumentTransport,
    private val destinationValidator: (URI) -> Unit,
    private val maxRedirects: Int,
) {
    constructor(
        connectTimeoutMs: Long = 7_000L,
        readTimeoutMs: Long = 12_000L,
        maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
        maxRedirects: Int = 2,
    ) : this(
        transport = OkHttpProviderDocumentTransport(
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            maxBodyBytes = maxBodyBytes,
        ),
        destinationValidator = providerDocumentDestinationValidator(),
        maxRedirects = maxRedirects,
    )

    init {
        require(maxRedirects >= 0)
    }

    /**
     * Validates the original iframe address independently of fetching/parsing its document.
     *
     * A provider may reject the lightweight HTML preflight while remaining usable in the isolated
     * WebView. Returning only a validated original address lets preparation retain that explicit
     * fallback without weakening the HTTPS/public-DNS boundary.
     */
    suspend fun validatedWebFallbackUrl(
        embedUrl: String,
        refererUrl: String,
    ): String? {
        val requestedUri = safeHttpsUri(embedUrl) ?: return null
        if (safeHttpsUri(refererUrl) == null) return null
        return try {
            validateDestination(requestedUri)
            requestedUri.toASCIIString()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetch(
        embedUrl: String,
        refererUrl: String,
    ): ProviderEmbedDocumentResult {
        val requestedUri = safeHttpsUri(embedUrl)
            ?: return failed(
                ProviderEmbedDocumentFailure.INVALID_ADDRESS,
                "Некорректный адрес источника",
            )
        val safeReferer = safeHttpsUri(refererUrl)
            ?: return failed(
                ProviderEmbedDocumentFailure.INVALID_ADDRESS,
                "Некорректный адрес страницы фильма",
            )

        return try {
            var currentUri = requestedUri
            var redirectCount = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                validateDestination(currentUri)
                val response = transport.get(
                    url = currentUri,
                    refererUrl = safeReferer.toASCIIString(),
                )
                if (response.statusCode in 300..399) {
                    if (redirectCount >= maxRedirects) {
                        return failed(
                            ProviderEmbedDocumentFailure.REDIRECT_REJECTED,
                            "Источник выполнил слишком много перенаправлений",
                        )
                    }
                    val redirected = response.location
                        ?.let(currentUri::resolve)
                        ?.takeIf(::isSafeHttpsUri)
                        ?: return failed(
                            ProviderEmbedDocumentFailure.REDIRECT_REJECTED,
                            "Источник вернул небезопасное перенаправление",
                        )
                    try {
                        validateDestination(redirected)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        return failed(
                            ProviderEmbedDocumentFailure.REDIRECT_REJECTED,
                            "Источник вернул небезопасное перенаправление",
                        )
                    }
                    currentUri = redirected
                    redirectCount++
                    continue
                }
                if (response.statusCode !in 200..299) {
                    return failed(
                        ProviderEmbedDocumentFailure.HTTP_ERROR,
                        "Сервер источника ответил с ошибкой ${response.statusCode}",
                    )
                }
                val charset = providerCharset(response.contentType)
                    ?: return failed(
                        ProviderEmbedDocumentFailure.UNSUPPORTED_CONTENT,
                        "Источник вернул неподдерживаемый тип данных",
                    )
                val html = response.body.toString(charset)
                if (html.isBlank()) {
                    return failed(
                        ProviderEmbedDocumentFailure.UNSUPPORTED_CONTENT,
                        "Источник вернул пустой документ",
                    )
                }
                return ProviderEmbedDocumentResult.Ready(
                    ProviderEmbedDocument(
                        requestedUrl = requestedUri.toASCIIString(),
                        resolvedUrl = currentUri.toASCIIString(),
                        html = html,
                    ),
                )
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ProviderDocumentTooLargeException) {
            failed(
                ProviderEmbedDocumentFailure.RESPONSE_TOO_LARGE,
                "Документ источника слишком большой",
            )
        } catch (_: Exception) {
            failed(
                ProviderEmbedDocumentFailure.NETWORK_ERROR,
                "Не удалось загрузить данные источника",
            )
        }
    }

    /**
     * DNS validation can perform a system lookup and a bounded DoH request. Playback preparation is
     * launched from Compose's main scope, so it must never run this blocking work on the UI thread.
     */
    private suspend fun validateDestination(uri: URI) {
        withContext(Dispatchers.IO) {
            destinationValidator(uri)
        }
    }

    private fun failed(
        reason: ProviderEmbedDocumentFailure,
        message: String,
    ) = ProviderEmbedDocumentResult.Failed(reason, message)

    private companion object {
        const val DEFAULT_MAX_BODY_BYTES = 3 * 1_024 * 1_024
    }
}

private class OkHttpProviderDocumentTransport(
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
    private val maxBodyBytes: Int,
    dns: Dns = ResilientPublicDns(),
) : ProviderDocumentTransport {
    private val client = OkHttpClient.Builder()
        .dns(dns)
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .cache(null)
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(connectTimeoutMs + readTimeoutMs + 2_000L, TimeUnit.MILLISECONDS)
        .build()

    init {
        require(connectTimeoutMs > 0L)
        require(readTimeoutMs > 0L)
        require(maxBodyBytes > 0)
    }

    override suspend fun get(
        url: URI,
        refererUrl: String,
    ): ProviderDocumentHttpResponse {
        val request = Request.Builder()
            .url(url.toASCIIString())
            .get()
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            .header("Accept-Language", "ru,en;q=0.7")
            .header("Referer", refererUrl)
            .header("User-Agent", PROVIDER_USER_AGENT)
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(e))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val body = response.body
                            val result = ProviderDocumentHttpResponse(
                                statusCode = response.code,
                                contentType = body?.contentType()?.toString(),
                                location = response.header("Location"),
                                body = readBounded(
                                    input = body?.byteStream(),
                                    declaredLength = body?.contentLength() ?: -1L,
                                    maxBytes = maxBodyBytes,
                                    isCancelled = call::isCanceled,
                                ),
                            )
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(result))
                            }
                        } catch (error: Exception) {
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.failure(error))
                            }
                        } finally {
                            response.close()
                        }
                    }
                },
            )
        }
    }

    private fun readBounded(
        input: java.io.InputStream?,
        declaredLength: Long,
        maxBytes: Int,
        isCancelled: () -> Boolean,
    ): ByteArray {
        if (declaredLength > maxBytes.toLong()) throw ProviderDocumentTooLargeException()
        if (input == null) return ByteArray(0)
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1_024)
            var total = 0
            while (true) {
                if (isCancelled()) throw IOException("Provider request cancelled")
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > maxBytes) throw ProviderDocumentTooLargeException()
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private companion object {
        const val PROVIDER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}

private class ProviderDocumentTooLargeException : IllegalStateException()

private fun providerDocumentDestinationValidator(): (URI) -> Unit {
    val dns = ResilientPublicDns()
    return { uri -> NetworkDestinationValidator.validateHttpsPublic(uri, dns) }
}

private fun safeHttpsUri(rawUrl: String): URI? {
    if (
        rawUrl.isBlank() ||
        rawUrl != rawUrl.trim() ||
        rawUrl.any(Char::isISOControl) ||
        '\\' in rawUrl
    ) {
        return null
    }
    return runCatching { URI(rawUrl) }
        .getOrNull()
        ?.takeIf(::isSafeHttpsUri)
}

private fun isSafeHttpsUri(uri: URI): Boolean =
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.isOpaque &&
        !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null &&
        uri.rawFragment == null &&
        (uri.port == -1 || uri.port == 443)

private fun providerCharset(contentType: String?): Charset? {
    if (contentType.isNullOrBlank()) return Charsets.UTF_8
    val mediaType = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
    if (
        mediaType !in setOf(
            "text/html",
            "application/xhtml+xml",
            "text/plain",
            "application/javascript",
        )
    ) {
        return null
    }
    val charsetName = Regex(
        """(?:^|;)\s*charset\s*=\s*(?:"([^"]+)"|'([^']+)'|([^;\s]+))""",
        RegexOption.IGNORE_CASE,
    ).find(contentType)
        ?.groupValues
        ?.drop(1)
        ?.firstOrNull(String::isNotBlank)
        ?: return Charsets.UTF_8
    return runCatching { Charset.forName(charsetName) }.getOrNull()
}
