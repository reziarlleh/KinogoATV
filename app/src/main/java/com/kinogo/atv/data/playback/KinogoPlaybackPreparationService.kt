package com.kinogo.atv.data.playback

import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import com.kinogo.atv.data.playback.cinemar.CinemarNativeResolution
import com.kinogo.atv.data.playback.cinemar.CinemarNativeSourceAdapter
import com.kinogo.atv.data.playback.collaps.CollapsNativePlaybackAdapter
import com.kinogo.atv.data.playback.collaps.CollapsNativePlaybackResult
import com.kinogo.atv.domain.PlaybackMediaPlan
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PlaybackPreparationRequest(
    val contentId: String,
    val title: String,
    val year: Int?,
    val originalTitle: String?,
    val documentOrigin: String,
    val documentUrl: String,
    /** Candidates must come from a detail page fetched immediately before this request. */
    val freshPageCandidates: List<PlayerEmbedCandidate>,
    /** A direct native plan makes the slower official recovery lookup unnecessary. */
    val useOfficialDiscoveryFallback: Boolean = true,
) {
    init {
        require(contentId.isNotBlank())
        require(title.isNotBlank())
        require(documentOrigin.isNotBlank())
        require(documentUrl.isNotBlank())
    }

    override fun toString(): String =
        "PlaybackPreparationRequest(" +
            "contentId=$contentId, title=$title, year=$year, originalTitle=$originalTitle, " +
            "documentOrigin=<redacted>, documentUrl=<redacted>, " +
            "freshPageCandidates=$freshPageCandidates, " +
            "useOfficialDiscoveryFallback=$useOfficialDiscoveryFallback)"
}

/**
 * Ephemeral launch data. It must remain in memory and be discarded when the player is closed.
 */
data class PreparedPlaybackSession(
    val nativePlan: PlaybackMediaPlan?,
    val webFallbacks: List<ResolvedPlaybackEmbed>,
    val notices: List<String> = emptyList(),
) {
    init {
        require(nativePlan != null || webFallbacks.isNotEmpty())
        require(webFallbacks.map(ResolvedPlaybackEmbed::id).distinct().size == webFallbacks.size)
    }

    override fun toString(): String =
        "PreparedPlaybackSession(nativePlan=$nativePlan, webFallbacks=<redacted>, notices=$notices)"
}

sealed interface PlaybackPreparationResult {
    data class Ready(val session: PreparedPlaybackSession) : PlaybackPreparationResult

    data class Unavailable(val userMessage: String) : PlaybackPreparationResult
}

internal fun interface OptionalOfficialPlayerDiscovery {
    suspend fun discover(lookup: OfficialPlayerLookup): OfficialPlayerDiscoveryResult
}

/**
 * Builds one Media3 plan from fresh browser-delivered provider documents.
 *
 * HTML candidates are always tried first. The official application's volatile player descriptor is
 * only a playback-time fallback; it is never consulted during app startup and its URL is never
 * cached. Unsupported/DRM/JavaScript-computed providers remain available through the isolated web
 * fallback when their document itself passes the network boundary.
 */
class KinogoPlaybackPreparationService internal constructor(
    private val documentClient: ProviderEmbedDocumentClient,
    private val cinemarAdapter: CinemarNativeSourceAdapter,
    private val collapsAdapter: CollapsNativePlaybackAdapter,
    private val officialDiscovery: OptionalOfficialPlayerDiscovery?,
) {
    constructor(
        useOfficialPlayerDiscoveryFallback: Boolean = true,
    ) : this(
        documentClient = ProviderEmbedDocumentClient(),
        cinemarAdapter = CinemarNativeSourceAdapter(),
        collapsAdapter = CollapsNativePlaybackAdapter(),
        officialDiscovery = if (useOfficialPlayerDiscoveryFallback) {
            val discovery = OfficialGatewayPlayerDiscovery(
                OfficialGatewayClientConfig(enabled = true),
            )
            OptionalOfficialPlayerDiscovery(discovery::discover)
        } else {
            null
        },
    )

    suspend fun prepare(request: PlaybackPreparationRequest): PlaybackPreparationResult {
        val plans = linkedMapOf<String, PlaybackMediaPlan>()
        val webFallbacks = linkedMapOf<String, ResolvedPlaybackEmbed>()
        val confirmedWebFallbacks = hashSetOf<String>()
        val notices = linkedSetOf<String>()
        val pageCandidates = request.freshPageCandidates
            .distinctBy(PlayerEmbedCandidate::url)
            .filterNot { candidate -> candidate.url.looksLikeDirectMedia() }
            .take(MAX_PAGE_CANDIDATES)
            .map { candidate -> PreparedCandidate(candidate, CandidateOrigin.PAGE) }

        suspend fun processCandidate(prepared: PreparedCandidate, index: Int) {
            val providerHint = prepared.candidate.normalizedProviderHint()
            val fallbackKey = "${prepared.origin.name}:$index"
            val validatedFallbackUrl = if (prepared.candidate.url.looksLikeDirectMedia()) {
                null
            } else {
                documentClient.validatedWebFallbackUrl(
                    embedUrl = prepared.candidate.url,
                    refererUrl = request.documentUrl,
                )
            }
            val preliminaryProviderId = validatedFallbackUrl?.let { fallbackUrl ->
                providerHint
                    ?: safeProviderId(fallbackUrl)
                    ?: "provider-$index"
            }
            if (validatedFallbackUrl != null && preliminaryProviderId != null) {
                webFallbacks[fallbackKey] = ResolvedPlaybackEmbed(
                    id = "web:${request.contentId}:$fallbackKey",
                    embedUrl = validatedFallbackUrl,
                    refererUrl = request.documentUrl,
                    providerId = preliminaryProviderId,
                    label = prepared.candidate.label.ifBlank { preliminaryProviderId },
                )
            }
            when (
                val fetched = documentClient.fetch(
                    embedUrl = prepared.candidate.url,
                    refererUrl = request.documentUrl,
                )
            ) {
                is ProviderEmbedDocumentResult.Failed -> {
                    if (fetched.reason == ProviderEmbedDocumentFailure.REDIRECT_REJECTED) {
                        // A destination known to redirect outside the admitted boundary is not a
                        // safe WebView fallback even when its original URL passed DNS validation.
                        webFallbacks.remove(fallbackKey)
                    }
                    notices += fetched.userMessage
                }
                is ProviderEmbedDocumentResult.Ready -> {
                    val document = fetched.document
                    val native = resolveNative(
                        providerHint = providerHint,
                        embedUrl = document.resolvedUrl,
                        html = document.html,
                    )
                    native.plan?.let { plan ->
                        val sourceId = plan.defaultSourceId
                        plans.putIfAbsent(sourceId, plan)
                    }

                    val providerId = native.providerId
                        ?: providerHint
                        ?: safeProviderId(document.resolvedUrl)
                        ?: "provider-$index"
                    // A successfully fetched final document is stronger than the validated original
                    // offer because its exact post-redirect origin is already known.
                    webFallbacks[fallbackKey] = ResolvedPlaybackEmbed(
                        id = "web:${request.contentId}:$fallbackKey",
                        embedUrl = document.resolvedUrl,
                        refererUrl = request.documentUrl,
                        providerId = providerId,
                        label = prepared.candidate.label.ifBlank { providerId },
                    )
                    confirmedWebFallbacks += fallbackKey
                    native.notice?.let(notices::add)
                }
            }
        }

        val pagePreparationCompleted = withContext(Dispatchers.Default) {
            withTimeoutOrNull(PAGE_PREPARATION_BUDGET_MS) {
                pageCandidates.forEachIndexed { index, candidate ->
                    processCandidate(candidate, index)
                }
                true
            }
        } == true
        if (!pagePreparationCompleted) {
            notices += "Часть источников не ответила вовремя"
        }

        // The gateway is a slower volatile recovery path. A page candidate that already produced a
        // native plan or a successfully fetched web document must not wait for it on every launch.
        val gatewayPreparedCandidates = mutableListOf<PreparedCandidate>()
        if (
            plans.isEmpty() &&
            confirmedWebFallbacks.isEmpty() &&
            request.useOfficialDiscoveryFallback &&
            officialDiscovery != null
        ) {
            val gatewayPreparationCompleted = withContext(Dispatchers.Default) {
                withTimeoutOrNull(GATEWAY_PREPARATION_BUDGET_MS) {
                    val pageUrls = pageCandidates.mapTo(hashSetOf()) { it.candidate.url }
                    gatewayPreparedCandidates += gatewayCandidates(request)
                        .asSequence()
                        .filterNot { it.url in pageUrls }
                        .distinctBy(PlayerEmbedCandidate::url)
                        .take((MAX_TOTAL_CANDIDATES - pageCandidates.size).coerceAtLeast(0))
                        .map { candidate -> PreparedCandidate(candidate, CandidateOrigin.GATEWAY) }
                        .toList()
                    gatewayPreparedCandidates.forEachIndexed { index, candidate ->
                        processCandidate(candidate, index)
                    }
                    true
                }
            } == true
            if (!gatewayPreparationCompleted) {
                notices += "Резервный поиск источника не ответил вовремя"
            }
        }

        if (pageCandidates.isEmpty() && gatewayPreparedCandidates.isEmpty()) {
            return PlaybackPreparationResult.Unavailable(
                "Сервис не вернул ни одного источника воспроизведения",
            )
        }

        val nativePlan = plans.values
            .toList()
            .takeIf(List<PlaybackMediaPlan>::isNotEmpty)
            ?.let(NativePlaybackPlanMapper::merge)
        if (nativePlan == null && webFallbacks.isEmpty()) {
            return PlaybackPreparationResult.Unavailable(
                notices.firstOrNull()
                    ?: "Не найден совместимый нативный или web-источник",
            )
        }
        return PlaybackPreparationResult.Ready(
            PreparedPlaybackSession(
                nativePlan = nativePlan,
                webFallbacks = webFallbacks.values.toList(),
                notices = notices.take(MAX_NOTICES),
            ),
        )
    }

    private suspend fun gatewayCandidates(
        request: PlaybackPreparationRequest,
    ): List<PlayerEmbedCandidate> {
        val discovery = officialDiscovery ?: return emptyList()
        val htmlPostId = runCatching { HtmlPostId(request.contentId) }.getOrNull()
            ?: return emptyList()
        val year = request.year ?: return emptyList()
        return try {
            when (
                val result = discovery.discover(
                    OfficialPlayerLookup(
                        htmlPostId = htmlPostId,
                        title = request.title,
                        year = year,
                        originalTitle = request.originalTitle,
                    ),
                )
            ) {
                is OfficialPlayerDiscoveryResult.Ready -> result.offers.map { offer ->
                    PlayerEmbedCandidate(
                        url = offer.iframeUrl,
                        label = offer.title.ifBlank {
                            offer.provider.wireName.replaceFirstChar(Char::uppercase)
                        },
                        providerId = offer.provider.wireName,
                    )
                }
                is OfficialPlayerDiscoveryResult.Rejected -> emptyList()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveNative(
        providerHint: String?,
        embedUrl: String,
        html: String,
    ): NativeCandidateResolution {
        val order = when (providerHint) {
            COLLAPS_PROVIDER -> listOf(COLLAPS_PROVIDER, CINEMAR_PROVIDER)
            else -> listOf(CINEMAR_PROVIDER, COLLAPS_PROVIDER)
        }
        var notice: String? = null
        for (provider in order) {
            try {
                when (provider) {
                    CINEMAR_PROVIDER -> when (
                        val result = cinemarAdapter.resolve(embedUrl = embedUrl, html = html)
                    ) {
                        is CinemarNativeResolution.Ready -> {
                            return NativeCandidateResolution(
                                providerId = CINEMAR_PROVIDER,
                                plan = NativePlaybackPlanMapper.fromCinemar(result.catalog),
                            )
                        }
                        is CinemarNativeResolution.Rejected -> {
                            if (providerHint == CINEMAR_PROVIDER) notice = result.userMessage
                        }
                    }
                    COLLAPS_PROVIDER -> when (
                        val result = collapsAdapter.resolve(embedUrl = embedUrl, html = html)
                    ) {
                        is CollapsNativePlaybackResult.Ready -> {
                            return NativeCandidateResolution(
                                providerId = COLLAPS_PROVIDER,
                                plan = NativePlaybackPlanMapper.fromCollaps(result.catalog),
                            )
                        }
                        is CollapsNativePlaybackResult.Rejected -> {
                            if (providerHint == COLLAPS_PROVIDER) {
                                notice = collapsNotice(result)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Provider formats evolve independently. A broken first adapter must not suppress
                // the other adapter or the already validated web fallback.
                if (notice == null) {
                    notice = "Нативная структура источника изменилась; доступен web-плеер"
                }
            }
        }
        return NativeCandidateResolution(providerId = providerHint, notice = notice)
    }

    private fun collapsNotice(result: CollapsNativePlaybackResult.Rejected): String = when (
        result.reason
    ) {
        com.kinogo.atv.data.playback.collaps.CollapsNativeRejection.INVALID_EMBED_URL ->
            "Некорректный адрес Collaps"
        com.kinogo.atv.data.playback.collaps.CollapsNativeRejection.CONFIG_TOO_LARGE ->
            "Ответ Collaps слишком большой"
        com.kinogo.atv.data.playback.collaps.CollapsNativeRejection.CONFIG_NOT_FOUND ->
            "В Collaps не найден публичный конфиг"
        com.kinogo.atv.data.playback.collaps.CollapsNativeRejection.MALFORMED_CONFIG ->
            "Collaps вернул повреждённый конфиг"
        com.kinogo.atv.data.playback.collaps.CollapsNativeRejection.REMOTE_PLAYLIST_UNSUPPORTED ->
            "Collaps требует web-плеер для удалённого плейлиста"
        com.kinogo.atv.data.playback.collaps.CollapsNativeRejection.BLOCKED ->
            "Источник Collaps временно заблокирован"
        com.kinogo.atv.data.playback.collaps.CollapsNativeRejection.NO_PLAYABLE_ITEMS ->
            "Collaps не вернул совместимые потоки"
        com.kinogo.atv.data.playback.collaps.CollapsNativeRejection.UNSAFE_DESTINATION ->
            "Источник Collaps не прошёл сетевую проверку"
    }

    private data class PreparedCandidate(
        val candidate: PlayerEmbedCandidate,
        val origin: CandidateOrigin,
    )

    private enum class CandidateOrigin {
        PAGE,
        GATEWAY,
    }

    private data class NativeCandidateResolution(
        val providerId: String?,
        val plan: PlaybackMediaPlan? = null,
        val notice: String? = null,
    )

    private companion object {
        const val CINEMAR_PROVIDER = "cinemar"
        const val COLLAPS_PROVIDER = "collaps"
        const val MAX_PAGE_CANDIDATES = 4
        const val MAX_TOTAL_CANDIDATES = 6
        const val MAX_NOTICES = 3
        const val PAGE_PREPARATION_BUDGET_MS = 18_000L
        const val GATEWAY_PREPARATION_BUDGET_MS = 12_000L
    }
}

private fun PlayerEmbedCandidate.normalizedProviderHint(): String? =
    providerId
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it in setOf("cinemar", "collaps") }

private fun safeProviderId(rawUrl: String): String? {
    val host = runCatching { URI(rawUrl).host }.getOrNull()?.lowercase(Locale.ROOT) ?: return null
    val sanitized = host.replace(Regex("[^a-z0-9.-]"), "-").take(80)
    return sanitized.takeIf(String::isNotBlank)
}

private fun String.looksLikeDirectMedia(): Boolean {
    val path = runCatching { URI(this).path }.getOrNull()?.lowercase(Locale.ROOT) ?: return false
    return path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp4")
}
