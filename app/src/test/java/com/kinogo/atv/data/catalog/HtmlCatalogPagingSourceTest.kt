package com.kinogo.atv.data.catalog

import androidx.paging.PagingSource
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.CatalogQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlCatalogPagingSourceTest {
    @Test
    fun `refresh and append follow parsed next page`() = runTest {
        val repository = FakeCatalogRepository(lastPage = 3)
        val source = HtmlCatalogPagingSource(repository, "https://kinogo.parts", CatalogQuery())

        val firstResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        )
        assertTrue(firstResult is PagingSource.LoadResult.Page)
        val first = firstResult as PagingSource.LoadResult.Page<Int, CatalogItem>
        val secondResult = source.load(
            PagingSource.LoadParams.Append(key = requireNotNull(first.nextKey), loadSize = 10, placeholdersEnabled = false),
        )
        assertTrue(secondResult is PagingSource.LoadResult.Page)
        val second = secondResult as PagingSource.LoadResult.Page<Int, CatalogItem>

        assertEquals(listOf(1, 2), repository.requestedPages)
        assertNull(first.prevKey)
        assertEquals(2, first.nextKey)
        assertEquals(1, second.prevKey)
        assertEquals(3, second.nextKey)
    }

    @Test
    fun `end of catalog comes from absent next page`() = runTest {
        val repository = FakeCatalogRepository(lastPage = 1)
        val source = HtmlCatalogPagingSource(repository, "https://kinogo.parts", CatalogQuery())

        val rawResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        )
        assertTrue(rawResult is PagingSource.LoadResult.Page)
        val result = rawResult as PagingSource.LoadResult.Page<Int, CatalogItem>

        assertNull(result.nextKey)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `repository errors stay typed paging errors`() = runTest {
        val failure = CatalogChallengeException()
        val repository = object : CatalogRepository {
            override suspend fun loadPage(origin: String, query: CatalogQuery): ParsedCatalogPage =
                throw failure

            override suspend fun loadDetails(
                origin: String,
                item: CatalogItem,
            ): ParsedContentPage = error("unused")
        }
        val source = HtmlCatalogPagingSource(repository, "https://kinogo.parts", CatalogQuery())

        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        )

        assertTrue(result is PagingSource.LoadResult.Error)
        assertTrue((result as PagingSource.LoadResult.Error).throwable === failure)
    }

    private class FakeCatalogRepository(private val lastPage: Int) : CatalogRepository {
        val requestedPages = mutableListOf<Int>()

        override suspend fun loadPage(origin: String, query: CatalogQuery): ParsedCatalogPage {
            requestedPages += query.page
            return ParsedCatalogPage(
                items = listOf(
                    CatalogItem(
                        id = query.page.toString(),
                        relativePath = "/filmy/${query.page}-item.html",
                        title = "Item ${query.page}",
                    ),
                ),
                page = query.page,
                nextPage = (query.page + 1).takeIf { query.page < lastPage },
            )
        }

        override suspend fun loadDetails(
            origin: String,
            item: CatalogItem,
        ): ParsedContentPage = error("unused")
    }
}
