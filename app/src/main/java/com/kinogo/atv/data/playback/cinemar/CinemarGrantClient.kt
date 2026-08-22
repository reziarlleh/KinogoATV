package com.kinogo.atv.data.playback.cinemar

import com.google.gson.JsonPrimitive
import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import com.kinogo.atv.data.playback.CinemarEmbedUrlPolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

enum class CinemarGrantFailureCode {
    MISSING_TOKEN,
    INVALID_EMBED_ADDRESS,
    NETWORK_ERROR,
    HTTP_ERROR,
    UNSUPPORTED_CONTENT,
    RESPONSE_TOO_LARGE,
    MALFORMED_RESPONSE,
    NO_PLAYABLE_STREAMS,
    UNSAFE_NETWORK_DESTINATION,
}

sealed interface CinemarGrantResolution {
    data class Ready(
        val stream: CinemarStream,
    ) : CinemarGrantResolution

    data class Rejected(
        val code: CinemarGrantFailureCode,
    ) : CinemarGrantResolution {
        val userMessage: String
            get() = when (code) {
                CinemarGrantFailureCode.MISSING_TOKEN ->
                    "Cinemar не вернул идентификатор выбранного варианта"
                CinemarGrantFailureCode.INVALID_EMBED_ADDRESS ->
                    "Некорректный адрес плеера Cinemar"
                CinemarGrantFailureCode.NETWORK_ERROR ->
                    "Не удалось обновить источник Cinemar"
                CinemarGrantFailureCode.HTTP_ERROR ->
                    "Сервер Cinemar не выдал источник воспроизведения"
                CinemarGrantFailureCode.UNSUPPORTED_CONTENT ->
                    "Cinemar вернул неподдерживаемый тип данных"
                CinemarGrantFailureCode.RESPONSE_TOO_LARGE ->
                    "Ответ Cinemar превышает допустимый размер"
                CinemarGrantFailureCode.MALFORMED_RESPONSE ->
                    "Cinemar вернул повреждённые данные источника"
                CinemarGrantFailureCode.NO_PLAYABLE_STREAMS ->
                    "Cinemar не вернул совместимый HTTPS-источник"
                CinemarGrantFailureCode.UNSAFE_NETWORK_DESTINATION ->
                    "Источник Cinemar не прошёл сетевую проверку"
            }
    }
}

internal data class CinemarGrantHttpResponse(
    val statusCode: Int,
    val contentType: String?,
    val body: ByteArray,
)

internal fun interface CinemarGrantTransport {
    suspend fun post(
        endpoint: URI,
        embedUri: URI,
        jsonBody: ByteArray,
    ): CinemarGrantHttpResponse
}

/**
 * Exchanges one selected opaque playlist leaf for a short-lived media grant.
 *
 * The request is deliberately constrained to the exact Cinemar origin and fixed
 * `/api/playlist/load` path. It has no cookie jar, follows no redirect and never retries or logs
 * the opaque request body. The caller is expected to discard both token and result with the active
 * playback session.
 */
class CinemarGrantClient internal constructor(
    private val transport: CinemarGrantTransport,
    private val destinationValidator: (URI) -> Unit,
    private val parser: CinemarPublicConfigParser = CinemarPublicConfigParser(),
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
) {
    constructor() : this(
        transport = OkHttpCinemarGrantTransport(
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MS,
            maxBodyBytes = DEFAULT_MAX_RESPONSE_BYTES,
        ),
        destinationValidator = cinemarGrantDestinationValidator(),
    )

    init {
        require(maxResponseBytes > 0)
    }

    suspend fun load(
        embedUrl: String,
        stream: CinemarStream,
    ): CinemarGrantResolution {
        val token = stream.grantToken
            ?: return CinemarGrantResolution.Rejected(CinemarGrantFailureCode.MISSING_TOKEN)
        val embedUri = CinemarEmbedUrlPolicy.validatedPlayerDocumentUri(embedUrl)
            ?: return CinemarGrantResolution.Rejected(
                CinemarGrantFailureCode.INVALID_EMBED_ADDRESS,
            )
        val endpoint = URI(
            "https",
            null,
            embedUri.host,
            -1,
            GRANT_PATH,
            null,
            null,
        )

        return try {
            validateDestination(embedUri)
            validateDestination(endpoint)
            val requestBody = JsonPrimitive(token.valueForRequest())
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
            val response = transport.post(
                endpoint = endpoint,
                embedUri = embedUri,
                jsonBody = requestBody,
            )
            if (response.statusCode !in 200..299) {
                return CinemarGrantResolution.Rejected(CinemarGrantFailureCode.HTTP_ERROR)
            }
            if (response.body.size > maxResponseBytes) {
                return CinemarGrantResolution.Rejected(
                    CinemarGrantFailureCode.RESPONSE_TOO_LARGE,
                )
            }
            if (!isSupportedJsonContentType(response.contentType)) {
                return CinemarGrantResolution.Rejected(
                    CinemarGrantFailureCode.UNSUPPORTED_CONTENT,
                )
            }
            val json = strictUtf8(response.body)
                ?: return CinemarGrantResolution.Rejected(
                    CinemarGrantFailureCode.MALFORMED_RESPONSE,
                )
            when (val parsed = parser.parseGrant(embedUrl, stream, json)) {
                is CinemarGrantParseResult.Parsed -> {
                    parsed.stream.mediaVariants.forEach { variant ->
                        validateDestination(variant.url.asUri())
                    }
                    parsed.stream.subtitles.forEach { subtitle ->
                        validateDestination(subtitle.url.asUri())
                    }
                    CinemarGrantResolution.Ready(parsed.stream)
                }
                is CinemarGrantParseResult.Rejected -> CinemarGrantResolution.Rejected(
                    parsed.code.toGrantFailureCode(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CinemarGrantTooLargeException) {
            CinemarGrantResolution.Rejected(CinemarGrantFailureCode.RESPONSE_TOO_LARGE)
        } catch (_: SecurityException) {
            CinemarGrantResolution.Rejected(
                CinemarGrantFailureCode.UNSAFE_NETWORK_DESTINATION,
            )
        } catch (_: IllegalArgumentException) {
            CinemarGrantResolution.Rejected(
                CinemarGrantFailureCode.UNSAFE_NETWORK_DESTINATION,
            )
        } catch (_: Exception) {
            CinemarGrantResolution.Rejected(CinemarGrantFailureCode.NETWORK_ERROR)
        }
    }

    private suspend fun validateDestination(uri: URI) {
        withContext(Dispatchers.IO) {
            destinationValidator(uri)
        }
    }

    private companion object {
        const val GRANT_PATH = "/api/playlist/load"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000L
        const val DEFAULT_READ_TIMEOUT_MS = 12_000L
        const val DEFAULT_MAX_RESPONSE_BYTES = 512 * 1_024
    }
}

private class OkHttpCinemarGrantTransport(
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
    private val maxBodyBytes: Int,
    dns: Dns = ResilientPublicDns(),
) : CinemarGrantTransport {
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

    override suspend fun post(
        endpoint: URI,
        embedUri: URI,
        jsonBody: ByteArray,
    ): CinemarGrantHttpResponse {
        val origin = "https://${requireNotNull(endpoint.host).lowercase(Locale.ROOT)}"
        val request = Request.Builder()
            .url(endpoint.toASCIIString())
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Accept-Language", "ru,en;q=0.7")
            .header("Origin", origin)
            .header("Referer", embedUri.toASCIIString())
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
                            val result = CinemarGrantHttpResponse(
                                statusCode = response.code,
                                contentType = body?.contentType()?.toString(),
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
        if (declaredLength > maxBytes.toLong()) throw CinemarGrantTooLargeException()
        if (input == null) return ByteArray(0)
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1_024)
            var total = 0
            while (true) {
                if (isCancelled()) throw IOException("Cinemar grant request cancelled")
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > maxBytes) throw CinemarGrantTooLargeException()
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val PROVIDER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}

private class CinemarGrantTooLargeException : IllegalStateException()

private fun cinemarGrantDestinationValidator(): (URI) -> Unit {
    val dns = ResilientPublicDns()
    return { uri -> NetworkDestinationValidator.validateHttpsPublic(uri, dns) }
}

private fun isSupportedJsonContentType(contentType: String?): Boolean {
    if (contentType.isNullOrBlank()) return false
    val mediaType = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
    return mediaType == "application/json" || mediaType.endsWith("+json")
}

private fun strictUtf8(bytes: ByteArray): String? = runCatching {
    StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}.getOrNull()

private fun CinemarNativeFailureCode.toGrantFailureCode(): CinemarGrantFailureCode = when (this) {
    CinemarNativeFailureCode.INVALID_EMBED_ADDRESS ->
        CinemarGrantFailureCode.INVALID_EMBED_ADDRESS
    CinemarNativeFailureCode.DOCUMENT_TOO_LARGE ->
        CinemarGrantFailureCode.RESPONSE_TOO_LARGE
    CinemarNativeFailureCode.NO_PLAYABLE_STREAMS ->
        CinemarGrantFailureCode.NO_PLAYABLE_STREAMS
    CinemarNativeFailureCode.UNSAFE_NETWORK_DESTINATION ->
        CinemarGrantFailureCode.UNSAFE_NETWORK_DESTINATION
    CinemarNativeFailureCode.CONFIG_NOT_FOUND,
    CinemarNativeFailureCode.MALFORMED_CONFIG,
    -> CinemarGrantFailureCode.MALFORMED_RESPONSE
}
