package com.kinogo.atv.ui.mapper

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentDetails
import com.kinogo.atv.domain.ContentType
import com.kinogo.atv.ui.model.DetailsPlaybackChoiceUiModel
import com.kinogo.atv.ui.model.DetailsUiModel
import com.kinogo.atv.ui.model.EpisodeUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import java.util.Locale

private val CARD_PALETTE = longArrayOf(
    0xFF3B526E,
    0xFF6B3E45,
    0xFF355C4D,
    0xFF4A4268,
    0xFF70513A,
    0xFF315B6D,
    0xFF5E3C68,
    0xFF53613B,
)

private val QUALITY_LABEL_PREFIX =
    Regex("""^\s*(?:качество|якість)\s*:?\s*""", RegexOption.IGNORE_CASE)

fun CatalogItem.toPosterUiModel(): PosterUiModel =
    PosterUiModel(
        id = id,
        title = title,
        subtitle = listOfNotNull(year?.toString(), type.uiLabel()).joinToString(" • ")
            .ifEmpty { "Без категории" },
        posterUrl = posterUrl,
        badge = qualityBadge.normalizedQualityBadge() ?: episodeBadge,
        accentArgb = stableAccent(id),
    )

internal fun String?.normalizedQualityBadge(): String? =
    this
        ?.replace(QUALITY_LABEL_PREFIX, "")
        ?.trim()
        ?.takeIf(String::isNotEmpty)

fun ContentDetails.toDetailsUiModel(
    playbackAvailable: Boolean,
    statusMessage: String?,
): DetailsUiModel {
    val allPlaybackOptions = buildList {
        moviePlaybackOptions?.let(::add)
        seasons.flatMapTo(this) { season ->
            season.episodes.map { episode -> episode.playbackOptions }
        }
    }
    val voices = allPlaybackOptions
        .flatMap { it.voices }
        .distinctBy { it.id }
    val qualities = voices
        .flatMap { it.qualities }
        .distinctBy { it.id }
    val episodeEntries = seasons.flatMapIndexed { seasonIndex, season ->
        val seasonNumber = season.number ?: seasonIndex + 1
        season.episodes.mapIndexed { episodeIndex, episode ->
            episode to EpisodeUiModel(
                id = episode.id,
                season = seasonNumber,
                number = episode.number ?: episodeIndex + 1,
                title = episode.title,
                duration = episode.durationMs?.let(::formatDuration).orEmpty(),
            )
        }
    }
    val episodeModels = episodeEntries.map { it.second }
    val playbackChoices = buildList {
        moviePlaybackOptions?.voices?.forEach { voice ->
            add(
                DetailsPlaybackChoiceUiModel(
                    voiceover = voice.label,
                    qualities = voice.qualities.map { it.label }.distinct(),
                ),
            )
        }
        episodeEntries.forEach { (episode, episodeModel) ->
            episode.playbackOptions.voices.forEach { voice ->
                add(
                    DetailsPlaybackChoiceUiModel(
                        voiceover = voice.label,
                        episode = episodeModel,
                        qualities = voice.qualities.map { it.label }.distinct(),
                    ),
                )
            }
        }
    }
    val rating = buildList {
        catalogItem.ratings.kinopoisk?.let { add("КП ${formatRating(it)}") }
        catalogItem.ratings.imdb?.let { add("IMDb ${formatRating(it)}") }
    }.joinToString("   ")
    val metadata = buildList {
        catalogItem.year?.let { add(it.toString()) }
        catalogItem.type.uiLabel()?.let(::add)
        if (genres.isNotEmpty()) add(genres.joinToString(", "))
    }.joinToString(" • ")

    return DetailsUiModel(
        id = catalogItem.id,
        title = catalogItem.title,
        originalTitle = catalogItem.originalTitle.orEmpty(),
        summary = description,
        metadata = metadata,
        rating = rating,
        accentArgb = stableAccent(catalogItem.id),
        posterUrl = catalogItem.posterUrl,
        seasons = seasons.mapIndexed { index, season -> season.number ?: index + 1 },
        episodes = episodeModels,
        voiceovers = voices.map { it.label },
        qualities = qualities.map { it.label },
        playbackChoices = playbackChoices,
        resumeLabel = if (playbackAvailable) "Смотреть" else "Видео недоступно",
        playbackAvailable = playbackAvailable,
        statusMessage = statusMessage,
    )
}

private fun ContentType.uiLabel(): String? = when (this) {
    ContentType.MOVIE -> "Фильм"
    ContentType.SERIES -> "Сериал"
    ContentType.ANIMATION -> "Мультфильм"
    ContentType.ANIME -> "Аниме"
    ContentType.UNKNOWN -> null
}

private fun stableAccent(id: String): Long {
    val index = (id.hashCode().toLong() and 0x7FFF_FFFFL).rem(CARD_PALETTE.size).toInt()
    return CARD_PALETTE[index]
}

private fun formatRating(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.ROOT, "%.1f", value)

private fun formatDuration(durationMs: Long): String =
    "${(durationMs / 60_000L).coerceAtLeast(1L)} мин"
