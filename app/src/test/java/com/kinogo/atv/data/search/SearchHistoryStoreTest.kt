package com.kinogo.atv.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryStoreTest {
    @Test
    fun `newest query is first and duplicate moves to front`() {
        val first = SearchHistoryCollection.record(
            current = listOf("Дюна", "Матрица", "Чужой"),
            rawQuery = "  матрица  ",
        )

        assertEquals(listOf("матрица", "Дюна", "Чужой"), first)
    }

    @Test
    fun `history is bounded and whitespace is normalized`() {
        val current = (1..SearchHistoryCollection.MAX_ITEMS).map { "Запрос $it" }

        val updated = SearchHistoryCollection.record(current, " Новый\n   запрос ")

        assertEquals(SearchHistoryCollection.MAX_ITEMS, updated.size)
        assertEquals("Новый запрос", updated.first())
        assertEquals("Запрос 9", updated.last())
    }

    @Test
    fun `codec keeps ordering and ignores malformed duplicates`() {
        val decoded = SearchHistoryCodec.decode("Новый\nстарый\nНОВЫЙ\n\nтретий")

        assertEquals(listOf("Новый", "старый", "третий"), decoded)
        assertEquals(decoded, SearchHistoryCodec.decode(SearchHistoryCodec.encode(decoded)))
    }
}
