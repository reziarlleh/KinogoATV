package com.kinogo.atv.data.playback

import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlaybackSourceRequest(
    val contentId: String,
    val documentOrigin: String,
    val documentUrl: String = documentOrigin,
    val candidate: PlayerEmbedCandidate,
) {
    init {
        require(contentId.isNotBlank())
        require(documentOrigin.isNotBlank())
        require(documentUrl.isNotBlank())
    }

    override fun toString(): String =
        "PlaybackSourceRequest(" +
            "contentId=$contentId, documentOrigin=<redacted>, documentUrl=<redacted>, " +
            "candidate=$candidate)"
}

enum class PlaybackMediaKind(val mimeType: String) {
    HLS("application/x-mpegURL"),
    DASH("application/dash+xml"),
    MP4("video/mp4"),
}

/** A short-lived source kept in memory only; signed URLs must never be persisted or logged. */
data class ResolvedPlaybackSource(
    val id: String,
    val mediaUrl: String,
    val mediaKind: PlaybackMediaKind,
    val providerId: String,
    val label: String,
) {
    init {
        require(id.isNotBlank())
        require(mediaUrl.isNotBlank())
        require(providerId.isNotBlank())
        require(label.isNotBlank())
    }

    val mimeType: String
        get() = mediaKind.mimeType

    override fun toString(): String =
        "ResolvedPlaybackSource(" +
            "id=$id, mediaUrl=<redacted>, mediaKind=$mediaKind, " +
            "providerId=$providerId, label=$label)"
}

/**
 * A provider-owned HTML player that passed the same public-network boundary as native media.
 *
 * This is deliberately not a [ResolvedPlaybackSource]: callers must render it in the isolated
 * provider player and must never feed the embed URL to Media3 as if it were a media manifest.
 */
data class ResolvedPlaybackEmbed(
    val id: String,
    val embedUrl: String,
    val refererUrl: String,
    val providerId: String,
    val label: String,
) {
    init {
        require(id.isNotBlank())
        require(embedUrl.isNotBlank())
        require(refererUrl.isNotBlank())
        require(providerId.isNotBlank())
        require(label.isNotBlank())
    }

    override fun toString(): String =
        "ResolvedPlaybackEmbed(" +
            "id=$id, embedUrl=<redacted>, refererUrl=<redacted>, " +
            "providerId=$providerId, label=$label)"
}

sealed interface PlaybackSourceResolution {
    data class Resolved(val source: ResolvedPlaybackSource) : PlaybackSourceResolution

    data class Embedded(val embed: ResolvedPlaybackEmbed) : PlaybackSourceResolution

    data class Unsupported(val reason: String) : PlaybackSourceResolution

    data class Rejected(val reason: String) : PlaybackSourceResolution
}

interface PlaybackSourceResolver {
    val id: String

    fun supports(candidate: PlayerEmbedCandidate): Boolean

    suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceResolution
}

/**
 * Selects a resolver by declared capability. It never falls back to executing provider HTML or
 * JavaScript: unsupported embeds remain explicit until a documented provider adapter is added.
 */
class PlaybackSourceResolverRegistry(
    private val resolvers: List<PlaybackSourceResolver>,
) {
    init {
        require(resolvers.map { it.id }.distinct().size == resolvers.size) {
            "Playback resolver ids must be unique"
        }
    }

    suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceResolution {
        val resolver = resolvers.firstOrNull { it.supports(request.candidate) }
            ?: return PlaybackSourceResolution.Unsupported(
                "Для этого типа встроенного плеера пока нет нативного адаптера",
            )
        return resolver.resolve(request)
    }

    suspend fun resolveFirst(
        contentId: String,
        documentOrigin: String,
        documentUrl: String = documentOrigin,
        candidates: List<PlayerEmbedCandidate>,
    ): PlaybackSourceResolution {
        if (candidates.isEmpty()) {
            return PlaybackSourceResolution.Unsupported(
                "На странице не найден совместимый источник воспроизведения",
            )
        }

        var firstRejected: PlaybackSourceResolution.Rejected? = null
        var firstUnsupported: PlaybackSourceResolution.Unsupported? = null
        candidates.forEach { candidate ->
            when (
                val result = resolve(
                    PlaybackSourceRequest(
                        contentId = contentId,
                        documentOrigin = documentOrigin,
                        documentUrl = documentUrl,
                        candidate = candidate,
                    ),
                )
            ) {
                is PlaybackSourceResolution.Resolved -> return result
                is PlaybackSourceResolution.Embedded -> return result
                is PlaybackSourceResolution.Rejected -> if (firstRejected == null) {
                    firstRejected = result
                }
                is PlaybackSourceResolution.Unsupported -> if (firstUnsupported == null) {
                    firstUnsupported = result
                }
            }
        }
        return firstRejected
            ?: firstUnsupported
            ?: PlaybackSourceResolution.Unsupported("Источник воспроизведения не поддерживается")
    }
}

/**
 * Resolves only explicit media documents. DNS is checked against the same public-address policy as
 * mirrors, and no cookies, page headers or provider scripts are copied into the player request.
 */
class DirectMediaResolver internal constructor(
    private val destinationValidator: (URI) -> Unit,
) : PlaybackSourceResolver {
    constructor() : this(resilientDestinationValidator())

    override val id: String = "direct-media"

    override fun supports(candidate: PlayerEmbedCandidate): Boolean =
        mediaKind(candidate.url) != null

    override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceResolution {
        val uri = parseUri(request.candidate.url)
            ?: return PlaybackSourceResolution.Rejected("Некорректный адрес медиаисточника")
        val kind = mediaKind(uri)
            ?: return PlaybackSourceResolution.Unsupported("Ссылка не является HLS, DASH или MP4")
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.isOpaque ||
            uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null ||
            uri.rawFragment != null
        ) {
            return PlaybackSourceResolution.Rejected(
                "Медиаисточник должен быть публичным HTTPS-адресом без учётных данных",
            )
        }
        val documentOrigin = normalizedDocumentOrigin(request.documentOrigin)
            ?: return PlaybackSourceResolution.Rejected("Некорректный origin страницы")
        if (normalizedOrigin(uri) != documentOrigin) {
            return PlaybackSourceResolution.Unsupported(
                "Внешний CDN требует отдельного проверенного адаптера провайдера",
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                destinationValidator(uri)
                val providerId = request.candidate.providerId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: requireNotNull(uri.host).lowercase(Locale.ROOT)
                PlaybackSourceResolution.Resolved(
                    ResolvedPlaybackSource(
                        id = "$id:${request.contentId}:${kind.name.lowercase(Locale.ROOT)}",
                        mediaUrl = uri.toASCIIString(),
                        mediaKind = kind,
                        providerId = providerId,
                        label = request.candidate.label,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PlaybackSourceResolution.Rejected(
                    "Адрес медиаисточника не прошёл сетевую проверку",
                )
            }
        }
    }

    private fun parseUri(rawUrl: String): URI? {
        if (rawUrl.isBlank() || rawUrl != rawUrl.trim() || rawUrl.any { it.isISOControl() }) {
            return null
        }
        return runCatching { URI(rawUrl) }.getOrNull()
    }

    private fun mediaKind(rawUrl: String): PlaybackMediaKind? =
        parseUri(rawUrl)?.let(::mediaKind)

    private fun mediaKind(uri: URI): PlaybackMediaKind? {
        val path = uri.path?.lowercase(Locale.ROOT) ?: return null
        return when {
            path.endsWith(".m3u8") -> PlaybackMediaKind.HLS
            path.endsWith(".mpd") -> PlaybackMediaKind.DASH
            path.endsWith(".mp4") -> PlaybackMediaKind.MP4
            else -> null
        }
    }

    private fun normalizedDocumentOrigin(rawOrigin: String): String? {
        val uri = runCatching { URI(rawOrigin) }.getOrNull() ?: return null
        if (
            (!uri.path.isNullOrEmpty() && uri.path != "/") ||
            uri.rawQuery != null ||
            uri.rawFragment != null
        ) {
            return null
        }
        return normalizedOrigin(uri)
    }

    private fun normalizedOrigin(uri: URI): String? {
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null
        ) {
            return null
        }
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://${requireNotNull(uri.host).lowercase(Locale.ROOT)}$port"
    }
}

private fun resilientDestinationValidator(): (URI) -> Unit {
    val dns = ResilientPublicDns()
    return { uri -> NetworkDestinationValidator.validateHttpsPublic(uri, dns) }
}
