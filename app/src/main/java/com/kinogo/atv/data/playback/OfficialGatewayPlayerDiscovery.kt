package com.kinogo.atv.data.playback

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
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

/** ID from the server-rendered Kinogo HTML page. It is not a gateway post ID. */
@JvmInline
value class HtmlPostId(val value: String) {
    init {
        require(POST_ID.matches(value)) { "HTML post id must be a positive decimal number" }
    }
}

/** ID returned by the official application's gateway. It is not an HTML post ID. */
@JvmInline
value class GatewayPostId(val value: String) {
    init {
        require(POST_ID.matches(value)) { "Gateway post id must be a positive decimal number" }
    }
}

private val POST_ID = Regex("[1-9][0-9]*")

data class OfficialPlayerLookup(
    val htmlPostId: HtmlPostId,
    val title: String,
    val year: Int,
    val originalTitle: String? = null,
    val kinopoiskId: String? = null,
) {
    init {
        require(title.isNotBlank())
        require(year in 1800..2999)
    }
}

enum class OfficialPlayerProvider(val wireName: String) {
    CINEMAR("cinemar"),
    COLLAPS("collaps"),
    ;

    companion object {
        internal fun fromWireName(value: String): OfficialPlayerProvider? =
            entries.firstOrNull { it.wireName == value.trim().lowercase(Locale.ROOT) }
    }
}

/**
 * A fresh iframe offer returned by one discovery call.
 *
 * The URL can contain a short-lived token. Callers must keep this object in memory only and must
 * not log or persist it. [toString] is deliberately redacted to make accidental structured logging
 * less likely.
 */
data class OfficialPlayerOffer(
    val provider: OfficialPlayerProvider,
    val title: String,
    val iframeUrl: String,
) {
    init {
        require(title.isNotBlank())
        require(iframeUrl.isNotBlank())
    }

    override fun toString(): String =
        "OfficialPlayerOffer(provider=$provider, title=$title, iframeUrl=<redacted>)"
}

enum class OfficialPlayerDiscoveryRejection {
    GATEWAY_DISABLED,
    NO_EXACT_MATCH,
    AMBIGUOUS_MATCH,
    NO_PLAYER,
    NO_SUPPORTED_OFFERS,
    UNAVAILABLE,
}

sealed interface OfficialPlayerDiscoveryResult {
    data class Ready(
        val htmlPostId: HtmlPostId,
        val gatewayPostId: GatewayPostId,
        val offers: List<OfficialPlayerOffer>,
    ) : OfficialPlayerDiscoveryResult {
        init {
            require(offers.isNotEmpty())
        }
    }

    data class Rejected(
        val htmlPostId: HtmlPostId,
        val reason: OfficialPlayerDiscoveryRejection,
    ) : OfficialPlayerDiscoveryResult
}

object OfficialGatewayContract {
    const val BASE_URL = "https://api.kinogo-10.biz/gateway"

    /**
     * Public SHA-256 fingerprint of the signing certificate of the official `biz.kinogo.app` APK
     * inspected on 2026-07-22. This is a public application identifier, not a password or user
     * secret. The private signing key is neither present nor required. The undocumented gateway can
     * reject or revoke this value at any time, so use of this client remains explicitly optional.
     */
    const val OFFICIAL_APK_CERTIFICATE_SHA256 =
        "3245f97f56a9cff39b6be6a6729becfc127cbcd93d8e1ddcea0cf429f387379d"
}

data class OfficialGatewayClientConfig(
    /** The undocumented gateway is opt-in and must never become an application startup dependency. */
    val enabled: Boolean = false,
    /** Null omits `X-App-Signature`; the gateway currently answers such requests with HTTP 403. */
    val appSignatureSha256: String? = OfficialGatewayContract.OFFICIAL_APK_CERTIFICATE_SHA256,
) {
    init {
        require(appSignatureSha256 == null || SHA256.matches(appSignatureSha256)) {
            "Application signature must be a lowercase SHA-256 fingerprint"
        }
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

/**
 * Optional official-app gateway discovery. Every invocation performs a new lightsearch and player
 * request; neither gateway IDs, response bodies nor iframe URLs are cached.
 */
class OfficialGatewayPlayerDiscovery internal constructor(
    private val enabled: Boolean,
    private val transport: OfficialGatewayTransport,
    private val destinationValidator: (URI) -> Unit,
) {
    constructor(config: OfficialGatewayClientConfig = OfficialGatewayClientConfig()) : this(
        enabled = config.enabled,
        transport = OfficialGatewayHttpClient(config),
        destinationValidator = officialOfferDestinationValidator(),
    )

    suspend fun discover(lookup: OfficialPlayerLookup): OfficialPlayerDiscoveryResult {
        if (!enabled) return lookup.rejected(OfficialPlayerDiscoveryRejection.GATEWAY_DISABLED)

        return try {
            val searchResponse = transport.get(OfficialGatewayRoutes.lightSearch(lookup.title))
            if (searchResponse.statusCode !in 200..299) {
                return lookup.rejected(OfficialPlayerDiscoveryRejection.UNAVAILABLE)
            }
            val candidates = OfficialGatewayJsonParser.parseLightSearch(searchResponse.body)
            val gatewayPostId = when (
                val match = OfficialGatewayPostMatcher.match(lookup, candidates)
            ) {
                is OfficialGatewayPostMatch.Mapped -> match.gatewayPostId
                OfficialGatewayPostMatch.Missing ->
                    return lookup.rejected(OfficialPlayerDiscoveryRejection.NO_EXACT_MATCH)
                OfficialGatewayPostMatch.Ambiguous ->
                    return lookup.rejected(OfficialPlayerDiscoveryRejection.AMBIGUOUS_MATCH)
            }

            val playerResponse = transport.get(OfficialGatewayRoutes.player(gatewayPostId))
            if (playerResponse.statusCode !in 200..299) {
                return lookup.rejected(OfficialPlayerDiscoveryRejection.UNAVAILABLE)
            }
            val snapshot = OfficialGatewayJsonParser.parsePlayer(playerResponse.body)
            if (snapshot.gatewayPostId != gatewayPostId) {
                return lookup.rejected(OfficialPlayerDiscoveryRejection.UNAVAILABLE)
            }
            if (!snapshot.hasPlayer) {
                return lookup.rejected(OfficialPlayerDiscoveryRejection.NO_PLAYER)
            }

            val offers = withContext(Dispatchers.IO) {
                snapshot.offers.mapNotNull { offer ->
                    currentCoroutineContext().ensureActive()
                    runCatching {
                        destinationValidator(offer.iframeUri)
                        OfficialPlayerOffer(
                            provider = offer.provider,
                            title = offer.title,
                            iframeUrl = offer.iframeUri.toASCIIString(),
                        )
                    }.getOrNull()
                }
            }
            if (offers.isEmpty()) {
                lookup.rejected(OfficialPlayerDiscoveryRejection.NO_SUPPORTED_OFFERS)
            } else {
                OfficialPlayerDiscoveryResult.Ready(
                    htmlPostId = lookup.htmlPostId,
                    gatewayPostId = gatewayPostId,
                    offers = offers,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not include a request or iframe URL in errors: provider URLs can carry tokens.
            lookup.rejected(OfficialPlayerDiscoveryRejection.UNAVAILABLE)
        }
    }
}

private fun OfficialPlayerLookup.rejected(reason: OfficialPlayerDiscoveryRejection) =
    OfficialPlayerDiscoveryResult.Rejected(htmlPostId = htmlPostId, reason = reason)

internal data class OfficialGatewayHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal fun interface OfficialGatewayTransport {
    suspend fun get(relativeRoute: String): OfficialGatewayHttpResponse
}

/** Dedicated stateless transport: no cookies, redirects, cache or logging interceptors. */
internal class OfficialGatewayHttpClient(
    private val config: OfficialGatewayClientConfig,
    private val dns: Dns = ResilientPublicDns(),
    connectTimeoutMs: Long = 6_000L,
    readTimeoutMs: Long = 10_000L,
) : OfficialGatewayTransport {
    private val client = OkHttpClient.Builder()
        .dns(dns)
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .cache(null)
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(connectTimeoutMs + readTimeoutMs + 1_000L, TimeUnit.MILLISECONDS)
        .build()

    init {
        require(connectTimeoutMs > 0L)
        require(readTimeoutMs > 0L)
    }

    override suspend fun get(relativeRoute: String): OfficialGatewayHttpResponse =
        withContext(Dispatchers.IO) {
            require(config.enabled) { "Official gateway client is disabled" }
            val uri = OfficialGatewayRoutes.absoluteUri(relativeRoute)
            NetworkDestinationValidator.validateHttpsPublic(uri, dns)

            val request = Request.Builder()
                .url(uri.toASCIIString())
                .get()
                .header("Accept", "application/json")
                .header("Accept-Language", "ru,en;q=0.7")
                .header("User-Agent", USER_AGENT)
                .apply {
                    config.appSignatureSha256?.let { header("X-App-Signature", it) }
                }
                .build()
            val response = executeCancellable(request)
            try {
                OfficialGatewayHttpResponse(
                    statusCode = response.code,
                    body = readLimitedBody(response.body?.byteStream(), response.body?.contentLength()),
                )
            } finally {
                response.close()
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

    private fun readLimitedBody(input: java.io.InputStream?, declaredLength: Long?): String {
        if (input == null) return ""
        require(declaredLength == null || declaredLength < 0L || declaredLength <= MAX_BODY_BYTES) {
            "Gateway response is too large"
        }
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1_024)
            var remaining = MAX_BODY_BYTES
            while (remaining > 0) {
                val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            require(stream.read() < 0) { "Gateway response is too large" }
            return output.toString(StandardCharsets.UTF_8.name())
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 512 * 1_024
        const val USER_AGENT = "KinogoTV/0.2 (Android TV; optional player discovery)"
    }
}

internal object OfficialGatewayRoutes {
    private val baseUri = URI.create("${OfficialGatewayContract.BASE_URL}/")

    fun lightSearch(title: String): String {
        val normalizedQuery = title.trim()
        require(normalizedQuery.length >= 3) { "Gateway lightsearch requires at least 3 characters" }
        return "v1/post/lightsearch?q=${encode(normalizedQuery)}"
    }

    fun player(gatewayPostId: GatewayPostId): String =
        "v1/post/${gatewayPostId.value}/player"

    fun absoluteUri(relativeRoute: String): URI {
        require(relativeRoute.startsWith("v1/") && !relativeRoute.contains('\\')) {
            "Gateway route must remain under v1"
        }
        require(relativeRoute.none { it.isISOControl() || it.isWhitespace() }) {
            "Invalid gateway route"
        }
        val uri = baseUri.resolve(relativeRoute)
        require(uri.scheme == "https" && uri.host == baseUri.host)
        require(uri.port == -1 || uri.port == 443)
        require(uri.rawUserInfo == null && uri.rawFragment == null)
        require(uri.rawPath.startsWith("/gateway/v1/"))
        return uri
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

internal data class OfficialGatewayPostCandidate(
    val gatewayPostId: GatewayPostId,
    val title: String,
    val year: Int?,
    val originalTitle: String?,
    val kinopoiskId: String?,
)

internal sealed interface OfficialGatewayPostMatch {
    data class Mapped(val gatewayPostId: GatewayPostId) : OfficialGatewayPostMatch
    data object Missing : OfficialGatewayPostMatch
    data object Ambiguous : OfficialGatewayPostMatch
}

internal object OfficialGatewayPostMatcher {
    fun match(
        lookup: OfficialPlayerLookup,
        candidates: List<OfficialGatewayPostCandidate>,
    ): OfficialGatewayPostMatch {
        val expectedTitle = normalizeText(lookup.title)
        val expectedOriginalTitle = lookup.originalTitle
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeText)
        val expectedKinopoisk = lookup.kinopoiskId?.trim()?.takeIf { it.isNotEmpty() }

        val exact = candidates.asSequence()
            .filter { normalizeText(it.title) == expectedTitle && it.year == lookup.year }
            .filter { candidate ->
                expectedOriginalTitle == null ||
                    candidate.originalTitle?.let(::normalizeText) == expectedOriginalTitle
            }
            .filter { candidate ->
                expectedKinopoisk == null || candidate.kinopoiskId?.trim() == expectedKinopoisk
            }
            .distinctBy { it.gatewayPostId }
            .toList()
        return when (exact.size) {
            0 -> OfficialGatewayPostMatch.Missing
            1 -> OfficialGatewayPostMatch.Mapped(exact.single().gatewayPostId)
            else -> OfficialGatewayPostMatch.Ambiguous
        }
    }

    private fun normalizeText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()

    private val WHITESPACE = Regex("[\\s\\u00a0\\u2007\\u202f]+")
}

internal data class ParsedOfficialPlayerOffer(
    val provider: OfficialPlayerProvider,
    val title: String,
    val iframeUri: URI,
)

internal data class OfficialPlayerSnapshot(
    val gatewayPostId: GatewayPostId,
    val hasPlayer: Boolean,
    val offers: List<ParsedOfficialPlayerOffer>,
)

internal object OfficialGatewayJsonParser {
    fun parseLightSearch(json: String): List<OfficialGatewayPostCandidate> {
        val root = parseOkEnvelope(json)
        val data = root.get("data")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?: error("Gateway search data is missing")
        return data.mapNotNull { element ->
            val post = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val id = post.string("id")?.let(::safeGatewayPostId) ?: return@mapNotNull null
            val title = post.string("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val xfields = post.get("xfields")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            OfficialGatewayPostCandidate(
                gatewayPostId = id,
                title = title,
                year = xfields?.string("year-teg-xfsearch")?.let(::parseYear),
                originalTitle = xfields?.string("orig-title"),
                kinopoiskId = xfields?.string("kinopoisk_id"),
            )
        }
    }

    fun parsePlayer(json: String): OfficialPlayerSnapshot {
        val root = parseOkEnvelope(json)
        val data = root.get("data")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: error("Gateway player data is missing")
        val gatewayPostId = data.string("post_id")?.let(::safeGatewayPostId)
            ?: error("Gateway player post id is missing")
        val hasPlayer = data.get("has_player")?.takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive?.let { primitive ->
                when {
                    primitive.isBoolean -> primitive.asBoolean
                    primitive.isString -> primitive.asString.equals("true", ignoreCase = true)
                    primitive.isNumber -> primitive.asInt != 0
                    else -> false
                }
            } ?: false
        val tabs = data.get("tabs")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?: return OfficialPlayerSnapshot(gatewayPostId, hasPlayer, emptyList())
        val seenUrls = linkedSetOf<String>()
        val offers = tabs.mapNotNull { element ->
            val tab = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val provider = tab.string("balancer")?.let(OfficialPlayerProvider::fromWireName)
                ?: return@mapNotNull null
            val iframeUri = tab.string("iframe_url")?.let(::safeIframeUri)
                ?: return@mapNotNull null
            val canonicalUrl = iframeUri.toASCIIString()
            if (!seenUrls.add(canonicalUrl)) return@mapNotNull null
            ParsedOfficialPlayerOffer(
                provider = provider,
                title = tab.string("title")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: provider.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase),
                iframeUri = iframeUri,
            )
        }
        // `mirrors` is intentionally not inspected: only explicitly named tabs are trusted.
        return OfficialPlayerSnapshot(gatewayPostId, hasPlayer, offers)
    }

    private fun parseOkEnvelope(json: String): JsonObject {
        require(json.length <= MAX_JSON_CHARS) { "Gateway JSON is too large" }
        val root = JsonParser.parseString(json).takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: error("Gateway JSON root is invalid")
        require(root.string("status").equals("ok", ignoreCase = true)) {
            "Gateway status is not ok"
        }
        return root
    }

    private fun safeGatewayPostId(value: String): GatewayPostId? =
        runCatching { GatewayPostId(value.trim()) }.getOrNull()

    private fun parseYear(value: String): Int? =
        YEAR.find(value)?.value?.toIntOrNull()

    private fun safeIframeUri(value: String): URI? {
        if (
            value.isBlank() || value != value.trim() || value.any(Char::isISOControl) ||
            '\\' in value
        ) {
            return null
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (
            !uri.scheme.equals("https", ignoreCase = true) || uri.isOpaque || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.rawFragment != null || (uri.port != -1 && uri.port != 443)
        ) {
            return null
        }
        return uri
    }

    private fun JsonObject.string(name: String): String? {
        val value = get(name)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive ?: return null
        return runCatching { value.asString }.getOrNull()
    }

    private const val MAX_JSON_CHARS = 512 * 1_024
    private val YEAR = Regex("(?<![0-9])(?:18|19|20|21)[0-9]{2}(?![0-9])")
}

private fun officialOfferDestinationValidator(): (URI) -> Unit {
    val dns = ResilientPublicDns()
    return { uri -> NetworkDestinationValidator.validateHttpsPublic(uri, dns) }
}
