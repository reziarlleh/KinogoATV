package com.kinogo.atv.data.catalog

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogControls
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.CatalogQuery
import com.kinogo.atv.domain.CatalogSortDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface CatalogRepository {
    suspend fun loadPage(origin: String, query: CatalogQuery): ParsedCatalogPage

    suspend fun loadDetails(origin: String, item: CatalogItem): ParsedContentPage
}

class HtmlCatalogRepository(
    private val transport: CatalogFilterHtmlTransport,
    private val parser: KinogoHtmlParser,
) : CatalogRepository {
    private val browseMutex = Mutex()
    private var appliedQuery: AppliedCatalogQuery? = null

    override suspend fun loadPage(origin: String, query: CatalogQuery): ParsedCatalogPage =
        browseMutex.withLock {
            var sessionRetries = 0
            var networkRetries = 0
            while (true) {
                try {
                    return@withLock loadPageInStableSession(origin, query)
                } catch (cancelled: CancellationException) {
                    // A cancelled POST may already have changed the server-side xSort session.
                    appliedQuery = null
                    throw cancelled
                } catch (changed: CatalogSessionChangedException) {
                    appliedQuery = null
                    if (sessionRetries >= MAX_SESSION_CHANGE_RETRIES) throw changed
                    sessionRetries++
                    delay(SESSION_CHANGE_RETRY_DELAY_MS * sessionRetries)
                } catch (network: CatalogNetworkException) {
                    // Never retry one xSort POST: repeating the same command toggles direction.
                    // Forget the ambiguous session and replay the complete clear/apply transaction.
                    appliedQuery = null
                    if (networkRetries >= MAX_NETWORK_RETRIES) throw network
                    networkRetries++
                    delay(NETWORK_RETRY_DELAY_MS * networkRetries)
                } catch (error: Exception) {
                    // Parsing and HTTP errors can also happen after a mutating POST. The next user
                    // action must re-establish the complete selection instead of trusting the cache.
                    appliedQuery = null
                    throw error
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        }

    private suspend fun loadPageInStableSession(
        origin: String,
        query: CatalogQuery,
    ): ParsedCatalogPage {
        val identity = query.identity
        val initialSessionEpoch = transport.sessionEpoch(origin)
        val selectionChanged = appliedQuery != AppliedCatalogQuery(
            origin = origin,
            identity = identity,
            sessionEpoch = initialSessionEpoch,
        )
        val response = if (selectionChanged) {
            applyBrowseSelection(origin, identity, query.page)
        } else {
            transport.get(origin, KinogoRoutes.catalog(query))
        }
        val responseSessionEpoch = transport.sessionEpoch(origin)
        if (!selectionChanged && responseSessionEpoch != initialSessionEpoch) {
            throw CatalogSessionChangedException()
        }

        val parsed = withContext(Dispatchers.Default) {
            parseSafely {
                parser.parseCatalog(
                    html = response.body,
                    origin = response.resolvedOrigin,
                    page = query.page,
                )
            }
        }
        val commitSessionEpoch = transport.sessionEpoch(origin)
        if (commitSessionEpoch != responseSessionEpoch) {
            throw CatalogSessionChangedException()
        }
        if (identity.searchTerm == null) {
            try {
                validateExplicitActiveFilters(identity.filters, parsed.controls)
            } catch (error: CatalogParseException) {
                appliedQuery = null
                throw error
            }
        }
        if (!allowsEmptyPage(query, parsed)) {
            throw CatalogParseException("Каталог не содержит поддерживаемых карточек")
        }
        appliedQuery = AppliedCatalogQuery(
            origin = origin,
            identity = identity,
            sessionEpoch = commitSessionEpoch,
        )
        return parsed
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

    private suspend fun applyBrowseSelection(
        origin: String,
        identity: CatalogQuery,
        requestedPage: Int,
    ): HtmlResponse {
        val basePath = KinogoRoutes.catalog(identity)
        var response = transport.postCatalogForm(origin, basePath, CLEAR_XSORT_FORM)
        val controls = withContext(Dispatchers.Default) {
            parseSafely { parser.parseCatalogControls(response.body, response.resolvedOrigin) }
        }

        if (identity.searchTerm == null) {
            xSortCommands(identity.filters, controls).forEach { form ->
                response = transport.postCatalogForm(origin, basePath, form)
            }
        }

        return if (requestedPage == 1) {
            response
        } else {
            transport.get(origin, KinogoRoutes.catalog(identity.copy(page = requestedPage)))
        }
    }

    private fun xSortCommands(
        requested: CatalogBrowseFilters,
        controls: CatalogControls,
    ): List<Map<String, String>> = buildList {
        requested.defaultSort?.let { sort ->
            if (controls.sortOptions.none { it.value == sort }) {
                throw CatalogParseException("Выбранная сортировка недоступна на этой странице")
            }
            val activeSort = controls.activeFilters.defaultSort
            val activeDirection = controls.activeFilters.sortDirection
            val postCount = when {
                activeSort == sort && activeDirection == requested.sortDirection -> 0
                activeSort == sort -> 1
                requested.sortDirection == CatalogSortDirection.ASC -> 2
                else -> 1
            }
            repeat(postCount) {
                add(xSortForm(XSORT_DEFAULT_SORT_FIELD, sort.wireValue))
            }
        }

        requested.collection?.let { option ->
            if (controls.collectionOptions.none { it.value == option.value }) {
                throw CatalogParseException("Выбранная подборка недоступна на этой странице")
            }
            if (controls.activeFilters.collection?.value != option.value) {
                add(xSortForm(XSORT_COLLECTION_FIELD, option.value))
            }
        }

        requested.year?.let { year ->
            val value = year.toString()
            if (controls.yearOptions.none { it.value == value }) {
                throw CatalogParseException("Выбранный год недоступен на этой странице")
            }
            if (controls.activeFilters.year != year) {
                add(xSortForm(XSORT_YEAR_FIELD, value))
            }
        }

        requested.country?.let { option ->
            if (controls.countryOptions.none { it.value == option.value }) {
                throw CatalogParseException("Выбранная страна недоступна на этой странице")
            }
            if (controls.activeFilters.country?.value != option.value) {
                add(xSortForm(XSORT_COUNTRY_FIELD, option.value))
            }
        }
    }

    private fun validateExplicitActiveFilters(
        requested: CatalogBrowseFilters,
        controls: CatalogControls,
    ) {
        val active = controls.activeFilters
        requested.defaultSort?.let { sort ->
            if (active.defaultSort != sort || active.sortDirection != requested.sortDirection) {
                throw CatalogParseException(
                    "Сервер не применил выбранную сортировку и направление",
                )
            }
        }
        requested.collection?.let { option ->
            if (active.collection?.value != option.value) {
                throw CatalogParseException("Сервер не применил выбранную подборку")
            }
        }
        requested.year?.let { year ->
            if (active.year != year) {
                throw CatalogParseException("Сервер не применил выбранный год")
            }
        }
        requested.country?.let { option ->
            if (active.country?.value != option.value) {
                throw CatalogParseException("Сервер не применил выбранную страну")
            }
        }
    }

    private fun allowsEmptyPage(
        query: CatalogQuery,
        parsed: ParsedCatalogPage,
    ): Boolean {
        if (parsed.items.isNotEmpty() || query.searchTerm != null) return true
        if (query.page > 1) return parsed.nextPage == null
        return query.filters.collection != null ||
            query.filters.year != null ||
            query.filters.country != null
    }

    private companion object {
        data class AppliedCatalogQuery(
            val origin: String,
            val identity: CatalogQuery,
            val sessionEpoch: Long,
        )

        const val XSORT_DEFAULT_SORT_FIELD = "defaultsort"
        const val XSORT_COLLECTION_FIELD = "podborki"
        const val XSORT_YEAR_FIELD = "year"
        const val XSORT_COUNTRY_FIELD = "country"
        const val MAX_SESSION_CHANGE_RETRIES = 3
        const val SESSION_CHANGE_RETRY_DELAY_MS = 100L
        const val MAX_NETWORK_RETRIES = 1
        const val NETWORK_RETRY_DELAY_MS = 250L

        val CLEAR_XSORT_FORM = linkedMapOf(
            "xsort" to "1",
            "xs_field" to "clearallfields",
        )

        fun xSortForm(field: String, value: String): Map<String, String> = linkedMapOf(
            "xsort" to "1",
            "xs_field" to field,
            "xs_value" to value,
        )
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
