package com.kinogo.atv.data.fixture

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kinogo.atv.domain.CatalogItem

/**
 * Deterministic page-number source for UI development before the HTML adapter is connected.
 * It intentionally serves exactly [pageSize] records because the upstream site has fixed pages.
 */
class FixtureCatalogPagingSource(
    items: List<CatalogItem>,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val failingPages: Set<Int> = emptySet(),
) : PagingSource<Int, CatalogItem>() {
    private val snapshot = items.toList()

    init {
        require(pageSize > 0)
        require(failingPages.all { it >= FIRST_PAGE })
    }

    override fun getRefreshKey(state: PagingState<Int, CatalogItem>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        return anchorPage.prevKey?.plus(1) ?: anchorPage.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CatalogItem> {
        val page = params.key ?: FIRST_PAGE
        if (page < FIRST_PAGE) {
            return LoadResult.Error(IllegalArgumentException("Page must be >= $FIRST_PAGE"))
        }
        if (page in failingPages) {
            return LoadResult.Error(FixturePageException(page))
        }

        val fromIndex = (page - FIRST_PAGE) * pageSize
        if (fromIndex >= snapshot.size) {
            return LoadResult.Page(
                data = emptyList(),
                prevKey = page.takeIf { it > FIRST_PAGE }?.minus(1),
                nextKey = null,
            )
        }

        val toIndex = (fromIndex + pageSize).coerceAtMost(snapshot.size)
        return LoadResult.Page(
            data = snapshot.subList(fromIndex, toIndex),
            prevKey = page.takeIf { it > FIRST_PAGE }?.minus(1),
            nextKey = if (toIndex < snapshot.size) page + 1 else null,
        )
    }

    companion object {
        const val FIRST_PAGE: Int = 1
        const val DEFAULT_PAGE_SIZE: Int = 10
    }
}

class FixturePageException(page: Int) : RuntimeException("Fixture page $page failed")
