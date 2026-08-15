package com.kinogo.atv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogCategory
import com.kinogo.atv.domain.CatalogControls
import com.kinogo.atv.ui.components.CatalogFilterBar
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvPosterGrid
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.PosterUiModel

@Composable
fun CatalogScreen(
    items: List<PosterUiModel>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenDetails: (String) -> Unit,
    controls: CatalogControls,
    selectedCategory: CatalogCategory,
    filters: CatalogBrowseFilters,
    onCategorySelected: (CatalogCategory) -> Unit,
    onFiltersChanged: (CatalogBrowseFilters) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    statusLabel: String? = null,
    onRetry: () -> Unit = {},
    requestInitialFocus: Boolean = true,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items.firstOrNull()?.id, requestInitialFocus) {
        if (requestInitialFocus && items.isNotEmpty()) firstFocus.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TvSectionTitle(
            text = "Каталог",
            trailing = "${items.size}${if (hasMore) "+" else ""} материалов",
        )

        CatalogFilterBar(
            controls = controls,
            filters = filters,
            onFiltersChanged = onFiltersChanged,
            category = selectedCategory,
            showCategories = true,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.fillMaxWidth(),
        )

        if (errorMessage != null || isLoading || statusLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = when {
                        errorMessage != null -> errorMessage
                        isLoading && items.isEmpty() -> "Загрузка каталога…"
                        isLoading -> "Загружается следующая страница…"
                        else -> statusLabel.orEmpty()
                    },
                    modifier = Modifier.weight(1f),
                    color = if (errorMessage != null) Color(0xFFFFD0CC) else Color(0xFFD1E0E6),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
                if (errorMessage != null) {
                    TvActionButton(text = "Повторить", onClick = onRetry, leadingMark = "↺")
                }
            }
        }

        TvPosterGrid(
            items = items,
            onOpenDetails = onOpenDetails,
            emptyTitle = if (errorMessage == null) {
                "Каталог загружается"
            } else {
                "Не удалось загрузить каталог"
            },
            emptyDescription = errorMessage ?: "Получаем материалы выбранной категории",
            hasMore = hasMore,
            onNearEnd = onLoadMore,
            modifier = Modifier.fillMaxSize(),
            firstFocus = firstFocus,
            pagingKey = selectedCategory to filters,
        )
    }
}
