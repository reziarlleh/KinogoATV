package com.kinogo.atv.data.fixture

import androidx.paging.PagingSource
import com.kinogo.atv.domain.CatalogItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureCatalogPagingSourceTest {
    @Test
    fun `serves fixed ten item pages and a short final page`() = runBlocking {
        val source = FixtureCatalogPagingSource(items = fixtureItems(23))

        val first = source.load(refreshParams())
        assertTrue(first is PagingSource.LoadResult.Page)
        first as PagingSource.LoadResult.Page<Int, CatalogItem>
        assertEquals(10, first.data.size)
        assertEquals(2, first.nextKey)
        assertNull(first.prevKey)

        val last = source.load(appendParams(key = 3))
        assertTrue(last is PagingSource.LoadResult.Page)
        last as PagingSource.LoadResult.Page<Int, CatalogItem>
        assertEquals(3, last.data.size)
        assertEquals(2, last.prevKey)
        assertNull(last.nextKey)
    }

    @Test
    fun `configured failure is exposed as paging error`() = runBlocking {
        val source = FixtureCatalogPagingSource(fixtureItems(20), failingPages = setOf(2))

        val result = source.load(appendParams(key = 2))

        assertTrue(result is PagingSource.LoadResult.Error)
    }

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(
            key = null,
            loadSize = 30,
            placeholdersEnabled = false,
        )

    private fun appendParams(key: Int): PagingSource.LoadParams.Append<Int> =
        PagingSource.LoadParams.Append(
            key = key,
            loadSize = 10,
            placeholdersEnabled = false,
        )

    private fun fixtureItems(count: Int): List<CatalogItem> =
        List(count) { index ->
            CatalogItem(
                id = "item-$index",
                relativePath = "/movies/$index-item.html",
                title = "Item $index",
            )
        }
}
