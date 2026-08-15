package com.kinogo.atv.data.catalog

import com.kinogo.atv.data.mirror.MirrorUrlNormalizer
import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class SessionHttpResponse(
    val requestedOrigin: String,
    val resolvedOrigin: String,
    val relativePath: String,
    val statusCode: Int,
    val body: String,
) {
    override fun toString(): String =
        "SessionHttpResponse(requestedOrigin=$requestedOrigin, resolvedOrigin=$resolvedOrigin, " +
            "relativePath=${relativePath.redactQuery()}, statusCode=$statusCode, " +
            "body=<${body.length} chars>)"
}

data class SessionBinaryResponse(
    val requestedOrigin: String,
    val resolvedOrigin: String,
    val relativePath: String,
    val statusCode: Int,
    val contentType: String?,
    val body: ByteArray,
) {
    override fun toString(): String =
        "SessionBinaryResponse(requestedOrigin=$requestedOrigin, " +
            "resolvedOrigin=$resolvedOrigin, relativePath=${relativePath.redactQuery()}, " +
            "statusCode=$statusCode, contentType=$contentType, body=<${body.size} bytes>)"
}

private fun String.redactQuery(): String =
    substringBefore('?') + if ('?' in this) "?<redacted>" else ""

/**
 * Stateful HTTPS transport for the server-rendered Kinogo/DLE protocol.
 *
 * Cookies are intentionally isolated by verified origin. They are not copied when a mirror changes;
 * the session manager signs in to the new origin again with the persisted account credentials.
 */
class KinogoSessionHttpClient(
    private val connectTimeoutMs: Int = 7_000,
    private val readTimeoutMs: Int = 12_000,
    private val maxRedirects: Int = 4,
    private val maxBodyBytes: Int = 2 * 1_024 * 1_024,
    private val dns: Dns = ResilientPublicDns(),
) : CatalogFilterHtmlTransport {
    private val cookieStore = OriginCookieStore()
    private val client = OkHttpClient.Builder()
        .dns(dns)
        // DLE/xSort is a stateful sequence of mutating requests. Some Android TV network stacks
        // leave the service's HTTP/2 stream waiting for response headers until our call timeout,
        // while the same endpoint responds reliably over HTTP/1.1. Keep this isolated catalog,
        // auth and library session on HTTP/1.1; playback uses separate clients.
        .protocols(listOf(Protocol.HTTP_1_1))
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .callTimeout((connectTimeoutMs + readTimeoutMs + 1_000L), TimeUnit.MILLISECONDS)
        .build()

    init {
        require(connectTimeoutMs > 0)
        require(readTimeoutMs > 0)
        require(maxRedirects >= 0)
        require(maxBodyBytes > 0)
    }

    override suspend fun get(rawOrigin: String, rawRelativePath: String): HtmlResponse {
        val response = request(
            rawOrigin = rawOrigin,
            rawRelativePath = rawRelativePath,
            method = "GET",
            form = emptyMap(),
        )
        if (response.statusCode !in 200..299) {
            throw CatalogHttpStatusException(response.statusCode)
        }
        CatalogHtmlDocumentPolicy.validate(response.body)
        return HtmlResponse(
            requestedOrigin = response.requestedOrigin,
            resolvedOrigin = response.resolvedOrigin,
            relativePath = response.relativePath,
            statusCode = response.statusCode,
            body = response.body,
        )
    }

    suspend fun getRaw(rawOrigin: String, rawRelativePath: String): SessionHttpResponse =
        request(rawOrigin, rawRelativePath, "GET", emptyMap())

    /**
     * Fetches a small same-origin binary resource in the same cookie session.
     *
     * This is intentionally narrower than a general downloader and currently exists for the
     * image CAPTCHA on the DLE registration page. Redirects, DNS and origin boundaries are the
     * same as for HTML requests, while the caller supplies a strict response-size ceiling.
     */
    suspend fun getBinaryRaw(
        rawOrigin: String,
        rawRelativePath: String,
        maxBytes: Int = DEFAULT_BINARY_BODY_BYTES,
    ): SessionBinaryResponse {
        require(maxBytes in 1..maxBodyBytes) { "Invalid binary response limit" }
        return requestBody(rawOrigin, rawRelativePath, "GET", emptyMap(), maxBytes)
    }

    suspend fun postForm(
        rawOrigin: String,
        rawRelativePath: String,
        form: Map<String, String>,
    ): SessionHttpResponse = request(rawOrigin, rawRelativePath, "POST", form)

    override suspend fun postCatalogForm(
        rawOrigin: String,
        rawRelativePath: String,
        form: Map<String, String>,
    ): HtmlResponse {
        val response = postForm(rawOrigin, rawRelativePath, form)
        if (response.statusCode !in 200..299) {
            throw CatalogHttpStatusException(response.statusCode)
        }
        CatalogHtmlDocumentPolicy.validate(response.body)
        return HtmlResponse(
            requestedOrigin = response.requestedOrigin,
            resolvedOrigin = response.resolvedOrigin,
            relativePath = response.relativePath,
            statusCode = response.statusCode,
            body = response.body,
        )
    }

    override fun sessionEpoch(rawOrigin: String): Long =
        cookieStore.epoch(MirrorUrlNormalizer.normalize(rawOrigin))

    fun clearCookies(rawOrigin: String) {
        cookieStore.clear(MirrorUrlNormalizer.normalize(rawOrigin))
    }

    internal fun cookieHeader(rawOrigin: String): String? =
        cookieStore.header(MirrorUrlNormalizer.normalize(rawOrigin))

    private suspend fun request(
        rawOrigin: String,
        rawRelativePath: String,
        method: String,
        form: Map<String, String>,
    ): SessionHttpResponse {
        val response = requestBody(rawOrigin, rawRelativePath, method, form, maxBodyBytes)
        val body = CatalogHtmlBodyDecoder(maxBodyBytes).read(
            input = ByteArrayInputStream(response.body),
            contentType = response.contentType,
            declaredLength = response.body.size.toLong(),
        )
        return SessionHttpResponse(
            requestedOrigin = response.requestedOrigin,
            resolvedOrigin = response.resolvedOrigin,
            relativePath = response.relativePath,
            statusCode = response.statusCode,
            body = body,
        )
    }

    private suspend fun requestBody(
        rawOrigin: String,
        rawRelativePath: String,
        method: String,
        form: Map<String, String>,
        responseLimitBytes: Int,
    ): SessionBinaryResponse = withContext(Dispatchers.IO) {
        val origin = MirrorUrlNormalizer.normalize(rawOrigin)
        val relativePath = SessionRouteNormalizer.normalize(rawRelativePath)
        var currentUri = URI.create(origin).resolve(relativePath)
        var currentMethod = method
        var currentForm = form
        var redirects = 0

        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                NetworkDestinationValidator.validateHttpsPublic(currentUri, dns)
                val currentOrigin = originOf(currentUri)
                if (currentOrigin != origin) throw CatalogRedirectException(currentOrigin)

                val requestBuilder = Request.Builder()
                    .url(currentUri.toASCIIString())
                    .header("Accept", "text/html,application/xhtml+xml,application/json,*/*;q=0.7")
                    .header("Accept-Language", "ru,en;q=0.7")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Requested-With", "XMLHttpRequest")
                cookieStore.header(origin)?.let { requestBuilder.header("Cookie", it) }
                if (currentMethod == "POST") {
                    requestBuilder.post(
                        encodeForm(currentForm).toRequestBody(FORM_MEDIA_TYPE),
                    )
                } else {
                    requestBuilder.get()
                }

                val response = executeCancellable(requestBuilder.build())
                try {
                    val statusCode = response.code
                    cookieStore.absorb(origin, response.headers.values("Set-Cookie"))

                    if (statusCode in 300..399) {
                        if (redirects >= maxRedirects) {
                            throw CatalogNetworkException(IllegalStateException("Too many redirects"))
                        }
                        val location = response.header("Location")
                            ?: throw CatalogNetworkException(
                                IllegalStateException("Redirect without Location"),
                        )
                        val redirected = currentUri.resolve(location)
                        NetworkDestinationValidator.validateHttpsPublic(redirected, dns)
                        val redirectedOrigin = originOf(redirected)
                        if (redirectedOrigin != origin) {
                            throw CatalogRedirectException(redirectedOrigin)
                        }
                        currentUri = redirected
                        if (statusCode == 301 || statusCode == 302 || statusCode == 303) {
                            currentMethod = "GET"
                            currentForm = emptyMap()
                        }
                        redirects++
                        continue
                    }

                    val responseBody = response.body
                    val declaredLength = responseBody?.contentLength() ?: -1L
                    require(declaredLength < 0L || declaredLength <= responseLimitBytes) {
                        "Session response is too large"
                    }
                    val body = responseBody?.byteStream()?.use { input ->
                        val output = ByteArrayOutputStream(minOf(responseLimitBytes, 32 * 1_024))
                        val buffer = ByteArray(8 * 1_024)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            require(total <= responseLimitBytes) { "Session response is too large" }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    } ?: ByteArray(0)
                    return@withContext SessionBinaryResponse(
                        requestedOrigin = origin,
                        resolvedOrigin = originOf(currentUri),
                        relativePath = finalSessionRelativePath(currentUri),
                        statusCode = statusCode,
                        contentType = responseBody?.contentType()?.toString(),
                        body = body,
                    )
                } finally {
                    response.close()
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

    private suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
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
                        if (continuation.isActive) {
                            continuation.resume(response) { _, value, _ -> value.close() }
                        } else {
                            response.close()
                        }
                    }
                },
            )
        }

    private fun originOf(uri: URI): String {
        val authority = requireNotNull(uri.rawAuthority) { "HTTPS origin is missing" }
        return MirrorUrlNormalizer.normalize("https://$authority")
    }

    private companion object {
        const val DEFAULT_BINARY_BODY_BYTES = 512 * 1_024
        const val USER_AGENT = "KinogoATV/0.5 (Android TV; native HTML client)"
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()

        fun encodeForm(form: Map<String, String>): String = form.entries.joinToString("&") { entry ->
            "${encode(entry.key)}=${encode(entry.value)}"
        }

        fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}

/**
 * Returns the route that actually produced the terminal response, rather than the route before a
 * same-origin redirect chain. Host data is deliberately discarded and the resulting route is
 * passed through the same boundary used for caller-supplied session paths.
 */
internal fun finalSessionRelativePath(uri: URI): String {
    val route = buildString {
        append(uri.rawPath?.takeIf(String::isNotEmpty) ?: "/")
        uri.rawQuery?.let { append('?').append(it) }
    }
    return SessionRouteNormalizer.normalize(route)
}

internal object SessionRouteNormalizer {
    private val encodedControl = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)
    private val encodedSeparatorOrPercent = Regex("%(?:2f|5c|25)", RegexOption.IGNORE_CASE)
    private val encodedDot = Regex("%2e", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String {
        require(raw.startsWith('/') && !raw.startsWith("//")) {
            "Session path must stay inside the selected mirror"
        }
        require(raw.none { it == '\\' || it == '\u0000' || it.isWhitespace() || it.code == 0x7f }) {
            "Invalid session path"
        }
        require(!encodedControl.containsMatchIn(raw)) { "Encoded control characters are not allowed" }
        val uri = URI(raw)
        require(!uri.isOpaque && uri.scheme == null && uri.rawAuthority == null && uri.rawFragment == null)
        val path = uri.rawPath ?: "/"
        require(!encodedSeparatorOrPercent.containsMatchIn(path))
        require(path.split('/').none { segment ->
            val dotDecoded = encodedDot.replace(segment, ".")
            dotDecoded == "." || dotDecoded == ".."
        })
        return uri.toASCIIString()
    }
}

/** Minimal same-origin cookie jar; only name/value pairs are ever replayed. */
internal class OriginCookieStore {
    private val cookiesByOrigin = linkedMapOf<String, LinkedHashMap<String, String>>()
    private val epochsByOrigin = linkedMapOf<String, Long>()

    @Synchronized
    fun absorb(origin: String, headers: Map<String?, List<String>>) {
        val values = headers.entries
            .filter { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
            .flatMap { it.value }
        absorb(origin, values)
    }

    @Synchronized
    fun absorb(origin: String, values: List<String>) {
        if (values.isEmpty()) return
        val before = cookiesByOrigin[origin]?.toMap().orEmpty()
        val target = cookiesByOrigin.getOrPut(origin) { linkedMapOf() }
        values.forEach { raw ->
            val pair = raw.substringBefore(';').trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            val attributes = raw.lowercase(Locale.ROOT)
            if (value.isEmpty() || "max-age=0" in attributes || "expires=thu, 01 jan 1970" in attributes) {
                target.remove(name)
            } else {
                target[name] = value
            }
        }
        if (target.isEmpty()) cookiesByOrigin.remove(origin)
        val after = cookiesByOrigin[origin]?.toMap().orEmpty()
        if (before != after) incrementEpoch(origin)
    }

    @Synchronized
    fun header(origin: String): String? = cookiesByOrigin[origin]
        ?.entries
        ?.joinToString("; ") { (name, value) -> "$name=$value" }
        ?.takeIf(String::isNotEmpty)

    @Synchronized
    fun epoch(origin: String): Long = epochsByOrigin[origin] ?: 0L

    @Synchronized
    fun clear(origin: String) {
        if (cookiesByOrigin.remove(origin) != null) incrementEpoch(origin)
    }

    private fun incrementEpoch(origin: String) {
        epochsByOrigin[origin] = (epochsByOrigin[origin] ?: 0L) + 1L
    }
}
