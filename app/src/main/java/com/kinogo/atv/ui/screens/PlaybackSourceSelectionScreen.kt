package com.kinogo.atv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.ui.KinogoTvTheme
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvChoiceChip
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel

/** URL-free description of one explicit provider-owned web alternative. */
data class PlaybackWebFallbackUiModel(
    val id: String,
    val label: String,
    val providerLabel: String,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(providerLabel.isNotBlank())
    }
}

/**
 * Full-screen D-pad pre-play selector. Short-lived media/embed addresses never enter its state or
 * callbacks: native selection returns stable fields and web selection returns only an opaque id.
 */
@Composable
fun PlaybackSourceSelectionScreen(
    title: String,
    requestedSelection: PlaybackSelectionUiModel,
    mediaPlan: PlaybackMediaPlan?,
    webFallbacks: List<PlaybackWebFallbackUiModel>,
    resumePositionMs: Long,
    onNativeSelected: (sourceId: String, selection: PlaybackSelectionUiModel) -> Unit,
    onWebSelected: (fallbackId: String, selection: PlaybackSelectionUiModel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false,
) {
    require(mediaPlan != null || webFallbacks.isNotEmpty())
    require(webFallbacks.map(PlaybackWebFallbackUiModel::id).distinct().size == webFallbacks.size)
    BackHandler(onBack = onBack)

    var nativeState by remember(mediaPlan, requestedSelection) {
        mutableStateOf(
            mediaPlan?.let {
                PlaybackSourceSelectionModel.initial(
                    plan = it,
                    requested = requestedSelection,
                )
            },
        )
    }
    val primaryFocus = remember(mediaPlan, webFallbacks) { FocusRequester() }
    val effectiveSelection = nativeState?.toPlaybackSelection(requestedSelection)
        ?: requestedSelection
    val mayContinue = requestedSelection.resume &&
        resumePositionMs > 0L &&
        effectiveSelection.isSamePlaybackUnitAs(requestedSelection)
    val launchSelection = effectiveSelection.copy(resume = mayContinue)

    LaunchedEffect(mediaPlan, webFallbacks) {
        runCatching { primaryFocus.requestFocus() }
    }

    KinogoTvTheme(highContrast = highContrast) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF101A20),
                            Color(0xFF1C3641),
                            Color(0xFF132831),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SelectorHeader(
                    title = title,
                    selection = effectiveSelection,
                    onBack = onBack,
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                val plan = mediaPlan
                val state = nativeState
                if (plan != null && state != null) {
                    item(key = "native-heading") {
                        SelectorSectionHeading(
                            title = "Нативный плеер",
                            description = "Источник, перевод, сезон и серия",
                        )
                    }
                    item(key = "source") {
                        SelectorOptionsRow(
                            title = "Источник",
                            options = plan.sourceOptions.map { it.id to it.label },
                            selectedId = state.sourceId,
                            onSelected = { sourceId ->
                                nativeState = PlaybackSourceSelectionModel.selectSource(
                                    plan = plan,
                                    state = state,
                                    sourceId = sourceId,
                                )
                            },
                        )
                    }
                    item(key = "voiceover") {
                        SelectorOptionsRow(
                            title = "Озвучка",
                            options = PlaybackSourceSelectionModel
                                .voiceoverOptions(plan, state)
                                .map { it to it },
                            selectedId = state.voiceover,
                            onSelected = { value ->
                                nativeState = PlaybackSourceSelectionModel.selectVoiceover(
                                    plan = plan,
                                    state = state,
                                    voiceover = value,
                                )
                            },
                        )
                    }
                    val seasons = PlaybackSourceSelectionModel.seasonOptions(plan, state)
                    if (seasons.isNotEmpty()) {
                        item(key = "season") {
                            SelectorOptionsRow(
                                title = "Сезон",
                                options = seasons.map { it.toString() to it.toString() },
                                selectedId = state.season?.toString().orEmpty(),
                                onSelected = { value ->
                                    nativeState = PlaybackSourceSelectionModel.selectSeason(
                                        plan = plan,
                                        state = state,
                                        season = value.toInt(),
                                    )
                                },
                            )
                        }
                    }
                    val episodes = PlaybackSourceSelectionModel.episodeOptions(plan, state)
                    if (episodes.isNotEmpty()) {
                        item(key = "episode") {
                            SelectorOptionsRow(
                                title = "Серия",
                                options = episodes.map { it.toString() to "Серия $it" },
                                selectedId = state.episode?.toString().orEmpty(),
                                onSelected = { value ->
                                    nativeState = PlaybackSourceSelectionModel.selectEpisode(
                                        plan = plan,
                                        state = state,
                                        episode = value.toInt(),
                                    )
                                },
                            )
                        }
                    }
                    item(key = "quality") {
                        SelectorOptionsRow(
                            title = "Качество",
                            options = PlaybackSourceSelectionModel
                                .qualityOptions(plan, state)
                                .map { it to it },
                            selectedId = state.quality,
                            onSelected = { value ->
                                nativeState = PlaybackSourceSelectionModel.selectQuality(
                                    plan = plan,
                                    state = state,
                                    quality = value,
                                )
                            },
                        )
                    }
                }

                if (webFallbacks.isNotEmpty()) {
                    item(key = "web-heading") {
                        SelectorSectionHeading(
                            title = "Оригинальный web-плеер",
                            description = "Альтернатива для несовместимых источников",
                        )
                    }
                    item(key = "web-options") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            items(
                                items = webFallbacks,
                                key = PlaybackWebFallbackUiModel::id,
                            ) { fallback ->
                                val focusModifier = if (mediaPlan == null &&
                                    fallback.id == webFallbacks.first().id
                                ) {
                                    Modifier.focusRequester(primaryFocus)
                                } else {
                                    Modifier
                                }
                                TvActionButton(
                                    text = "${fallback.label} · ${fallback.providerLabel}",
                                    onClick = {
                                        onWebSelected(fallback.id, launchSelection)
                                    },
                                    modifier = focusModifier,
                                    leadingMark = "↗",
                                )
                            }
                        }
                    }
                }
                }

                if (mediaPlan != null && nativeState != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Выбор можно изменить во время просмотра",
                            color = Color(0xFFB7C8CF),
                            fontSize = 12.sp,
                        )
                        TvActionButton(
                            text = if (mayContinue) {
                                "Продолжить · ${formatPlaybackPosition(resumePositionMs)}"
                            } else {
                                "Смотреть"
                            },
                            onClick = {
                                onNativeSelected(
                                    requireNotNull(nativeState).sourceId,
                                    launchSelection,
                                )
                            },
                            modifier = Modifier.focusRequester(primaryFocus),
                            primary = true,
                            leadingMark = "▶",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorHeader(
    title: String,
    selection: PlaybackSelectionUiModel,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvActionButton(
            text = "Назад",
            onClick = onBack,
            leadingMark = "‹",
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val unit = if (selection.season != null && selection.episode != null) {
                "Сезон ${selection.season} · Серия ${selection.episode}"
            } else {
                "Фильм"
            }
            Text(
                text = "$unit · ${selection.voiceover} · ${selection.quality}",
                color = Color(0xFF9FB1C8),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectorSectionHeading(
    title: String,
    description: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xB3162538),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SelectorOptionsRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = Color(0xFFD8E2EF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(items = options, key = { it.first }) { (id, label) ->
                TvChoiceChip(
                    text = label,
                    selected = id == selectedId,
                    onClick = { onSelected(id) },
                )
            }
        }
    }
}

internal fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
