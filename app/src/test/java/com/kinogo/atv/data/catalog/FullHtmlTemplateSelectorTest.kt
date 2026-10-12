package com.kinogo.atv.data.catalog

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullHtmlTemplateSelectorTest {
    @Test
    fun selectsOnlyOnceForAllDocumentsAndReselectsAfterCookieClear() = runTest {
        val selector = FullHtmlTemplateSelector()
        val cookies = OriginCookieStore()
        var calls = 0
        repeat(6) { selector.ensure(ORIGIN, { cookies.clearGeneration(ORIGIN) }) { calls++ } }
        cookies.absorb(ORIGIN, listOf("PHPSESSID=fixture; Path=/; Secure"))
        selector.ensure(ORIGIN, { cookies.clearGeneration(ORIGIN) }) { calls++ }
        assertEquals(1, calls)
        cookies.clear(ORIGIN)
        selector.ensure(ORIGIN, { cookies.clearGeneration(ORIGIN) }) { calls++ }
        assertEquals(2, calls)
    }

    @Test
    fun originsHaveIndependentTemplateSelections() = runTest {
        val selector = FullHtmlTemplateSelector()
        val selected = mutableListOf<String>()
        listOf(ORIGIN, "https://other.example", ORIGIN).forEach { origin ->
            selector.ensure(origin, { 0L }) { selected += origin }
        }
        assertEquals(listOf(ORIGIN, "https://other.example"), selected)
    }

    @Test
    fun concurrentDocumentLoadsShareOneSelection() = runTest {
        val selector = FullHtmlTemplateSelector()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { selector.ensure(ORIGIN, { 0L }) { calls++; entered.complete(Unit); release.await() } }
        entered.await()
        val second = async { selector.ensure(ORIGIN, { 0L }) { calls++ } }
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, calls)
    }

    @Test
    fun failureAndCancellationNeverCacheSelection() = runTest {
        val selector = FullHtmlTemplateSelector()
        listOf(IOException("fixture failure"), CancellationException("fixture cancellation")).forEach { error ->
            assertTrue(runCatching { selector.ensure(ORIGIN, { 0L }) { throw error } }.exceptionOrNull() === error)
        }
        var calls = 0
        selector.ensure(ORIGIN, { 0L }) { calls++ }
        assertEquals(1, calls)
    }

    @Test
    fun cookieClearDuringSelectionRejectsTheStaleResult() = runTest {
        val selector = FullHtmlTemplateSelector()
        var generation = 0L
        val error = runCatching { selector.ensure(ORIGIN, { generation }) { generation++ } }.exceptionOrNull()
        assertTrue(error is CatalogSessionChangedException)
        var calls = 0
        selector.ensure(ORIGIN, { generation }) { calls++ }
        assertEquals(1, calls)
    }

    private companion object {
        const val ORIGIN = "https://kinogo.example"
    }
}
