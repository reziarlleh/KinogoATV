package com.kinogo.atv.data.catalog

import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogCategory
import com.kinogo.atv.domain.CatalogDefaultSort
import com.kinogo.atv.domain.CatalogFilterOption
import com.kinogo.atv.domain.CatalogQuery
import com.kinogo.atv.domain.CatalogSortDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup
import java.net.SocketTimeoutException

class HtmlCatalogRepositoryXSortTest {
    @Test
    fun appliesClearAndEverySelectedFieldInStableOrder() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.DRAMA,
            filters = CatalogBrowseFilters(
                defaultSort = CatalogDefaultSort.RATING,
                sortDirection = CatalogSortDirection.ASC,
                collection = CatalogFilterOption("Marvel", "Marvel"),
                year = 2025,
                country = CatalogFilterOption("Россия", "Россия"),
            ),
        )

        repository.loadPage(ORIGIN, query)

        assertEquals(
            listOf(
                PostCall("/drama/", form("clearallfields")),
                PostCall("/drama/", form("defaultsort", "rating")),
                PostCall("/drama/", form("defaultsort", "rating")),
                PostCall("/drama/", form("podborki", "Marvel")),
                PostCall("/drama/", form("year", "2025")),
                PostCall("/drama/", form("country", "Россия")),
            ),
            transport.calls,
        )
    }

    @Test
    fun doesNotToggleHomeDefaultWhenClearAlreadySelectedTopDescending() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())

        val result = repository.loadPage(
            ORIGIN,
            CatalogQuery(
                filters = CatalogBrowseFilters(
                    defaultSort = CatalogDefaultSort.TOP_3_DAYS,
                    sortDirection = CatalogSortDirection.DESC,
                ),
            ),
        )

        assertEquals(listOf(PostCall("/", form("clearallfields"))), transport.calls)
        assertEquals(CatalogDefaultSort.TOP_3_DAYS, result.controls.activeFilters.defaultSort)
    }

    @Test
    fun nextPageUsesSameAppliedSessionAndExactQueryRoute() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.ALL_MOVIES,
            filters = CatalogBrowseFilters(defaultSort = CatalogDefaultSort.DATE),
        )

        repository.loadPage(ORIGIN, query)
        transport.calls.clear()
        repository.loadPage(ORIGIN, query.copy(page = 2))

        assertEquals(listOf(GetCall("/filmy/page/2/")), transport.calls)
    }

    @Test
    fun searchClearsPriorBrowseStateAndKeepsItsTermForAppend() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())

        repository.loadPage(
            ORIGIN,
            CatalogQuery(
                category = CatalogCategory.ALL_SERIES,
                filters = CatalogBrowseFilters(defaultSort = CatalogDefaultSort.DATE),
            ),
        )
        transport.calls.clear()

        val search = CatalogQuery.search(" Игра   престолов ")
        repository.loadPage(ORIGIN, search)
        repository.loadPage(ORIGIN, search.copy(page = 2))

        assertEquals(
            listOf(
                PostCall(
                    "/search/%D0%98%D0%B3%D1%80%D0%B0%20%D0%BF%D1%80%D0%B5%D1%81%D1%82%D0%BE%D0%BB%D0%BE%D0%B2/",
                    form("clearallfields"),
                ),
                GetCall(
                    "/search/%D0%98%D0%B3%D1%80%D0%B0%20%D0%BF%D1%80%D0%B5%D1%81%D1%82%D0%BE%D0%BB%D0%BE%D0%B2/page/2/",
                ),
            ),
            transport.calls,
        )
    }

    @Test
    fun unavailableServerOptionFailsWithoutCachingPartialSelection() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            filters = CatalogBrowseFilters(
                country = CatalogFilterOption("Несуществующая", "Несуществующая"),
            ),
        )

        repeat(2) {
            val error = runCatching { repository.loadPage(ORIGIN, query) }.exceptionOrNull()
            assertTrue(error is CatalogParseException)
        }

        assertEquals(2, transport.calls.count { it is PostCall })
    }

    @Test
    fun changedSessionEpochForcesSelectionReapplyBeforeAppend() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.ALL_MOVIES,
            filters = CatalogBrowseFilters(defaultSort = CatalogDefaultSort.DATE),
        )

        repository.loadPage(ORIGIN, query)
        transport.calls.clear()
        transport.replaceSession()
        repository.loadPage(ORIGIN, query.copy(page = 2))

        assertEquals(
            listOf(
                PostCall("/filmy/", form("clearallfields")),
                PostCall("/filmy/", form("defaultsort", "date")),
                GetCall("/filmy/page/2/"),
            ),
            transport.calls,
        )
    }

    @Test
    fun sessionChangeDuringAppendReappliesSelectionAutomatically() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.ALL_MOVIES,
            filters = CatalogBrowseFilters(defaultSort = CatalogDefaultSort.DATE),
        )

        repository.loadPage(ORIGIN, query)
        transport.calls.clear()
        transport.changeSessionOnNextGet = true
        val result = repository.loadPage(ORIGIN, query.copy(page = 2))

        assertTrue(result.items.isNotEmpty())
        assertEquals(
            listOf(
                GetCall("/filmy/page/2/"),
                PostCall("/filmy/", form("clearallfields")),
                PostCall("/filmy/", form("defaultsort", "date")),
                GetCall("/filmy/page/2/"),
            ),
            transport.calls,
        )
    }

    @Test
    fun ambiguousPostTimeoutRestartsWholeXSortTransaction() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html")).apply {
            postFailuresAfterMutation["defaultsort"] = 1
        }
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.ALL_MOVIES,
            filters = CatalogBrowseFilters(
                defaultSort = CatalogDefaultSort.RATING,
                sortDirection = CatalogSortDirection.DESC,
            ),
        )

        val result = repository.loadPage(ORIGIN, query)

        assertEquals(
            listOf(
                PostCall("/filmy/", form("clearallfields")),
                PostCall("/filmy/", form("defaultsort", "rating")),
                PostCall("/filmy/", form("clearallfields")),
                PostCall("/filmy/", form("defaultsort", "rating")),
            ),
            transport.calls,
        )
        assertEquals(CatalogDefaultSort.RATING, result.controls.activeFilters.defaultSort)
        assertEquals(CatalogSortDirection.DESC, result.controls.activeFilters.sortDirection)
    }

    @Test
    fun repeatedPostTimeoutStopsAfterOneWholeTransactionRetry() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html")).apply {
            postFailuresAfterMutation["defaultsort"] = Int.MAX_VALUE
        }
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.ALL_MOVIES,
            filters = CatalogBrowseFilters(
                defaultSort = CatalogDefaultSort.RATING,
                sortDirection = CatalogSortDirection.DESC,
            ),
        )

        val error = runCatching { repository.loadPage(ORIGIN, query) }.exceptionOrNull()

        assertTrue(error is CatalogNetworkException)
        assertTrue(error?.cause is SocketTimeoutException)
        assertEquals(
            listOf(
                PostCall("/filmy/", form("clearallfields")),
                PostCall("/filmy/", form("defaultsort", "rating")),
                PostCall("/filmy/", form("clearallfields")),
                PostCall("/filmy/", form("defaultsort", "rating")),
            ),
            transport.calls,
        )
    }

    @Test
    fun appendIsRejectedWhenServerLosesActiveFiltersInsideSameSession() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.ALL_MOVIES,
            filters = CatalogBrowseFilters(defaultSort = CatalogDefaultSort.DATE),
        )

        repository.loadPage(ORIGIN, query)
        transport.resetFiltersOnNextGet = true
        val error = runCatching {
            repository.loadPage(ORIGIN, query.copy(page = 2))
        }.exceptionOrNull()

        assertTrue(error is CatalogParseException)
        transport.calls.clear()

        repository.loadPage(ORIGIN, query.copy(page = 2))

        assertEquals(
            listOf(
                PostCall("/filmy/", form("clearallfields")),
                PostCall("/filmy/", form("defaultsort", "date")),
                GetCall("/filmy/page/2/"),
            ),
            transport.calls,
        )
    }

    @Test
    fun ignoredXSortSelectionIsRejectedAndNeverCached() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html")).apply {
            honorXSortCommands = false
        }
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.ALL_MOVIES,
            filters = CatalogBrowseFilters(
                defaultSort = CatalogDefaultSort.RATING,
                sortDirection = CatalogSortDirection.ASC,
            ),
        )

        repeat(2) {
            val error = runCatching { repository.loadPage(ORIGIN, query) }.exceptionOrNull()
            assertTrue(error is CatalogParseException)
        }

        assertEquals(
            2,
            transport.calls.count { call ->
                call is PostCall && call.form["xs_field"] == "clearallfields"
            },
        )
    }

    @Test
    fun narrowingFiltersMayProduceAnEmptyFirstPage() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html")).apply {
            emptyAllResponses = true
        }
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())

        val result = repository.loadPage(
            ORIGIN,
            CatalogQuery(
                category = CatalogCategory.ALL_MOVIES,
                filters = CatalogBrowseFilters(
                    country = CatalogFilterOption("Россия", "Россия"),
                ),
            ),
        )

        assertTrue(result.items.isEmpty())
        assertEquals(null, result.nextPage)
    }

    @Test
    fun emptyUnfilteredOrSortOnlyFirstPageStillFails() = runTest {
        val queries = listOf(
            CatalogQuery(),
            CatalogQuery(
                category = CatalogCategory.ALL_MOVIES,
                filters = CatalogBrowseFilters(defaultSort = CatalogDefaultSort.DATE),
            ),
        )

        queries.forEach { query ->
            val transport = RecordingCatalogTransport(fixture("catalog_controls.html")).apply {
                emptyAllResponses = true
            }
            val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
            val error = runCatching { repository.loadPage(ORIGIN, query) }.exceptionOrNull()
            assertTrue(error is CatalogParseException)
        }
    }

    @Test
    fun emptyLastAppendPageIsAcceptedAndStopsPagination() = runTest {
        val transport = RecordingCatalogTransport(fixture("catalog_controls.html"))
        val repository = HtmlCatalogRepository(transport, KinogoHtmlParser())
        val query = CatalogQuery(
            category = CatalogCategory.ALL_MOVIES,
            filters = CatalogBrowseFilters(defaultSort = CatalogDefaultSort.DATE),
        )

        repository.loadPage(ORIGIN, query)
        transport.emptyGetPaths += "/filmy/page/2/"
        val result = repository.loadPage(ORIGIN, query.copy(page = 2))

        assertTrue(result.items.isEmpty())
        assertEquals(null, result.nextPage)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/catalog/$name")).readText()

    private fun form(field: String, value: String? = null): Map<String, String> = linkedMapOf(
        "xsort" to "1",
        "xs_field" to field,
    ).apply {
        if (value != null) put("xs_value", value)
    }

    private companion object {
        const val ORIGIN = "https://kinogo.parts"
    }
}

private sealed interface CatalogTransportCall {
    val path: String
}

private data class GetCall(override val path: String) : CatalogTransportCall

private data class PostCall(
    override val path: String,
    val form: Map<String, String>,
) : CatalogTransportCall

private class RecordingCatalogTransport(
    private val html: String,
) : CatalogFilterHtmlTransport {
    val calls = mutableListOf<CatalogTransportCall>()
    val emptyGetPaths = mutableSetOf<String>()
    val postFailuresAfterMutation = mutableMapOf<String, Int>()
    var emptyAllResponses: Boolean = false
    var honorXSortCommands: Boolean = true
    var changeSessionOnNextGet: Boolean = false
    var resetFiltersOnNextGet: Boolean = false
    private var epoch: Long = 0L
    private var activeFilters = DEFAULT_FILTERS

    override fun sessionEpoch(rawOrigin: String): Long = epoch

    fun replaceSession() {
        epoch++
        activeFilters = DEFAULT_FILTERS
    }

    override suspend fun get(rawOrigin: String, rawRelativePath: String): HtmlResponse {
        calls += GetCall(rawRelativePath)
        if (changeSessionOnNextGet) {
            changeSessionOnNextGet = false
            replaceSession()
        }
        if (resetFiltersOnNextGet) {
            resetFiltersOnNextGet = false
            activeFilters = DEFAULT_FILTERS
        }
        return response(
            origin = rawOrigin,
            path = rawRelativePath,
            empty = emptyAllResponses || rawRelativePath in emptyGetPaths,
        )
    }

    override suspend fun postCatalogForm(
        rawOrigin: String,
        rawRelativePath: String,
        form: Map<String, String>,
    ): HtmlResponse {
        calls += PostCall(rawRelativePath, LinkedHashMap(form))
        if (honorXSortCommands) applyXSort(rawRelativePath, form)
        val field = form["xs_field"]
        val failuresRemaining = field?.let(postFailuresAfterMutation::get) ?: 0
        if (field != null && failuresRemaining > 0) {
            postFailuresAfterMutation[field] = failuresRemaining - 1
            throw CatalogNetworkException(SocketTimeoutException("timeout after POST mutation"))
        }
        return response(rawOrigin, rawRelativePath, empty = emptyAllResponses)
    }

    private fun applyXSort(path: String, form: Map<String, String>) {
        when (form["xs_field"]) {
            "clearallfields" -> {
                activeFilters = if (path == "/") DEFAULT_FILTERS else CatalogBrowseFilters()
            }
            "defaultsort" -> {
                val sort = form["xs_value"]?.let(CatalogDefaultSort::fromWireValue) ?: return
                activeFilters = if (activeFilters.defaultSort == sort) {
                    activeFilters.copy(
                        sortDirection = if (
                            activeFilters.sortDirection == CatalogSortDirection.DESC
                        ) {
                            CatalogSortDirection.ASC
                        } else {
                            CatalogSortDirection.DESC
                        },
                    )
                } else {
                    activeFilters.copy(
                        defaultSort = sort,
                        sortDirection = CatalogSortDirection.DESC,
                    )
                }
            }
            "podborki" -> form["xs_value"]?.let { value ->
                activeFilters = activeFilters.copy(
                    collection = CatalogFilterOption(value, value),
                )
            }
            "year" -> form["xs_value"]?.toIntOrNull()?.let { year ->
                activeFilters = activeFilters.copy(year = year)
            }
            "country" -> form["xs_value"]?.let { value ->
                activeFilters = activeFilters.copy(
                    country = CatalogFilterOption(value, value),
                )
            }
        }
    }

    private fun response(origin: String, path: String, empty: Boolean) = HtmlResponse(
        requestedOrigin = origin,
        resolvedOrigin = origin,
        relativePath = path,
        statusCode = 200,
        body = renderHtml(empty),
    )

    private fun renderHtml(empty: Boolean): String {
        val document = Jsoup.parse(html)
        if (empty) {
            document.select("#dle-content article.shortStory").remove()
            document.select(".pagiNation").remove()
        }

        document.select(".xsort-ul[data-field] li[data-val]").forEach { item ->
            item.removeClass("current").removeClass("xasc").removeClass("xdesc")
        }
        val sortValue = activeFilters.defaultSort?.wireValue
        document.selectFirst(".xsort-ul[data-field=defaultsort]")
            ?.select("li[data-val]")
            ?.firstOrNull { it.attr("data-val") == sortValue }
            ?.addClass("current")
            ?.addClass(
                if (activeFilters.sortDirection == CatalogSortDirection.ASC) "xasc" else "xdesc",
            )
        document.select(".xsort-selected span").forEach { selected ->
            selected.removeClass("xasc").removeClass("xdesc").addClass(
                if (activeFilters.sortDirection == CatalogSortDirection.ASC) "xasc" else "xdesc",
            )
        }
        markCurrent("podborki", activeFilters.collection?.value, document)
        markCurrent("year", activeFilters.year?.toString(), document)
        markCurrent("country", activeFilters.country?.value, document)
        return document.outerHtml()
    }

    private fun markCurrent(field: String, value: String?, document: org.jsoup.nodes.Document) {
        document.selectFirst(".xsort-ul[data-field=$field]")
            ?.select("li[data-val]")
            ?.firstOrNull { it.attr("data-val") == value }
            ?.addClass("current")
    }

    private companion object {
        val DEFAULT_FILTERS = CatalogBrowseFilters(
            defaultSort = CatalogDefaultSort.TOP_3_DAYS,
            sortDirection = CatalogSortDirection.DESC,
        )
    }
}
