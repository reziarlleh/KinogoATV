package com.kinogo.atv.data.playback.collaps

import java.net.URI

/**
 * Native, in-memory representation of the public configuration passed to Collaps' web player.
 *
 * Stream URLs are intentionally transient. None of these models is serializable and every model
 * which contains a URL redacts it from [toString] to reduce accidental logging.
 */
data class CollapsParsedCatalog(
    val title: String,
    val movie: CollapsPlaybackItem? = null,
    val seasons: List<CollapsSeason> = emptyList(),
    val flatEpisodes: List<CollapsPlaybackItem> = emptyList(),
    val currentSelection: CollapsCurrentSelection? = null,
) {
    init {
        val contentKinds = listOf(
            movie != null,
            seasons.isNotEmpty(),
            flatEpisodes.isNotEmpty(),
        ).count { it }
        require(contentKinds == 1) { "Exactly one Collaps content layout is required" }
    }

    val playableItems: List<CollapsPlaybackItem>
        get() = buildList {
            movie?.let(::add)
            seasons.forEach { addAll(it.episodes) }
            addAll(flatEpisodes)
        }
}

data class CollapsSeason(
    val number: String,
    val blocked: Boolean,
    val episodes: List<CollapsPlaybackItem>,
) {
    init {
        require(number.isNotBlank())
        require(episodes.isNotEmpty())
    }
}

data class CollapsPlaybackItem(
    val id: String,
    val season: String? = null,
    val episode: String? = null,
    val title: String,
    val blocked: Boolean,
    val streams: List<CollapsStream>,
    val audioTracks: List<CollapsAudioTrack>,
    val subtitles: List<CollapsSubtitle>,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(blocked || streams.isNotEmpty()) {
            "An unblocked Collaps item must contain at least one native stream"
        }
    }
}

data class CollapsAudioTrack(
    /** Zero-based track index in the adaptive manifest. */
    val manifestTrackIndex: Int,
    val name: String,
) {
    init {
        require(manifestTrackIndex >= 0)
        require(name.isNotBlank())
    }
}

enum class CollapsStreamType {
    HLS,
    DASH,
    FILE,
}

/**
 * A manifest or progressive file delivered to the native player.
 *
 * Equality is deliberately identity-based: callers should use [id] as the stable UI key and never
 * persist the expiring [uri].
 */
class CollapsStream internal constructor(
    val id: String,
    val type: CollapsStreamType,
    val qualityHeight: Int?,
    val uri: URI,
) {
    init {
        require(id.isNotBlank())
        require(qualityHeight == null || qualityHeight > 0)
    }

    override fun toString(): String =
        "CollapsStream(id=$id, type=$type, qualityHeight=$qualityHeight, uri=<redacted>)"
}

class CollapsSubtitle internal constructor(
    val id: String,
    val name: String,
    val uri: URI,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
    }

    override fun toString(): String =
        "CollapsSubtitle(id=$id, name=$name, uri=<redacted>)"
}

data class CollapsCurrentSelection(
    val id: String? = null,
    val season: String? = null,
    val episode: String? = null,
) {
    init {
        require(id != null || season != null || episode != null)
    }
}

enum class CollapsNativeRejection {
    INVALID_EMBED_URL,
    CONFIG_TOO_LARGE,
    CONFIG_NOT_FOUND,
    MALFORMED_CONFIG,
    REMOTE_PLAYLIST_UNSUPPORTED,
    BLOCKED,
    NO_PLAYABLE_ITEMS,
    UNSAFE_DESTINATION,
}

sealed interface CollapsNativePlaybackResult {
    data class Ready(val catalog: CollapsParsedCatalog) : CollapsNativePlaybackResult

    data class Rejected(val reason: CollapsNativeRejection) : CollapsNativePlaybackResult
}
