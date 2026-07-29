package com.kinogo.atv.data.playback.cinemar

import java.net.URI

/**
 * A short-lived provider URL. It is intentionally not a data class: diagnostics and accidental
 * collection logging must not reveal signed paths or query parameters.
 */
class CinemarTransientUrl internal constructor(
    private val uri: URI,
) {
    /** The only intentional escape hatch for a Media3 request built in memory. */
    fun valueForPlayback(): String = uri.toASCIIString()

    internal fun asUri(): URI = uri

    override fun equals(other: Any?): Boolean =
        other is CinemarTransientUrl && uri == other.uri

    override fun hashCode(): Int = uri.hashCode()

    override fun toString(): String = "CinemarTransientUrl(<redacted>)"
}

enum class CinemarMediaKind(
    val mimeType: String,
) {
    HLS("application/x-mpegURL"),
    DASH("application/dash+xml"),
    MP4("video/mp4"),
}

enum class CinemarSubtitleKind(
    val mimeType: String,
) {
    WEBVTT("text/vtt"),
    SUBRIP("application/x-subrip"),
    SSA("text/x-ssa"),
}

data class CinemarMediaVariant(
    val id: String,
    val label: String,
    val kind: CinemarMediaKind,
    val url: CinemarTransientUrl,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
    }
}

data class CinemarSubtitle(
    val id: String,
    val label: String,
    val kind: CinemarSubtitleKind,
    val url: CinemarTransientUrl,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
    }
}

data class CinemarFolderPathEntry(
    val id: String,
    val title: String,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
    }
}

sealed interface CinemarPlaylistNode {
    val id: String
    val title: String
}

data class CinemarFolder(
    override val id: String,
    override val title: String,
    val children: List<CinemarPlaylistNode>,
) : CinemarPlaylistNode {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(children.isNotEmpty())
    }
}

/**
 * A playable leaf in the provider tree.
 *
 * [folderPath] retains the public hierarchy without guessing whether a provider named a level
 * "season", "episode", or something else. A UI adapter can map the first two levels to season and
 * episode while still handling unusual provider layouts.
 */
data class CinemarStream(
    override val id: String,
    override val title: String,
    val contextTitle: String?,
    val providerNodeId: String?,
    val sourceId: String?,
    val voiceId: String,
    val durationMs: Long?,
    val folderPath: List<CinemarFolderPathEntry>,
    val mediaVariants: List<CinemarMediaVariant>,
    val subtitles: List<CinemarSubtitle>,
) : CinemarPlaylistNode {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(voiceId.isNotBlank())
        require(durationMs == null || durationMs > 0L)
        require(mediaVariants.isNotEmpty())
        require(mediaVariants.map { it.id }.distinct().size == mediaVariants.size)
        require(subtitles.map { it.id }.distinct().size == subtitles.size)
    }
}

/**
 * Ephemeral parsed result. This type belongs to the provider adapter and must not be persisted.
 */
data class CinemarParsedCatalog(
    val videoId: Long,
    val roots: List<CinemarPlaylistNode>,
    val streams: List<CinemarStream>,
) {
    init {
        require(videoId > 0L)
        require(roots.isNotEmpty())
        require(streams.isNotEmpty())
        require(streams.map { it.id }.distinct().size == streams.size)
    }
}

enum class CinemarNativeFailureCode {
    INVALID_EMBED_ADDRESS,
    DOCUMENT_TOO_LARGE,
    CONFIG_NOT_FOUND,
    MALFORMED_CONFIG,
    NO_PLAYABLE_STREAMS,
    UNSAFE_NETWORK_DESTINATION,
}

sealed interface CinemarNativeResolution {
    data class Ready(
        val catalog: CinemarParsedCatalog,
    ) : CinemarNativeResolution

    data class Rejected(
        val code: CinemarNativeFailureCode,
    ) : CinemarNativeResolution {
        val userMessage: String
            get() = when (code) {
                CinemarNativeFailureCode.INVALID_EMBED_ADDRESS ->
                    "Некорректный адрес плеера Cinemar"
                CinemarNativeFailureCode.DOCUMENT_TOO_LARGE ->
                    "Ответ плеера Cinemar превышает допустимый размер"
                CinemarNativeFailureCode.CONFIG_NOT_FOUND ->
                    "В ответе Cinemar не найден публичный конфиг воспроизведения"
                CinemarNativeFailureCode.MALFORMED_CONFIG ->
                    "Cinemar вернул повреждённый конфиг воспроизведения"
                CinemarNativeFailureCode.NO_PLAYABLE_STREAMS ->
                    "Cinemar не вернул совместимые HTTPS-источники"
                CinemarNativeFailureCode.UNSAFE_NETWORK_DESTINATION ->
                    "Источник Cinemar не прошёл сетевую проверку"
            }
    }
}
