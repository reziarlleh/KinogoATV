package com.kinogo.atv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.kinogo.atv.ui.components.PosterCard
import com.kinogo.atv.ui.components.EmptyState
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.DetailsUiModel
import com.kinogo.atv.ui.model.HomeSectionUiModel

@Composable
fun HomeScreen(
    featured: DetailsUiModel?,
    sections: List<HomeSectionUiModel>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
) {
    if (featured == null) {
        val retryFocus = remember { FocusRequester() }
        LaunchedEffect(errorMessage) {
            if (errorMessage != null) retryFocus.requestFocus()
        }
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState(
                title = if (errorMessage == null) {
                    "Каталог загружается"
                } else {
                    "Не удалось загрузить главную"
                },
                description = errorMessage
                    ?: "Проверяем доступное зеркало и получаем новинки",
            )
            if (errorMessage != null) {
                TvActionButton(
                    text = "Повторить",
                    onClick = onRetry,
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .focusRequester(retryFocus),
                    primary = true,
                    leadingMark = "↺",
                )
            }
        }
        return
    }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(featured.id) { initialFocus.requestFocus() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = 18.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "home-hero") {
            HomeHero(
                featured = featured,
                onOpenDetails = { onOpenDetails(featured.id) },
                initialFocus = initialFocus,
            )
        }
        items(items = sections, key = { it.id }) { section ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvSectionTitle(
                    text = section.title,
                    trailing = if (section.id == "continue") "Синхронизировано локально" else null,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(items = section.items, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpenDetails(item.id) },
                            modifier = Modifier.width(132.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    featured: DetailsUiModel,
    onOpenDetails: () -> Unit,
    initialFocus: FocusRequester,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF111827), Color(featured.accentArgb), Color(0xFF111827)),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF20B101A),
                        0.58f to Color(0x9C0B101A),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.66f)
                .padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "РЕКОМЕНДУЕМ",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = featured.title,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = featured.metadata,
                color = Color(0xFFD5DCE8),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = featured.summary,
                color = Color(0xFFB6C1D2),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    text = if (featured.playbackAvailable) {
                        featured.resumeLabel
                    } else {
                        "Перейти"
                    },
                    onClick = onOpenDetails,
                    modifier = Modifier.focusRequester(initialFocus),
                    primary = true,
                    leadingMark = if (featured.playbackAvailable) "▶" else "›",
                )
                if (featured.playbackAvailable) {
                    TvActionButton(
                        text = "Подробнее",
                        onClick = onOpenDetails,
                        leadingMark = "i",
                    )
                }
            }
        }
    }
}
