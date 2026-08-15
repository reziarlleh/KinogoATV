package com.kinogo.atv.ui.model

import com.kinogo.atv.domain.WatchStatus

enum class TvDestination(
    val title: String,
    val mark: String,
) {
    Home("Главная", "⌂"),
    Catalog("Каталог", "▦"),
    Search("Поиск", "⌕"),
    Favorites("Закладки", "♥"),
    History("История", "↶"),
    Settings("Настройки", "⚙"),
}

data class PosterUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val posterUrl: String? = null,
    val badge: String? = null,
    val progress: Float? = null,
    val accentArgb: Long = 0xFF334155,
    val isFavorite: Boolean = false,
)

data class BookmarkUiModel(
    val poster: PosterUiModel,
    val watchStatus: WatchStatus? = null,
    val favorite: Boolean = false,
)

data class EpisodeUiModel(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val duration: String,
    val progress: Float? = null,
)

/**
 * One playable branch exposed by a fully parsed details document.
 *
 * Keeping the episode next to its voiceover preserves the provider's sparse availability matrix:
 * a translation does not implicitly become available in every season or episode.
 */
data class DetailsPlaybackChoiceUiModel(
    val voiceover: String,
    val episode: EpisodeUiModel? = null,
    val qualities: List<String> = emptyList(),
)

data class DetailsUiModel(
    val id: String,
    val title: String,
    val originalTitle: String,
    val summary: String,
    val metadata: String,
    val rating: String,
    val accentArgb: Long,
    val posterUrl: String? = null,
    val seasons: List<Int> = emptyList(),
    val episodes: List<EpisodeUiModel> = emptyList(),
    val voiceovers: List<String>,
    val qualities: List<String>,
    val playbackChoices: List<DetailsPlaybackChoiceUiModel> = emptyList(),
    val resumeLabel: String,
    val playbackAvailable: Boolean = true,
    val statusMessage: String? = null,
    val providerPlayback: Boolean = false,
)

data class PlaybackSelectionUiModel(
    val contentId: String,
    val season: Int?,
    val episode: Int?,
    val voiceover: String,
    val quality: String,
    val resume: Boolean,
    /** Stable adapter/source id for in-memory refresh and handoff; never a media URL. */
    val sourceId: String? = null,
)

data class HistoryUiModel(
    val id: String,
    val poster: PosterUiModel,
    val episodeLabel: String,
    val positionLabel: String,
    val lastWatchedLabel: String,
    val progress: Float,
)

data class SettingUiModel(
    val id: String,
    val title: String,
    val description: String,
    val value: String,
    val enabled: Boolean = true,
    val control: SettingControlUi = SettingControlUi.VALUE,
    val selectedOptionId: String? = null,
    val options: List<SettingOptionUiModel> = emptyList(),
)

enum class SettingControlUi {
    VALUE,
    SWITCH,
    DROPDOWN,
}

data class SettingOptionUiModel(
    val id: String,
    val label: String,
)

data class SettingSectionUiModel(
    val id: String,
    val title: String,
    val items: List<SettingUiModel>,
)

enum class MirrorStatusUi {
    Active,
    Available,
    Quarantined,
    Error,
}

data class MirrorUiModel(
    val id: String,
    val url: String,
    val status: MirrorStatusUi,
    val statusDetail: String,
    val latencyMs: Int? = null,
    val isManual: Boolean = false,
    val httpStatusCode: Int? = null,
    val checkedAtEpochMs: Long? = null,
    val redirectOrigin: String? = null,
    val diagnostic: String? = null,
)

data class MirrorUiState(
    val mirrors: List<MirrorUiModel>,
    val isChecking: Boolean = false,
    val lastCheckedLabel: String? = null,
)

object KinogoFixtures {
    private val palette = listOf(
        0xFF3B526E,
        0xFF6B3E45,
        0xFF355C4D,
        0xFF4A4268,
        0xFF70513A,
        0xFF315B6D,
        0xFF5E3C68,
        0xFF53613B,
    )

    val catalog: List<PosterUiModel> = List(40) { index ->
        val number = index + 1
        val titles = listOf(
            "Предел тишины",
            "Северный ветер",
            "Последний сигнал",
            "Город теней",
            "Другая орбита",
            "Письма из будущего",
            "Третий берег",
            "Холодное солнце",
        )
        PosterUiModel(
            id = "title-$number",
            title = titles[index % titles.size],
            subtitle = "${2026 - index % 7} • ${if (index % 3 == 0) "Сериал" else "Фильм"}",
            badge = when {
                index % 9 == 0 -> "4K"
                index % 4 == 0 -> "1080p"
                else -> null
            },
            progress = if (index in 1..5) (index + 1) / 8f else null,
            accentArgb = palette[index % palette.size],
            isFavorite = index % 6 == 0,
        )
    }

    val details: List<DetailsUiModel> = catalog.mapIndexed { index, poster ->
        val isSeries = index % 3 == 0 || poster.id == "title-2"
        val seasons = if (isSeries) listOf(1, 2, 3) else emptyList()
        val episodes = seasons.flatMap { season ->
            (1..8).map { episode ->
                EpisodeUiModel(
                    id = "${poster.id}-s$season-e$episode",
                    season = season,
                    number = episode,
                    title = "Серия $episode",
                    duration = "${42 + episode % 5} мин",
                    progress = if (poster.progress != null && season == 2 && episode == 5) {
                        poster.progress
                    } else {
                        null
                    },
                )
            }
        }
        val fixtureQualities = listOf("Авто", "4K", "1080p", "720p")
        val playbackChoices = if (isSeries) {
            episodes.flatMap { episode ->
                val availableVoiceovers = when (episode.season) {
                    1 -> listOf("Дубляж", "Оригинал")
                    2 -> listOf("Профессиональная", "Оригинал")
                    else -> listOf("Оригинал")
                }
                availableVoiceovers.map { voiceover ->
                    DetailsPlaybackChoiceUiModel(
                        voiceover = voiceover,
                        episode = episode,
                        qualities = fixtureQualities,
                    )
                }
            }
        } else {
            listOf("Дубляж", "Профессиональная", "Оригинал").map { voiceover ->
                DetailsPlaybackChoiceUiModel(
                    voiceover = voiceover,
                    qualities = fixtureQualities,
                )
            }
        }
        DetailsUiModel(
            id = poster.id,
            title = poster.title,
            originalTitle = "${poster.title} / Original title",
            summary = "История о выборе, который меняет привычный мир героев. " +
                "Им предстоит разобраться в прошлом и успеть остановить события, " +
                "последствия которых затронут всех.",
            metadata = "2026 • ${if (isSeries) "Сериал" else "Фильм"} • Драма, триллер • 16+",
            rating = "КП 7.${(index % 8) + 1}   IMDb 7.${(index % 6) + 2}",
            accentArgb = poster.accentArgb,
            posterUrl = poster.posterUrl,
            seasons = seasons,
            episodes = episodes,
            voiceovers = listOf("Дубляж", "Профессиональная", "Оригинал"),
            qualities = fixtureQualities,
            playbackChoices = playbackChoices,
            resumeLabel = if (poster.progress != null) {
                if (isSeries) "Продолжить S02E05 с 17:42" else "Продолжить с 47:18"
            } else {
                "Смотреть"
            },
        )
    }

    val history = catalog.slice(1..9).mapIndexed { index, poster ->
        val progress = poster.progress ?: (0.18f + index * 0.07f).coerceAtMost(0.94f)
        HistoryUiModel(
            id = "history-${poster.id}",
            poster = poster.copy(progress = progress),
            episodeLabel = if (index % 2 == 0) "Сезон 2, серия ${index + 1}" else "Фильм",
            positionLabel = "${(progress * 90).toInt()} из 90 мин",
            lastWatchedLabel = if (index < 3) "Сегодня" else "${index + 1} дней назад",
            progress = progress,
        )
    }

    val settings = listOf(
        SettingSectionUiModel(
            id = "sources",
            title = "Источник и зеркала",
            items = listOf(
                SettingUiModel("active-mirror", "Активное зеркало", "Используется для каталога и плеера", "kinogo.parts"),
                SettingUiModel("check-mirrors", "Проверить зеркала", "Обновить состояние доступных адресов", "Проверить"),
                SettingUiModel("manual-mirror", "Добавить адрес вручную", "Новый адрес сначала попадёт в карантин", "Добавить"),
            ),
        ),
        SettingSectionUiModel(
            id = "playback",
            title = "Воспроизведение",
            items = listOf(
                SettingUiModel("quality", "Качество по умолчанию", "Автовыбор по экрану и скорости сети", "Авто"),
                SettingUiModel("next", "Следующая серия", "Запускать после короткого обратного отсчёта", "Вкл."),
                SettingUiModel("seek", "Шаг перемотки", "Короткое нажатие Left или Right", "10 сек"),
            ),
        ),
        SettingSectionUiModel(
            id = "interface",
            title = "Интерфейс и доступность",
            items = listOf(
                SettingUiModel("contrast", "Высокий контраст", "Усиленные контуры и подписи", "Выкл."),
                SettingUiModel("motion", "Уменьшить анимацию", "Меньше масштабирования и переходов", "Выкл."),
                SettingUiModel("captions", "Субтитры", "Использовать системный стиль Android TV", "Системные"),
            ),
        ),
        SettingSectionUiModel(
            id = "updates",
            title = "Обновления",
            items = listOf(
                SettingUiModel(
                    "auto_check_updates",
                    "Проверять обновления автоматически",
                    "Проверять новую версию при запуске приложения",
                    "Вкл.",
                ),
            ),
        ),
    )

    val mirrorState = MirrorUiState(
        mirrors = listOf(
            MirrorUiModel(
                id = "kinogo-parts",
                url = "https://kinogo.parts/",
                status = MirrorStatusUi.Active,
                statusDetail = "Основной источник",
                latencyMs = 148,
            ),
            MirrorUiModel(
                id = "kinogo-online",
                url = "https://kinogo.online/",
                status = MirrorStatusUi.Available,
                statusDetail = "Редирект подтверждён",
                latencyMs = 231,
            ),
            MirrorUiModel(
                id = "kinogo-family",
                url = "https://kinogo.family/",
                status = MirrorStatusUi.Quarantined,
                statusDetail = "Ожидает подтверждения",
                isManual = true,
            ),
            MirrorUiModel(
                id = "kinogo-online-biz",
                url = "https://kinogo-online.biz/",
                status = MirrorStatusUi.Error,
                statusDetail = "Не отвечает",
            ),
        ),
        isChecking = false,
        lastCheckedLabel = "Сегодня, 08:42",
    )

    fun detailsFor(id: String): DetailsUiModel = details.firstOrNull { it.id == id } ?: details.first()
}
