package com.kinogo.atv.data.history

import com.kinogo.atv.domain.CatalogItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryMetadataRefresherTest {
    private fun item(id: String) = CatalogItem(id, "/serialy/$id-series.html", "Сериал $id")

    @Test
    fun `each history opening loads fresh metadata and deduplicates content`() = runTest {
        var reads = 0
        val refresher = HistoryMetadataRefresher(load = {
            it.copy(episodeBadge = "1 сезон ${++reads} серия")
        })
        val results = mutableListOf<CatalogItem?>()
        repeat(2) {
            refresher.refresh(listOf(item("42"), item("42"))) { _, fresh -> results += fresh }
        }

        assertEquals(2, reads)
        assertEquals(listOf("1 сезон 1 серия", "1 сезон 2 серия"), results.map { it?.episodeBadge })
    }

    @Test
    fun `reads are bounded and results arrive without waiting for slow cards`() = runTest {
        val slow = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val results = mutableMapOf<String, CatalogItem?>()
        val refresher = HistoryMetadataRefresher(load = {
            active++
            maximum = maxOf(maximum, active)
            try {
                if (it.id == "1") slow.await() else delay(10)
                it.copy(episodeBadge = "2 сезон 4 серия")
            } finally {
                active--
            }
        })
        val job = launch { refresher.refresh((1..5).map { item(it.toString()) }, results::put) }
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(2, maximum)
        assertEquals(setOf("2", "3", "4", "5"), results.keys)
        slow.complete(Unit)
        job.join()
        assertEquals(5, results.size)
    }

    @Test
    fun `failure timeout and wrong content do not poison successful reads`() = runTest {
        val results = mutableMapOf<String, CatalogItem?>()
        val refresher = HistoryMetadataRefresher(timeoutMs = 100, load = {
            when (it.id) {
                "1" -> error("Unavailable")
                "2" -> { delay(101); it }
                "3" -> item("other")
                else -> it.copy(episodeBadge = "3 сезон 8 серия")
            }
        })
        refresher.refresh((1..4).map { item(it.toString()) }, results::put)

        assertEquals(setOf("1", "2", "3", "4"), results.keys)
        assertNull(results["1"])
        assertNull(results["2"])
        assertNull(results["3"])
        assertEquals("3 сезон 8 серия", results["4"]?.episodeBadge)
    }

    @Test
    fun `leaving history cancels pending reads without publishing stale results`() = runTest {
        var cancelledReads = 0
        val results = mutableListOf<String>()
        val refresher = HistoryMetadataRefresher(load = {
            try { delay(1_000); it } finally { cancelledReads++ }
        })
        val job = launch { refresher.refresh((1..4).map { item(it.toString()) }) { id, _ -> results += id } }
        runCurrent()
        job.cancel()
        job.join()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertEquals(2, cancelledReads)
        assertTrue(results.isEmpty())
    }
}
