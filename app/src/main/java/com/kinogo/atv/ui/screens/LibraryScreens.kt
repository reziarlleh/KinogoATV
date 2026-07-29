package com.kinogo.atv.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.ui.components.EmptyState
import com.kinogo.atv.ui.components.PosterCard
import com.kinogo.atv.ui.components.PosterGridMinimumWidth
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvChoiceChip
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.BookmarkUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import com.kinogo.atv.domain.LibraryFilter
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    catalog: List<PosterUiModel>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    useRemoteResults: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onQueryChanged: (String) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var voiceSearchError by remember { mutableStateOf(false) }
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { query = it }
            voiceSearchError = false
        }
    }
    LaunchedEffect(query, useRemoteResults) {
        if (useRemoteResults) {
            delay(350L)
            onQueryChanged(query.trim())
        }
    }
    val results = if (useRemoteResults) {
        catalog
    } else if (query.isBlank()) catalog.take(10) else {
        catalog.filter { it.title.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        TvSectionTitle(text = "Поиск", trailing = "Голосом или с пульта")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Название фильма или сериала") },
                singleLine = true,
            )
            TvActionButton(
                text = "Голосовой поиск",
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Что найти?")
                    }
                    try {
                        voiceSearchLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        voiceSearchError = true
                    }
                },
                leadingMark = "●",
            )
        }
        if (voiceSearchError) {
            Text(
                text = "Голосовой ввод недоступен на этом устройстве",
                color = Color(0xFFFFB4AB),
                fontSize = 12.sp,
            )
        }
        Text(
            text = when {
                isLoading -> "Ищем на активном зеркале…"
                errorMessage != null -> errorMessage
                query.isBlank() -> "Новинки каталога"
                else -> "Найдено: ${results.size}"
            },
            color = if (errorMessage != null) Color(0xFFFFB4AB) else Color(0xFFC8D1DF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        PosterGrid(
            items = results,
            onOpenDetails = onOpenDetails,
            emptyTitle = "Ничего не найдено",
            emptyDescription = "Попробуйте изменить запрос",
        )
    }
}

@Composable
fun FavoritesScreen(
    favorites: List<PosterUiModel>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BookmarksScreen(
        bookmarks = favorites.map { BookmarkUiModel(it, favorite = true) },
        onOpenDetails = onOpenDetails,
        modifier = modifier,
    )
}

@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkUiModel>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    val filtered = bookmarks.filter { bookmark ->
        when (selectedFilter) {
            LibraryFilter.ALL -> bookmark.favorite || bookmark.watchStatus != null
            LibraryFilter.WATCHING -> bookmark.watchStatus == com.kinogo.atv.domain.WatchStatus.WATCHING
            LibraryFilter.WATCHED -> bookmark.watchStatus == com.kinogo.atv.domain.WatchStatus.WATCHED
            LibraryFilter.PLANNED -> bookmark.watchStatus == com.kinogo.atv.domain.WatchStatus.PLANNED
            LibraryFilter.DROPPED -> bookmark.watchStatus == com.kinogo.atv.domain.WatchStatus.DROPPED
            LibraryFilter.FAVORITES -> bookmark.favorite
        }
    }.map { it.poster }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        TvSectionTitle(text = "Закладки", trailing = "${filtered.size} материалов")
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(LibraryFilter.entries, key = LibraryFilter::name) { filter ->
                TvChoiceChip(
                    text = filter.title,
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                )
            }
        }
        PosterGrid(
            items = filtered,
            onOpenDetails = onOpenDetails,
            emptyTitle = "Здесь пока пусто",
            emptyDescription = "Выберите статус или добавьте материал в избранное",
        )
    }
}

@Composable
private fun PosterGrid(
    items: List<PosterUiModel>,
    onOpenDetails: (String) -> Unit,
    emptyTitle: String,
    emptyDescription: String,
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = emptyTitle, description = emptyDescription)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = PosterGridMinimumWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, top = 7.dp, end = 18.dp, bottom = 38.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(items = items, key = { it.id }) { item ->
            PosterCard(item = item, onClick = { onOpenDetails(item.id) })
        }
    }
}
