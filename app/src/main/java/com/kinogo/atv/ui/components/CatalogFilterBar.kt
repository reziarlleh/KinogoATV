package com.kinogo.atv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogCategory
import com.kinogo.atv.domain.CatalogCategoryGroup
import com.kinogo.atv.domain.CatalogControls
import com.kinogo.atv.domain.CatalogDefaultSort
import com.kinogo.atv.domain.CatalogFilterOption
import com.kinogo.atv.domain.CatalogSortDirection
import com.kinogo.atv.domain.CatalogSortOption

/** Compact TV adaptation of the controls parsed from the current Kinogo listing. */
@Composable
fun CatalogFilterBar(
    controls: CatalogControls,
    filters: CatalogBrowseFilters,
    onFiltersChanged: (CatalogBrowseFilters) -> Unit,
    modifier: Modifier = Modifier,
    category: CatalogCategory? = null,
    showCategories: Boolean = false,
    onCategorySelected: (CatalogCategory) -> Unit = {},
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCategories) {
            CategoryDropdown(
                selected = category,
                categories = controls.availableCategoryOptions(),
                onSelected = onCategorySelected,
                modifier = Modifier.weight(1.18f),
            )
        }

        Text(
            text = "Сортировать:",
            color = Color(0xFFD1E0E6),
            fontSize = 11.sp,
            maxLines = 1,
        )

        val selectedSort = controls.sortOptions.firstOrNull { it.value == filters.defaultSort }
        FilterDropdown(
            selectedTitle = selectedSort?.title
                ?: filters.defaultSort?.fallbackTitle
                ?: "по умолчанию",
            options = controls.sortOptions,
            optionTitle = CatalogSortOption::title,
            optionSelected = { it.value == filters.defaultSort },
            onSelected = { option ->
                onFiltersChanged(filters.withDefaultSort(option.value))
            },
            enabled = controls.sortOptions.isNotEmpty(),
            modifier = Modifier.weight(1.05f),
        )

        TvActionButton(
            text = if (filters.sortDirection == CatalogSortDirection.DESC) "↓" else "↑",
            onClick = { onFiltersChanged(filters.toggleSortDirection()) },
            modifier = Modifier
                .width(46.dp)
                .semantics {
                    contentDescription = if (
                        filters.sortDirection == CatalogSortDirection.DESC
                    ) {
                        "Направление сортировки: по убыванию"
                    } else {
                        "Направление сортировки: по возрастанию"
                    }
                },
            enabled = filters.defaultSort != null,
        )

        val selectedCollection = controls.collectionOptions
            .firstOrNull { it.value == filters.collection?.value }
            ?: filters.collection
        FilterDropdown(
            selectedTitle = selectedCollection?.title ?: "Подборка",
            options = nullableOptions(controls.collectionOptions),
            optionTitle = { it?.title ?: "Подборка" },
            optionSelected = { it?.value == filters.collection?.value },
            onSelected = { onFiltersChanged(filters.copy(collection = it)) },
            enabled = controls.collectionOptions.isNotEmpty(),
            modifier = Modifier.weight(1f),
        )

        val selectedYearTitle = filters.year?.toString() ?: "Год"
        FilterDropdown(
            selectedTitle = selectedYearTitle,
            options = nullableOptions(controls.yearOptions),
            optionTitle = { it?.title ?: "Год" },
            optionSelected = { it?.value?.toIntOrNull() == filters.year },
            onSelected = { option ->
                onFiltersChanged(filters.copy(year = option?.value?.toIntOrNull()))
            },
            enabled = controls.yearOptions.isNotEmpty(),
            modifier = Modifier.weight(0.66f),
        )

        val selectedCountry = controls.countryOptions
            .firstOrNull { it.value == filters.country?.value }
            ?: filters.country
        FilterDropdown(
            selectedTitle = selectedCountry?.title ?: "Страны",
            options = nullableOptions(controls.countryOptions),
            optionTitle = { it?.title ?: "Страны" },
            optionSelected = { it?.value == filters.country?.value },
            onSelected = { onFiltersChanged(filters.copy(country = it)) },
            enabled = controls.countryOptions.isNotEmpty(),
            modifier = Modifier.weight(0.9f),
        )

        TvActionButton(
            text = "×",
            onClick = { onFiltersChanged(CatalogBrowseFilters()) },
            modifier = Modifier
                .width(46.dp)
                .semantics { contentDescription = "Сбросить фильтры" },
            enabled = !filters.isEmpty,
        )
    }
}

@Composable
private fun RowScope.CategoryDropdown(
    selected: CatalogCategory?,
    categories: List<CatalogCategory>,
    onSelected: (CatalogCategory) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var restoreTriggerFocus by remember { mutableStateOf(false) }
    val triggerFocus = remember { FocusRequester() }
    val selectedItemFocus = remember { FocusRequester() }
    val menuFocusCategory = selected?.takeIf(categories::contains) ?: categories.firstOrNull()

    LaunchedEffect(expanded, menuFocusCategory) {
        if (expanded && menuFocusCategory != null) {
            requestPopupFocus(selectedItemFocus)
        }
    }
    LaunchedEffect(expanded, restoreTriggerFocus) {
        if (!expanded && restoreTriggerFocus) {
            requestPopupFocus(triggerFocus)
            restoreTriggerFocus = false
        }
    }

    fun dismissMenu() {
        expanded = false
        restoreTriggerFocus = true
    }

    Box(modifier = modifier) {
        TvActionButton(
            text = selected?.title ?: "Категории",
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(triggerFocus),
            leadingMark = "▾",
            enabled = categories.isNotEmpty(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = ::dismissMenu,
            modifier = Modifier
                .widthIn(min = 250.dp, max = 340.dp)
                .heightIn(max = 430.dp)
                .background(Color(0xFF263D48)),
        ) {
            CatalogCategoryGroup.entries.forEach { group ->
                val groupItems = categories.filter { it.group == group }
                if (groupItems.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (group == CatalogCategoryGroup.MOVIES) {
                                    "Фильмы"
                                } else {
                                    "Сериалы"
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    groupItems.forEach { option ->
                        CatalogDropdownMenuItem(
                            title = option.title,
                            selected = option == selected,
                            modifier = if (option == menuFocusCategory) {
                                Modifier.focusRequester(selectedItemFocus)
                            } else {
                                Modifier
                            },
                            onClick = {
                                dismissMenu()
                                onSelected(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> RowScope.FilterDropdown(
    selectedTitle: String,
    options: List<T>,
    optionTitle: (T) -> String,
    optionSelected: (T) -> Boolean,
    onSelected: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var restoreTriggerFocus by remember { mutableStateOf(false) }
    val triggerFocus = remember { FocusRequester() }
    val selectedItemFocus = remember { FocusRequester() }
    val menuFocusIndex = options.indexOfFirst(optionSelected).takeIf { it >= 0 } ?: 0

    LaunchedEffect(expanded, menuFocusIndex) {
        if (expanded && options.isNotEmpty()) {
            requestPopupFocus(selectedItemFocus)
        }
    }
    LaunchedEffect(expanded, restoreTriggerFocus) {
        if (!expanded && restoreTriggerFocus) {
            requestPopupFocus(triggerFocus)
            restoreTriggerFocus = false
        }
    }

    fun dismissMenu() {
        expanded = false
        restoreTriggerFocus = true
    }

    Box(modifier = modifier) {
        TvActionButton(
            text = selectedTitle,
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(triggerFocus),
            leadingMark = "▾",
            enabled = enabled,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = ::dismissMenu,
            modifier = Modifier
                .widthIn(min = 190.dp, max = 360.dp)
                .heightIn(max = 430.dp)
                .background(Color(0xFF263D48)),
        ) {
            options.forEach { option ->
                CatalogDropdownMenuItem(
                    title = optionTitle(option),
                    selected = optionSelected(option),
                    modifier = if (options.indexOf(option) == menuFocusIndex) {
                        Modifier.focusRequester(selectedItemFocus)
                    } else {
                        Modifier
                    },
                    onClick = {
                        dismissMenu()
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogDropdownMenuItem(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = title,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        modifier = modifier,
    )
}

private fun nullableOptions(
    options: List<CatalogFilterOption>,
): List<CatalogFilterOption?> = listOf(null) + options

/** Selecting an item changes only the sort field; direction has its own explicit TV control. */
internal fun CatalogBrowseFilters.withDefaultSort(
    sort: CatalogDefaultSort?,
): CatalogBrowseFilters = copy(
    defaultSort = sort,
    sortDirection = if (sort == null) CatalogSortDirection.DESC else sortDirection,
)

internal fun CatalogBrowseFilters.toggleSortDirection(): CatalogBrowseFilters =
    if (defaultSort == null) {
        this
    } else {
        copy(
            sortDirection = if (sortDirection == CatalogSortDirection.DESC) {
                CatalogSortDirection.ASC
            } else {
                CatalogSortDirection.DESC
            },
        )
    }

/** xSort fragment responses may omit the sidebar; only the verified route allowlist is used. */
internal fun CatalogControls.availableCategoryOptions(): List<CatalogCategory> =
    categories.ifEmpty { CatalogCategory.entries }

private suspend fun requestPopupFocus(requester: FocusRequester) {
    repeat(POPUP_FOCUS_ATTEMPTS) {
        withFrameNanos { }
        if (runCatching { requester.requestFocus() }.getOrDefault(false)) return
    }
}

private const val POPUP_FOCUS_ATTEMPTS = 4
