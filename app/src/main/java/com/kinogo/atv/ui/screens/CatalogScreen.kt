package com.kinogo.atv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.kinogo.atv.ui.components.PosterCard
import com.kinogo.atv.ui.components.PosterGridMinimumWidth
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvChoiceChip
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.PosterUiModel
import com.kinogo.atv.domain.CatalogSection

@Composable
fun CatalogScreen(
    items: List<PosterUiModel>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    statusLabel: String? = null,
    onRetry: () -> Unit = {},
    useRemoteSections: Boolean = false,
    selectedSection: CatalogSection = CatalogSection.ROOT,
    onSectionSelected: (CatalogSection) -> Unit = {},
) {
    val sectionFilters = listOf(
        "Все" to CatalogSection.ROOT,
        "Фильмы" to CatalogSection.MOVIES,
        "Сериалы" to CatalogSection.SERIES,
        "Мультфильмы" to CatalogSection.CARTOONS,
        "Аниме" to CatalogSection.ANIME,
    )
    val filters = if (useRemoteSections) {
        sectionFilters.map { it.first }
    } else {
        listOf("Все", "Фильмы", "Сериалы", "2026", "4K/1080p")
    }
    val sortModes = listOf("новые", "по названию", "качество")
    var selectedFilter by remember { mutableStateOf(filters.first()) }
    var sortMode by remember { mutableStateOf(sortModes.first()) }
    val selectedFilterLabel = if (useRemoteSections) {
        sectionFilters.first { it.second == selectedSection }.first
    } else {
        selectedFilter
    }
    val visibleItems = remember(items, selectedFilterLabel, sortMode, useRemoteSections) {
        val filtered = when {
            useRemoteSections -> items
            selectedFilterLabel == "Фильмы" -> items.filter { "Фильм" in it.subtitle }
            selectedFilterLabel == "Сериалы" -> items.filter { "Сериал" in it.subtitle }
            selectedFilterLabel == "2026" -> items.filter { it.subtitle.startsWith("2026") }
            selectedFilterLabel == "4K/1080p" -> items.filter {
                it.badge == "4K" || it.badge == "1080p"
            }
            else -> items
        }
        when (sortMode) {
            "по названию" -> filtered.sortedBy { it.title }
            "качество" -> filtered.sortedWith(
                compareBy<PosterUiModel> {
                    when (it.badge) {
                        "4K" -> 0
                        "1080p" -> 1
                        else -> 2
                    }
                }.thenBy { it.title },
            )
            else -> filtered.sortedByDescending {
                it.subtitle.substringBefore(' ').toIntOrNull() ?: 0
            }
        }
    }
    val initialFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(visibleItems.isNotEmpty(), selectedFilterLabel, sortMode) {
        if (visibleItems.isNotEmpty()) initialFocus.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                TvSectionTitle(
                    text = "Каталог",
                    trailing = "${visibleItems.size}${if (hasMore) "+" else ""} материалов",
                )
                Text(
                    text = when {
                        errorMessage != null -> errorMessage
                        isLoading && items.isEmpty() -> "Загрузка каталога…"
                        isLoading -> "Загружается следующая страница…"
                        statusLabel != null -> statusLabel
                        else -> "Автоподгрузка включена — листайте вниз"
                    },
                    color = if (errorMessage != null) Color(0xFFFFB4AB) else Color(0xFF8F9DB2),
                    fontSize = 12.sp,
                )
            }
            if (errorMessage != null) {
                TvActionButton(text = "Повторить", onClick = onRetry, leadingMark = "↺")
            }
            TvActionButton(
                text = "Сортировка: $sortMode",
                onClick = {
                    sortMode = sortModes[(sortModes.indexOf(sortMode) + 1) % sortModes.size]
                },
            )
            TvActionButton(
                text = "Следующий фильтр",
                onClick = {
                    val currentIndex = filters.indexOf(selectedFilterLabel).coerceAtLeast(0)
                    val next = filters[(currentIndex + 1) % filters.size]
                    if (useRemoteSections) {
                        onSectionSelected(sectionFilters.first { it.first == next }.second)
                    } else {
                        selectedFilter = next
                    }
                },
                leadingMark = "≡",
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(filters.size) { index ->
                val filter = filters[index]
                TvChoiceChip(
                    text = filter,
                    selected = selectedFilterLabel == filter,
                    onClick = {
                        if (useRemoteSections) {
                            onSectionSelected(sectionFilters.first { it.first == filter }.second)
                        } else {
                            selectedFilter = filter
                        }
                    },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PosterGridMinimumWidth),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, top = 7.dp, end = 18.dp, bottom = 38.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(
                items = visibleItems,
                key = { _, item -> item.id },
            ) { index, item ->
                PosterCard(
                    item = item,
                    onClick = { onOpenDetails(item.id) },
                    focusRequester = if (index == 0) initialFocus else null,
                    onFocused = {
                        if (
                            hasMore &&
                            shouldPreloadCatalog(
                                focusedIndex = index,
                                lastIndex = visibleItems.lastIndex,
                                columnCount = gridState.layoutInfo.maxSpan,
                            )
                        ) {
                            onLoadMore()
                        }
                    },
                )
            }
        }
    }
}

internal fun shouldPreloadCatalog(
    focusedIndex: Int,
    lastIndex: Int,
    columnCount: Int,
    preloadRows: Int = 2,
): Boolean {
    if (focusedIndex < 0 || lastIndex < 0) return false
    val preloadDistance =
        columnCount.coerceAtLeast(1) * preloadRows.coerceAtLeast(1)
    return focusedIndex >= (lastIndex - preloadDistance).coerceAtLeast(0)
}
