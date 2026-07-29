package com.kinogo.atv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.ui.components.EmptyState
import com.kinogo.atv.ui.components.PosterCard
import com.kinogo.atv.ui.components.PosterGridColumnCount
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.DetailsUiModel
import com.kinogo.atv.ui.model.HomeSectionUiModel
import com.kinogo.atv.ui.model.PosterUiModel

@Composable
fun HomeScreen(
    featured: DetailsUiModel?,
    sections: List<HomeSectionUiModel>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    val historySection = remember(sections) { sections.historySectionOrNull() }
    val newSection = remember(sections) { sections.newSectionOrNull(historySection?.id) }
    val historyItems = historySection?.items.orEmpty()
    val newItems = newSection?.items.orEmpty()

    if (historyItems.isEmpty() && newItems.isEmpty()) {
        val retryFocus = remember { FocusRequester() }
        LaunchedEffect(errorMessage) {
            if (errorMessage != null) retryFocus.requestFocus()
        }
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState(
                title = if (errorMessage == null) {
                    "Новинки загружаются"
                } else {
                    "Не удалось загрузить главную"
                },
                description = errorMessage
                    ?: if (featured == null) {
                        "Проверяем зеркало и получаем каталог"
                    } else {
                        "Получаем список материалов"
                    },
            )
            if (errorMessage != null) {
                TvActionButton(
                    text = "Повторить",
                    onClick = onRetry,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .focusRequester(retryFocus),
                    primary = true,
                    leadingMark = "↺",
                )
            }
        }
        return
    }

    val initialFocus = remember { FocusRequester() }
    val initialItemId = historyItems.firstOrNull()?.id ?: newItems.firstOrNull()?.id
    LaunchedEffect(initialItemId) {
        if (initialItemId != null) initialFocus.requestFocus()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (historyItems.isNotEmpty()) {
            item(key = "history-title") {
                TvSectionTitle(
                    text = "История просмотра",
                    trailing = "${historyItems.size} материалов",
                )
            }
            item(key = "history-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = historyItems, key = PosterUiModel::id) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpenDetails(item.id) },
                            modifier = Modifier
                                .fillParentMaxWidth(1f / PosterGridColumnCount)
                                .then(
                                    if (item.id == initialItemId) {
                                        Modifier.focusRequester(initialFocus)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }

        if (newItems.isNotEmpty()) {
            item(key = "new-title") {
                TvSectionTitle(
                    text = "Новинки",
                    trailing = "${newItems.size} материалов",
                )
            }
            items(
                items = newItems.chunked(PosterGridColumnCount),
                key = { row -> "new-row-${row.first().id}" },
            ) { rowItems ->
                HomePosterRow(
                    items = rowItems,
                    onOpenDetails = onOpenDetails,
                    initialItemId = initialItemId,
                    initialFocus = initialFocus,
                )
            }
        }

        if (errorMessage != null) {
            item(key = "home-error") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFFFD0CC),
                        fontSize = 12.sp,
                    )
                    TvActionButton(text = "Повторить", onClick = onRetry, leadingMark = "↺")
                }
            }
        }
    }
}

@Composable
private fun HomePosterRow(
    items: List<PosterUiModel>,
    onOpenDetails: (String) -> Unit,
    initialItemId: String?,
    initialFocus: FocusRequester,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            PosterCard(
                item = item,
                onClick = { onOpenDetails(item.id) },
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (item.id == initialItemId) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        repeat((PosterGridColumnCount - items.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

internal fun List<HomeSectionUiModel>.historySectionOrNull(): HomeSectionUiModel? =
    firstOrNull { section ->
        section.id.startsWith("continue") ||
            section.title.contains("продолж", ignoreCase = true) ||
            section.title.contains("истори", ignoreCase = true)
    }

internal fun List<HomeSectionUiModel>.newSectionOrNull(
    historySectionId: String?,
): HomeSectionUiModel? =
    firstOrNull { section ->
        section.id.contains("new", ignoreCase = true) ||
            section.title.contains("новин", ignoreCase = true)
    } ?: firstOrNull { it.id != historySectionId }
