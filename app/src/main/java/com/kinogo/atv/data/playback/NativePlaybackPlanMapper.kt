package com.kinogo.atv.data.playback

import com.kinogo.atv.data.playback.cinemar.CinemarMediaKind
import com.kinogo.atv.data.playback.cinemar.CinemarParsedCatalog
import com.kinogo.atv.data.playback.cinemar.CinemarStream
import com.kinogo.atv.data.playback.cinemar.CinemarDeferredGrantRegistry
import com.kinogo.atv.data.playback.collaps.CollapsParsedCatalog
import com.kinogo.atv.data.playback.collaps.CollapsPlaybackItem
import com.kinogo.atv.data.playback.collaps.CollapsStream
import com.kinogo.atv.data.playback.collaps.CollapsStreamType
import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant
import com.kinogo.atv.domain.PlaybackSubtitleTrack
import java.net.URI
import java.util.Locale

/** Maps provider-owned ephemeral catalogs into the one native TV-player matrix. */
object NativePlaybackPlanMapper {
    fun fromCinemar(
        catalog: CinemarParsedCatalog,
        deferredEmbedUrl: String? = null,
    ): PlaybackMediaPlan {
        var deferredRegistry: CinemarDeferredGrantRegistry? = null
        val coordinates = catalog.streams.associateWith(::cinemarCoordinates)
        val episodic = coordinates.values.any { it.episodeNumber != null }
        val voices = UniqueLabelScope()
        val variants = buildList {
            catalog.streams.forEach { stream ->
                val coordinate = coordinates.getValue(stream)
                if (episodic && coordinate.episodeNumber == null) return@forEach
                val seasonNumber = if (episodic) coordinate.seasonNumber ?: 1 else null
                val voiceover = voices.next(
                    scope = "$seasonNumber:${coordinate.episodeNumber}",
                    rawLabel = stream.title,
                )
                val qualities = UniqueLabelScope()
                val subtitles = stream.subtitles.map { subtitle ->
                    PlaybackSubtitleTrack(
                        id = "cinemar:${subtitle.id}",
                        label = subtitle.label,
                        mediaUrl = subtitle.url.valueForPlayback(),
                        mimeType = subtitle.kind.mimeType,
                        languageTag = inferLanguageTag(subtitle.label),
                    )
                }
                stream.mediaVariants.forEach { media ->
                    add(
                        PlaybackMediaVariant(
                            id = "cinemar:${stream.id}:${media.id}",
                            sourceId = CINEMAR_SOURCE_ID,
                            sourceLabel = CINEMAR_SOURCE_LABEL,
                            seasonNumber = seasonNumber,
                            episodeNumber = if (episodic) coordinate.episodeNumber else null,
                            voiceover = voiceover,
                            quality = qualities.next(stream.id, media.label),
                            mediaUrl = media.url.valueForPlayback(),
                            mimeType = media.kind.mimeType,
                            subtitleTracks = subtitles,
                        ),
                    )
                }
                if (stream.mediaVariants.isEmpty() && stream.grantToken != null) {
                    val embedUrl = requireNotNull(deferredEmbedUrl) {
                        "Deferred Cinemar streams require their resolved embed address"
                    }
                    add(
                        PlaybackMediaVariant(
                            id = "cinemar:${stream.id}:grant",
                            sourceId = CINEMAR_SOURCE_ID,
                            sourceLabel = CINEMAR_SOURCE_LABEL,
                            seasonNumber = seasonNumber,
                            episodeNumber = if (episodic) coordinate.episodeNumber else null,
                            voiceover = voiceover,
                            quality = qualities.next(stream.id, "Авто"),
                            mediaUrl = (deferredRegistry ?: CinemarDeferredGrantRegistry().also {
                                deferredRegistry = it
                            }).register(embedUrl, stream),
                            // The current playlist/load contract returns HLS. A different provider
                            // kind is rejected by the lazy resolver and falls back to web safely.
                            mimeType = CinemarMediaKind.HLS.mimeType,
                            subtitleTracks = subtitles,
                        ),
                    )
                }
            }
        }
        require(variants.isNotEmpty()) { "Cinemar catalog has no mappable native variants" }
        return PlaybackMediaPlan(
            variants = variants,
            mediaUrlResolver = deferredRegistry,
        )
    }

    fun fromCollaps(catalog: CollapsParsedCatalog): PlaybackMediaPlan {
        val episodic = catalog.movie == null
        val variants = buildList {
            if (catalog.movie != null) {
                addAll(mapCollapsItem(catalog.movie, seasonNumber = null, episodeNumber = null))
            } else if (catalog.seasons.isNotEmpty()) {
                catalog.seasons.forEachIndexed { seasonIndex, season ->
                    val seasonNumber = positiveNumber(season.number) ?: seasonIndex + 1
                    season.episodes.forEachIndexed { episodeIndex, item ->
                        val episodeNumber = positiveNumber(item.episode) ?: episodeIndex + 1
                        addAll(mapCollapsItem(item, seasonNumber, episodeNumber))
                    }
                }
            } else {
                catalog.flatEpisodes.forEachIndexed { episodeIndex, item ->
                    val episodeNumber = positiveNumber(item.episode)
                        ?: positiveNumber(item.id)
                        ?: episodeIndex + 1
                    addAll(mapCollapsItem(item, seasonNumber = 1, episodeNumber = episodeNumber))
                }
            }
        }
        require(variants.isNotEmpty()) { "Collaps catalog has no mappable native variants" }
        require(variants.all { (it.episodeNumber != null) == episodic }) {
            "Collaps catalog mixes film and episode variants"
        }
        return PlaybackMediaPlan(variants)
    }

    /**
     * Merges sources only when they describe the same content shape. A malformed provider cannot
     * make a known-good source unusable.
     */
    fun merge(plans: List<PlaybackMediaPlan>): PlaybackMediaPlan {
        require(plans.isNotEmpty())
        val episodic = plans.first().isEpisodic
        val compatible = plans.filter { it.isEpisodic == episodic }
        val resolvers = compatible.mapNotNull(PlaybackMediaPlan::mediaUrlResolver)
        return PlaybackMediaPlan(
            variants = compatible.flatMap(PlaybackMediaPlan::variants),
            mediaUrlResolver = when (resolvers.size) {
                0 -> null
                1 -> resolvers.single()
                else -> CompositePlaybackMediaUrlResolver(resolvers)
            },
        )
    }

    private fun mapCollapsItem(
        item: CollapsPlaybackItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): List<PlaybackMediaVariant> {
        if (item.blocked || item.streams.isEmpty()) return emptyList()
        val voiceOptions = if (item.audioTracks.isEmpty()) {
            listOf(CollapsVoice("По умолчанию", null))
        } else {
            val labels = UniqueLabelScope()
            item.audioTracks.map { audio ->
                CollapsVoice(
                    label = labels.next(item.id, audio.name),
                    manifestTrackIndex = audio.manifestTrackIndex,
                )
            }
        }
        val qualityLabels = collapsQualityLabels(item.streams)
        val subtitles = item.subtitles.map { subtitle ->
            PlaybackSubtitleTrack(
                id = "collaps:${subtitle.id}",
                label = subtitle.name,
                mediaUrl = subtitle.uri.toASCIIString(),
                mimeType = subtitleMimeType(subtitle.uri),
                languageTag = inferLanguageTag(subtitle.name),
            )
        }
        return buildList {
            item.streams.forEachIndexed { streamIndex, stream ->
                voiceOptions.forEachIndexed { voiceIndex, voice ->
                    add(
                        PlaybackMediaVariant(
                            id = "collaps:${item.id}:${stream.id}:a$voiceIndex",
                            sourceId = COLLAPS_SOURCE_ID,
                            sourceLabel = COLLAPS_SOURCE_LABEL,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            voiceover = voice.label,
                            quality = qualityLabels[streamIndex],
                            mediaUrl = stream.uri.toASCIIString(),
                            mimeType = collapsMimeType(stream),
                            preferredAudioTrackIndex = voice.manifestTrackIndex,
                            subtitleTracks = subtitles,
                        ),
                    )
                }
            }
        }
    }

    private fun collapsQualityLabels(streams: List<CollapsStream>): List<String> {
        val labels = UniqueLabelScope()
        return streams.map { stream ->
            val raw = stream.qualityHeight?.let { "${it}p" } ?: when (stream.type) {
                CollapsStreamType.HLS -> if (streams.size == 1) "Авто" else "Авто · HLS"
                CollapsStreamType.DASH -> if (streams.size == 1) "Авто" else "Авто · DASH"
                CollapsStreamType.FILE -> "Файл"
            }
            labels.next("quality", raw)
        }
    }

    private fun collapsMimeType(stream: CollapsStream): String? = when (stream.type) {
        CollapsStreamType.HLS -> PlaybackMediaKind.HLS.mimeType
        CollapsStreamType.DASH -> PlaybackMediaKind.DASH.mimeType
        CollapsStreamType.FILE -> when (stream.uri.path?.substringAfterLast('.', "")?.lowercase()) {
            "mp4", "m4v" -> PlaybackMediaKind.MP4.mimeType
            else -> null
        }
    }

    private fun subtitleMimeType(uri: URI): String = when (
        uri.path?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
    ) {
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        else -> "text/vtt"
    }

    private fun cinemarCoordinates(stream: CinemarStream): EpisodeCoordinate {
        val titles = stream.folderPath.map { it.title }
        val season = titles.firstNotNullOfOrNull { SEASON_NUMBER.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: SXX_EXX.find(titles.joinToString(" "))?.groupValues?.get(1)?.toIntOrNull()
        val episode = titles.firstNotNullOfOrNull { EPISODE_NUMBER.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: SXX_EXX.find(titles.joinToString(" "))?.groupValues?.get(2)?.toIntOrNull()

        if (episode != null) return EpisodeCoordinate(season ?: 1, episode)
        if (titles.size >= 2) {
            val inferredSeason = season ?: positiveNumber(titles[0]) ?: 1
            val inferredEpisode = positiveNumber(titles[1])
            if (inferredEpisode != null) return EpisodeCoordinate(inferredSeason, inferredEpisode)
        }
        if (titles.size == 1 && season == null) {
            positiveNumber(titles.single())?.let { return EpisodeCoordinate(1, it) }
        }
        val contextEpisode = stream.contextTitle?.let { title ->
            EPISODE_NUMBER.find(title)?.groupValues?.get(1)?.toIntOrNull()
        }
        return EpisodeCoordinate(
            seasonNumber = if (contextEpisode == null) season else season ?: 1,
            episodeNumber = contextEpisode,
        )
    }

    private fun positiveNumber(value: String?): Int? =
        value?.let { FIRST_POSITIVE_NUMBER.find(it)?.value?.toIntOrNull() }?.takeIf { it > 0 }

    private fun inferLanguageTag(label: String): String? {
        val normalized = label.lowercase(Locale.ROOT)
        return when {
            Regex("""(?:^|\W)(?:ru|rus|рус)(?:\W|$)""").containsMatchIn(normalized) -> "ru"
            Regex("""(?:^|\W)(?:uk|ukr|укр)(?:\W|$)""").containsMatchIn(normalized) -> "uk"
            Regex("""(?:^|\W)(?:en|eng|англ)(?:\W|$)""").containsMatchIn(normalized) -> "en"
            else -> null
        }
    }

    private data class EpisodeCoordinate(
        val seasonNumber: Int?,
        val episodeNumber: Int?,
    )

    private data class CollapsVoice(
        val label: String,
        val manifestTrackIndex: Int?,
    )

    private class UniqueLabelScope {
        private val counts = linkedMapOf<String, Int>()

        fun next(scope: String, rawLabel: String): String {
            val label = rawLabel.trim().ifEmpty { "Вариант" }
            val key = "$scope\u0000${label.lowercase(Locale.ROOT)}"
            val number = (counts[key] ?: 0) + 1
            counts[key] = number
            return if (number == 1) label else "$label · $number"
        }
    }

    private const val CINEMAR_SOURCE_ID = "cinemar"
    private const val CINEMAR_SOURCE_LABEL = "Cinemar"
    private const val COLLAPS_SOURCE_ID = "collaps"
    private const val COLLAPS_SOURCE_LABEL = "Collaps"
    private val FIRST_POSITIVE_NUMBER = Regex("""(?<!\d)[1-9]\d*""")
    private val SEASON_NUMBER = Regex(
        """(?:сезон|season)\s*[:№#.-]?\s*([1-9]\d*)""",
        RegexOption.IGNORE_CASE,
    )
    private val EPISODE_NUMBER = Regex(
        """(?:сер(?:ия|ія)|episode|ep)\s*[:№#.-]?\s*([1-9]\d*)""",
        RegexOption.IGNORE_CASE,
    )
    private val SXX_EXX = Regex("""s([1-9]\d*)\s*e([1-9]\d*)""", RegexOption.IGNORE_CASE)
}

private class CompositePlaybackMediaUrlResolver(
    private val delegates: List<com.kinogo.atv.domain.PlaybackMediaUrlResolver>,
) : com.kinogo.atv.domain.PlaybackMediaUrlResolver {
    override fun resolveOrNull(mediaUrl: String): String? =
        delegates.firstNotNullOfOrNull { it.resolveOrNull(mediaUrl) }

    override fun toString(): String = "CompositePlaybackMediaUrlResolver(<redacted>)"
}
