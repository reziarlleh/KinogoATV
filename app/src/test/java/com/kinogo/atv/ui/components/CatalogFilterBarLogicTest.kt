package com.kinogo.atv.ui.components

import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogCategory
import com.kinogo.atv.domain.CatalogControls
import com.kinogo.atv.domain.CatalogDefaultSort
import com.kinogo.atv.domain.CatalogSortDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFilterBarLogicTest {
    @Test
    fun selectingSameSortDoesNotChangeDirection() {
        val initial = CatalogBrowseFilters(
            defaultSort = CatalogDefaultSort.VIEWS,
            sortDirection = CatalogSortDirection.ASC,
        )

        assertEquals(
            initial,
            initial.withDefaultSort(CatalogDefaultSort.VIEWS),
        )
    }

    @Test
    fun directionChangesOnlyThroughDedicatedAction() {
        val initial = CatalogBrowseFilters(
            defaultSort = CatalogDefaultSort.DATE,
            sortDirection = CatalogSortDirection.DESC,
        )

        assertEquals(
            CatalogSortDirection.ASC,
            initial.toggleSortDirection().sortDirection,
        )
    }

    @Test
    fun defaultOrderingCannotKeepAscendingDirection() {
        val initial = CatalogBrowseFilters(
            defaultSort = CatalogDefaultSort.RATING,
            sortDirection = CatalogSortDirection.ASC,
        )

        assertEquals(
            CatalogBrowseFilters(),
            initial.withDefaultSort(null),
        )
    }

    @Test
    fun xSortFragmentWithoutSidebarUsesOnlyVerifiedCategoryAllowlist() {
        assertEquals(
            CatalogCategory.entries,
            CatalogControls().availableCategoryOptions(),
        )
    }

    @Test
    fun parsedCategorySubsetIsPreserved() {
        val parsed = listOf(CatalogCategory.ALL_MOVIES, CatalogCategory.ALL_SERIES)

        assertEquals(
            parsed,
            CatalogControls(categories = parsed).availableCategoryOptions(),
        )
    }
}
