package com.kinogo.atv.data.playback

import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact-origin policy implemented by every provider rendered in the isolated WebView. */
interface TrustedEmbedUrlPolicy {
    val providerId: String
    val displayName: String

    fun validatedEmbedUri(rawUrl: String): URI?

    fun isAllowedMainFrameUrl(rawUrl: String): Boolean
}

/** Security policy shared by Cinemar discovery and its isolated WebView. */
object CinemarEmbedUrlPolicy : TrustedEmbedUrlPolicy {
    const val PROVIDER_HOST = "cinemar.cc"
    private const val EMBED_PATH_PREFIX = "/embed/"

    override val providerId: String = "cinemar"
    override val displayName: String = "Cinemar"

    fun isCinemarCandidate(rawUrl: String): Boolean =
        parseUri(rawUrl)?.host.equals(PROVIDER_HOST, ignoreCase = true)

    override fun validatedEmbedUri(rawUrl: String): URI? {
        val uri = parseUri(rawUrl) ?: return null
        if (!isSafeProviderUri(uri)) return null
        val path = uri.rawPath ?: return null
        if (!path.startsWith(EMBED_PATH_PREFIX) || path.length == EMBED_PATH_PREFIX.length) {
            return null
        }
        return uri
    }

    /** Provider navigation stays on the exact Cinemar origin; subdomains are not inherited. */
    override fun isAllowedMainFrameUrl(rawUrl: String): Boolean {
        val uri = parseUri(rawUrl) ?: return false
        return isSafeProviderUri(uri)
    }

    /**
     * Prevents leaking credentials or an unrelated URL through the Referer header. The complete
     * Kinogo detail URL is allowed only when it belongs to the already selected document origin.
     */
    fun validatedRefererUrl(rawUrl: String, documentOrigin: String): String? {
        val referer = parseUri(rawUrl) ?: return null
        val origin = parseUri(documentOrigin) ?: return null
        if (!isSafeHttpsUri(referer) || !isSafeHttpsUri(origin)) return null
        if (!origin.rawPath.isNullOrEmpty() && origin.rawPath != "/") return null
        if (origin.rawQuery != null || origin.rawFragment != null) return null
        if (referer.rawFragment != null || normalizedOrigin(referer) != normalizedOrigin(origin)) {
            return null
        }
        return referer.toASCIIString()
    }

    private fun isSafeProviderUri(uri: URI): Boolean =
        isSafeHttpsUri(uri) &&
            uri.host.equals(PROVIDER_HOST, ignoreCase = true) &&
            uri.rawFragment == null

    private fun isSafeHttpsUri(uri: URI): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.isOpaque &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443)

    private fun normalizedOrigin(uri: URI): String? {
        if (!isSafeHttpsUri(uri)) return null
        return "https://${requireNotNull(uri.host).lowercase(Locale.ROOT)}"
    }

    private fun parseUri(rawUrl: String): URI? {
        if (
            rawUrl.isBlank() ||
            rawUrl != rawUrl.trim() ||
            rawUrl.any(Char::isISOControl) ||
            '\\' in rawUrl
        ) {
            return null
        }
        return runCatching { URI(rawUrl) }.getOrNull()
    }
}

/** Known providers are deliberately allowlisted; a gateway label alone never grants WebView access. */
object TrustedEmbedUrlPolicies {
    private val policies = listOf<TrustedEmbedUrlPolicy>(
        CinemarEmbedUrlPolicy,
        CollapsEmbedUrlPolicy,
    )

    fun forProvider(providerId: String): TrustedEmbedUrlPolicy? =
        policies.firstOrNull { it.providerId == providerId.trim().lowercase(Locale.ROOT) }
}

/**
 * Resolves only the public Cinemar embed document. It intentionally does not inspect provider
 * JavaScript, decode its configuration, or expose the provider's underlying media requests.
 */
class CinemarEmbedResolver internal constructor(
    private val destinationValidator: (URI) -> Unit,
) : PlaybackSourceResolver {
    constructor() : this(cinemarDestinationValidator())

    override val id: String = "cinemar-embed"

    override fun supports(candidate: PlayerEmbedCandidate): Boolean =
        CinemarEmbedUrlPolicy.isCinemarCandidate(candidate.url)

    override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceResolution {
        val embedUri = CinemarEmbedUrlPolicy.validatedEmbedUri(request.candidate.url)
            ?: return PlaybackSourceResolution.Rejected(
                "Адрес встроенного плеера Cinemar не прошёл проверку",
            )
        val referer = CinemarEmbedUrlPolicy.validatedRefererUrl(
            rawUrl = request.documentUrl,
            documentOrigin = request.documentOrigin,
        ) ?: return PlaybackSourceResolution.Rejected("Некорректный адрес страницы фильма")

        return withContext(Dispatchers.IO) {
            try {
                destinationValidator(embedUri)
                PlaybackSourceResolution.Embedded(
                    ResolvedPlaybackEmbed(
                        id = "$id:${request.contentId}",
                        embedUrl = embedUri.toASCIIString(),
                        refererUrl = referer,
                        providerId = "cinemar",
                        label = request.candidate.label,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PlaybackSourceResolution.Rejected(
                    "Адрес встроенного плеера не прошёл сетевую проверку",
                )
            }
        }
    }
}

private fun cinemarDestinationValidator(): (URI) -> Unit {
    val dns = ResilientPublicDns()
    return { uri -> NetworkDestinationValidator.validateHttpsPublic(uri, dns) }
}
