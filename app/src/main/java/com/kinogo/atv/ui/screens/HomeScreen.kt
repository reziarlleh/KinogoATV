package com.kinogo.atv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogControls
import com.kinogo.atv.ui.components.CatalogFilterBar
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvPosterGrid
import com.kinogo.atv.ui.model.PosterUiModel

@Composable
fun HomeScreen(
    items: List<PosterUiModel>,
    controls: CatalogControls,
    filters: CatalogBrowseFilters,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onFiltersChanged: (CatalogBrowseFilters) -> Unit,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items.firstOrNull()?.id) {
        if (items.isNotEmpty()) firstFocus.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CatalogFilterBar(
            controls = controls,
            filters = filters,
            onFiltersChanged = onFiltersChanged,
            modifier = Modifier.fillMaxWidth(),
        )

        if (errorMessage != null || isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = when {
                        errorMessage != null -> errorMessage
                        items.isEmpty() -> "Загрузка каталога…"
                        else -> "Загружается следующая страница…"
                    },
                    modifier = Modifier.weight(1f),
                    color = if (errorMessage != null) Color(0xFFFFD0CC) else Color(0xFFD1E0E6),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
                if (errorMessage != null) {
                    TvActionButton(
                        text = "Повторить",
                        onClick = onRetry,
                        modifier = if (items.isEmpty()) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                        leadingMark = "↺",
                    )
                }
            }
        }

        TvPosterGrid(
            items = items,
            onOpenDetails = onOpenDetails,
            emptyTitle = if (errorMessage == null) {
                "Каталог загружается"
            } else {
                "Не удалось загрузить главную"
            },
            emptyDescription = errorMessage ?: "Проверяем зеркало и получаем материалы",
            hasMore = hasMore,
            onNearEnd = onLoadMore,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 1.dp),
            firstFocus = firstFocus,
            pagingKey = filters,
        )
    }
}
