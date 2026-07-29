package com.kinogo.atv.data.playback

import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact-origin policy for the Collaps endpoint returned by the official player descriptor. */
object CollapsEmbedUrlPolicy : TrustedEmbedUrlPolicy {
    const val PROVIDER_HOST = "api.ortified.ws"
    private const val EMBED_PATH_PREFIX = "/embed/"

    override val providerId: String = "collaps"
    override val displayName: String = "Collaps"

    fun isCollapsCandidate(rawUrl: String): Boolean =
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

    override fun isAllowedMainFrameUrl(rawUrl: String): Boolean =
        parseUri(rawUrl)?.let(::isSafeProviderUri) == true

    private fun isSafeProviderUri(uri: URI): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.isOpaque &&
            uri.host.equals(PROVIDER_HOST, ignoreCase = true) &&
            uri.rawUserInfo == null &&
            uri.rawFragment == null &&
            (uri.port == -1 || uri.port == 443)

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

/** Resolves only the documented Collaps iframe entry; it never extracts its media URLs. */
class CollapsEmbedResolver internal constructor(
    private val destinationValidator: (URI) -> Unit,
) : PlaybackSourceResolver {
    constructor() : this(collapsDestinationValidator())

    override val id: String = "collaps-embed"

    override fun supports(candidate: PlayerEmbedCandidate): Boolean =
        CollapsEmbedUrlPolicy.isCollapsCandidate(candidate.url)

    override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceResolution {
        val embedUri = CollapsEmbedUrlPolicy.validatedEmbedUri(request.candidate.url)
            ?: return PlaybackSourceResolution.Rejected(
                "Адрес встроенного плеера Collaps не прошёл проверку",
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
                        providerId = CollapsEmbedUrlPolicy.providerId,
                        label = request.candidate.label.ifBlank { CollapsEmbedUrlPolicy.displayName },
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

private fun collapsDestinationValidator(): (URI) -> Unit {
    val dns = ResilientPublicDns()
    return { uri -> NetworkDestinationValidator.validateHttpsPublic(uri, dns) }
}
