package com.kinogo.atv.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefetchPolicyTest {
    private val policy = PrefetchPolicy(rowsBeforeEnd = 2)

    @Test
    fun `paging distance is two complete grid rows`() {
        assertEquals(10, policy.pagingPrefetchDistance(columns = 5))
        assertEquals(12, policy.pagingPrefetchDistance(columns = 6))
    }

    @Test
    fun `request begins on penultimate loaded row`() {
        assertFalse(
            policy.shouldRequestNextPage(
                anchorItemIndex = 39,
                loadedItemCount = 50,
                columns = 5,
            ),
        )
        assertTrue(
            policy.shouldRequestNextPage(
                anchorItemIndex = 40,
                loadedItemCount = 50,
                columns = 5,
            ),
        )
    }

    @Test
    fun `partial last row uses row math instead of item remainder`() {
        assertFalse(policy.shouldRequestNextPage(34, loadedItemCount = 43, columns = 5))
        assertTrue(policy.shouldRequestNextPage(35, loadedItemCount = 43, columns = 5))
    }

    @Test
    fun `request is suppressed during append and after end`() {
        assertFalse(
            policy.shouldRequestNextPage(
                45,
                loadedItemCount = 50,
                columns = 5,
                appendInProgress = true,
            ),
        )
        assertFalse(
            policy.shouldRequestNextPage(
                45,
                loadedItemCount = 50,
                columns = 5,
                endReached = true,
            ),
        )
    }
}
