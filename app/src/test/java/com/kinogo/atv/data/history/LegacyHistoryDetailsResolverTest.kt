package com.kinogo.atv.data.history

import com.kinogo.atv.data.catalog.CatalogRepository
import com.kinogo.atv.data.catalog.ParsedCatalogPage
import com.kinogo.atv.data.catalog.ParsedContentPage
import com.kinogo.atv.data.catalog.SessionHttpResponse
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.CatalogQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyHistoryDetailsResolverTest {
    @Test
    fun `410 suggestion resolves confirmed 35182 canonical path through repository`() = runTest {
        val repository = RecordingRepository()
        val resolver = LegacyHistoryDetailsResolver(
            rawProbe = { origin, path ->
                assertEquals("https://kinogo.example", origin)
                assertEquals("/serialy/35182-history.html", path)
                rawResponse(
                    status = 410,
                    path = path,
                    body = """
                        <html><body>
                          <div class="info">
                            <a href="/serialy/35182-ierihon.html">Возможно вы искали →</a>
                          </div>
                        </body></html>
                    """.trimIndent(),
                )
            },
            repository = repository,
        )

        val parsed = resolver.resolve("https://kinogo.example", "35182")

        assertEquals("/serialy/35182-ierihon.html", repository.requestedItem?.relativePath)
        assertEquals("35182", repository.requestedItem?.id)
        assertEquals("/serialy/35182-ierihon.html", parsed.catalogItem.relativePath)
    }

    @Test
    fun `200 probe uses final same origin redirect path reported by session client`() = runTest {
        val repository = RecordingRepository()
        val resolver = LegacyHistoryDetailsResolver(
            rawProbe = { _, requestedPath ->
                assertEquals("/serialy/35182-history.html", requestedPath)
                rawResponse(
                    status = 200,
                    path = "/serialy/35182-ierihon.html",
                )
            },
            repository = repository,
        )

        resolver.resolve("https://kinogo.example", "35182")

        assertEquals("/serialy/35182-ierihon.html", repository.requestedItem?.relativePath)
    }

    @Test
    fun `suggestion parser accepts only exact id root relative content paths`() {
        assertEquals(
            "/serialy/35182-ierihon.html",
            suggestion("/serialy/35182-ierihon.html"),
        )
        assertNull(suggestion("/serialy/99999-ierihon.html"))
        assertNull(suggestion("/serialy/351820-ierihon.html"))
        assertNull(suggestion("https://kinogo.example/serialy/35182-ierihon.html"))
        assertNull(suggestion("https://evil.example/serialy/35182-ierihon.html"))
        assertNull(suggestion("//evil.example/serialy/35182-ierihon.html"))
        assertNull(suggestion("/serialy/../serialy/35182-ierihon.html"))
        assertNull(suggestion("/serialy/%2e%2e/35182-ierihon.html"))
        assertNull(suggestion("/serialy/35182-ierihon.html?from=history"))
        assertNull(suggestion("/serialy/35182-ierihon.html#player"))
    }

    @Test
    fun `suggestion parser accepts only known content root directories`() {
        listOf(
            "filmy",
            "serialy",
            "multfilmy",
            "multserialy",
            "anime",
            "anime-serialy",
        ).forEach { root ->
            assertEquals(
                "/$root/35182-title.html",
                suggestion("/$root/35182-title.html"),
            )
        }

        assertNull(suggestion("/engine/ajax/35182-title.html"))
        assertNull(suggestion("/favorites/35182-title.html"))
        assertNull(suggestion("/serialy-other/35182-title.html"))
    }

    @Test
    fun `links outside info hint are ignored`() {
        val html = """
            <a href="/serialy/35182-attacker-controlled.html">Реклама</a>
            <div class="other">
              <a href="/serialy/35182-wrong-container.html">Другая ссылка</a>
            </div>
        """.trimIndent()

        assertNull(LegacyHistorySuggestionParser.findPath(html, "35182"))
    }

    private fun suggestion(href: String): String? =
        LegacyHistorySuggestionParser.findPath(
            html = """<div class="info"><a href="$href">Подсказка</a></div>""",
            contentId = "35182",
        )

    private fun rawResponse(
        status: Int,
        path: String,
        body: String = "",
    ) = SessionHttpResponse(
        requestedOrigin = "https://kinogo.example",
        resolvedOrigin = "https://kinogo.example",
        relativePath = path,
        statusCode = status,
        body = body,
    )

    private class RecordingRepository : CatalogRepository {
        var requestedItem: CatalogItem? = null

        override suspend fun loadPage(
            origin: String,
            query: CatalogQuery,
        ): ParsedCatalogPage = error("Not used")

        override suspend fun loadDetails(
            origin: String,
            item: CatalogItem,
        ): ParsedContentPage {
            requestedItem = item
            assertTrue(item.id == "35182")
            return ParsedContentPage(
                catalogItem = item.copy(title = "Иерихон"),
                description = "Описание",
                countries = emptyList(),
                genres = emptyList(),
                directors = emptyList(),
                cast = emptyList(),
                durationMinutes = null,
                metadata = emptyMap(),
                playerEmbeds = emptyList(),
            )
        }
    }
}
