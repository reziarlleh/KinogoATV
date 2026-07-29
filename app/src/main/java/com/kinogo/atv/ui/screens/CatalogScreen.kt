package com.kinogo.atv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinogo.atv.domain.CatalogFilter
import com.kinogo.atv.domain.CatalogGenre
import com.kinogo.atv.domain.CatalogSection
import com.kinogo.atv.ui.components.PosterCard
import com.kinogo.atv.ui.components.PosterGridColumnCount
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvChoiceChip
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.PosterUiModel
import java.time.Year

private const val FILTER_YEAR_FLOOR = 2014

internal enum class CatalogSortMode(val title: String) {
    NEWEST("Сначала новые"),
    TITLE("По названию"),
    QUALITY("По качеству"),
}

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
    selectedAdvancedFilter: CatalogFilter? = null,
    onAdvancedFilterSelected: (CatalogFilter?) -> Unit = {},
) {
    val sections = listOf(
        "Все" to CatalogSection.ROOT,
        "Фильмы" to CatalogSection.MOVIES,
        "Сериалы" to CatalogSection.SERIES,
        "Мультфильмы" to CatalogSection.CARTOONS,
    )
    var localSection by remember { mutableStateOf(CatalogSection.ROOT) }
    var localAdvancedFilter by remember { mutableStateOf<CatalogFilter?>(null) }
    var sortModeName by rememberSaveable { mutableStateOf(CatalogSortMode.NEWEST.name) }
    var sortExpanded by remember { mutableStateOf(false) }
    var filterDialogVisible by rememberSaveable { mutableStateOf(false) }
    val sortMode = CatalogSortMode.entries.firstOrNull { it.name == sortModeName }
        ?: CatalogSortMode.NEWEST
    val effectiveSection = if (useRemoteSections) selectedSection else localSection
    val effectiveFilter = if (useRemoteSections) selectedAdvancedFilter else localAdvancedFilter
    val visibleItems = remember(items, effectiveSection, effectiveFilter, sortMode) {
        val sectionItems = if (useRemoteSections || effectiveFilter != null) {
            items
        } else {
            items.filterForSection(effectiveSection)
        }
        sectionItems.sortedForCatalog(sortMode)
    }
    val initialFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(visibleItems.firstOrNull()?.id, effectiveSection, effectiveFilter, sortMode) {
        if (visibleItems.isNotEmpty()) initialFocus.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
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
                        effectiveFilter != null -> "Фильтр: ${effectiveFilter.title}"
                        statusLabel != null -> statusLabel
                        else -> "Следующая страница загружается заранее"
                    },
                    color = if (errorMessage != null) {
                        Color(0xFFFFD0CC)
                    } else {
                        Color(0xFFD0DEE4)
                    },
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            if (errorMessage != null) {
                TvActionButton(text = "Повторить", onClick = onRetry, leadingMark = "↺")
            }
            Box {
                TvActionButton(
                    text = "Сортировка: ${sortMode.title}",
                    onClick = { sortExpanded = true },
                    leadingMark = "▾",
                )
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                    modifier = Modifier
                        .width(220.dp)
                        .background(Color(0xFF263D48)),
                ) {
                    CatalogSortMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.title,
                                    color = if (option == sortMode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White
                                    },
                                    fontWeight = if (option == sortMode) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                )
                            },
                            onClick = {
                                sortModeName = option.name
                                sortExpanded = false
                            },
                        )
                    }
                }
            }
            TvActionButton(
                text = effectiveFilter?.let { "Фильтр: ${it.title}" } ?: "Фильтр",
                onClick = { filterDialogVisible = true },
                leadingMark = "≡",
                primary = effectiveFilter != null,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(items = sections, key = { it.second.name }) { (title, section) ->
                TvChoiceChip(
                    text = title,
                    selected = effectiveFilter == null && effectiveSection == section,
                    onClick = {
                        if (useRemoteSections) {
                            onSectionSelected(section)
                        } else {
                            localAdvancedFilter = null
                            localSection = section
                        }
                    },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(PosterGridColumnCount),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 2.dp, top = 3.dp, end = 5.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                columnCount = PosterGridColumnCount,
                            )
                        ) {
                            onLoadMore()
                        }
                    },
                )
            }
        }
    }

    if (filterDialogVisible) {
        CatalogFilterDialog(
            selected = effectiveFilter,
            availableYears = catalogFilterYears(items),
            onDismiss = { filterDialogVisible = false },
            onApply = { filter ->
                if (useRemoteSections) {
                    onAdvancedFilterSelected(filter)
                } else {
                    localSection = CatalogSection.ROOT
                    localAdvancedFilter = filter
                }
                filterDialogVisible = false
            },
        )
    }
}

@Composable
private fun CatalogFilterDialog(
    selected: CatalogFilter?,
    availableYears: List<Int>,
    onDismiss: () -> Unit,
    onApply: (CatalogFilter?) -> Unit,
) {
    var draft by remember(selected) { mutableStateOf(selected) }
    val resetFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(Unit) { resetFocus.requestFocus() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.82f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF263D48),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 22.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TvSectionTitle(
                        text = "Фильтр каталога",
                        trailing = "Один серверный фильтр за раз",
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "reset") {
                            FilterChoiceGroup(
                                title = "Сброс",
                                choices = listOf(null),
                                selected = draft,
                                label = { "Все материалы" },
                                onSelected = { draft = null },
                                firstFocus = resetFocus,
                            )
                        }
                        item(key = "collection") {
                            FilterChoiceGroup(
                                title = "Подборка",
                                choices = listOf(CatalogFilter.NewReleases),
                                selected = draft,
                                label = { it?.title.orEmpty() },
                                onSelected = { draft = it },
                            )
                        }
                        item(key = "years") {
                            FilterChoiceGroup(
                                title = "Год",
                                choices = availableYears.map { CatalogFilter.Year(it) },
                                selected = draft,
                                label = { it?.title.orEmpty() },
                                onSelected = { draft = it },
                            )
                        }
                        item(key = "countries") {
                            FilterChoiceGroup(
                                title = "Страна",
                                choices = catalogCountries().map { CatalogFilter.Country(it) },
                                selected = draft,
                                label = { it?.title.orEmpty() },
                                onSelected = { draft = it },
                            )
                        }
                        item(key = "genres") {
                            FilterChoiceGroup(
                                title = "Жанр",
                                choices = CatalogGenre.entries.map { CatalogFilter.Genre(it) },
                                selected = draft,
                                label = { it?.title.orEmpty() },
                                onSelected = { draft = it },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TvActionButton(text = "Отмена", onClick = onDismiss)
                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        TvActionButton(
                            text = if (draft == null) "Сбросить" else "Применить",
                            onClick = { onApply(draft) },
                            primary = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChoiceGroup(
    title: String,
    choices: List<CatalogFilter?>,
    selected: CatalogFilter?,
    label: (CatalogFilter?) -> String,
    onSelected: (CatalogFilter?) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(items = choices, key = { it?.let(::catalogFilterKey) ?: "none" }) { filter ->
                TvChoiceChip(
                    text = label(filter),
                    selected = selected == filter,
                    onClick = { onSelected(filter) },
                    modifier = if (filter == choices.firstOrNull() && firstFocus != null) {
                        Modifier.focusRequester(firstFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

internal fun List<PosterUiModel>.filterForSection(section: CatalogSection): List<PosterUiModel> =
    when (section) {
        CatalogSection.ROOT -> this
        CatalogSection.MOVIES -> filter { "Фильм" in it.subtitle }
        CatalogSection.SERIES -> filter { "Сериал" in it.subtitle }
        CatalogSection.CARTOONS -> filter { "Мультфильм" in it.subtitle }
        CatalogSection.ANIME -> filter { "Аниме" in it.subtitle }
    }

internal fun List<PosterUiModel>.sortedForCatalog(
    mode: CatalogSortMode,
): List<PosterUiModel> = when (mode) {
    CatalogSortMode.NEWEST -> sortedByDescending {
        it.subtitle.substringBefore(' ').toIntOrNull() ?: 0
    }

    CatalogSortMode.TITLE -> sortedBy { it.title.lowercase() }
    CatalogSortMode.QUALITY -> sortedWith(
        compareBy<PosterUiModel> { posterQualityRank(it.badge) }.thenBy { it.title.lowercase() },
    )
}

internal fun catalogFilterYears(
    items: List<PosterUiModel>,
    currentYear: Int = Year.now().value,
): List<Int> {
    val loaded = items.mapNotNull { it.subtitle.substringBefore(' ').toIntOrNull() }
    return (loaded + (currentYear downTo FILTER_YEAR_FLOOR))
        .filter { it in FILTER_YEAR_FLOOR..currentYear }
        .distinct()
        .sortedDescending()
}

internal fun catalogCountries(): List<String> = listOf(
    "США",
    "Турция",
    "Россия",
    "Казахстан",
    "Индия",
    "Корея",
    "Великобритания",
    "Франция",
    "Германия",
    "Канада",
    "Япония",
    "Китай",
)

private fun posterQualityRank(badge: String?): Int {
    val normalized = badge.orEmpty().lowercase()
    return when {
        "2160" in normalized || "4k" in normalized -> 0
        "1080" in normalized -> 1
        "720" in normalized -> 2
        "480" in normalized -> 3
        else -> 4
    }
}

private fun catalogFilterKey(filter: CatalogFilter): String = when (filter) {
    CatalogFilter.NewReleases -> "new"
    is CatalogFilter.Year -> "year:${filter.value}"
    is CatalogFilter.Country -> "country:${filter.title}"
    is CatalogFilter.Genre -> "genre:${filter.value.name}"
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
