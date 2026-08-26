package com.kinogo.atv.domain

enum class QualityMode {
    AUTO,
    FIXED,
}

data class VideoQuality(
    val id: String,
    val label: String,
    val mode: QualityMode = QualityMode.FIXED,
    val height: Int? = null,
    val bitrate: Long? = null,
    val isDefault: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(height == null || height > 0)
        require(bitrate == null || bitrate > 0)
    }
}

data class VoiceOption(
    val id: String,
    val label: String,
    val languageTag: String? = null,
    val isOriginal: Boolean = false,
    val isDefault: Boolean = false,
    val qualities: List<VideoQuality>,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(qualities.map { it.id }.distinct().size == qualities.size) {
            "Quality ids must be unique within a voice option"
        }
    }
}

data class PlaybackOptions(
    val voices: List<VoiceOption>,
) {
    init {
        require(voices.map { it.id }.distinct().size == voices.size) {
            "Voice ids must be unique within playback options"
        }
    }

    fun findVoice(voiceId: String): VoiceOption? = voices.firstOrNull { it.id == voiceId }

    fun findQuality(voiceId: String, qualityId: String): VideoQuality? =
        findVoice(voiceId)?.qualities?.firstOrNull { it.id == qualityId }
}

data class Episode(
    val id: String,
    val number: Int?,
    val title: String,
    val durationMs: Long? = null,
    val playbackOptions: PlaybackOptions,
) {
    init {
        require(id.isNotBlank())
        require(number == null || number > 0)
        require(title.isNotBlank())
        require(durationMs == null || durationMs > 0)
    }
}

data class Season(
    val id: String,
    val number: Int?,
    val title: String,
    val episodes: List<Episode>,
) {
    init {
        require(id.isNotBlank())
        require(number == null || number > 0)
        require(title.isNotBlank())
        require(episodes.map { it.id }.distinct().size == episodes.size) {
            "Episode ids must be unique within a season"
        }
    }
}

/** A complete, user-confirmed choice that can be resolved to a fresh media URL. */
data class PlaybackSelection(
    val contentId: String,
    val seasonId: String? = null,
    val episodeId: String? = null,
    val voiceId: String,
    val qualityId: String,
    /** Stable provider adapter id only; never a media or iframe URL. */
    val sourceId: String? = null,
) {
    init {
        require(contentId.isNotBlank())
        require(voiceId.isNotBlank())
        require(qualityId.isNotBlank())
        require(sourceId == null || sourceId.isNotBlank())
        require((seasonId == null) == (episodeId == null)) {
            "Season and episode must either both be set or both be absent"
        }
        require(seasonId == null || seasonId.isNotBlank())
        require(episodeId == null || episodeId.isNotBlank())
    }

    val playbackUnitId: String
        get() = episodeId ?: contentId

    val isEpisode: Boolean
        get() = episodeId != null
}
