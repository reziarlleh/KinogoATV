package com.kinogo.atv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.components.shouldRequestFirstPosterFocus
import com.kinogo.atv.ui.model.HistoryUiModel
import com.kinogo.atv.ui.model.PosterUiModel

@Composable
fun HistoryScreen(
    history: List<HistoryUiModel>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
    lastFocusedItemId: String? = null,
    onFocusedItemChanged: (String) -> Unit = {},
    onDeleteContent: (contentId: String, preferredFocusItemId: String?) -> Unit = { _, _ -> },
    onClearHistory: () -> Unit = {},
) {
    val firstFocus = remember { FocusRequester() }
    val posters = remember(history) { history.map(HistoryUiModel::toHistoryPoster) }
    var restorePreferredFocus by remember {
        mutableStateOf(requestInitialFocus && lastFocusedItemId != null)
    }
    var actionContentId by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val itemIds = remember(posters) { posters.map(PosterUiModel::id) }
    LaunchedEffect(itemIds, actionContentId) {
        if (actionContentId != null && actionContentId !in itemIds) {
            actionContentId = null
            confirmClearAll = false
        }
    }
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
            text = "ОК — перейти к карточке • удерживайте ОК — удалить или очистить историю",
            color = Color(0xFFD0DEE4),
            fontSize = 11.sp,
        )
        PosterGrid(
            items = posters,
            onOpenDetails = onOpenDetails,
            emptyTitle = "История пока пуста",
            emptyDescription = "Начните просмотр — материал появится здесь автоматически",
            firstFocus = firstFocus,
            preferredFocusItemId = lastFocusedItemId,
            requestPreferredFocus = restorePreferredFocus,
            onFocusedItemChanged = { contentId ->
                restorePreferredFocus = false
                onFocusedItemChanged(contentId)
            },
            onItemLongClick = { contentId ->
                actionContentId = contentId
                confirmClearAll = false
            },
            itemLongClickLabel = "Управление историей",
        )
    }

    val selectedContentId = actionContentId
    if (selectedContentId != null) {
        val selectedTitle = posters.firstOrNull { it.id == selectedContentId }?.title
            ?: "выбранный материал"
        HistoryManagementDialog(
            title = selectedTitle,
            itemCount = history.size,
            confirmClearAll = confirmClearAll,
            onDismiss = {
                actionContentId = null
                confirmClearAll = false
                restorePreferredFocus = true
            },
            onDeleteContent = {
                val preferredFocus = preferredHistoryFocusAfterRemoval(
                    itemIds = itemIds,
                    removedItemId = selectedContentId,
                )
                actionContentId = null
                confirmClearAll = false
                restorePreferredFocus = preferredFocus != null
                preferredFocus?.let(onFocusedItemChanged)
                onDeleteContent(selectedContentId, preferredFocus)
            },
            onRequestClearAll = { confirmClearAll = true },
            onClearAll = {
                actionContentId = null
                confirmClearAll = false
                restorePreferredFocus = false
                onClearHistory()
            },
        )
    }
}

internal fun preferredHistoryFocusAfterRemoval(
    itemIds: List<String>,
    removedItemId: String,
): String? {
    val removedIndex = itemIds.indexOf(removedItemId)
    if (removedIndex < 0) return itemIds.firstOrNull()
    return itemIds.getOrNull(removedIndex + 1) ?: itemIds.getOrNull(removedIndex - 1)
}

@Composable
private fun HistoryManagementDialog(
    title: String,
    itemCount: Int,
    confirmClearAll: Boolean,
    onDismiss: () -> Unit,
    onDeleteContent: () -> Unit,
    onRequestClearAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    val cancelFocus = remember(confirmClearAll) { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(confirmClearAll) { cancelFocus.requestFocus() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.74f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(720.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF213842),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 26.dp,
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = if (confirmClearAll) "Очистить всю историю?" else "История просмотра",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (confirmClearAll) {
                            "Будут удалены позиции просмотра для всех $itemCount материалов."
                        } else {
                            "Выбран материал «$title». Что удалить?"
                        },
                        color = Color(0xFFD0DEE4),
                        fontSize = 15.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvActionButton(
                            text = "Отмена",
                            onClick = onDismiss,
                            modifier = Modifier.focusRequester(cancelFocus),
                            primary = true,
                        )
                        Spacer(Modifier.width(10.dp))
                        if (confirmClearAll) {
                            TvActionButton(text = "Очистить всё", onClick = onClearAll)
                        } else {
                            TvActionButton(text = "Удалить материал", onClick = onDeleteContent)
                            Spacer(Modifier.width(10.dp))
                            TvActionButton(text = "Очистить всё", onClick = onRequestClearAll)
                        }
                    }
                }
            }
        }
    }
}

internal fun HistoryUiModel.toHistoryPoster(): PosterUiModel =
    poster.copy(
        subtitle = listOf(episodeLabel, positionLabel, lastWatchedLabel)
            .filter(String::isNotBlank)
            .joinToString(" • "),
        progress = progress,
    )
