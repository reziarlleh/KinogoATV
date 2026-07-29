package com.kinogo.atv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvChoiceChip
import com.kinogo.atv.ui.model.DetailsUiModel
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import com.kinogo.atv.domain.WatchStatus
import com.kinogo.atv.domain.VideoQualityPreference

@Composable
fun DetailsScreen(
    details: DetailsUiModel,
    onBack: () -> Unit,
    onPlay: (PlaybackSelectionUiModel) -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    watchStatus: WatchStatus? = null,
    onWatchStatusChange: (WatchStatus?) -> Unit = {},
    defaultQuality: VideoQualityPreference = VideoQualityPreference.AUTO,
) {
    val playbackChoices = details.playbackChoices
    val voiceovers = DetailsPlaybackChoiceModel.voiceovers(playbackChoices)
    var selectedVoiceover by remember(details.id, playbackChoices, details.voiceovers) {
        mutableStateOf(voiceovers.firstOrNull() ?: details.voiceovers.firstOrNull().orEmpty())
    }
    val seasons = DetailsPlaybackChoiceModel.seasons(playbackChoices, selectedVoiceover)
    var selectedSeason by remember(details.id, playbackChoices, selectedVoiceover) {
        mutableStateOf(seasons.firstOrNull())
    }
    val episodes = DetailsPlaybackChoiceModel.episodes(
        choices = playbackChoices,
        voiceover = selectedVoiceover,
        season = selectedSeason,
    )
    var selectedEpisode by remember(
        details.id,
        playbackChoices,
        selectedVoiceover,
        selectedSeason,
    ) {
        mutableStateOf(episodes.firstOrNull()?.number)
    }
    val qualities = if (playbackChoices.isNotEmpty()) {
        DetailsPlaybackChoiceModel.qualities(
            choices = playbackChoices,
            voiceover = selectedVoiceover,
            season = selectedSeason,
            episode = selectedEpisode,
        )
    } else {
        details.qualities
    }
    var selectedQuality by remember(
        details.id,
        playbackChoices,
        selectedVoiceover,
        selectedSeason,
        selectedEpisode,
        details.qualities,
        defaultQuality,
    ) {
        val firstAvailable = qualities.firstOrNull().orEmpty()
        mutableStateOf(defaultQuality.resolve(firstAvailable, qualities))
    }
    val backFocus = remember(details.id) { FocusRequester() }
    val playbackFocus = remember(details.id) { FocusRequester() }

    LaunchedEffect(details.id, details.playbackAvailable) {
        when (detailsFocusTarget(details.playbackAvailable)) {
            DetailsFocusTarget.BACK -> backFocus.requestFocus()
            DetailsFocusTarget.PLAYBACK -> playbackFocus.requestFocus()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = 18.dp, bottom = 38.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "details-hero-${details.id}") {
            DetailsHero(
                details = details,
                onBack = onBack,
                onResume = {
                    onPlay(
                        PlaybackSelectionUiModel(
                            contentId = details.id,
                            season = selectedSeason,
                            episode = selectedEpisode,
                            voiceover = selectedVoiceover,
                            quality = selectedQuality,
                            resume = true,
                        ),
                    )
                },
                onRestart = {
                    onPlay(
                        PlaybackSelectionUiModel(
                            contentId = details.id,
                            season = selectedSeason,
                            episode = selectedEpisode,
                            voiceover = selectedVoiceover,
                            quality = selectedQuality,
                            resume = false,
                        ),
                    )
                },
                isFavorite = isFavorite,
                onFavoriteToggle = onFavoriteToggle,
                backFocus = backFocus,
                playbackFocus = playbackFocus,
            )
        }

        item(key = "details-library-${details.id}") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item(key = "none") {
                    TvChoiceChip(
                        text = "Не смотрел",
                        selected = watchStatus == null,
                        onClick = { onWatchStatusChange(null) },
                    )
                }
                items(items = WatchStatus.entries, key = WatchStatus::folder) { status ->
                    TvChoiceChip(
                        text = status.title,
                        selected = watchStatus == status,
                        onClick = { onWatchStatusChange(status) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsHero(
    details: DetailsUiModel,
    onBack: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    backFocus: FocusRequester,
    playbackFocus: FocusRequester,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = when {
                    details.providerPlayback -> 380.dp
                    details.statusMessage != null -> 370.dp
                    else -> 340.dp
                },
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0B101A), Color(details.accentArgb), Color(0xFF111827)),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF70A0F18),
                        0.64f to Color(0xB80A0F18),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TvActionButton(
                text = "Назад",
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocus),
                leadingMark = "‹",
            )
            Text(
                text = details.title,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = details.originalTitle, color = Color(0xFF9EABC0), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = details.rating,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(text = details.metadata, color = Color(0xFFD4DCE9), fontSize = 14.sp)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (details.providerPlayback) {
                    TvActionButton(
                        text = "Смотреть",
                        onClick = onResume,
                        modifier = Modifier.focusRequester(playbackFocus),
                        primary = true,
                        leadingMark = "▶",
                        enabled = details.playbackAvailable,
                    )
                } else {
                    TvActionButton(
                        text = details.resumeLabel,
                        onClick = onResume,
                        modifier = Modifier.focusRequester(playbackFocus),
                        primary = true,
                        leadingMark = "▶",
                        enabled = details.playbackAvailable,
                    )
                    TvActionButton(
                        text = "С начала",
                        onClick = onRestart,
                        leadingMark = "↺",
                        enabled = details.playbackAvailable,
                    )
                }
                TvActionButton(
                    text = if (isFavorite) "В избранном" else "В избранное",
                    onClick = onFavoriteToggle,
                    leadingMark = if (isFavorite) "✓" else "♥",
                )
            }
            details.statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (details.playbackAvailable) {
                        Color(0xFFB8D8BA)
                    } else {
                        Color(0xFFFFD18A)
                    },
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = details.summary,
                color = Color(0xFFC0CAD9),
                fontSize = 13.sp,
            )
        }
    }
}

internal enum class DetailsFocusTarget {
    BACK,
    PLAYBACK,
}

internal fun detailsFocusTarget(playbackAvailable: Boolean): DetailsFocusTarget =
    if (playbackAvailable) DetailsFocusTarget.PLAYBACK else DetailsFocusTarget.BACK
