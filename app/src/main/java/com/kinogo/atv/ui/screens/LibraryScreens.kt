package com.kinogo.atv.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kinogo.atv.domain.LibraryFilter
import com.kinogo.atv.ui.components.MicrophoneMark
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvChoiceChip
import com.kinogo.atv.ui.components.TvIconButton
import com.kinogo.atv.ui.components.TvPosterGrid
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.BookmarkUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import kotlinx.coroutines.delay

internal const val SEARCH_DEBOUNCE_MILLIS = 750L

@Composable
fun SearchScreen(
    catalog: List<PosterUiModel>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    useRemoteResults: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onQueryChanged: (String) -> Unit = {},
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    requestInitialFocus: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf<String?>(null) }
    var voiceSearchError by remember { mutableStateOf(false) }
    var focusResultsWhenReady by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit(value: String) {
        val normalized = value.trim()
        submittedQuery = normalized
        focusResultsWhenReady = true
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        if (useRemoteResults) onQueryChanged(normalized)
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { spokenQuery ->
                    query = spokenQuery
                    submit(spokenQuery)
                }
            voiceSearchError = false
        }
    }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) inputFocus.requestFocus()
    }
    LaunchedEffect(query, useRemoteResults, submittedQuery) {
        val normalized = query.trim()
        if (useRemoteResults && normalized != submittedQuery) {
            delay(SEARCH_DEBOUNCE_MILLIS)
            onQueryChanged(normalized)
        }
    }
    val results = if (useRemoteResults) {
        catalog
    } else if (query.isBlank()) {
        catalog.take(12)
    } else {
        catalog.filter { it.title.contains(query, ignoreCase = true) }
    }
    LaunchedEffect(results.firstOrNull()?.id, focusResultsWhenReady, isLoading) {
        if (focusResultsWhenReady && !isLoading && results.isNotEmpty()) {
            firstResultFocus.requestFocus()
            focusResultsWhenReady = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TvSectionTitle(text = "Поиск", trailing = "Введите запрос или нажмите микрофон")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    submittedQuery = null
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(inputFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            submit(query)
                            true
                        } else {
                            false
                        }
                    },
                label = { Text("Название фильма или сериала") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF6C8792),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color(0xFFD0DEE4),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color(0xFF29424D),
                    unfocusedContainerColor = Color(0xFF29424D),
                ),
            )
            TvIconButton(
                contentDescription = "Голосовой поиск",
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
            ) { color ->
                MicrophoneMark(color = color)
            }
        }
        if (voiceSearchError) {
            Text(
                text = "Голосовой ввод недоступен на этом устройстве",
                color = Color(0xFFFFD0CC),
                fontSize = 11.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    isLoading -> "Ищем на активном зеркале…"
                    errorMessage != null -> errorMessage
                    query.isBlank() -> "Введите название фильма или сериала"
                    else -> "Найдено: ${results.size}"
                },
                modifier = Modifier.weight(1f),
                color = if (errorMessage != null) Color(0xFFFFD0CC) else Color(0xFFE0EBEF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            if (errorMessage != null && query.isNotBlank()) {
                TvActionButton(
                    text = "Повторить",
                    onClick = { onQueryChanged(query.trim()) },
                    leadingMark = "↺",
                )
            }
        }
        PosterGrid(
            items = results,
            onOpenDetails = onOpenDetails,
            emptyTitle = "Ничего не найдено",
            emptyDescription = if (query.isBlank()) {
                "Введите запрос в строке выше"
            } else {
                "Попробуйте изменить запрос"
            },
            firstFocus = firstResultFocus,
            hasMore = hasMore,
            onNearEnd = onLoadMore,
            pagingKey = query.trim(),
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
            LibraryFilter.WATCHING ->
                bookmark.watchStatus == com.kinogo.atv.domain.WatchStatus.WATCHING

            LibraryFilter.WATCHED ->
                bookmark.watchStatus == com.kinogo.atv.domain.WatchStatus.WATCHED

            LibraryFilter.PLANNED ->
                bookmark.watchStatus == com.kinogo.atv.domain.WatchStatus.PLANNED

            LibraryFilter.DROPPED ->
                bookmark.watchStatus == com.kinogo.atv.domain.WatchStatus.DROPPED

            LibraryFilter.FAVORITES -> bookmark.favorite
        }
    }.map { it.poster }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TvSectionTitle(text = "Закладки", trailing = "${filtered.size} материалов")
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
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
internal fun PosterGrid(
    items: List<PosterUiModel>,
    onOpenDetails: (String) -> Unit,
    emptyTitle: String,
    emptyDescription: String,
    firstFocus: FocusRequester? = null,
    hasMore: Boolean = false,
    onNearEnd: () -> Unit = {},
    pagingKey: Any? = null,
) {
    TvPosterGrid(
        items = items,
        onOpenDetails = onOpenDetails,
        emptyTitle = emptyTitle,
        emptyDescription = emptyDescription,
        hasMore = hasMore,
        onNearEnd = onNearEnd,
        firstFocus = firstFocus,
        pagingKey = pagingKey,
    )
}
