package com.kinogo.atv

import com.kinogo.atv.data.catalog.CatalogHttpStatusException
import com.kinogo.atv.data.catalog.CatalogRepository
import com.kinogo.atv.data.catalog.ParsedCatalogPage
import com.kinogo.atv.data.catalog.ParsedContentPage
import com.kinogo.atv.data.catalog.SessionHttpResponse
import com.kinogo.atv.data.history.LegacyHistoryDetailsResolver
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.CatalogQuery
import com.kinogo.atv.domain.PlaybackSelection
import com.kinogo.atv.domain.WatchProgress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class LegacyHistoryLookupItemTest {
    @Test
    fun `numeric legacy id maps only to the constrained resolver probe`() {
        val item = legacyHistoryLookupItem("35182")

        assertEquals("35182", item?.id)
        assertEquals("/serialy/35182-history.html", item?.relativePath)
        assertEquals("Загрузка карточки…", item?.title)
    }

    @Test
    fun `non numeric and non positive ids cannot create a request route`() {
        assertNull(legacyHistoryLookupItem("../admin"))
        assertNull(legacyHistoryLookupItem("35182&do=evil"))
        assertNull(legacyHistoryLookupItem("0"))
        assertNull(legacyHistoryLookupItem("-1"))
        assertNull(legacyHistoryLookupItem("+35182"))
        assertNull(legacyHistoryLookupItem("035182"))
    }

    @Test
    fun `persisted snapshot takes precedence over a legacy lookup placeholder`() {
        val snapshot = CatalogItem(
            id = "35182",
            relativePath = "/serialy/example-35182.html",
            title = "Реальное название",
            posterUrl = "https://cdn.example.org/35182.webp",
        )
        val progress = WatchProgress(
            selection = PlaybackSelection(
                contentId = "35182",
                voiceId = "voice",
                qualityId = "auto",
            ),
            positionMs = 120_000,
            durationMs = 2_400_000,
            updatedAtEpochMs = 42,
            contentSnapshot = snapshot,
        )

        assertEquals(snapshot, progress.historyCatalogItem())
    }

    @Test
    fun `playback document url uses canonical path returned by legacy resolution`() {
        val legacyProbe = requireNotNull(legacyHistoryLookupItem("35182"))
        val canonicalItem = legacyProbe.copy(
            relativePath = "/serialy/35182-ierihon.html",
            title = "Иерихон",
        )
        val freshDetails = ParsedContentPage(
            catalogItem = canonicalItem,
            description = "Описание",
            countries = emptyList(),
            genres = emptyList(),
            directors = emptyList(),
            cast = emptyList(),
            durationMinutes = null,
            metadata = emptyMap(),
            playerEmbeds = emptyList(),
        )

        assertEquals(
            "https://kinogo.example/serialy/35182-ierihon.html",
            resolvedPlaybackDocumentUrl("https://kinogo.example", freshDetails),
        )
    }

    @Test
    fun `stale canonical history snapshot falls back through constrained resolver`() = runTest {
        val staleItem = CatalogItem(
            id = "35182",
            relativePath = "/serialy/35182-old-slug.html",
            title = "Иерихон",
        )
        val requestedPaths = mutableListOf<String>()
        val repository = object : CatalogRepository {
            override suspend fun loadPage(
                origin: String,
                query: CatalogQuery,
            ): ParsedCatalogPage = error("Not used")

            override suspend fun loadDetails(
                origin: String,
                item: CatalogItem,
            ): ParsedContentPage {
                requestedPaths += item.relativePath
                if (item.relativePath == staleItem.relativePath) {
                    throw CatalogHttpStatusException(410)
                }
                return details(item.copy(title = "Иерихон"))
            }
        }
        val resolver = LegacyHistoryDetailsResolver(
            rawProbe = { _, path ->
                assertEquals("/serialy/35182-history.html", path)
                SessionHttpResponse(
                    requestedOrigin = "https://kinogo.example",
                    resolvedOrigin = "https://kinogo.example",
                    relativePath = path,
                    statusCode = 410,
                    body =
                        """<div class="info"><a href="/serialy/35182-ierihon.html">""" +
                            "Подсказка</a></div>",
                )
            },
            repository = repository,
        )

        val parsed = loadCatalogDetailsWithLegacyFallback(
            origin = "https://kinogo.example",
            item = staleItem,
            repository = repository,
            legacyResolver = resolver,
        )

        assertEquals(
            listOf(
                "/serialy/35182-old-slug.html",
                "/serialy/35182-ierihon.html",
            ),
            requestedPaths,
        )
        assertEquals("/serialy/35182-ierihon.html", parsed.catalogItem.relativePath)
    }

    @Test
    fun `legacy fallback does not mask unrelated http status`() = runTest {
        val expected = CatalogHttpStatusException(500)
        val item = CatalogItem(
            id = "35182",
            relativePath = "/serialy/35182-ierihon.html",
            title = "Иерихон",
        )
        val repository = object : CatalogRepository {
            override suspend fun loadPage(
                origin: String,
                query: CatalogQuery,
            ): ParsedCatalogPage = error("Not used")

            override suspend fun loadDetails(
                origin: String,
                item: CatalogItem,
            ): ParsedContentPage = throw expected
        }
        val resolver = LegacyHistoryDetailsResolver(
            rawProbe = { _, _ -> error("Resolver must not run for HTTP 500") },
            repository = repository,
        )

        try {
            loadCatalogDetailsWithLegacyFallback(
                origin = "https://kinogo.example",
                item = item,
                repository = repository,
                legacyResolver = resolver,
            )
            fail("Expected original HTTP error")
        } catch (actual: CatalogHttpStatusException) {
            assertSame(expected, actual)
        }
    }

    private fun details(item: CatalogItem) = ParsedContentPage(
        catalogItem = item,
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
