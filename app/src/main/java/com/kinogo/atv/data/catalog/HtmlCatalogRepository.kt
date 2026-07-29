package com.kinogo.atv.data.catalog

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.CatalogQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CatalogRepository {
    suspend fun loadPage(origin: String, query: CatalogQuery): ParsedCatalogPage

    suspend fun loadDetails(origin: String, item: CatalogItem): ParsedContentPage
}

class HtmlCatalogRepository(
    private val transport: HtmlTransport,
    private val parser: KinogoHtmlParser,
) : CatalogRepository {
    override suspend fun loadPage(origin: String, query: CatalogQuery): ParsedCatalogPage {
        val response = transport.get(origin, KinogoRoutes.catalog(query))
        return withContext(Dispatchers.Default) {
            parseSafely {
                parser.parseCatalog(
                    html = response.body,
                    origin = response.resolvedOrigin,
                    page = query.page,
                )
            }
        }.also { parsed ->
            if (query.searchTerm == null && parsed.items.isEmpty()) {
                throw CatalogParseException("Каталог не содержит поддерживаемых карточек")
            }
        }
    }

    override suspend fun loadDetails(origin: String, item: CatalogItem): ParsedContentPage {
        val response = transport.get(origin, item.relativePath)
        return withContext(Dispatchers.Default) {
            parseSafely {
                parser.parseDetails(
                    html = response.body,
                    origin = response.resolvedOrigin,
                    relativePath = item.relativePath,
                )
            }
        }.also { parsed ->
            if (parsed.catalogItem.id != item.id) {
                throw CatalogParseException(
                    "Идентификатор карточки изменился: ожидался ${item.id}, получен " +
                        parsed.catalogItem.id,
                )
            }
        }
    }

    private inline fun <T> parseSafely(block: () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (known: CatalogException) {
        throw known
    } catch (error: Exception) {
        throw CatalogParseException("Не удалось разобрать HTML сервиса", error)
    }
}

/** One paging generation is permanently pinned to [origin]. */
class HtmlCatalogPagingSource(
    private val repository: CatalogRepository,
    private val origin: String,
    private val query: CatalogQuery,
) : PagingSource<Int, CatalogItem>() {
    override fun getRefreshKey(state: PagingState<Int, CatalogItem>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        return anchorPage.prevKey?.plus(1) ?: anchorPage.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CatalogItem> {
        val page = params.key ?: query.page
        if (page < 1) {
            return LoadResult.Error(IllegalArgumentException("Catalog page must be positive"))
        }
        return try {
            val result = repository.loadPage(origin, query.copy(page = page))
            val nextPage = result.nextPage?.takeIf { it > page }
                ?: result.nextPage?.let {
                    return LoadResult.Error(
                        CatalogParseException("Каталог вернул повторяющуюся страницу $it"),
                    )
                }
            LoadResult.Page(
                data = result.items,
                prevKey = page.takeIf { it > 1 }?.minus(1),
                nextKey = nextPage,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }
}
