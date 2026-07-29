package com.kinogo.atv.data.favorites

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentRatings
import com.kinogo.atv.domain.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteCodecTest {
    @Test
    fun `codec round trips unicode metadata and nullable fields`() {
        val entries = listOf(
            FavoriteEntry(
                item = CatalogItem(
                    id = "фильм-42",
                    relativePath = "/filmy/42-test.html",
                    title = "Тестовый фильм\tс символами",
                    originalTitle = "Original / Title",
                    posterUrl = "https://kinogo.parts/uploads/poster 42.jpg",
                    year = 2026,
                    type = ContentType.MOVIE,
                    ratings = ContentRatings(kinopoisk = 7.8, imdb = 7.1),
                    qualityBadge = "WEB-DL 1080p",
                ),
                addedAtEpochMs = 1_234L,
            ),
        )

        assertEquals(entries, FavoriteCodec.decode(FavoriteCodec.encode(entries)))
    }

    @Test
    fun `decoder skips a malformed line without losing valid favorites`() {
        val valid = FavoriteEntry(item("one"), 20L)
        val payload = "broken\tline\n${FavoriteCodec.encode(listOf(valid))}"

        assertEquals(listOf(valid), FavoriteCodec.decode(payload))
    }

    @Test
    fun `collection keeps newest duplicate and orders recent favorites first`() {
        val normalized = FavoriteCollection.normalize(
            listOf(
                FavoriteEntry(item("one", title = "Old"), 10L),
                FavoriteEntry(item("two"), 20L),
                FavoriteEntry(item("one", title = "New"), 30L),
            ),
        )

        assertEquals(listOf("one", "two"), normalized.map { it.item.id })
        assertEquals("New", normalized.first().item.title)
    }

    @Test
    fun `toggle adds and then removes the same content id`() {
        val catalogItem = item("one")
        val added = FavoriteCollection.toggle(emptyList(), catalogItem, 100L)
        val removed = FavoriteCollection.toggle(added, catalogItem.copy(title = "Updated"), 200L)

        assertEquals(listOf("one"), added.map { it.item.id })
        assertTrue(removed.isEmpty())
    }

    private fun item(id: String, title: String = "Title $id") = CatalogItem(
        id = id,
        relativePath = "/filmy/$id.html",
        title = title,
        type = ContentType.MOVIE,
    )
}
