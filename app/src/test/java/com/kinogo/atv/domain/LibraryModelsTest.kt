package com.kinogo.atv.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelsTest {
    private val item = CatalogItem("115576", "/serialy/115576-test.html", "Тест")

    @Test
    fun `not watched clears status sections and never creates its own library section`() {
        val untracked = LibraryRecord(item = item, status = null, favorite = false)
        val favoriteWithoutStatus = untracked.copy(favorite = true)

        assertFalse(LibraryFilter.ALL.accepts(untracked))
        assertFalse(LibraryFilter.FAVORITES.accepts(untracked))
        assertFalse(LibraryFilter.WATCHING.accepts(untracked))
        assertTrue(LibraryFilter.ALL.accepts(favoriteWithoutStatus))
        assertTrue(LibraryFilter.FAVORITES.accepts(favoriteWithoutStatus))
        assertTrue(WatchStatus.fromFolder("0") == null)
    }
}
