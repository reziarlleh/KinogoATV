package com.kinogo.atv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.components.shouldRequestFirstPosterFocus
import com.kinogo.atv.ui.model.HistoryUiModel
import com.kinogo.atv.ui.model.PosterUiModel

@Composable
fun HistoryScreen(
    history: List<HistoryUiModel>,
    onResume: (String) -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
    lastFocusedItemId: String? = null,
    onFocusedItemChanged: (String) -> Unit = {},
) {
    val firstFocus = remember { FocusRequester() }
    val posters = remember(history) { history.map(HistoryUiModel::toHistoryPoster) }
    var restorePreferredFocus by remember {
        mutableStateOf(requestInitialFocus && lastFocusedItemId != null)
    }
    val itemIds = remember(posters) { posters.map(PosterUiModel::id) }
    LaunchedEffect(itemIds, requestInitialFocus, lastFocusedItemId) {
        if (
            shouldRequestFirstPosterFocus(
                requestInitialFocus = requestInitialFocus,
                preferredItemId = lastFocusedItemId,
                itemIds = itemIds,
            ) && posters.isNotEmpty()
        ) {
            firstFocus.requestFocus()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TvSectionTitle(text = "История", trailing = "${history.size} записей")
        Text(
            text = "Позиция, сезон и серия сохраняются автоматически",
            color = Color(0xFFD0DEE4),
            fontSize = 11.sp,
        )
        PosterGrid(
            items = posters,
            onOpenDetails = onResume,
            emptyTitle = "История пока пуста",
            emptyDescription = "Начните просмотр — материал появится здесь автоматически",
            firstFocus = firstFocus,
            preferredFocusItemId = lastFocusedItemId,
            requestPreferredFocus = restorePreferredFocus,
            onFocusedItemChanged = { contentId ->
                restorePreferredFocus = false
                onFocusedItemChanged(contentId)
            },
        )
    }
}

internal fun HistoryUiModel.toHistoryPoster(): PosterUiModel =
    poster.copy(
        subtitle = listOf(episodeLabel, positionLabel, lastWatchedLabel)
            .filter(String::isNotBlank)
            .joinToString(" • "),
        progress = progress,
    )
