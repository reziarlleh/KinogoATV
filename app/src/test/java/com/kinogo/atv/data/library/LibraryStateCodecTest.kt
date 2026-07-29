package com.kinogo.atv.data.library

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.LibraryRecord
import com.kinogo.atv.domain.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateCodecTest {
    @Test
    fun recordsRoundTripStatusAndIndependentFavorite() {
        val record = LibraryRecord(
            item = CatalogItem("115576", "/serialy/115576-test.html", "Тестовый сериал"),
            status = WatchStatus.WATCHING,
            favorite = true,
            updatedAtEpochMs = 1234L,
        )

        assertEquals(listOf(record), LibraryStateCodec.decodeRecords(LibraryStateCodec.encodeRecords(listOf(record))))
    }

    @Test
    fun pendingMutationsAreDeduplicatedByContentAndDimension() {
        val old = PendingLibraryMutation("115576", LibraryMutationKind.STATUS, "watch", 10L)
        val latest = old.copy(value = "done", updatedAtEpochMs = 20L)
        val favorite = PendingLibraryMutation("115576", LibraryMutationKind.FAVORITE, "true", 15L)

        val decoded = LibraryStateCodec.decodeMutations(
            LibraryStateCodec.encodeMutations(listOf(old, latest, favorite)),
        )

        assertEquals(2, decoded.size)
        assertTrue(decoded.contains(latest))
        assertTrue(decoded.contains(favorite))
    }
}
